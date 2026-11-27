"""Focused, dependency-free publication guard; never print matched secret values.

Inspect tracked and unignored candidate files. Historical review and unknown credential formats
remain manual responsibilities. Test fixture strings are not globally allowlisted.
"""
from pathlib import Path
import re
import subprocess
import sys

PATTERNS = {
    "provider token": re.compile(
        rb"(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{40,}"
        rb"|gsk_[A-Za-z0-9]{40,}|sk-(?:proj-)?[A-Za-z0-9_-]{40,}"
        rb"|AIza[A-Za-z0-9_-]{35}|AKIA[0-9A-Z]{16}"
        rb"|xox[baprs]-[A-Za-z0-9-]{20,})"
    ),
    "embedded private key": re.compile(
        rb"-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----\s+"
        rb"[A-Za-z0-9+/=\r\n]{32,}"
    ),
    "credential-bearing URL": re.compile(
        rb"(?:https?|postgres(?:ql)?|mysql)://[^\s/:'\"]+:[^\s/@'\"]+@"
    ),
    "database dump": re.compile(
        rb"(?:\APGDMP|\ASQLite format 3\x00|^-- PostgreSQL database dump|^COPY .+ FROM stdin;)",
        re.MULTILINE,
    ),
}


def findings(name, data):
    path = Path(name)
    problems = []
    if (set(path.parts) & {"node_modules", "target", "dist", "test-results", "playwright-report", "__pycache__"}
            or (path.name.startswith(".env") and path.name != ".env.example")
            or path.suffix.lower() in {".pem", ".key", ".p12", ".pfx", ".jks", ".dump", ".backup", ".sqlite", ".db", ".jar", ".war", ".class", ".pyc"}):
        problems.append((0, "sensitive or generated path"))
    for label, pattern in PATTERNS.items():
        for match in pattern.finditer(data):
            # The explicit synthetic URL rejection case, not a test-directory bypass.
            if (label == "credential-bearing URL" and name == "showcase/build.test.mjs"
                    and match.group() == b"https://" + b"user:secret@"):
                continue
            problems.append((data[:match.start()].count(b"\n") + 1, label))
    return problems


def main():
    root = Path(subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip())
    names = subprocess.check_output(["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], cwd=root).decode().split("\0")
    failed = False
    for name in filter(None, names):
        path = root / name
        if not path.exists():  # A tracked deletion contributes no publication bytes.
            continue
        for line, label in findings(name, path.read_bytes()):
            print(f"{name}:{line}: {label}", file=sys.stderr)
            failed = True
    if not failed:
        print("Publication guard passed (recognized patterns and artifact paths).")
    return int(failed)


if __name__ == "__main__":
    sys.exit(main())
