#!/usr/bin/env python3
"""Read-only health checks; output only fixed boolean fields."""
import json
import os
import subprocess
import sys


def check(command, sql=None):
    try:
        result = subprocess.run(command, input=sql, text=True, capture_output=True, timeout=10)
        return result.returncode == 0 and (sql is None or result.stdout.strip() == "1")
    except (OSError, subprocess.TimeoutExpired):
        return False


def main():
    if os.geteuid() != 0:
        return 1
    prefix = ["docker", "exec", "-i", "--user", "0", "runab-mysql"]
    defaults = "--defaults-extra-file=/run/runab-secrets/root-client.cnf"
    tcp = ["--protocol=TCP", "--host=127.0.0.1", "--port=3306", "--connect-timeout=3"]
    sql = ["--batch", "--skip-column-names"]
    result = {
        "mysql_socket_authenticated": check(prefix + ["mysql", defaults, "--protocol=SOCKET"] + sql, "SELECT 1;\n"),
        "mysql_tcp_authenticated": check(prefix + ["mysql", defaults] + tcp + sql, "SELECT 1;\n"),
        "mysql_tcp_server_reachable": check(prefix + ["mysqladmin", defaults] + tcp + ["ping"]),
        "nginx_active": check(["systemctl", "is-active", "--quiet", "nginx"]),
        "api_active": check(["systemctl", "is-active", "--quiet", "runab-api"]),
    }
    print(json.dumps(result))
    return 0


if __name__ == "__main__":
    sys.exit(main())
