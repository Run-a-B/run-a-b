#!/usr/bin/env python3
"""Root-only, pinned fresh-schema/public-policy preparation. Never starts an app.

prepare/verify --schema PATH --seed PATH
configure-integrations reads exactly the existing six integration keys as JSON stdin.
All subprocess output stays in memory; the CLI emits fixed health booleans only.
Partial DDL or a committed import without its completion marker requires local root
inspection. Never repair such a state by deleting tables, data, or the marker.
"""
import argparse
import fcntl
import gzip
import hashlib
import io
import json
import os
from pathlib import Path
import platform
import re
import stat
import subprocess
import sys
import tempfile
import types

SCHEMA_SHA = '25330a620f8debecf82bd7cf60fcceaeb5bf3a0512db6308ab3ab9b2d93735de'
SEED_SHA = '2012fb83362ba98f5d3d79dae608d0b108461a70dd2b16f8fa9659839e647266'
BOOTSTRAP_SHA = '94216fe9b50f45be86b25b9947cf25c254f7b87434588596b61db76d194ab173'
DOMAIN = 'run-a-b.com'
TABLES = {'business_info', 'email_verification', 'policy', 'policy_card', 'policy_summary', 'report', 'users'}
INTEGRATIONS = {'GOOGLE_CLIENT_ID', 'MAIL_USERNAME', 'MAIL_APP_PASSWORD', 'OPENAI_API_KEY', 'OPENAI_MODEL', 'BIZINFO_API_KEY'}
MAX_SQL = 16 * 1024 * 1024
STATE = '/etc/runab/database-prepared.json'
MANAGED = {'/srv/runab/ops/bootstrap.py', '/srv/runab/ops/ec2_ops.py',
           '/etc/systemd/system/runab-api.service', '/etc/nginx/conf.d/runab.conf',
           '/etc/runab/mysql/root-password', '/etc/runab/mysql/app-password',
           '/etc/runab/mysql/root-client.cnf', '/etc/runab/api.env'}


def require(value):
    if not value:
        raise RuntimeError('preparation prerequisite failed; inspect retained state locally')


def digest(value):
    return hashlib.sha256(value).hexdigest()


def command(args, *, input=None, allow_failure=False):
    result = subprocess.run(args, input=input, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, timeout=900)
    require(not result.returncode or allow_failure)
    return result.stdout.strip() if not result.returncode else ''


def payloads(schema_path, seed_path):
    # Read and pin before decoding or constructing SQL. No plaintext seed file.
    with open(schema_path, 'rb') as handle:
        raw_schema = handle.read(128 * 1024 + 1)
    with open(seed_path, 'rb') as handle:
        raw_seed = handle.read(2 * 1024 * 1024 + 1)
    require(digest(raw_schema) == SCHEMA_SHA and digest(raw_seed) == SEED_SHA)
    with gzip.GzipFile(fileobj=io.BytesIO(raw_seed)) as compressed:
        raw_sql = compressed.read(MAX_SQL + 1)
        require(len(raw_sql) <= MAX_SQL)
    schema = raw_schema.decode('utf-8')
    inserts = []
    for line in raw_sql.decode('utf-8').splitlines():
        if not line or line == '--' or line.startswith('-- ') or line.startswith('/*!'):
            continue
        require(re.fullmatch(r'INSERT INTO `policy` \((?:`[a-z_]+`,? ?)+\) VALUES .+;', line))
        inserts.append(line)
    require(len(inserts) == 5)
    # Pinned INSERTs only; discard dump SET/autocommit/lock directives. Constraints
    # stay enabled, and a mysql connection failure rolls back the entire seed.
    seed = "SET NAMES utf8mb4;\nSET SESSION sql_mode='STRICT_TRANS_TABLES,NO_AUTO_VALUE_ON_ZERO';\nSET SESSION time_zone='+00:00';\nSTART TRANSACTION;\n"
    return schema, seed + '\n'.join(inserts) + '\nCOMMIT;\n'


