#!/usr/bin/env python3
"""MONIQUE development CLI for the existing authenticated, planning-only API."""
from __future__ import annotations
import argparse
import getpass
import json
import os
from pathlib import Path
import stat
import sys
from urllib import error, parse, request


class MoniqueError(Exception):
    """An error safe to display without exposing tokens or upstream bodies."""


def base_url(value: str) -> str:
    p = parse.urlsplit(value.strip())
    if (p.scheme not in ("http", "https") or not p.hostname
            or p.username is not None or p.password is not None
            or p.query or p.fragment or p.path not in ("", "/")):
        raise MoniqueError("Use a root MONIQUE URL without embedded credentials or path")
    if p.scheme == "http" and p.hostname.lower() not in ("localhost", "127.0.0.1", "::1"):
        raise MoniqueError("Remote MONIQUE connections require HTTPS")
    return value.strip().rstrip("/")


class NoRedirect(request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Client:
    def __init__(self, url: str, token: str, opener=None) -> None:
        self.url = base_url(url)
        if not token or len(token) < 32:
            raise MoniqueError("MONIQUE_ADMIN_TOKEN must contain at least 32 characters")
        self.token = token
        self.opener = opener or request.build_opener(request.ProxyHandler({}), NoRedirect())

    def call(self, method: str, path: str, payload: object = None) -> object:
        if not path.startswith("/api/") or "?" in path or "#" in path:
            raise MoniqueError("Invalid API path")
        data = json.dumps(payload).encode() if payload is not None else None
        headers = {"Authorization": "Bearer " + self.token, "Accept": "application/json"}
        if data is not None:
            headers["Content-Type"] = "application/json"
        req = request.Request(self.url + path, data=data, headers=headers, method=method)
        try:
            with self.opener.open(req, timeout=20) as response:
                body = response.read(2_000_000)
                return json.loads(body) if body else None
        except error.HTTPError as exc:
            if exc.code in (301, 302, 303, 307, 308):
                raise MoniqueError("Redirect refused to protect the admin token") from None
            if exc.code in (401, 403):
                raise MoniqueError("Admin token rejected (HTTP " + str(exc.code) + ")") from None
            raise MoniqueError("MONIQUE API error HTTP " + str(exc.code)) from None
        except (error.URLError, TimeoutError, OSError):
            raise MoniqueError("Cannot connect to the MONIQUE API") from None
        except (UnicodeError, ValueError):
            raise MoniqueError("Invalid JSON returned from the API") from None


def credential_file(path: str) -> str:
    file = Path(path)
    if not file.is_file():
        raise MoniqueError("Credential file not found")
    if os.name == "posix" and stat.S_IMODE(file.stat().st_mode) & 0o077:
        raise MoniqueError("Credential file must have private permissions (chmod 600)")
    value = file.read_text(encoding="utf-8").strip()
    if not value:
        raise MoniqueError("Credential file is empty")
    return value


def pod_add(client: Client, args: argparse.Namespace) -> None:
    if args.credential_type == "PRIVATE_KEY" and not args.credential_file:
        raise MoniqueError("Use --credential-file for SSH private keys")
    secret = credential_file(args.credential_file) if args.credential_file else getpass.getpass(
        "SSH password (encrypted in MONIQUE): "
    )
    if not secret.strip():
        raise MoniqueError("SSH credential cannot be empty")
    addresses = []
    for item in args.address:
        if "=" not in item:
            raise MoniqueError("Use IP=ROLE, e.g. 192.0.2.10=PRIMARY")
        address, role = item.rsplit("=", 1)
        addresses.append({"address": address, "role": role.upper()})
    payload = {
        "name": args.name, "sshHost": args.host, "sshPort": args.port,
        "sshUsername": args.username, "credentialType": args.credential_type,
        "credential": secret, "addresses": addresses
    }
    obj = client.call("POST", "/api/pods", payload)
    print("Pod registered:", obj["name"], obj["id"])
    print("Inventory only: MONIQUE has not connected to the server.")


def pod_list(client: Client) -> None:
    pods = client.call("GET", "/api/pods")
    if not pods:
        print("No registered Pods")
        return
    for pod in pods:
        print("{id}  {name}  {sshHost}:{sshPort} ({sshUsername})".format(**pod))


def chat(client: Client, prompt: str, provider: str, pod_ids: list[str]) -> None:
    if not prompt.strip():
        raise MoniqueError("Chat prompt cannot be empty")
    result = client.call("POST", "/api/ai/chat", {
        "message": prompt, "provider": provider.upper(), "podIds": pod_ids
    })
    print(result.get("answer", "No answer"))
    if not result.get("executed", False):
        print("\n[Planning only — no infrastructure operations executed]")


def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="monique", description="MONIQUE development CLI")
    p.add_argument("--url", default=os.getenv("MONIQUE_URL", "http://127.0.0.1:8088"))
    sub = p.add_subparsers(dest="command")
    sub.add_parser("status")
    pods = sub.add_parser("pod")
    pod = pods.add_subparsers(dest="action", required=True)
    pod.add_parser("list")
    show = pod.add_parser("show")
    show.add_argument("id")
    add = pod.add_parser("add")
    add.add_argument("--name", required=True)
    add.add_argument("--host", required=True)
    add.add_argument("--port", type=int, default=22)
    add.add_argument("--username", required=True)
    add.add_argument("--credential-type", choices=["PASSWORD", "PRIVATE_KEY"], default="PASSWORD")
    add.add_argument("--credential-file")
    add.add_argument("--address", action="append", default=[])
    delete = pod.add_parser("delete")
    delete.add_argument("id")
    delete.add_argument("--yes", action="store_true")
    ai = sub.add_parser("chat")
    ai.add_argument("message", nargs="+")
    ai.add_argument("--provider", choices=["claude", "codex"], default="claude")
    ai.add_argument("--pod-id", action="append", default=[])
    return p


