import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

MODULE = Path(__file__).with_name("bootstrap.py")


class BootstrapTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.commands = []
        self.container = None
        self.digest = "mysql@sha256:" + "a" * 64
        self.fail = None
        self.nginx_active = False
        self.tables = "business_info\nemail_verification\npolicy\npolicy_card\npolicy_summary\nreport\nusers\n"
        self.account = ""

    def module(self):
        self.assertTrue(MODULE.is_file(), "fresh-server bootstrap is missing")
        spec = importlib.util.spec_from_file_location("bootstrap", MODULE)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def runner(self, command, *, input=None, allow_failure=False):
        self.commands.append((command, input))
        if self.fail and self.fail in command:
            raise RuntimeError("controlled dependency failure")
        if command[:3] == ["docker", "container", "inspect"]:
            return json.dumps([self.container]) if self.container else ""
        if command[:2] == ["getent", "passwd"]:
            return self.account
        if command[0] == "useradd":
            self.account = "runab:x:995:995::/nonexistent:/sbin/nologin"
        if command[:2] == ["id", "-Gn"]:
            return "runab"
        if command[:3] == ["docker", "image", "inspect"]:
            return json.dumps([self.digest])
        if command[:2] == ["docker", "run"]:
            def values(flag):
                return [command[i + 1] for i, value in enumerate(command) if value == flag]
            ip, port, _ = values("--publish")[0].split(":")
            self.container = {
                "Config": {"Image": self.digest, "Labels": dict(value.split("=", 1) for value in values("--label")),
                           "Env": values("--env"), "Cmd": command[command.index(self.digest) + 1:]},
                "HostConfig": {"Memory": int(values("--memory")[0][:-1]) * 1024**2,
                               "MemorySwap": int(values("--memory-swap")[0][:-1]) * 1024**2,
                               "PidsLimit": int(values("--pids-limit")[0]),
                               "RestartPolicy": {"Name": values("--restart")[0]},
                               "PortBindings": {"3306/tcp": [{"HostIp": ip, "HostPort": port}]},
                               "LogConfig": {"Type": values("--log-driver")[0],
                                             "Config": dict(value.split("=", 1) for value in values("--log-opt"))}},
                "Mounts": [{"Source": value.split("src=", 1)[1].split(",")[0],
                            "Destination": value.split("dst=", 1)[1].split(",")[0],
                            "RW": "readonly" not in value} for value in values("--mount")]}
        if command[:3] == ["systemctl", "is-active", "nginx"]:
            return "active" if self.nginx_active else ""
        if command == ["systemctl", "enable", "--now", "nginx"]:
            self.nginx_active = True
        if command == ["nginx", "-T"]:
            return (self.root / "etc/nginx/conf.d/runab.conf").read_text()
        if command[:2] == ["docker", "exec"]:
            if input and "information_schema.TABLES" in input:
                return self.tables
            return "1\n"
        return ""

    def bootstrap(self):
        return self.module().Bootstrap(self.root, self.runner, free_bytes=6 * 1024**3)

    def test_apply_generates_private_secrets_and_safe_persistent_container_once(self):
        tool = self.bootstrap()
        tool.apply("run-a-b.com")
        env = self.root / "etc/runab/api.env"
        original = env.read_bytes()
        self.assertEqual(0o600, env.stat().st_mode & 0o777)
        self.assertIn(b"DB_USERNAME=runab_app", original)
        self.assertNotIn(b"DB_USERNAME=root", original)
        self.assertIn(b"127.0.0.1:3307/runab_db", original)
        run = next(args for args, _ in self.commands if args[:2] == ["docker", "run"])
        self.assertIn("127.0.0.1:3307:3306", run)
        self.assertIn("512m", run)
        self.assertIn(self.digest, run)
        for line in original.decode().splitlines():
            if "PASSWORD=" in line or "JWT_SECRET=" in line:
                self.assertNotIn(line.split("=", 1)[1], repr(self.commands))
        tool.apply("run-a-b.com")
        self.assertEqual(original, env.read_bytes())
        self.assertEqual(1, sum(args[:2] == ["docker", "run"] for args, _ in self.commands))
        self.assertTrue((self.root / "srv/runab/releases").is_dir())
        self.assertFalse((self.root / "srv/runab/current").exists())
        self.assertFalse(any(args[:3] == ["systemctl", "start", "runab-api"] for args, _ in self.commands))

    def test_existing_unmanaged_files_and_container_fail_closed(self):
        tool = self.bootstrap()
        path = self.root / "etc/runab/api.env"
        path.parent.mkdir(parents=True)
        path.write_text("existing credentials")
        with self.assertRaises(Exception):
            tool.apply("run-a-b.com")
        self.assertEqual("existing credentials", path.read_text())
        self.assertEqual([], self.commands)

    def test_existing_database_without_marker_is_never_adopted(self):
        tool = self.bootstrap()
        path = self.root / "var/lib/runab/mysql"
        path.mkdir(parents=True)
        (path / "existing-data").write_text("keep")
        with self.assertRaises(Exception):
            tool.apply("run-a-b.com")
        self.assertEqual("keep", (path / "existing-data").read_text())

    def test_changed_managed_configuration_and_container_are_rejected(self):
        tool = self.bootstrap()
        tool.apply("run-a-b.com")
        self.container["HostConfig"]["PortBindings"]["3306/tcp"][0]["HostIp"] = "0.0.0.0"
        with self.assertRaises(Exception):
            tool.apply("run-a-b.com")
        self.container["HostConfig"]["PortBindings"]["3306/tcp"][0]["HostIp"] = "127.0.0.1"
        env = self.root / "etc/runab/api.env"
        env.chmod(0o644)
        with self.assertRaises(Exception):
            tool.apply("run-a-b.com")

    def test_low_disk_and_install_failure_never_report_completion(self):
        module = self.module()
        with self.assertRaises(Exception):
            module.Bootstrap(self.root, self.runner, free_bytes=1024**3).apply("run-a-b.com")
        self.assertEqual([], self.commands)
        self.fail = "dnf"
        with self.assertRaises(Exception):
            self.bootstrap().apply("run-a-b.com")
        self.assertFalse((self.root / "etc/runab/bootstrap.json").exists())

    def test_preflight_refuses_http_only_staging_without_starting_application(self):
        tool = self.bootstrap()
        tool.apply("run-a-b.com")
        self.commands.clear()
        with self.assertRaises(Exception):
            tool.preflight("run-a-b.com")
        self.assertFalse(any(args[:2] == ["systemctl", "start"] for args, _ in self.commands))

    def install_https_fixture(self):
        config = Path(__file__).with_name("nginx.conf.template").read_text().replace("RUNAB_DOMAIN", "run-a-b.com")
        (self.root / "etc/nginx/conf.d/runab.conf").write_text(config)
        certs = self.root / "etc/letsencrypt/live/run-a-b.com"
        certs.mkdir(parents=True)
        for name in ("cert.pem", "chain.pem", "fullchain.pem", "privkey.pem"):
            (certs / name).write_text("certificate fixture handled by controlled openssl process")

    def test_preflight_accepts_checked_https_and_complete_schema_without_starting_app(self):
        tool = self.bootstrap()
        tool.apply("run-a-b.com")
        self.install_https_fixture()
        self.commands.clear()
        tool.preflight("run-a-b.com")
        verification = next(args for args, _ in self.commands if args[:2] == ["openssl", "verify"])
        self.assertIn("-verify_hostname", verification)
        self.assertIn("-purpose", verification)
        self.assertFalse(any("start" in args or "run" in args for args, _ in self.commands))

    def test_preflight_rejects_certificate_failure_and_missing_schema(self):
        tool = self.bootstrap()
        tool.apply("run-a-b.com")
        self.install_https_fixture()
        self.fail = "openssl"
        with self.assertRaises(RuntimeError):
            tool.preflight("run-a-b.com")
        self.fail = None
        self.tables = "policy\nusers\n"
        with self.assertRaises(RuntimeError):
            tool.preflight("run-a-b.com")

    def test_bootstrap_creates_unprivileged_service_user_and_crud_only_db_grants(self):
        self.bootstrap().apply("run-a-b.com")
        unit = (self.root / "etc/systemd/system/runab-api.service").read_text()
        self.assertIn("User=runab\nGroup=runab", unit)
        created_user = next(args for args, _ in self.commands if args[0] == "useradd")
        self.assertIn("--system", created_user)
        self.assertIn("/sbin/nologin", created_user)
        grants = next(stdin for args, stdin in self.commands if stdin and "GRANT " in stdin)
        self.assertIn("GRANT SELECT, INSERT, UPDATE, DELETE ON", grants)
        self.assertNotIn("GRANT ALL", grants)

    def test_integrations_preserve_db_jwt_and_quote_values_without_command_leak(self):
        tool = self.bootstrap()
        tool.apply("run-a-b.com")
        env_file = self.root / "etc/runab/api.env"
        before = env_file.read_text()
        self.commands.clear()
        tool.configure_integrations("run-a-b.com", {"OPENAI_API_KEY": 'test "quoted" \\ $value', "MAIL_APP_PASSWORD": "space separated password"})
        after = env_file.read_text()
        for line in before.splitlines():
            self.assertIn(line + "\n", after)
        self.assertIn('OPENAI_API_KEY="test \\"quoted\\" \\\\ $value"', after)
        self.assertEqual(0o600, env_file.stat().st_mode & 0o777)
        self.assertEqual([], self.commands)
        tool.apply("run-a-b.com")

    def test_integrations_reject_unknown_keys_non_strings_and_control_characters(self):
        tool = self.bootstrap()
        tool.apply("run-a-b.com")
        env_file = self.root / "etc/runab/api.env"
        before = env_file.read_bytes()
        for payload in ({"DB_PASSWORD": "replacement"}, {"OPENAI_API_KEY": "bad\nDB_USERNAME=root"},
                        {"OPENAI_API_KEY": None}, {"OPENAI_API_KEY": "bad\x00value"}, ["OPENAI_API_KEY"]):
            with self.assertRaises(RuntimeError):
                tool.configure_integrations("run-a-b.com", payload)
            self.assertEqual(before, env_file.read_bytes())

    def test_replay_rejects_privileged_service_account(self):
        tool = self.bootstrap()
        tool.apply("run-a-b.com")
        self.account = "runab:x:0:0::/root:/bin/bash"
        with self.assertRaises(RuntimeError):
            tool.apply("run-a-b.com")

    def test_apply_waits_for_final_networked_server_before_adjusting_application_grants(self):
        module = self.module()
        final_server = False

        def finish_initialization(seconds):
            nonlocal final_server
            final_server = True

        def startup_runner(args, *, input=None, allow_failure=False):
            if args[:2] == ["docker", "exec"]:
                # Official entrypoint's temporary init server accepts socket
                # queries after setting root's password but before creating users.
                if "--protocol=TCP" in args and not final_server:
                    raise RuntimeError("temporary initialization server has networking disabled")
                if input and "REVOKE " in input and not final_server:
                    raise RuntimeError("application user/grants do not exist yet")
            return self.runner(args, input=input, allow_failure=allow_failure)

        tool = module.Bootstrap(self.root, startup_runner, free_bytes=6 * 1024**3)
        with patch.object(module.time, "sleep", side_effect=finish_initialization):
            tool.apply("run-a-b.com")

        self.assertTrue(final_server)
        self.assertTrue((self.root / "etc/runab/bootstrap.json").is_file())


if __name__ == "__main__":
    unittest.main()
