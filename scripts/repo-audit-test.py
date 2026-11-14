"""Exercise the publication check on disposable files, without services or devices."""
import pathlib
import subprocess
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).with_name("repo-audit.py")


class PublicationAuditTest(unittest.TestCase):
    def audit(self, content, suffix=".md"):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory, "sample" + suffix)
            path.write_text(content)
            return subprocess.run(["python3", str(SCRIPT), str(path)], capture_output=True, text=True)

    def test_detects_credentials_without_echoing_them(self):
        for secret in ["sk-" + "x" * 40, "ghp_" + "a" * 36,
                       '"token": "' + "abc123" * 8 + '"',
                       "Authorization: Bearer " + "abc123" * 8,
                       "-----BEGIN " + "OPENSSH PRIVATE KEY-----"]:
            with self.subTest(kind=secret[:8]):
                result = self.audit(secret)
                self.assertEqual(1, result.returncode, result.stderr)
                self.assertNotIn(secret, result.stdout + result.stderr)
                self.assertIn("sample.md:1", result.stdout)

    def test_detects_personal_data(self):
        for content in ["192.168." + "12.34", "10.42." + "0.2", "172.31." + "4.5",
                        "person@" + "private-company.fr", "this@" + "private-company.fr",
                        "/home/" + "private-person/file"]:
            with self.subTest(content=content):
                self.assertEqual(1, self.audit(content).returncode)

    def test_allows_documented_examples_and_local_addresses(self):
        content = '\n'.join(['http://127.0.0.1:47600', '203.0.113.1',
                             'user@example.invalid', 'bench@regie.invalid', 'someone@example.com',
                             'this@Fixture.sessions', 'this@until.collect',
                             '"token": "<redacted>"', 'Authorization: Bearer ${token}',
                             'noreply@anthropic.com', '/home/USER/file', '`/home/USER`'])
        result = self.audit(content)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_mixed_line_does_not_hide_a_real_address(self):
        result = self.audit('user@example.invalid person@' + 'private-company.fr')
        self.assertEqual(1, result.returncode)

    def test_missing_input_fails_closed(self):
        result = subprocess.run(["python3", str(SCRIPT), "/nonexistent/regie-audit-input"], capture_output=True)
        self.assertNotEqual(0, result.returncode)


if __name__ == "__main__":
    unittest.main()
