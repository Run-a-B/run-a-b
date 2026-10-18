#!/usr/bin/env python3
"""Explicit local EC2 operations; see --help. Requires Python 3.9+, MySQL 8 client.

Run deployment as the owner of an application root (normally /srv/runab).
Stage a unique release, then activate it after reviewed DB migrations/backup.
The prod profile validates schema at startup; this tool never migrates it.
Keep DB defaults files, backups and service EnvironmentFile outside dist/.
During a dump, freeze schema changes; only InnoDB base tables are accepted.
Restore only trusted backups, using a DB account restricted to the isolated
verification database. Failed verification databases are retained for inspection.
"""
import argparse
import contextlib
import datetime
import fcntl
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import stat
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


class OperationError(Exception):
    pass


def require(condition, message):
    if not condition:
        raise OperationError(message)


def identifier(value):
    require(bool(re.fullmatch(r"[A-Za-z][A-Za-z0-9_]{0,63}", value)), "invalid database/table identifier")
    return value


def release_id(value):
    require(bool(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,95}", value)), "invalid release id")
    return value


def app_root(value):
    root = Path(value)
    require(root.is_absolute() and root.is_dir() and root == root.resolve() and root.parent != root,
            "root must be an existing absolute directory without symlink components")
    releases = root / "releases"
    require(not releases.is_symlink(), "releases must not be a symlink")
    if not releases.exists():
        releases.mkdir(mode=0o755)
        releases.chmod(0o755)
    return root


@contextlib.contextmanager
def deployment_lock(root):
    lock = root / ".deploy.lock"
    require(not lock.is_symlink(), "deployment lock must not be a symlink")
    with lock.open("a") as handle:
        try:
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise OperationError("another deployment is running") from None
        yield


def validate_tree(directory):
    require(directory.is_dir() and not directory.is_symlink(), "release/dist must be a real directory")
    for entry in directory.rglob("*"):
        require(not entry.is_symlink() and (entry.is_file() or entry.is_dir()),
                "release trees may contain only real files and directories")


def validate_release(root, name):
    directory = root / "releases" / release_id(name)
    validate_tree(directory)
    require((directory / "api.jar").is_file() and (directory / "api.jar").stat().st_size > 0,
            "release requires a nonempty api.jar")
    require((directory / "dist/index.html").is_file(), "release requires dist/index.html")
    return directory


def stage(args):
    root = app_root(args.root)
    target = root / "releases" / release_id(args.release)
    jar, dist = Path(args.jar), Path(args.dist)
    require(jar.is_file() and not jar.is_symlink() and jar.stat().st_size > 0, "jar must be a nonempty real file")
    validate_tree(dist)
    require((dist / "index.html").is_file(), "dist requires index.html")
    with deployment_lock(root):
        require(not target.exists() and not target.is_symlink(), "release already exists; use a unique id")
        # Failed staging remains under a hidden unique name; no recursive deletion.
        staging = root / "releases" / (".staging-" + uuid.uuid4().hex)
        staging.mkdir(mode=0o755)
        shutil.copyfile(jar, staging / "api.jar")
        shutil.copytree(dist, staging / "dist")
        for entry in staging.rglob("*"):
            entry.chmod(0o555 if entry.is_dir() else 0o444)
        staging.chmod(0o555)
        staging.rename(target)
    print(json.dumps({"staged": str(target)}))


def swap_link(current, target):
    temporary = current.parent / (".current-" + uuid.uuid4().hex)
    try:
        temporary.symlink_to(target)
        os.replace(temporary, current)
    finally:
        if temporary.is_symlink():
            temporary.unlink()


def service(action, name):
    result = subprocess.run(["systemctl", action, name], stdout=subprocess.DEVNULL,
                            stderr=subprocess.DEVNULL, timeout=90)
    require(result.returncode == 0, "service " + action + " failed")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def health_url(value):
    url = urllib.parse.urlsplit(value)
    require(url.scheme == "http" and url.hostname in ("127.0.0.1", "::1")
            and not url.username and not url.password and not url.fragment
            and url.path == "/api/v1/policies", "readiness URL must be loopback HTTP /api/v1/policies")
    return value


def ready(url, timeout):
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    deadline = time.monotonic() + timeout
    while True:
        try:
            with opener.open(url, timeout=min(3, max(0.1, deadline - time.monotonic()))) as response:
                payload = json.load(response)
                if response.status == 200 and payload.get("success") is True and isinstance(payload.get("data", {}).get("policies"), list):
                    return True
        except (OSError, ValueError, AttributeError, urllib.error.URLError):
            pass
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            return False
        time.sleep(min(1, remaining))


def activate(args):
    url = health_url(args.health_url)
    require(0 < args.timeout <= 300, "timeout must be between 0 and 300 seconds")
    require(bool(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]*(?:\.service)?", args.service)), "invalid service name")
    root = app_root(args.root)
    with deployment_lock(root):
        target = validate_release(root, args.release)
        current = root / "current"
        require(current.is_symlink() or not current.exists(), "existing current must be a symlink")
        previous = None
        if current.is_symlink():
            previous = current.resolve(strict=True)
            require(previous.parent == root / "releases", "previous release points outside releases")
            validate_release(root, previous.name)
        require(target != previous, "release is already current")
        watched_signals = (signal.SIGTERM, signal.SIGHUP, signal.SIGINT)
        previous_handlers = {number: signal.getsignal(number) for number in watched_signals}

        def interrupted(number, frame):
            raise OperationError("activation interrupted")

        try:
            for number in watched_signals:
                signal.signal(number, interrupted)
            try:
                swap_link(current, target)
                service("restart", args.service)
                require(ready(url, args.timeout), "new release readiness failed")
            except BaseException:
                # Finish restoring state even if the caller repeats termination.
                # SIGKILL and power loss cannot be handled by a running process.
                for number in watched_signals:
                    signal.signal(number, signal.SIG_IGN)
                if previous:
                    swap_link(current, previous)
                    try:
                        service("restart", args.service)
                        require(ready(url, args.timeout), "previous release readiness failed")
                    except (OperationError, OSError, subprocess.SubprocessError):
                        raise OperationError("activation failed; symlink restored, previous service requires inspection") from None
                else:
                    if current.is_symlink():
                        current.unlink()
                    service("stop", args.service)
                raise OperationError("activation failed; previous state restored") from None
        finally:
            for number, handler in previous_handlers.items():
                signal.signal(number, handler)
    print(json.dumps({"activated": args.release, "previous": previous.name if previous else None}))


def defaults_file(value):
    path = Path(value)
    require(path.is_absolute() and not path.is_symlink() and path.is_file(), "defaults file must be an absolute real file")
    info = path.stat()
    require(info.st_uid == os.geteuid() and stat.S_IMODE(info.st_mode) in (0o400, 0o600),
            "defaults file must belong to this user and have mode 0400 or 0600")
    return str(path)


def mysql_args(defaults):
    # --defaults-file must be first. Never put passwords in arguments or output.
    return ["mysql", "--defaults-file=" + defaults, "--batch", "--skip-column-names"]


def query(defaults, sql):
    result = subprocess.run(mysql_args(defaults) + ["--execute", sql], capture_output=True, text=True, timeout=120)
    require(result.returncode == 0, "MySQL query failed; inspect client/server locally")
    return result.stdout.strip()


def schema(defaults, database):
    # Hash metadata, not application data. Names/order are independent of DB name.
    columns = query(defaults, "SELECT TABLE_NAME, ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA "
                    "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '" + database + "' ORDER BY TABLE_NAME, ORDINAL_POSITION")
    indexes = query(defaults, "SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME, SUB_PART, INDEX_TYPE "
                    "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = '" + database + "' ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX")
    return hashlib.sha256((columns + "\n" + indexes).encode()).hexdigest()


def checksum(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def backup(args):
    defaults, database = defaults_file(args.defaults_file), identifier(args.database)
    output = Path(args.output)
    manifest = output.with_name(output.name + ".json")
    require(output.is_absolute() and output.name.endswith(".sql.gz") and output.parent.is_dir(), "output must be an absolute .sql.gz path with an existing parent")
    require(not output.exists() and not output.is_symlink() and not manifest.exists() and not manifest.is_symlink(), "backup/manifest already exists")
    require(query(defaults, "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '" + database + "'") == database,
            "source database does not exist")
    require(not query(defaults, "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = '" + database + "' AND TABLE_TYPE = 'BASE TABLE' AND ENGINE <> 'InnoDB'"),
            "consistent backup requires only InnoDB base tables")
    before = schema(defaults, database)
    fd, name = tempfile.mkstemp(prefix=".backup-", dir=output.parent)
    temporary = Path(name)
    try:
        with os.fdopen(fd, "wb") as handle:
            process = subprocess.Popen(["mysqldump", "--defaults-file=" + defaults,
                                        "--single-transaction", "--quick", "--routines", "--events", "--triggers",
                                        "--hex-blob", "--set-gtid-purged=OFF", "--no-tablespaces", database],
                                       stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
            try:
                with gzip.GzipFile(fileobj=handle, mode="wb", filename="", mtime=0) as archive:
                    shutil.copyfileobj(process.stdout, archive)
                require(process.wait() == 0, "mysqldump failed; no completed backup published")
            finally:
                process.stdout.close()
                if process.poll() is None:
                    process.kill()
                    process.wait()
            handle.flush()
            os.fsync(handle.fileno())
        require(before == schema(defaults, database), "schema changed during dump; retry with schema changes frozen")
        metadata = {"source_database": database, "sha256": checksum(temporary), "schema_sha256": before,
                    "schema_scope": "columns_and_indexes", "created_utc": datetime.datetime.now(datetime.timezone.utc).isoformat()}
        # Hard links publish without overwriting an existing artifact, even on a race.
        os.link(temporary, output)
        with open(manifest, "x", opener=lambda path, flags: os.open(path, flags, 0o600)) as handle:
            json.dump(metadata, handle, sort_keys=True)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
    finally:
        temporary.unlink(missing_ok=True)
    print(json.dumps({"backup": str(output), **metadata}))


def verify_restore(args):
    defaults, database = defaults_file(args.defaults_file), identifier(args.database)
    archive = Path(args.backup)
    require(archive.is_absolute() and archive.is_file() and not archive.is_symlink(), "backup must be an absolute real file")
    manifest = archive.with_name(archive.name + ".json")
    require(manifest.is_file() and not manifest.is_symlink(), "backup manifest missing")
    metadata = json.loads(manifest.read_text())
    source = identifier(metadata["source_database"])
    require(database.lower() != source.lower(), "restore database must differ from source")
    require(checksum(archive) == metadata["sha256"], "backup checksum mismatch")
    # Verify gzip CRC before creating any database, without buffering dump in memory.
    with gzip.open(archive, "rb") as stream:
        for _ in iter(lambda: stream.read(1024 * 1024), b""):
            pass
    require(not query(defaults, "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '" + database + "'"),
            "restore database already exists; choose a new isolated name")
    query(defaults, "CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci")
    process = subprocess.Popen(mysql_args(defaults) + ["--database", database], stdin=subprocess.PIPE,
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        with gzip.open(archive, "rb") as stream:
            shutil.copyfileobj(stream, process.stdin)
        process.stdin.close()
        require(process.wait() == 0, "restore failed; isolated database retained for inspection")
    finally:
        if not process.stdin.closed:
            process.stdin.close()
        if process.poll() is None:
            process.kill()
            process.wait()
    restored_schema = schema(defaults, database)
    require(restored_schema == metadata["schema_sha256"], "restored schema differs; isolated database retained")
    tables = query(defaults, "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = '" + database + "' AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME").splitlines()
    counts = {table: int(query(defaults, "SELECT COUNT(*) FROM `" + database + "`.`" + identifier(table) + "`")) for table in tables}
    print(json.dumps({"verified_database": database, "source_database": source,
                      "schema_sha256": restored_schema, "schema_scope": "columns_and_indexes", "row_counts": counts}, sort_keys=True))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    for name, function in (("stage", stage), ("activate", activate), ("backup", backup), ("verify-restore", verify_restore)):
        command = commands.add_parser(name)
        command.set_defaults(function=function)
        if name in ("stage", "activate"):
            command.add_argument("--root", required=True)
            command.add_argument("--release", required=True)
        if name == "stage":
            command.add_argument("--jar", required=True)
            command.add_argument("--dist", required=True)
        if name == "activate":
            command.add_argument("--service", required=True)
            command.add_argument("--health-url", required=True)
            command.add_argument("--timeout", type=float, default=90)
        if name in ("backup", "verify-restore"):
            command.add_argument("--defaults-file", required=True)
            command.add_argument("--database", required=True)
            command.add_argument("--output" if name == "backup" else "--backup", required=True)
    args = parser.parse_args()
    os.umask(0o077)
    try:
        args.function(args)
    except (OperationError, OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        # Never print captured client stderr, SQL data, or credential contents.
        print("ERROR: " + (str(error) if isinstance(error, OperationError) else "operation failed; inspect local inputs and retained artifacts"), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
