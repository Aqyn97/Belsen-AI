#!/usr/bin/env python3
"""Stream PostgreSQL 17 to age; optionally verify a private R2 upload. No cloud calls in create/status.

Production dependencies: Python 3, Docker Compose, age and AWS CLI v2.
The recovery identity belongs off-server; only public recipients are needed here.
"""
import argparse
import datetime as dt
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import uuid

MiB = 1024 * 1024
PREFIX = "belsen/production/postgres/"
NAME = re.compile(r"belsen-\d{8}T\d{6}Z-[a-f0-9]{12}\.dump\.age")


def private_file(path):
    path = Path(path)
    if not path.is_file() or path.stat().st_mode & 0o077:
        raise ValueError("Secret file must exist with owner-only permissions.")
    return path.resolve()


def run(command, **kwargs):
    # Never relay child stderr: client diagnostics may include credentials or database details.
    if kwargs.pop("capture_output", False):
        kwargs["stdout"] = subprocess.PIPE
    return subprocess.run(command, check=True, stderr=subprocess.DEVNULL, **kwargs)


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as source:
        for chunk in iter(lambda: source.read(MiB), b""):
            digest.update(chunk)
    return digest.hexdigest()


def atomic_json(path, value):
    temporary = path.with_suffix(path.suffix + ".partial")
    try:
        temporary.write_text(json.dumps(value, sort_keys=True) + "\n")
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def directory(args):
    path = Path(args.out_dir).resolve()
    path.mkdir(mode=0o700, parents=True, exist_ok=True)
    if path.stat().st_mode & 0o077:
        raise ValueError("Backup directory must have owner-only permissions.")
    return path


def compose(args, command):
    if not re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,62}", args.project):
        raise ValueError("Invalid explicit Compose project.")
    if not Path(args.compose_file).is_file() or not Path(args.env_file).is_file():
        raise ValueError("Compose configuration and environment files are required.")
    # Fixed roles/paths agree with the production bootstrap. Password stays inside the container.
    return ["docker", "compose", "--project-name", args.project, "--env-file", str(Path(args.env_file).resolve()),
            "--file", str(Path(args.compose_file).resolve()), "exec", "-T", "postgres", "sh", "-eu", "-c",
            'export PGPASSWORD="$(cat /run/secrets/postgres_backup_password)"; export PGOPTIONS="-c statement_timeout=600000 -c lock_timeout=10000"; ' + command]


