"""Exercise bootstrap with local fixtures, not a cached Maven installation."""
import hashlib
import io
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from zipfile import ZipFile, ZipInfo


class MavenWrapperBootstrapTest(unittest.TestCase):
    def bootstrap(self, failures=0, corrupt_checksum=False):
        archive = io.BytesIO()
        with ZipFile(archive, "w") as fixture:
            executable = ZipInfo("apache-maven-3.9.12/bin/mvn")
            executable.create_system = 3
            executable.external_attr = 0o100755 << 16
            fixture.writestr(executable, "#!/bin/sh\necho bootstrap-fixture-ready\n")
        payload = archive.getvalue()
        requests = []

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                requests.append(self.path)
                self.send_response(503 if len(requests) <= failures else 200)
                self.end_headers()
                if len(requests) > failures:
                    self.wfile.write(payload)

            def log_message(self, *_args):
                pass

        with tempfile.TemporaryDirectory(prefix="belsen-wrapper-test-") as directory:
            root = Path(directory)
            shutil.copyfile(Path(__file__).resolve().parents[2] / "backend/mvnw", root / "mvnw")
            properties = root / ".mvn/wrapper"
            properties.mkdir(parents=True)
            checksum = "0" * 64 if corrupt_checksum else hashlib.sha256(payload).hexdigest()
            with ThreadingHTTPServer(("127.0.0.1", 0), Handler) as server:
                thread = threading.Thread(target=server.serve_forever, daemon=True)
                thread.start()
                try:
                    (properties / "maven-wrapper.properties").write_text(
                        f"distributionUrl=http://127.0.0.1:{server.server_port}/apache-maven-3.9.12-bin.zip\n"
                        f"distributionSha256Sum={checksum}\n"
                    )
                    env = {k: v for k, v in os.environ.items() if not k.startswith("MVNW_")}
                    env.update(MAVEN_USER_HOME=str(root / "cache"), MVNW_VERBOSE="true")
                    result = subprocess.run(["sh", str(root / "mvnw"), "--version"],
                                            env=env, capture_output=True, text=True, timeout=30)
                finally:
                    server.shutdown()
                    thread.join()
        return result, requests

    def test_cold_bootstrap_retries_transient_http_failures(self):
        result, requests = self.bootstrap(failures=2)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(len(requests), 3)
        self.assertIn("Found curl ... using curl", result.stdout)
        self.assertIn("bootstrap-fixture-ready", result.stdout)

    def test_checksum_mismatch_never_executes_download(self):
        result, requests = self.bootstrap(corrupt_checksum=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(len(requests), 1)
        self.assertIn("Failed to validate Maven distribution SHA-256", result.stderr)
        self.assertNotIn("bootstrap-fixture-ready", result.stdout)
