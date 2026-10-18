"""Behavior tests: no AWS, systemd, MySQL server, or third-party libraries needed."""
import contextlib
import gzip
import http.server
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import threading
import time
import unittest

CLI = Path(__file__).with_name("ec2_ops.py")


class OperationsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name).resolve()
        self.root = self.base / "app"
        (self.root / "releases").mkdir(parents=True)
        self.bin = self.base / "bin"
        self.bin.mkdir()
        self.state = self.base / "state.json"
        self.state.write_text(json.dumps({"databases": ["production"], "calls": []}))
        self.env = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ["PATH"],
                        TEST_STATE=str(self.state), TEST_ROOT=str(self.root))
        self.defaults = self.base / "mysql.cnf"
        self.defaults.write_text("[client]\npassword=DO_NOT_PRINT_SECRET\n")
        self.defaults.chmod(0o600)
        self.fake("systemctl", """
data['calls'].append(sys.argv[1:] + [os.readlink(os.path.join(os.environ['TEST_ROOT'], 'current')) if os.path.islink(os.path.join(os.environ['TEST_ROOT'], 'current')) else 'absent'])
# The parent polls this file while the child reports a restart. Publish a complete
# state atomically so the fixture never exposes its truncate/write window.
temporary_state = state + '.' + str(os.getpid())
with open(temporary_state, 'w') as state_file:
    json.dump(data, state_file)
os.replace(temporary_state, state)
if os.environ.get('BLOCK_ROLLBACK') and data['calls'][-1][-1].endswith('/old'):
    import time
    open(os.environ['BLOCK_ROLLBACK'], 'w').close()
    while not os.path.exists(os.environ['BLOCK_ROLLBACK'] + '.continue'):
        time.sleep(0.01)
sys.exit(1 if os.environ.get('FAIL_RESTART') and sys.argv[1] == 'restart' else 0)
""")
        self.fake("mysql", """
sql = sys.argv[sys.argv.index('--execute') + 1] if '--execute' in sys.argv else sys.stdin.read()
if '--execute' not in sys.argv:
    data['restored'] = sql
elif 'SCHEMA_NAME' in sql:
    name = sql.split("SCHEMA_NAME = '")[1].split("'")[0]
    print(name if name in data['databases'] else '', end='')
elif sql.startswith('CREATE DATABASE'):
    data['databases'].append(sql.split('`')[1])
elif 'ENGINE' in sql:
    print('legacy' if os.environ.get('NON_INNODB') else '', end='')
elif 'information_schema.COLUMNS' in sql:
    print('items\\t1\\tid\\tbigint\\tNO\\tNULL\\t')
elif 'information_schema.TABLES' in sql:
    print('items')
elif 'COUNT(*)' in sql:
    print('2')
open(state, 'w').write(json.dumps(data))
sys.exit(1 if os.environ.get('FAIL_MYSQL') or (os.environ.get('FAIL_RESTORE') and '--execute' not in sys.argv) else 0)
""")
        self.fake("mysqldump", """
assert '--single-transaction' in sys.argv and '--no-tablespaces' in sys.argv
print('CREATE TABLE items (id bigint); INSERT INTO items VALUES (1),(2);')
sys.exit(1 if os.environ.get('FAIL_DUMP') else 0)
""")

    def fake(self, name, body):
        path = self.bin / name
        path.write_text(f"#!{sys.executable}\nimport os, sys, json\nstate = os.environ['TEST_STATE']\ndata = json.load(open(state))\n" + body)
        path.chmod(0o700)

    def run_cli(self, *args, **env):
        return subprocess.run([sys.executable, str(CLI), *map(str, args)],
                              env=dict(self.env, **env), text=True, capture_output=True)

    def release(self, name):
        directory = self.root / "releases" / name
        (directory / "dist").mkdir(parents=True)
        (directory / "api.jar").write_bytes(b"jar")
        (directory / "dist" / "index.html").write_text(name)
        return directory

    @contextlib.contextmanager
    def health(self, healthy, payload=b'{"success":true,"data":{"policies":[]}}'):
        class Handler(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                self.send_response(200 if healthy else 503)
                self.end_headers()
                self.wfile.write(payload)

            def log_message(self, *args):
                pass
        server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            yield f"http://127.0.0.1:{server.server_port}/api/v1/policies?page=1&size=1"
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def activate(self, url, release="new", **env):
        return self.run_cli("activate", "--root", self.root, "--release", release,
                            "--service", "runab-api", "--health-url", url,
                            "--timeout", "0.1", **env)

    def test_stage_keeps_existing_release_and_copies_read_only_artifacts(self):
        source = self.release("input")
        result = self.run_cli("stage", "--root", self.root, "--release", "new",
                              "--jar", source / "api.jar", "--dist", source / "dist")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(b"jar", (self.root / "releases/new/api.jar").read_bytes())
        self.assertEqual(0, (self.root / "releases/new/api.jar").stat().st_mode & 0o222)
        repeated = self.run_cli("stage", "--root", self.root, "--release", "new",
                                "--jar", source / "api.jar", "--dist", source / "dist")
        self.assertNotEqual(0, repeated.returncode)
        self.assertEqual("input", (self.root / "releases/new/dist/index.html").read_text())

    def test_activation_swaps_symlink_and_preserves_previous_release(self):
        old = self.release("old")
        new = self.release("new")
        (self.root / "current").symlink_to(old)
        with self.health(True) as url:
            result = self.activate(url)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(new, (self.root / "current").resolve())
        self.assertTrue((old / "api.jar").is_file())

    def test_failed_readiness_restores_symlink_and_restarts_previous_service(self):
        old = self.release("old")
        self.release("new")
        (self.root / "current").symlink_to(old)
        with self.health(False) as url:
            result = self.activate(url)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(old, (self.root / "current").resolve())
        calls = json.loads(self.state.read_text())["calls"]
        self.assertEqual(["restart", "runab-api", str(old)], calls[-1])
        self.assertEqual(2, len(calls))

    def test_failed_first_activation_removes_only_new_symlink_and_stops_service(self):
        new = self.release("new")
        with self.health(False) as url:
            result = self.activate(url)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse((self.root / "current").is_symlink())
        self.assertTrue(new.is_dir())
        self.assertEqual([["restart", "runab-api", str(new)], ["stop", "runab-api", "absent"]],
                         json.loads(self.state.read_text())["calls"])

    def test_restart_failure_also_rolls_back(self):
        old = self.release("old")
        self.release("new")
        (self.root / "current").symlink_to(old)
        with self.health(True) as url:
            result = self.activate(url, FAIL_RESTART="1")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(old, (self.root / "current").resolve())

    def test_http_200_without_policy_response_is_not_readiness(self):
        self.release("new")
        with self.health(True, b'{"success":false}') as url:
            result = self.activate(url)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse((self.root / "current").is_symlink())

    def test_real_term_and_hup_restore_previous_service_and_ignore_repeated_signals(self):
        old, new = self.release("old"), self.release("new")
        current = self.root / "current"
        for number in (signal.SIGTERM, signal.SIGHUP):
            with self.subTest(signal=number):
                if current.is_symlink():
                    current.unlink()
                current.symlink_to(old)
                self.state.write_text(json.dumps({"databases": ["production"], "calls": []}))
                blocked = self.base / ("rollback-" + str(number))
                with self.health(False) as url:
                    process = subprocess.Popen([sys.executable, str(CLI), "activate", "--root", str(self.root),
                                                "--release", "new", "--service", "runab-api", "--health-url", url,
                                                "--timeout", "1"], env=dict(self.env, BLOCK_ROLLBACK=str(blocked)),
                                               stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
                    try:
                        deadline = time.monotonic() + 5
                        while not (current.resolve() == new and json.loads(self.state.read_text())["calls"]):
                            self.assertIsNone(process.poll(), "activation exited before signal test")
                            self.assertLess(time.monotonic(), deadline, "activation did not start")
                            time.sleep(0.01)
                        # The actual OS signal reaches the real CLI process.
                        process.send_signal(number)
                        while not blocked.exists():
                            self.assertIsNone(process.poll(), "signal exited without rollback")
                            self.assertLess(time.monotonic(), deadline, "rollback did not start")
                            time.sleep(0.01)
                        process.send_signal(signal.SIGTERM)
                        process.send_signal(signal.SIGHUP)
                        blocked.with_name(blocked.name + ".continue").touch()
                        stdout, stderr = process.communicate(timeout=5)
                        self.assertEqual(1, process.returncode, stderr)
                        self.assertEqual("", stdout)
                        self.assertEqual(old, current.resolve())
                        self.assertEqual(["restart", "runab-api", str(old)],
                                         json.loads(self.state.read_text())["calls"][-1])
                    finally:
                        blocked.with_name(blocked.name + ".continue").touch()
                        if process.poll() is None:
                            process.kill()
                        process.communicate(timeout=5)

    def test_activation_rejects_unsafe_targets_without_touching_service(self):
        self.release("new")
        with self.health(True) as url:
            for name in ("../new", "new/../new", "missing", "-new"):
                self.assertNotEqual(0, self.activate(url, name).returncode)
            for unsafe in ("https://127.0.0.1/api/v1/policies", "http://example.com/api/v1/policies",
                           "http://127.0.0.1/other", "http://user@127.0.0.1/api/v1/policies"):
                self.assertNotEqual(0, self.activate(unsafe).returncode)
            (self.root / "current").mkdir()
            self.assertNotEqual(0, self.activate(url).returncode)
        self.assertEqual([], json.loads(self.state.read_text())["calls"])

    def test_activation_rejects_release_symlink_and_external_previous_target(self):
        self.release("old")
        (self.root / "releases/new").symlink_to(self.root / "releases/old")
        with self.health(True) as url:
            self.assertNotEqual(0, self.activate(url).returncode)
            (self.root / "current").symlink_to(self.base)
            self.assertNotEqual(0, self.activate(url, "old").returncode)

    def backup(self, **env):
        return self.run_cli("backup", "--defaults-file", self.defaults,
                            "--database", "production", "--output", self.base / "backup.sql.gz", **env)

    def restore(self, target="restore_check", **env):
        return self.run_cli("verify-restore", "--defaults-file", self.defaults,
                            "--backup", self.base / "backup.sql.gz", "--database", target, **env)

    def test_backup_and_isolated_restore_emit_counts_and_schema_proof(self):
        result = self.backup()
        self.assertEqual(0, result.returncode, result.stderr)
        archive = self.base / "backup.sql.gz"
        self.assertIn(b"CREATE TABLE", gzip.decompress(archive.read_bytes()))
        self.assertEqual(0, archive.stat().st_mode & 0o077)
        restored = self.restore()
        self.assertEqual(0, restored.returncode, restored.stderr)
        proof = json.loads(restored.stdout)
        self.assertEqual({"items": 2}, proof["row_counts"])
        self.assertEqual(64, len(proof["schema_sha256"]))
        self.assertNotIn("INSERT", restored.stdout)
        self.assertNotIn("DO_NOT_PRINT_SECRET", result.stdout + result.stderr + restored.stdout + restored.stderr)

    def test_dump_failure_never_publishes_backup_or_success(self):
        result = self.backup(FAIL_DUMP="1")
        self.assertNotEqual(0, result.returncode)
        self.assertFalse((self.base / "backup.sql.gz").exists())
        self.assertFalse((self.base / "backup.sql.gz.json").exists())
        self.assertEqual("", result.stdout)

    def test_backup_refuses_nontransactional_tables_and_insecure_credentials(self):
        self.assertNotEqual(0, self.backup(NON_INNODB="1").returncode)
        self.defaults.chmod(0o644)
        self.assertNotEqual(0, self.backup().returncode)
        self.assertFalse((self.base / "backup.sql.gz").exists())

    def test_restore_rejects_source_existing_database_and_corrupt_archive(self):
        self.assertEqual(0, self.backup().returncode)
        self.assertNotEqual(0, self.restore("production").returncode)
        data = json.loads(self.state.read_text())
        data["databases"].append("already_exists")
        self.state.write_text(json.dumps(data))
        self.assertNotEqual(0, self.restore("already_exists").returncode)
        (self.base / "backup.sql.gz").write_bytes(b"corrupted")
        self.assertNotEqual(0, self.restore().returncode)
        self.assertNotIn("restore_check", json.loads(self.state.read_text())["databases"])

    def test_invalid_database_never_reaches_mysql(self):
        result = self.run_cli("backup", "--defaults-file", self.defaults,
                              "--database", "production;DROP DATABASE production", "--output", self.base / "backup.sql.gz")
        self.assertNotEqual(0, result.returncode)
        self.assertFalse((self.base / "backup.sql.gz").exists())

    def test_restore_failure_never_reports_success_or_drops_database(self):
        self.assertEqual(0, self.backup().returncode)
        result = self.restore(FAIL_RESTORE="1")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("", result.stdout)
        self.assertIn("restore_check", json.loads(self.state.read_text())["databases"])


if __name__ == "__main__":
    unittest.main()