def schema_contract(schema):
    columns, indexes, foreign_keys = [], [], []
    sections = re.findall(r'CREATE TABLE `([a-z_]+)` \(\n(.*?)\n\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;', schema, re.S)
    require({name for name, _ in sections} == TABLES and len(sections) == 7)
    for table, body in sections:
        position = 0
        for line in body.splitlines():
            col = re.match(r"  `([a-z_]+)` ([a-z]+(?:\([^)]*\))?)(.*)", line)
            if col:
                position += 1
                name, kind, rest = col.groups()
                default = re.search(r" DEFAULT '([^']*)'", rest)
                character = kind.startswith(('varchar', 'enum', 'text', 'longtext'))
                columns.append((table, name, str(position), kind, 'NO' if 'NOT NULL' in rest else 'YES',
                                default.group(1) if default else '<NULL>',
                                'auto_increment' if 'AUTO_INCREMENT' in rest else '-',
                                'utf8mb4' if character else '<NULL>', 'utf8mb4_0900_ai_ci' if character else '<NULL>'))
            index = re.match(r'  (PRIMARY KEY|UNIQUE KEY `([^`]+)`|KEY `([^`]+)`) \(([^)]+)\)', line)
            if index:
                prefix, unique_name, plain_name, names = index.groups()
                name = unique_name or plain_name or 'PRIMARY'
                for number, field in enumerate(re.findall(r'`([^`]+)`', names), 1):
                    indexes.append((table, name, '1' if plain_name else '0', str(number), field, 'A', '<NULL>', 'BTREE'))
            fk = re.match(r'  CONSTRAINT `([^`]+)` FOREIGN KEY \(`([^`]+)`\) REFERENCES `([^`]+)` \(`([^`]+)`\)', line)
            if fk:
                name, field, target, target_field = fk.groups()
                foreign_keys.append((table, name, field, target, target_field, 'NO ACTION', 'NO ACTION'))
    return sorted(columns), sorted(indexes), sorted(foreign_keys)


