import base64
import json
import unittest
from sign_apk import decode_bundle


class SigningBundleTests(unittest.TestCase):
    def setUp(self):
        # Synthetic bytes only; this is not a private signing key.
        self.fingerprint = "a" * 64
        self.data = {"alias": "tvinbox", "store_password": "a" * 32, "key_password": "b" * 32, "certificate_sha256": self.fingerprint, "keystore_base64": base64.b64encode(b"x" * 2048).decode()}

    def encode(self):
        return base64.b64encode(json.dumps(self.data).encode()).decode()

    def test_accepts_complete_bundle(self):
        self.assertEqual(decode_bundle(self.encode(), self.fingerprint)["keystore"], b"x" * 2048)

    def test_rejects_missing_or_invalid_encoding(self):
        for data in ("", "!not-base64", "x" * 50000):
            with self.subTest(data=data[:20]), self.assertRaises(ValueError):
                decode_bundle(data, self.fingerprint)

    def test_rejects_unknown_or_missing_fields(self):
        self.data["extra"] = "value"
        with self.assertRaises(ValueError):
            decode_bundle(self.encode(), self.fingerprint)
        del self.data["extra"]
        del self.data["alias"]
        with self.assertRaises(ValueError):
            decode_bundle(self.encode(), self.fingerprint)

    def test_rejects_identity_changes(self):
        with self.assertRaises(ValueError):
            decode_bundle(self.encode(), "b" * 64)

    def test_rejects_alias_and_password_injection(self):
        self.data["alias"] = "name\ninjected"
        with self.assertRaises(ValueError):
            decode_bundle(self.encode(), self.fingerprint)
        self.data["alias"] = "tvinbox"
        self.data["key_password"] = "abc\ninjected"
        with self.assertRaises(ValueError):
            decode_bundle(self.encode(), self.fingerprint)

    def test_rejects_small_or_invalid_keystore(self):
        for content in ("!invalid", base64.b64encode(b"short").decode()):
            self.data["keystore_base64"] = content
            with self.assertRaises(ValueError):
                decode_bundle(self.encode(), self.fingerprint)

    def test_rejects_non_string_values(self):
        self.data["alias"] = 1
        with self.assertRaises(ValueError):
            decode_bundle(self.encode(), self.fingerprint)


if __name__ == "__main__":
    unittest.main()
