import io
from pathlib import Path
import sys
import tempfile
import unittest
from urllib.error import HTTPError

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from monique import Client, MoniqueError, base_url, credential_file


class StubOpener:
    def __init__(self):
        self.body = b'{"name":"MONIQUE","executionEnabled":false}'
        self.last = None
        self.fail = None

    def open(self, req, timeout):
        self.last = req
        if self.fail:
            raise self.fail
        return io.BytesIO(self.body)


class ClientTests(unittest.TestCase):
    def setUp(self):
        self.opener = StubOpener()
        self.client = Client("http://127.0.0.1:8088", "a" * 32, self.opener)

    def test_auth_and_status(self):
        result = self.client.call("GET", "/api/status")
        self.assertEqual(result["name"], "MONIQUE")
        self.assertEqual(self.opener.last.get_header("Authorization"), "Bearer " + "a" * 32)
        self.assertEqual(self.opener.last.full_url, "http://127.0.0.1:8088/api/status")

    def test_reject_unencrypted_remote_connections(self):
        for url in ("http://example.com", "http://192.0.2.12:8080",
                    "https://user:pass@host.example", "https://host.example/path"):
            with self.subTest(url=url), self.assertRaises(MoniqueError):
                base_url(url)
        self.assertEqual(base_url("https://host.example/"), "https://host.example")

    def test_redirect_is_refused_without_leaking_secret(self):
        self.opener.fail = HTTPError("http://localhost/api/status", 302, "redirect", {}, None)
        with self.assertRaises(MoniqueError) as ctx:
            self.client.call("GET", "/api/status")
        self.assertNotIn("a" * 32, str(ctx.exception))

    def test_external_api_path_is_rejected(self):
        for path in ("/secret", "https://evil.example", "/api/status?x=1"):
            with self.subTest(path=path), self.assertRaises(MoniqueError):
                self.client.call("GET", path)

    def test_invalid_token_and_invalid_json(self):
        with self.assertRaises(MoniqueError):
            Client("http://localhost", "short")
        self.opener.body = b"INVALID"
        with self.assertRaises(MoniqueError):
            self.client.call("GET", "/api/status")

    def test_credential_permissions(self):
        with tempfile.TemporaryDirectory() as folder:
            f = Path(folder, "key")
            f.write_text("top-secret\n")
            f.chmod(0o600)
            self.assertEqual(credential_file(str(f)), "top-secret")
            if sys.platform != "win32":
                f.chmod(0o644)
                with self.assertRaises(MoniqueError):
                    credential_file(str(f))


if __name__ == "__main__":
    unittest.main()
