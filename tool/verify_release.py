#!/usr/bin/env python3
"""Verify the actual signed release APK before it is published."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RELEASE_CERTIFICATE = "668646d9ddc4cabf26c1e8553f86a92bfcb3e3d533ff58479b10230d047c7b3a"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sdk_tool(sdk, name):
    suffix = ".bat" if name == "apksigner" and os.name == "nt" else ".exe" if os.name == "nt" else ""
    tools = [path / (name + suffix) for path in (sdk / "build-tools").iterdir() if path.is_dir()]
    tools = [path for path in tools if path.is_file()]
    require(tools, f"Android SDK tool not found: {name}")
    return max(tools, key=lambda path: tuple(int(part) for part in re.findall(r"\d+", path.parent.name)))


def run(command):
    result = subprocess.run([str(value) for value in command], capture_output=True, text=True, encoding="utf-8", errors="replace")
    require(result.returncode == 0, f"{Path(command[0]).name} failed: {result.stderr.strip()}")
    return result.stdout


def verify_certificate(signing):
    signer_count = re.search(r"^Number of signers: (\d+)$", signing, re.MULTILINE)
    certificates = set(value.lower() for value in re.findall(
        r"^(?:Signer #\d+|V\d(?:\.\d+)? Signer:) certificate SHA-256 digest: ([0-9a-fA-F]{64})$",
        signing, re.MULTILINE))
    require(signer_count is not None and signer_count.group(1) == "1", "APK must have exactly one signer")
    require(certificates == {RELEASE_CERTIFICATE}, "APK signing certificate differs from the existing release key")
    return RELEASE_CERTIFICATE


def verify(apk, tag, sdk, lint_report, source_root=ROOT):
    gradle = (source_root / "app/build.gradle.kts").read_text(encoding="utf-8")
    version_name = re.search(r'\bversionName\s*=\s*"([^"]+)"', gradle).group(1)
    version_code = re.search(r"\bversionCode\s*=\s*(\d+)", gradle).group(1)
    require(tag == "v" + version_name, "Release tag differs from the app version")
    require(apk.is_file(), "Release APK is missing")
    require(lint_report.is_file(), "Lint report is missing")
    issues = ET.parse(lint_report).getroot().findall("issue")
    errors = [issue.attrib["id"] for issue in issues if issue.attrib.get("severity", "").lower() in {"error", "fatal"}]
    require(not errors, f"Lint contains errors: {errors}")
    badging = run([sdk_tool(sdk, "aapt"), "dump", "badging", apk])
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.MULTILINE)
    require(package is not None, "Cannot read APK package identity")
    require(package.groups() == ("org.lianye", version_code, version_name), "APK package or version is incorrect")
    require("application-debuggable" not in badging, "APK is debuggable")
    require("android.permission.INTERNET" not in badging, "APK declares network permission")
    signing = run([sdk_tool(sdk, "apksigner"), "verify", "--verbose", "--print-certs", apk])
    certificate = verify_certificate(signing)
    return {
        "package": package.group(1), "version": version_name, "version_code": int(version_code),
        "tag": tag, "apk": apk.name, "bytes": apk.stat().st_size,
        "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "certificate_sha256": certificate,
        "lint_errors": 0, "lint_warnings": sum(issue.attrib.get("severity") == "Warning" for issue in issues),
        "debuggable": False, "network_permission": False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--sdk", type=Path, default=os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT"))
    parser.add_argument("--lint-report", type=Path, default=ROOT / "app/build/reports/lint-results-debug.xml")
    parser.add_argument("--source-root", type=Path, default=ROOT)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    require(args.sdk is not None, "ANDROID_HOME or --sdk is required")
    result = verify(args.apk, args.tag, args.sdk, args.lint_report, args.source_root)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result))


if __name__ == "__main__":
    main()