class Preparation:
    def __init__(self, root=Path('/'), runner=command):
        self.root, self.run = Path(root), runner

    def path(self, name):
        value = self.root / name.lstrip('/')
        for parent in (value, *value.parents):
            require(not parent.is_symlink())
            if parent == self.root:
                break
        return value

    def read(self, name, private=False):
        value = self.path(name)
        info = value.stat()
        require(stat.S_ISREG(info.st_mode) and info.st_uid == os.geteuid() and info.st_nlink == 1)
        require(stat.S_IMODE(info.st_mode) == (0o600 if private else 0o644))
        return value.read_bytes()

    def save(self, state):
        path = self.path(STATE)
        fd, name = tempfile.mkstemp(prefix='.runab-database-', dir=path.parent)
        temporary = Path(name)
        try:
            with os.fdopen(fd, 'w') as handle:
                handle.write(json.dumps(state, sort_keys=True) + '\n')
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temporary, path)
            directory = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
            try:
                os.fsync(directory)
            finally:
                os.close(directory)
        finally:
            temporary.unlink(missing_ok=True)

    def inactive(self):
        require(not self.path('/srv/runab/current').exists())
        require(self.run(['systemctl', 'show', '--property=ActiveState', '--value', 'runab-api']) == 'inactive')

    def environment(self, integrations=False):
        result = {}
        for line in self.read('/etc/runab/api.env', True).decode().splitlines():
            if not line or line.startswith('#'):
                continue
            key, value = line.split('=', 1)
            require(re.fullmatch('[A-Z_]+', key) and key not in result)
            result[key] = json.loads(value) if value.startswith('"') else value
        require(result.get('DB_URL') == 'jdbc:mysql://127.0.0.1:3307/runab_db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8'
                and result.get('DB_USERNAME') == 'runab_app' and len(result.get('DB_PASSWORD', '')) >= 32
                and len(result.get('JWT_SECRET', '')) >= 43 and result.get('SPRING_PROFILES_ACTIVE') == 'prod'
                and result.get('SERVER_ADDRESS') == '127.0.0.1' and result.get('SERVER_PORT') == '8080'
                and result.get('TRUSTED_PROXY_IPS') == '127.0.0.1')
        if integrations:
            require(all(isinstance(result.get(key), str) and result[key].strip()
                        and result[key].strip().lower() not in {'changeme', 'placeholder', 'todo', 'replace_me'}
                        for key in INTEGRATIONS))
        return result

    def prerequisites(self):
        self.inactive()
        state = json.loads(self.read('/etc/runab/bootstrap.json', True))
        require(state['version'] == 1 and state['domain'] == DOMAIN and set(state['files']) == MANAGED)
        originals = {}
        for name, expected in state['files'].items():
            originals[name] = self.read(name, name.startswith('/etc/runab/'))
            require(digest(originals[name]) == expected)
        source = originals['/srv/runab/ops/bootstrap.py']
        require(digest(source) == BOOTSTRAP_SHA)
        module = types.ModuleType('approved_runab_bootstrap')
        module.__file__ = str(self.path('/srv/runab/ops/bootstrap.py'))
        exec(compile(source, module.__file__, 'exec'), module.__dict__)
        self.bootstrap = module.Bootstrap(self.root, self.run)
        self.bootstrap.check_user()
        actual = self.bootstrap.inspect()
        require(actual and actual['State']['Running'] and not actual['State']['OOMKilled']
                and not actual['State'].get('Paused', False))
        # The installed approved helper predates the reviewed Docker shape fix.
        # Validate the extra field before passing its original expected shape.
        restart = actual['HostConfig']['RestartPolicy']
        require(restart == {'Name': 'unless-stopped', 'MaximumRetryCount': 0})
        restart.pop('MaximumRetryCount')
        self.bootstrap.check_container(actual, state['mysql_digest'])
        self.environment()
        grants = set(self.sql("SHOW GRANTS FOR 'runab_app'@'%';\n").splitlines())
        require(grants == {"GRANT USAGE ON *.* TO `runab_app`@`%`",
                           "GRANT SELECT, INSERT, UPDATE, DELETE ON `runab\\_db`.* TO `runab_app`@`%`"})

    def sql(self, text):
        return self.run(['docker', 'exec', '-i', '--user', '0', 'runab-mysql', 'mysql',
                         '--defaults-extra-file=/run/runab-secrets/root-client.cnf', '--protocol=SOCKET',
                         '--connect-timeout=3', '--default-character-set=utf8mb4', '--batch',
                         '--skip-column-names', '--raw', '--binary-mode', '--database=runab_db'], input=text)

    def objects(self):
        # Objects capable of hidden writes are never adopted, even with no rows.
        require(self.sql("SELECT (SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA='runab_db') + (SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA='runab_db') + (SELECT COUNT(*) FROM information_schema.EVENTS WHERE EVENT_SCHEMA='runab_db');\n") == '0')
        rows = self.sql("SELECT TABLE_NAME,TABLE_TYPE,IFNULL(ENGINE,'<NULL>'),IFNULL(TABLE_COLLATION,'<NULL>') FROM information_schema.TABLES WHERE TABLE_SCHEMA='runab_db' ORDER BY TABLE_NAME;\n")
        result = set()
        for row in rows.splitlines():
            name, kind, engine, collation = row.split('\t')
            require(kind == 'BASE TABLE' and engine == 'InnoDB' and collation == 'utf8mb4_0900_ai_ci')
            result.add(name)
        return result

    def verify_schema(self, schema):
        require(self.objects() == TABLES)
        expected = schema_contract(schema)
        queries = [
            "SELECT TABLE_NAME,COLUMN_NAME,ORDINAL_POSITION,COLUMN_TYPE,IS_NULLABLE,IFNULL(COLUMN_DEFAULT,'<NULL>'),IFNULL(NULLIF(EXTRA,''),'-'),IFNULL(CHARACTER_SET_NAME,'<NULL>'),IFNULL(COLLATION_NAME,'<NULL>') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='runab_db'",
            "SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,COLLATION,IFNULL(SUB_PART,'<NULL>'),INDEX_TYPE FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='runab_db'",
            "SELECT k.TABLE_NAME,k.CONSTRAINT_NAME,k.COLUMN_NAME,k.REFERENCED_TABLE_NAME,k.REFERENCED_COLUMN_NAME,r.UPDATE_RULE,r.DELETE_RULE FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r ON k.CONSTRAINT_SCHEMA=r.CONSTRAINT_SCHEMA AND k.TABLE_NAME=r.TABLE_NAME AND k.CONSTRAINT_NAME=r.CONSTRAINT_NAME WHERE k.CONSTRAINT_SCHEMA='runab_db' AND k.REFERENCED_TABLE_NAME IS NOT NULL",
        ]
        for query, contract in zip(queries, expected):
            rows = self.sql(query + ';\n')
            require(sorted(tuple(row.split('\t')) for row in rows.splitlines()) == contract)

    def counts(self):
        rows = self.sql(' UNION ALL '.join("SELECT '" + name + "',COUNT(*) FROM `" + name + '`' for name in sorted(TABLES)) + ';\n')
        result = {name: int(count) for name, count in (row.split('\t') for row in rows.splitlines())}
        require(set(result) == TABLES)
        return result

    def policy_fingerprint(self, schema):
        columns = [row[1] for row in schema_contract(schema)[0] if row[0] == 'policy']
        expression = ','.join('`' + name + '`' for name in columns)
        rows = self.sql('SELECT SHA2(CAST(JSON_ARRAY(' + expression + ') AS CHAR CHARACTER SET utf8mb4),256) FROM `policy` ORDER BY `id`;\n').splitlines()
        require(len(rows) == 1993 and all(re.fullmatch('[0-9a-f]{64}', row) for row in rows))
        return digest(('\n'.join(rows) + '\n').encode())

    def completed(self, state, schema):
        require(state.get('stage') == 'complete' and set(state) == {'version', 'schema_sha', 'seed_sha', 'stage', 'policy_fingerprint'})
        self.verify_schema(schema)
        require(self.counts() == {name: 1993 if name == 'policy' else 0 for name in TABLES})
        require(re.fullmatch('[0-9a-f]{64}', state['policy_fingerprint']) and self.policy_fingerprint(schema) == state['policy_fingerprint'])
        self.inactive()

    def prepare(self, schema_path, seed_path, verify=False):
        schema, seed = payloads(schema_path, seed_path)
        self.prerequisites()
        checkpoint = self.path(STATE)
        base = {'version': 1, 'schema_sha': SCHEMA_SHA, 'seed_sha': SEED_SHA}
        if checkpoint.exists():
            state = json.loads(self.read(STATE, True))
            require(all(state.get(key) == value for key, value in base.items()))
            if state.get('stage') == 'complete':
                self.completed(state, schema)
                return
            require(not verify and state == dict(base, stage='schema-ready'))
        else:
            require(not verify and not self.objects())
            self.save(dict(base, stage='schema-started'))
            self.sql(schema)
            self.verify_schema(schema)
            require(not any(self.counts().values()))
            self.save(dict(base, stage='schema-ready'))
        self.verify_schema(schema)
        require(not any(self.counts().values()))
        self.sql(seed)
        self.verify_schema(schema)
        require(self.counts() == {name: 1993 if name == 'policy' else 0 for name in TABLES})
        state = dict(base, stage='complete', policy_fingerprint=self.policy_fingerprint(schema))
        self.inactive()
        self.save(state)
        self.completed(state, schema)

    def configure(self, values):
        require(isinstance(values, dict) and set(values) == INTEGRATIONS)
        require(all(isinstance(value, str) and value.strip()
                    and value.strip().lower() not in {'changeme', 'placeholder', 'todo', 'replace_me'}
                    for value in values.values()))
        self.prerequisites()
        original = self.environment()
        self.bootstrap.configure_integrations(DOMAIN, values)
        self.prerequisites()
        updated = self.environment(integrations=True)
        require({key: value for key, value in original.items() if key not in INTEGRATIONS}
                == {key: value for key, value in updated.items() if key not in INTEGRATIONS})
        self.inactive()


def main_operation(args):
    require(os.geteuid() == 0 and platform.machine() == 'x86_64')
    release = Path('/etc/os-release').read_text()
    require(re.search(r'^ID="?amzn"?$', release, re.M) and re.search(r'^VERSION_ID="?2023"?$', release, re.M))
    os.umask(0o077)
    with open('/run/runab-bootstrap.lock', 'a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        tool = Preparation()
        if args.operation == 'configure-integrations':
            payload = sys.stdin.read(65537)
            require(len(payload) <= 65536)
            tool.configure(json.loads(payload))
        else:
            require(args.schema and args.seed)
            tool.prepare(args.schema, args.seed, verify=args.operation == 'verify')
            if args.operation == 'verify':
                tool.environment(integrations=True)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=('prepare', 'verify', 'configure-integrations'))
    parser.add_argument('--schema', type=Path)
    parser.add_argument('--seed', type=Path)
    args = parser.parse_args(argv)
    try:
        main_operation(args)
        print(json.dumps({'integrations_ready' if args.operation == 'configure-integrations' else 'database_ready': True}))
        return 0
    except Exception:
        print(json.dumps({'integrations_ready' if args.operation == 'configure-integrations' else 'database_ready': False}), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
