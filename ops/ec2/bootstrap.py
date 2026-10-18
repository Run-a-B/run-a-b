#!/usr/bin/env python3
"""Fresh AL2023 host preparation; no application start, data import, or TLS issuance.

Usage: sudo -n python3 bootstrap.py apply --domain run-a-b.com
       sudo -n python3 bootstrap.py preflight --domain run-a-b.com

Upload this file beside ec2_ops.py and runab-api.service.template before apply.
Existing unmanaged resources or modified managed files are refused, never replaced.
After bootstrap, supply real integration secrets, reviewed schema, and trusted TLS;
replace /etc/nginx/conf.d/runab.conf with the reviewed HTTPS template and reload.
Preflight validates prerequisites; prod Hibernate validation remains authoritative.
"""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import secrets
import shutil
import stat
import subprocess
import sys
import tempfile
import time

LABEL = "com.runab.bootstrap"
DIGEST = re.compile(r"(?:docker\.io/library/)?mysql@sha256:[0-9a-f]{64}")
TABLES = {"business_info", "email_verification", "policy", "policy_card", "policy_summary", "report", "users"}
INTEGRATIONS = {"GOOGLE_CLIENT_ID", "MAIL_USERNAME", "MAIL_APP_PASSWORD", "OPENAI_API_KEY", "OPENAI_MODEL", "BIZINFO_API_KEY"}
MYSQL_OPTIONS = ["--innodb-buffer-pool-size=128M", "--innodb-redo-log-capacity=64M",
                 "--innodb-log-buffer-size=8M", "--performance-schema=OFF", "--max-connections=20",
                 "--table-open-cache=200", "--temptable-max-ram=16M", "--temptable-max-mmap=0",
                 "--mysqlx=OFF", "--skip-log-bin", "--character-set-server=utf8mb4",
                 "--collation-server=utf8mb4_0900_ai_ci"]


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def command(args, *, input=None, allow_failure=False):
    result = subprocess.run(args, input=input, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, timeout=900)
    if result.returncode and not allow_failure:
        raise RuntimeError("dependency failed: " + args[0] + "; inspect host locally (output suppressed)")
    return result.stdout.strip() if result.returncode == 0 else ""


def expected_container(digest):
    return {"Config": {"Image": digest, "Labels": {LABEL: "1"}, "Cmd": MYSQL_OPTIONS},
            "HostConfig": {"Memory": 512 * 1024**2, "MemorySwap": 512 * 1024**2,
                           "PidsLimit": 128, "RestartPolicy": {"Name": "unless-stopped"},
                           "PortBindings": {"3306/tcp": [{"HostIp": "127.0.0.1", "HostPort": "3307"}]},
                           "LogConfig": {"Type": "local", "Config": {"max-size": "10m", "max-file": "3"}}}}