def interactive(client: Client) -> None:
    print("MONIQUE CLI (development) — /status /pods /help /exit")
    print("AI replies are planning-only; remote execution is disabled.")
    while True:
        try:
            line = input("MONIQUE > ").strip()
            if not line:
                continue
            if line in ("/exit", "/quit", "exit", "quit"):
                return
            if line == "/help":
                print("/status /pods /exit; all other input is sent to Claude planning chat")
            elif line == "/status":
                print(json.dumps(client.call("GET", "/api/status"), indent=2))
            elif line == "/pods":
                pod_list(client)
            else:
                chat(client, line, "CLAUDE", [])
        except (KeyboardInterrupt, EOFError):
            print()
            return
        except MoniqueError as exc:
            print("MONIQUE:", exc, file=sys.stderr)


def main(argv=None) -> int:
    p = parser()
    args = p.parse_args(argv)
    try:
        token = os.getenv("MONIQUE_ADMIN_TOKEN", "")
        if not token and sys.stdin.isatty():
            token = getpass.getpass("MONIQUE admin token: ")
        client = Client(args.url, token)
        if args.command is None:
            interactive(client)
        elif args.command == "status":
            print(json.dumps(client.call("GET", "/api/status"), indent=2))
        elif args.command == "chat":
            chat(client, " ".join(args.message), args.provider, args.pod_id)
        elif args.action == "list":
            pod_list(client)
        elif args.action == "show":
            print(json.dumps(client.call("GET", "/api/pods/" + args.id), indent=2))
        elif args.action == "add":
            pod_add(client, args)
        elif args.action == "delete":
            if not args.yes:
                raise MoniqueError("Inventory deletion requires --yes; remote host is untouched")
            client.call("DELETE", "/api/pods/" + args.id)
            print("Inventory record deleted; remote host untouched.")
        return 0
    except MoniqueError as exc:
        print("MONIQUE:", exc, file=sys.stderr)
        return 1
    except (EOFError, KeyboardInterrupt):
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
