#!/usr/bin/env python3
"""One-off completion of runab-infra-39141443656-1, never fresh installation.

Execute only after review on the pinned 43.201.103.134 SSH connection:
  sudo -n python3 -I /tmp/runab-ec2-finalize.py
No deletion, secret generation, schema/data import, application start, or TLS work.
"""
import fcntl
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import stat
import subprocess
import sys
import time
import types

DOMAIN = "run-a-b.com"
BUNDLE = "/tmp/runab-infra-39141443656-1"
SOURCE_HASHES = {
    "bootstrap.py": "94216fe9b50f45be86b25b9947cf25c254f7b87434588596b61db76d194ab173",
    "ec2_ops.py": "79672f2c63a888588692c8a75f44e6ca6137c745b0e4f91d489d1ec72b134f9e",
    "runab-api.service.template": "f8555c5cf7d1e1ab63642218cb4bda0122087c5fb6fdbfc8fa18eb7b54981a43",
}
UNIT_HASH = "e664ff38ff17fe6ccc2e2b7135ffb5c2e9b84911ce6bd8337563e85678b51609"


def require(value):
    if not value:
        raise RuntimeError("one-off prerequisite mismatch")


def run(args, *, input=None, allow_failure=False):
    completed = subprocess.run(args, input=input, text=True, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, timeout=60)
    require(completed.returncode == 0 or allow_failure)
    return completed.stdout.strip() if completed.returncode == 0 else ""