class Bootstrap:
    def __init__(self, root=Path("/"), runner=command, free_bytes=None):
        self.root, self.run = root, runner
        self.free_bytes = shutil.disk_usage(root).free if free_bytes is None else free_bytes

    def path(self, name):
        path = self.root / name.lstrip("/")
        for parent in (path, *path.parents):
            require(not parent.is_symlink(), "managed paths must not contain symlinks")
            if parent == self.root:
                break
        return path

    def write_new(self, name, text, mode=0o600):
        path = self.path(name)
        path.parent.mkdir(parents=True, exist_ok=True)
        with open(path, "x", opener=lambda p, flags: os.open(p, flags, mode)) as handle:
            handle.write(text)
            handle.flush()
            os.fsync(handle.fileno())
        path.chmod(mode)

    def private(self, name):
        path = self.path(name)
        require(path.is_file() and path.stat().st_uid == os.geteuid()
                and stat.S_IMODE(path.stat().st_mode) == 0o600, "required root-owned 0600 file is missing or insecure: " + name)
        return path

    def inspect(self):
        output = self.run(["docker", "container", "inspect", "runab-mysql"], allow_failure=True)
        return json.loads(output)[0] if output else None

    def check_user(self):
        account = self.run(["getent", "passwd", "runab"]).split(":")
        require(len(account) == 7 and account[0] == "runab" and int(account[2]) >= 100
                and int(account[3]) != 0 and account[5] == "/nonexistent" and account[6] == "/sbin/nologin",
                "runab must be a dedicated unprivileged no-login account")
        require(self.run(["id", "-Gn", "runab"]).split() == ["runab"], "runab must not have supplementary privileged groups")

    def check_container(self, actual, digest):
        require(actual is not None, "managed MySQL container is missing")
        expected = expected_container(digest)
        for section, entries in expected.items():
            for key, value in entries.items():
                if key == "Labels":
                    require(actual[section].get(key, {}).get(LABEL) == "1", "unmanaged MySQL container")
                else:
                    require(actual[section].get(key) == value, "MySQL container configuration changed: " + key)
        mounts = {(mount["Source"], mount["Destination"], mount["RW"]) for mount in actual.get("Mounts", [])}
        require(mounts == {("/var/lib/runab/mysql", "/var/lib/mysql", True),
                           ("/etc/runab/mysql", "/run/runab-secrets", False)}, "unexpected MySQL mounts")

    def sql(self, sql):
        # The image's temporary initialization server is socket-only. Requiring
        # loopback TCP prevents root readiness before app users/grants exist.
        return self.run(["docker", "exec", "-i", "--user", "0", "runab-mysql", "mysql",
                         "--defaults-extra-file=/run/runab-secrets/root-client.cnf", "--protocol=TCP",
                         "--host=127.0.0.1", "--port=3306", "--batch", "--skip-column-names"], input=sql)

    def wait_database(self):
        deadline = time.monotonic() + 180
        while True:
            try:
                if self.sql("SELECT 1;\n").strip() == "1":
                    return
            except RuntimeError:
                pass
            require(time.monotonic() < deadline, "MySQL did not become ready; retained data/secrets require inspection")
            time.sleep(2)

    def apply(self, domain):
        self.valid_domain(domain)
        marker = self.path("/etc/runab/bootstrap.json")
        if marker.exists():
            state = json.loads(self.private("/etc/runab/bootstrap.json").read_text())
            require(state["version"] == 1 and state["domain"] == domain, "bootstrap domain/version conflict")
            self.check_user()
            for name, expected in state["files"].items():
                path = self.path(name)
                require(path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == expected,
                        "managed file changed; refusing bootstrap replay: " + name)
                if name.startswith("/etc/runab/"):
                    self.private(name)
            self.check_container(self.inspect(), state["mysql_digest"])
            self.wait_database()
            return

        require(self.free_bytes >= 4 * 1024**3, "at least 4 GiB free root space required before bootstrap")
        managed = ["/etc/runab", "/var/lib/runab/mysql", "/srv/runab/current", "/etc/nginx/conf.d/runab.conf",
                   "/etc/systemd/system/runab-api.service", "/srv/runab/ops/bootstrap.py", "/srv/runab/ops/ec2_ops.py"]
        for name in managed:
            require(not self.path(name).exists(), "existing unmanaged resource: " + name)
        require(not self.run(["getent", "passwd", "runab"], allow_failure=True),
                "existing unmanaged runab account; inspect privileges before adoption")
        if shutil.which("docker"):
            require(self.inspect() is None, "existing unmanaged MySQL container")
        require(not self.run(["systemctl", "is-active", "nginx"], allow_failure=True),
                "nginx is already active; inspect existing configuration first")
        self.run(["dnf", "install", "-y", "java-21-amazon-corretto-headless", "nginx", "docker", "openssl"])
        self.run(["useradd", "--system", "--user-group", "--home-dir", "/nonexistent", "--shell", "/sbin/nologin", "runab"])
        self.check_user()
        self.run(["systemctl", "enable", "--now", "docker"])
        require(self.inspect() is None, "existing unmanaged MySQL container")
        self.run(["docker", "pull", "mysql:8.4"])
        digests = json.loads(self.run(["docker", "image", "inspect", "mysql:8.4", "--format", "{{json .RepoDigests}}"] ))
        digest = next((value for value in digests if DIGEST.fullmatch(value)), None)
        require(digest is not None, "could not pin MySQL 8.4 image digest")

        self.path("/etc/runab").mkdir(mode=0o700, parents=True)
        self.path("/etc/runab/mysql").mkdir(mode=0o700)
        root_password, app_password, jwt = (secrets.token_urlsafe(48) for _ in range(3))
        files = {
            "/etc/runab/mysql/root-password": root_password + "\n",
            "/etc/runab/mysql/app-password": app_password + "\n",
            "/etc/runab/mysql/root-client.cnf": "[client]\nuser=root\npassword=" + root_password + "\nprotocol=socket\n",
            "/etc/runab/api.env": "DB_URL=jdbc:mysql://127.0.0.1:3307/runab_db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8\n"
                "DB_USERNAME=runab_app\nDB_PASSWORD=" + app_password + "\nJWT_SECRET=" + jwt + "\n"
                "TRUSTED_PROXY_IPS=127.0.0.1\nSPRING_PROFILES_ACTIVE=prod\nSERVER_ADDRESS=127.0.0.1\nSERVER_PORT=8080\n",
            "/etc/nginx/conf.d/runab.conf": "server {\n    listen 80;\n    server_name " + domain + ";\n"
                "    location ^~ /.well-known/acme-challenge/ { root /var/lib/runab/acme; }\n"
                "    location / { add_header Cache-Control no-store always; return 503; }\n}\n",
        }
        source = Path(__file__).parent
        unit = (source / "runab-api.service.template").read_text().replace("/usr/bin/java", "/usr/lib/jvm/java-21-amazon-corretto.x86_64/bin/java")
        unit = unit.replace("-Xmx640m", "-Xmx512m").replace("MaxMetaspaceSize=192m", "MaxMetaspaceSize=160m")
        unit = unit.replace("[Install]", "MemoryMax=896M\nMemorySwapMax=0\n\n[Install]")
        files["/etc/systemd/system/runab-api.service"] = unit
        files["/srv/runab/ops/ec2_ops.py"] = (source / "ec2_ops.py").read_text()
        files["/srv/runab/ops/bootstrap.py"] = Path(__file__).read_text()
        for name, text in files.items():
            self.write_new(name, text, 0o600 if name.startswith("/etc/runab/") else 0o644)
        for name in ("/srv/runab", "/srv/runab/releases", "/srv/runab/ops", "/var/lib/runab", "/var/lib/runab/acme"):
            self.path(name).mkdir(mode=0o755, parents=True, exist_ok=True)
            self.path(name).chmod(0o755)
        self.path("/var/lib/runab/mysql").mkdir(mode=0o700)
        self.run(["docker", "run", "--detach", "--name", "runab-mysql", "--label", LABEL + "=1",
                  "--restart", "unless-stopped", "--memory", "512m", "--memory-swap", "512m", "--pids-limit", "128",
                  "--publish", "127.0.0.1:3307:3306", "--log-driver", "local", "--log-opt", "max-size=10m", "--log-opt", "max-file=3",
                  "--mount", "type=bind,src=/var/lib/runab/mysql,dst=/var/lib/mysql",
                  "--mount", "type=bind,src=/etc/runab/mysql,dst=/run/runab-secrets,readonly",
                  "--env", "MYSQL_ROOT_HOST=localhost", "--env", "MYSQL_DATABASE=runab_db", "--env", "MYSQL_USER=runab_app",
                  "--env", "MYSQL_ROOT_PASSWORD_FILE=/run/runab-secrets/root-password",
                  "--env", "MYSQL_PASSWORD_FILE=/run/runab-secrets/app-password", digest, *MYSQL_OPTIONS])
        self.wait_database()
        # Application uses DML only; reviewed schema is installed separately by root.
        self.sql("REVOKE ALL PRIVILEGES ON `runab_db`.* FROM 'runab_app'@'%';\n"
                 "GRANT SELECT, INSERT, UPDATE, DELETE ON `runab_db`.* TO 'runab_app'@'%';\n")
        self.run(["nginx", "-t"])
        self.run(["systemctl", "daemon-reload"])
        self.run(["systemctl", "enable", "--now", "nginx"])
        state = {"version": 1, "domain": domain, "mysql_digest": digest,
                 "files": {name: hashlib.sha256(text.encode()).hexdigest() for name, text in files.items()}}
        self.write_new("/etc/runab/bootstrap.json", json.dumps(state, sort_keys=True) + "\n")

    @staticmethod
    def valid_domain(domain):
        require(bool(re.fullmatch(r"(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}", domain)), "invalid domain")

    def preflight(self, domain):
        self.valid_domain(domain)
        self.check_user()
        env = {}
        for line in self.private("/etc/runab/api.env").read_text().splitlines():
            if line and not line.startswith("#"):
                key, value = line.split("=", 1)
                env[key] = value.strip().strip('"')
        require(all(env.get(key) for key in ("DB_URL", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET")), "required production configuration missing")
        require(env["DB_URL"] == "jdbc:mysql://127.0.0.1:3307/runab_db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
                and env["DB_USERNAME"] == "runab_app" and len(env["DB_PASSWORD"]) >= 32 and len(env["JWT_SECRET"]) >= 43,
                "production DB/JWT configuration is unsafe or unexpected")
        config = self.path("/etc/nginx/conf.d/runab.conf").read_text()
        active = "\n".join(line.split("#", 1)[0] for line in config.splitlines())
        certs = "/etc/letsencrypt/live/" + domain
        for required in ("listen 443 ssl;", "server_name " + domain + ";", "root /srv/runab/current/dist;",
                         "proxy_pass http://127.0.0.1:8080;", "proxy_set_header X-Forwarded-For $remote_addr;",
                         "proxy_set_header X-Forwarded-Proto https;", "ssl_certificate " + certs + "/fullchain.pem;",
                         "ssl_certificate_key " + certs + "/privkey.pem;"):
            require(required in active, "reviewed production HTTPS nginx configuration missing")
        # Let's Encrypt live/ paths intentionally contain provider-managed symlinks.
        for name in ("cert.pem", "chain.pem", "fullchain.pem", "privkey.pem"):
            require((self.root / certs.lstrip("/") / name).is_file(), "trusted TLS certificate files missing")
        self.run(["openssl", "x509", "-in", certs + "/cert.pem", "-noout", "-checkend", "86400"])
        self.run(["openssl", "verify", "-verify_hostname", domain, "-purpose", "sslserver", "-CAfile",
                  "/etc/pki/tls/certs/ca-bundle.crt", "-untrusted", certs + "/chain.pem", certs + "/cert.pem"])
        self.run(["nginx", "-t"])
        require(config.strip() in self.run(["nginx", "-T"]), "production nginx file is not included in effective configuration")
        require(self.run(["systemctl", "is-active", "nginx"], allow_failure=True) == "active", "nginx is not active")
        tables = set(self.sql("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='runab_db' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME;\n").splitlines())
        require(TABLES <= tables, "reviewed application schema missing; bootstrap does not create it")

    def configure_integrations(self, domain, values):
        self.valid_domain(domain)
        require(isinstance(values, dict) and set(values) <= INTEGRATIONS, "integration JSON contains unsupported keys")
        require(all(isinstance(value, str) and len(value) <= 4096 and not any(ord(char) < 32 or ord(char) == 127 for char in value)
                    for value in values.values()), "integration values must be bounded strings without control characters")
        env_file = self.private("/etc/runab/api.env")
        marker = self.private("/etc/runab/bootstrap.json")
        state = json.loads(marker.read_text())
        require(state["version"] == 1 and state["domain"] == domain, "bootstrap domain/version conflict")
        original = env_file.read_text()
        require(state["files"]["/etc/runab/api.env"] == hashlib.sha256(original.encode()).hexdigest(), "environment changed outside managed configuration")
        lines = [line for line in original.splitlines() if line.split("=", 1)[0] not in values]
        for key, value in sorted(values.items()):
            lines.append(key + '="' + value.replace("\\", "\\\\").replace('"', '\\"') + '"')
        updated = "\n".join(lines) + "\n"
        self.replace_private(env_file, updated)
        state["files"]["/etc/runab/api.env"] = hashlib.sha256(updated.encode()).hexdigest()
        self.replace_private(marker, json.dumps(state, sort_keys=True) + "\n")

    @staticmethod
    def replace_private(path, text):
        fd, name = tempfile.mkstemp(prefix=".runab-", dir=path.parent)
        temporary = Path(name)
        try:
            with os.fdopen(fd, "w") as handle:
                handle.write(text)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temporary, path)
        finally:
            temporary.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("operation", choices=("apply", "preflight", "configure-integrations"))
    parser.add_argument("--domain", required=True)
    args = parser.parse_args()
    try:
        require(os.geteuid() == 0, "run as root with sudo -n")
        os_release = Path("/etc/os-release").read_text()
        require(re.search(r'^ID="?amzn"?$', os_release, re.M) and re.search(r'^VERSION_ID="?2023"?$', os_release, re.M)
                and platform.machine() == "x86_64", "requires Amazon Linux 2023 x86_64")
        os.umask(0o077)
        with open("/run/runab-bootstrap.lock", "a") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            tool = Bootstrap()
            if args.operation == "configure-integrations":
                payload = sys.stdin.read(65537)
                require(len(payload) <= 65536, "integration JSON is too large")
                tool.configure_integrations(args.domain, json.loads(payload))
            else:
                getattr(tool, args.operation)(args.domain)
        print(json.dumps({"operation": args.operation, "domain": args.domain, "status": "ready"}))
        return 0
    except (RuntimeError, OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        print("ERROR: " + (str(error) if isinstance(error, RuntimeError) else "bootstrap failed; inspect retained local state"), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