def create(args, out):
    if not Path(args.recipients_file).is_file():
        raise ValueError("Public age recipients file is required.")
    if not 1 <= args.max_mib <= 512:
        raise ValueError("Encrypted dump limit must be between 1 and 512 MiB.")
    limit = args.max_mib * MiB
    existing = sum(p.stat().st_size for p in out.iterdir() if p.is_file())
    if existing + 2 * limit > args.local_budget_mib * MiB or shutil.disk_usage(out).free < 2 * limit:
        raise ValueError("Local backup budget/free-space threshold reached; investigate, do not silently discard backups.")
    version = run(compose(args, "psql -h127.0.0.1 -U belsen_backup -d belsen -Atqc 'SHOW server_version_num'"), capture_output=True).stdout.strip()
    if not version.isdigit() or int(version) // 10000 != 17:
        raise ValueError("Source must be PostgreSQL 17.")
    now = dt.datetime.now(dt.timezone.utc)
    name = f"belsen-{now:%Y%m%dT%H%M%SZ}-{uuid.uuid4().hex[:12]}.dump.age"
    final = out / name
    temporary = out / (name + ".partial")
    dump = encrypt = None
    try:
        dump = subprocess.Popen(compose(args, "pg_dump -h127.0.0.1 -U belsen_backup -d belsen --format=custom --no-owner --no-acl"), stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        encrypt = subprocess.Popen([args.age_bin, "--encrypt", "--recipients-file", str(Path(args.recipients_file).resolve())], stdin=dump.stdout, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        dump.stdout.close()
        size = 0
        with temporary.open("xb") as target:
            while chunk := encrypt.stdout.read(MiB):
                size += len(chunk)
                if size > limit:
                    raise ValueError("Encrypted dump exceeds its configured size ceiling.")
                target.write(chunk)
            target.flush()
            os.fsync(target.fileno())
        if encrypt.wait() or dump.wait() or size == 0:
            raise ValueError("Dump/encryption failed; no successful backup was published.")
        os.replace(temporary, final)
        atomic_json(out / (name + ".json"), {"file": name, "sha256": sha256(final), "bytes": size,
                                            "createdAt": now.isoformat(), "postgresMajor": 17, "format": "pg-custom+age-v1"})
        return final
    finally:
        for process in (encrypt, dump):
            if process is not None and process.poll() is None:
                process.kill()
                process.wait()
        temporary.unlink(missing_ok=True)


def aws(args):
    credentials = private_file(args.credentials_file)
    if not re.fullmatch(r"https://[a-f0-9]{32}(?:\.eu|\.us)?\.r2\.cloudflarestorage\.com", args.endpoint):
        raise ValueError("R2 endpoint must be the explicit HTTPS account endpoint.")
    if not re.fullmatch(r"[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]", args.bucket) or "." in args.bucket:
        raise ValueError("Use an explicit DNS-safe bucket name without dots.")
    # File credentials only; do not accidentally fall back to environment/instance credentials.
    env = {k: v for k, v in os.environ.items() if not k.startswith("AWS_")}
    env.update(AWS_SHARED_CREDENTIALS_FILE=str(credentials), AWS_CONFIG_FILE=os.devnull,
               AWS_EC2_METADATA_DISABLED="true", AWS_MAX_ATTEMPTS="2", AWS_RETRY_MODE="standard",
               AWS_REQUEST_CHECKSUM_CALCULATION="WHEN_REQUIRED", AWS_RESPONSE_CHECKSUM_VALIDATION="WHEN_REQUIRED")
    base = [args.aws_bin, "--profile", "belsen-r2", "--region", "auto", "--endpoint-url", args.endpoint,
            "--no-cli-pager", "s3api"]
    return base, env


def upload(args, out, artifact):
    if artifact.parent.resolve() != out or not NAME.fullmatch(artifact.name):
        raise ValueError("Upload requires an artifact created in this backup directory.")
    manifest = json.loads((out / (artifact.name + ".json")).read_text())
    actual = sha256(artifact)
    if manifest.get("sha256") != actual or manifest.get("bytes") != artifact.stat().st_size:
        raise ValueError("Local encrypted backup integrity mismatch.")
    base, env = aws(args)
    listed = json.loads(run(base + ["list-objects-v2", "--bucket", args.bucket, "--prefix", PREFIX, "--output", "json"], env=env, capture_output=True).stdout)
    objects = listed.get("Contents", [])
    size = sum(int(item["Size"]) for item in objects)
    if len(objects) >= 16 or size + artifact.stat().st_size > args.remote_budget_mib * MiB:
        raise ValueError("Remote backup budget reached; inspect lifecycle/other usage before retrying.")
    key = PREFIX + artifact.name
    if any(item.get("Key") == key for item in objects):
        raise ValueError("Object already exists; preserve it and investigate before manual retry.")
    run(base + ["put-object", "--bucket", args.bucket, "--key", key, "--body", str(artifact),
                "--storage-class", "STANDARD", "--metadata", "sha256=" + actual, "--content-type", "application/octet-stream"], env=env, stdout=subprocess.DEVNULL)
    head = json.loads(run(base + ["head-object", "--bucket", args.bucket, "--key", key, "--output", "json"], env=env, capture_output=True).stdout)
    if int(head.get("ContentLength", -1)) != artifact.stat().st_size or head.get("Metadata", {}).get("sha256") != actual:
        raise ValueError("Uploaded object metadata mismatch; backup is not confirmed.")
    # A full encrypted round trip verifies content independently of metadata/ETag conventions.
    check = out / (artifact.name + ".uploadcheck")
    try:
        run(base + ["get-object", "--bucket", args.bucket, "--key", key, str(check)], env=env, stdout=subprocess.DEVNULL)
        if sha256(check) != actual:
            raise ValueError("Downloaded encrypted backup hash mismatch; backup is not confirmed.")
    finally:
        check.unlink(missing_ok=True)
    atomic_json(out / "last-success.json", {"confirmedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
                                           "bytes": artifact.stat().st_size, "sha256": actual, "object": key})
    # Only after a verified upload, retain the two newest local encrypted artifacts.
    old = sorted((p for p in out.iterdir() if NAME.fullmatch(p.name)), key=lambda p: p.name, reverse=True)[2:]
    for path in old:
        path.unlink()
        (out / (path.name + ".json")).unlink(missing_ok=True)
    print(json.dumps({"backup": "verified-upload", "bytes": artifact.stat().st_size,
                      "prefixBytesBeforeUpload": size, "retention": "operator-configured-8-day-R2-lifecycle"}))


def main():
    os.umask(0o077)
    signal.signal(signal.SIGTERM, lambda *_: (_ for _ in ()).throw(InterruptedError()))
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["create", "upload", "run", "status"])
    parser.add_argument("--out-dir", required=True)
    parser.add_argument("--compose-file")
    parser.add_argument("--env-file")
    parser.add_argument("--project", default="belsen-ai-production")
    parser.add_argument("--recipients-file")
    parser.add_argument("--age-bin", default="age")
    parser.add_argument("--aws-bin", default="aws")
    parser.add_argument("--artifact")
    parser.add_argument("--credentials-file")
    parser.add_argument("--endpoint")
    parser.add_argument("--bucket")
    parser.add_argument("--max-mib", type=int, default=512)
    parser.add_argument("--local-budget-mib", type=int, default=2048)
    parser.add_argument("--remote-budget-mib", type=int, default=8192)
    parser.add_argument("--max-age-hours", type=int, default=26)
    args = parser.parse_args()
    out = directory(args)
    with (out / ".backup.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        if args.command == "status":
            record = json.loads((out / "last-success.json").read_text())
            elapsed = dt.datetime.now(dt.timezone.utc) - dt.datetime.fromisoformat(record["confirmedAt"])
            if elapsed.total_seconds() < 0 or elapsed.total_seconds() > args.max_age_hours * 3600:
                raise ValueError("Verified off-site backup is missing/stale or system time moved backwards.")
            print("Verified off-site backup freshness is within policy.")
            return
        artifact = create(args, out) if args.command in ("create", "run") else Path(args.artifact).resolve()
        if args.command == "create":
            print(str(artifact))  # Local path, no credentials or user records.
        else:
            upload(args, out, artifact)


if __name__ == "__main__":
    try:
        main()
    except (Exception, KeyboardInterrupt) as failure:
        # Deliberately omit arbitrary child messages/arguments from operational logs.
        print("Backup failed safely (" + type(failure).__name__ + "); inspect private configuration and service status.", file=sys.stderr)
        sys.exit(1)
