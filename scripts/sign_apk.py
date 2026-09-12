#!/usr/bin/env python3
"""Sign one APK with the existing private key. Never generate or print private keys."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

FIELDS = {"alias", "store_password", "key_password", "certificate_sha256", "keystore_base64"}
ROOT = Path(__file__).resolve().parents[1]


def decode_bundle(encoded: str, expected: str) -> dict:
    if not encoded or len(encoded) > 48 * 1024:
        raise ValueError("Missing or oversized TVINBOX_SIGNING_BUNDLE")
    try:
        value = json.loads(base64.b64decode(encoded.strip(), validate=True))
    except (ValueError, UnicodeError) as failure:
        raise ValueError("Invalid signing bundle encoding") from None
    if not isinstance(value, dict) or set(value) != FIELDS:
        raise ValueError("Invalid signing bundle fields")
    if not all(isinstance(v, str) for v in value.values()):
        raise ValueError("Invalid signing bundle value types")
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", value["alias"]):
        raise ValueError("Invalid key alias")
    for name in ("store_password", "key_password"):
        if not re.fullmatch(r"[A-Za-z0-9_-]{16,256}", value[name]):
            raise ValueError("Invalid signing password format")
    if not re.fullmatch(r"[0-9a-f]{64}", expected) or value["certificate_sha256"] != expected:
        raise ValueError("Signing identity does not match the pinned certificate")
    try:
        data = base64.b64decode(value["keystore_base64"], validate=True)
    except ValueError:
        raise ValueError("Invalid keystore encoding") from None
    if not 1024 <= len(data) <= 32 * 1024:
        raise ValueError("Invalid keystore size")
    value["keystore"] = data
    return value


def run_private(command: list[str], env: dict[str, str], stage: str) -> bytes:
    # Tools must not dump password-bearing subprocess state into public logs.
    result = subprocess.run(command, env=env, capture_output=True, check=False)
    if result.returncode:
        raise RuntimeError(f"{stage} failed; private tool output is suppressed")
    return result.stdout


def sign(source: Path, destination: Path) -> None:
    expected = (ROOT / "signing/release-cert.sha256").read_text().strip()
    encoded = os.environ.get("TVINBOX_SIGNING_BUNDLE", "")
    bundle = decode_bundle(encoded, expected)
    if not source.is_file() or destination.exists() or source.resolve() == destination.resolve():
        raise ValueError("Source must exist and destination must be a new APK path")
    tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
    if os.environ.get("GITHUB_ACTIONS") == "true":
        for value in (encoded, bundle["store_password"], bundle["key_password"], bundle["keystore_base64"]):
            print(f"::add-mask::{value}", flush=True)
    env = os.environ.copy()
    env.pop("TVINBOX_SIGNING_BUNDLE", None)
    env["TVINBOX_STORE_PASSWORD"] = bundle["store_password"]
    env["TVINBOX_KEY_PASSWORD"] = bundle["key_password"]
    destination.parent.mkdir(parents=True, exist_ok=True)
    try:
        with tempfile.TemporaryDirectory(prefix="tvinbox-sign-", dir=os.environ.get("RUNNER_TEMP")) as temporary:
            private = Path(temporary)
            key = private / "release.p12"
            key.write_bytes(bundle["keystore"])
            key.chmod(0o600)
            cert = run_private(["keytool", "-exportcert", "-keystore", str(key), "-storetype", "PKCS12", "-storepass:env", "TVINBOX_STORE_PASSWORD", "-alias", bundle["alias"]], env, "Certificate verification")
            if hashlib.sha256(cert).hexdigest() != expected:
                raise ValueError("Keystore certificate does not match the pinned identity")
            aligned = private / "aligned.apk"
            run_private([str(tools / "zipalign"), "-f", "4", str(source), str(aligned)], env, "APK alignment")
            run_private([str(tools / "apksigner"), "sign", "--ks", str(key), "--ks-type", "PKCS12", "--ks-key-alias", bundle["alias"], "--ks-pass", "env:TVINBOX_STORE_PASSWORD", "--key-pass", "env:TVINBOX_KEY_PASSWORD", "--min-sdk-version", "23", "--v1-signing-enabled", "true", "--v2-signing-enabled", "true", "--v3-signing-enabled", "true", "--v4-signing-enabled", "false", "--out", str(destination), str(aligned)], env, "APK signing")
            report = run_private([str(tools / "apksigner"), "verify", "--verbose", "--print-certs", "--min-sdk-version", "23", str(destination)], env, "APK signature verification").decode("utf-8")
            match = re.search(r"^Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)$", report, re.MULTILINE)
            if not match or match.group(1).lower() != expected:
                raise ValueError("Signed APK certificate does not match the pinned identity")
        print(f"Fixed certificate verified: {expected}")
    except Exception:
        destination.unlink(missing_ok=True)
        raise


if __name__ == "__main__":
    try:
        if len(sys.argv) != 3:
            raise ValueError("Usage: sign_apk.py unsigned.apk signed.apk")
        sign(Path(sys.argv[1]), Path(sys.argv[2]))
    except Exception:
        # Do not print exception objects: they can contain private input.
        print("::error::Fixed-key signing failed. Check the signing bundle, pinned certificate and Android build tools.", file=sys.stderr)
        sys.exit(1)