def finalize(root=Path("/"), runner=run, mysql_uid=999):
    expected_owner = os.geteuid()
    def path(name):
        value = root / name.lstrip("/")
        for parent in (value, *value.parents):
            require(not parent.is_symlink())
            if parent == root:
                break
        return value

    def read(name, *, mode=None, owner=expected_owner):
        value = path(name)
        require(value.is_file())
        info = value.stat()
        require(owner is None or info.st_uid == owner)
        require(mode is None or stat.S_IMODE(info.st_mode) == mode)
        return value.read_bytes()

    def directory(name, mode, owner=expected_owner):
        value = path(name)
        require(value.is_dir() and value.stat().st_uid == owner
                and stat.S_IMODE(value.stat().st_mode) == mode)

    require(not path("/etc/runab/bootstrap.json").exists())
    require(not path("/srv/runab/current").exists())
    originals = {}
    for name, expected in SOURCE_HASHES.items():
        originals[name] = read(BUNDLE + "/" + name, owner=None)
        require(hashlib.sha256(originals[name]).hexdigest() == expected)
    print(json.dumps({"original_sources_verified": True}), flush=True)
    bootstrap = types.ModuleType("approved_original_bootstrap")
    bootstrap.__file__ = BUNDLE + "/bootstrap.py"
    # Execute exactly the snapshotted, pinned source bytes, not a second file read.
    exec(compile(originals["bootstrap.py"], bootstrap.__file__, "exec"), bootstrap.__dict__)
    original_expected_container = bootstrap.expected_container

    def corrected_expected_container(digest):
        expected = original_expected_container(digest)
        # Correct exactly the known Docker inspect shape in the pinned old helper.
        require(expected["HostConfig"]["RestartPolicy"] == {"Name": "unless-stopped"})
        expected["HostConfig"]["RestartPolicy"] = {"Name": "unless-stopped", "MaximumRetryCount": 0}
        return expected

    bootstrap.expected_container = corrected_expected_container
    tool = bootstrap.Bootstrap(root, runner)
    tool.check_user()
    print(json.dumps({"account_verified": True}), flush=True)

    installed = {
        "/srv/runab/ops/bootstrap.py": read("/srv/runab/ops/bootstrap.py", mode=0o644),
        "/srv/runab/ops/ec2_ops.py": read("/srv/runab/ops/ec2_ops.py", mode=0o644),
        "/etc/systemd/system/runab-api.service": read("/etc/systemd/system/runab-api.service", mode=0o644),
        "/etc/nginx/conf.d/runab.conf": read("/etc/nginx/conf.d/runab.conf", mode=0o644),
    }
    require(installed["/srv/runab/ops/bootstrap.py"] == originals["bootstrap.py"])
    require(installed["/srv/runab/ops/ec2_ops.py"] == originals["ec2_ops.py"])
    require(hashlib.sha256(installed["/etc/systemd/system/runab-api.service"]).hexdigest() == UNIT_HASH)
    expected_nginx = ("server {\n    listen 80;\n    server_name " + DOMAIN + ";\n"
                      "    location ^~ /.well-known/acme-challenge/ { root /var/lib/runab/acme; }\n"
                      "    location / { add_header Cache-Control no-store always; return 503; }\n}\n").encode()
    require(installed["/etc/nginx/conf.d/runab.conf"] == expected_nginx)
    print(json.dumps({"installed_config_verified": True}), flush=True)
    for name in ("/srv/runab", "/srv/runab/releases", "/srv/runab/ops", "/var/lib/runab", "/var/lib/runab/acme"):
        directory(name, 0o755)
    require(not any(path("/srv/runab/releases").iterdir()))
    print(json.dumps({"release_directories_verified": True}), flush=True)
    directory("/etc/runab", 0o700)
    directory("/etc/runab/mysql", 0o700)
    print(json.dumps({"private_directories_verified": True}), flush=True)
    directory("/var/lib/runab/mysql", 0o700, owner=mysql_uid)
    print(json.dumps({"mysql_directory_verified": True}), flush=True)
    print(json.dumps({"directories_verified": True}), flush=True)

    private_names = ("/etc/runab/mysql/root-password", "/etc/runab/mysql/app-password",
                     "/etc/runab/mysql/root-client.cnf", "/etc/runab/api.env")
    private = {name: read(name, mode=0o600) for name in private_names}
    root_password = private[private_names[0]].decode().removesuffix("\n")
    app_password = private[private_names[1]].decode().removesuffix("\n")
    require(private[private_names[0]] == (root_password + "\n").encode()
            and private[private_names[1]] == (app_password + "\n").encode())
    raw_env = private["/etc/runab/api.env"].decode()
    env = dict(line.split("=", 1) for line in raw_env.splitlines())
    jwt = env["JWT_SECRET"]
    require(all(re.fullmatch(r"[A-Za-z0-9_-]{64}", value) for value in (root_password, app_password, jwt)))
    require(len({root_password, app_password, jwt}) == 3)
    require(private["/etc/runab/mysql/root-client.cnf"] == (
        "[client]\nuser=root\npassword=" + root_password + "\nprotocol=socket\n").encode())
    expected_env = ("DB_URL=jdbc:mysql://127.0.0.1:3307/runab_db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8\n"
                    "DB_USERNAME=runab_app\nDB_PASSWORD=" + app_password + "\nJWT_SECRET=" + jwt + "\n"
                    "TRUSTED_PROXY_IPS=127.0.0.1\nSPRING_PROFILES_ACTIVE=prod\nSERVER_ADDRESS=127.0.0.1\nSERVER_PORT=8080\n")
    require(raw_env == expected_env)
    print(json.dumps({"private_config_verified": True}), flush=True)

    # No pull or external resolution. Match the exact locally pinned 8.4 artifact
    # used by the original bootstrap to its container's manifest and image ID.
    image = json.loads(runner(["docker", "image", "inspect", "mysql:8.4"]))[0]
    candidates = [digest for digest in image["RepoDigests"]
                  if re.fullmatch(r"(?:docker\.io/library/)?mysql@sha256:[0-9a-f]{64}", digest)]
    require(len(candidates) == 1)
    digest = candidates[0]
    image_env = dict(value.split("=", 1) for value in image["Config"]["Env"])
    require(image_env.get("MYSQL_MAJOR") == "8.4" and image_env.get("MYSQL_VERSION", "").startswith("8.4."))
    print(json.dumps({"image_verified": True}), flush=True)
    actual = tool.inspect()
    tool.check_container(actual, digest)
    require(actual["State"]["Running"] and not actual["State"]["OOMKilled"]
            and not actual["State"].get("Paused", False))
    require(image["Id"] == actual["Image"])
    container_env = dict(value.split("=", 1) for value in actual["Config"]["Env"])
    require(all(container_env.get(key) == value for key, value in {
        "MYSQL_ROOT_HOST": "localhost", "MYSQL_DATABASE": "runab_db", "MYSQL_USER": "runab_app",
        "MYSQL_ROOT_PASSWORD_FILE": "/run/runab-secrets/root-password",
        "MYSQL_PASSWORD_FILE": "/run/runab-secrets/app-password"}.items()))
    require(not {"MYSQL_ALLOW_EMPTY_PASSWORD", "MYSQL_RANDOM_ROOT_PASSWORD", "MYSQL_ROOT_PASSWORD", "MYSQL_PASSWORD"} & container_env.keys())
    print(json.dumps({"container_verified": True}), flush=True)
    require(not runner(["systemctl", "is-active", "runab-api"], allow_failure=True))
    require(not runner(["systemctl", "is-active", "nginx"], allow_failure=True))
    print(json.dumps({"services_inactive": True}), flush=True)
    print(json.dumps({"partial_state_verified": True}))

    ping = ["docker", "exec", "--user", "0", "runab-mysql", "mysqladmin", "--no-defaults",
            "--protocol=TCP", "--host=127.0.0.1", "--port=3306", "--connect-timeout=3",
            "--user=runab_readiness", "--skip-password", "ping"]
    mysql = ["docker", "exec", "-i", "--user", "0", "runab-mysql", "mysql",
             "--defaults-extra-file=/run/runab-secrets/root-client.cnf", "--protocol=SOCKET",
             "--connect-timeout=3", "--batch", "--skip-column-names"]
    deadline = time.monotonic() + 120
    while True:
        try:
            # Ping's exit status confirms the final networked server, including
            # access-denied replies. Root authentication remains socket-only.
            runner(ping)
            if runner(mysql, input="SELECT 1;\n").strip() == "1":
                break
        except RuntimeError:
            pass
        require(time.monotonic() < deadline)
        time.sleep(2)
    print(json.dumps({"final_mysql_ready": True}))
    runner(mysql, input="REVOKE IF EXISTS ALL PRIVILEGES ON `runab\\_db`.* FROM 'runab_app'@'%';\n"
                       "GRANT SELECT, INSERT, UPDATE, DELETE ON `runab\\_db`.* TO 'runab_app'@'%';\n")
    print(json.dumps({"application_crud_applied": True}))
    runner(["nginx", "-t"])
    runner(["systemctl", "daemon-reload"])
    runner(["systemctl", "enable", "--now", "nginx"])
    require(runner(["systemctl", "is-active", "nginx"], allow_failure=True) == "active")
    require(not runner(["systemctl", "is-active", "runab-api"], allow_failure=True))
    print(json.dumps({"maintenance_nginx_ready": True}))
    all_files = {**installed, **private}
    state = {"version": 1, "domain": DOMAIN, "mysql_digest": digest,
             "files": {name: hashlib.sha256(content).hexdigest() for name, content in all_files.items()}}
    tool.write_new("/etc/runab/bootstrap.json", json.dumps(state, sort_keys=True) + "\n")
    print(json.dumps({"completion_marker_created": True}))


def main():
    try:
        require(os.geteuid() == 0 and platform.machine() == "x86_64")
        release = Path("/etc/os-release").read_text()
        require(re.search(r'^ID="?amzn"?$', release, re.M) and re.search(r'^VERSION_ID="?2023"?$', release, re.M))
        os.umask(0o077)
        with open("/run/runab-bootstrap.lock", "a") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            finalize()
        return 0
    except Exception:
        print(json.dumps({"finalize": False}), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
