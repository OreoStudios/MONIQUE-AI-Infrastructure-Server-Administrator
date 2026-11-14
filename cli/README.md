# MONIQUE CLI — first implementation

This is a working Python standard-library client for the existing MONIQUE preview API, not the final one-click installer, OAuth membership login, or SSH execution worker.

Requires Python 3.10+, the current MONIQUE stack running (docker compose up --build), and the same MONIQUE_ADMIN_TOKEN used in your .env. Do not pass the token on the command line.

PowerShell:
~~~powershell
$env:MONIQUE_ADMIN_TOKEN = "your-existing-long-admin-token"
python cli/monique.py status
python cli/monique.py pod list
python cli/monique.py chat "Plan a safe backup strategy"
python cli/monique.py
~~~

Bash/zsh:
~~~sh
export MONIQUE_ADMIN_TOKEN="your-existing-long-admin-token"
python3 cli/monique.py status
python3 cli/monique.py pod list
python3 cli/monique.py pod add --name "Test VPS" --host 192.0.2.10 --username test --address 192.0.2.10=PRIMARY
~~~

The CLI uses http://127.0.0.1:8088 (existing frontend proxy) by default. For a remote HTTPS server, use MONIQUE_URL=https://your-monique-host or --url https://your-monique-host **before** the command. Remote HTTP and redirects are rejected to protect the admin bearer token.

For SSH private keys use --credential-type PRIVATE_KEY --credential-file /path/to/private-key (chmod 600 on Unix). Registration saves encrypted inventory metadata only; **no SSH host key is verified, and no SSH connection occurs**. Use disposable test credentials.

Running without a command opens a basic interactive prompt: /status, /pods, /help, /exit; otherwise text is sent to Claude as a planning-only message. The backend does not yet have account membership verification; Claude/OpenAI API keys must be separately configured.

Tests (no running server required):
~~~sh
python -m unittest discover -s cli/tests -v
~~~

Future work: native signed executables, OAuth login and secure token storage, membership verification, SSH host-key pinning, and durable approved execution workers.
