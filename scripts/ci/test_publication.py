import unittest
from check_publication import findings


class PublicationGuardTest(unittest.TestCase):
    def test_provider_tokens_report_locations_without_values(self):
        token = b"ghp_" + b"A" * 36
        self.assertEqual(findings("sample.txt", b"first\n" + token), [(2, "provider token")])

    def test_private_key_body_and_dump_are_rejected(self):
        key = b"-----BEGIN PRIVATE KEY-----\n" + b"A" * 64 + b"\n-----END PRIVATE KEY-----"
        self.assertIn((1, "embedded private key"), findings("sample.txt", key))
        self.assertIn((1, "database dump"), findings("sample.sql", b"-- PostgreSQL database dump\n"))

    def test_artifact_and_secret_paths_are_rejected(self):
        for name in [".env", ".env.production", "backend/target/file.txt", "archive.dump", "private.key", "frontend/node_modules/a.js"]:
            self.assertIn((0, "sensitive or generated path"), findings(name, b""))

    def test_examples_and_generated_key_source_are_allowed(self):
        self.assertEqual(findings(".env.example", b"BELSEN_DATABASE_PASSWORD=\n"), [])
        self.assertEqual(findings("Fixture.java", b'"-----BEGIN PRIVATE KEY-----\\n" + generated'), [])

    def test_url_fixture_exception_is_exact_and_does_not_hide_other_credentials(self):
        fixture = "showcase/build.test.mjs"
        self.assertEqual(findings(fixture, b"https://" + b"user:secret@demo.example.org"), [])
        self.assertIn((1, "credential-bearing URL"), findings("sample.txt", b"https://" + b"user:secret@demo.example.org"))
        self.assertIn((1, "credential-bearing URL"), findings(fixture, b"https://" + b"other:password@demo.example.org"))


if __name__ == "__main__":
    unittest.main()
