#!/usr/bin/env python3
"""Read-only fixed-boolean diagnosis; no arguments, no raw logs or host metadata.

Run as root via `python3 -I tls-diagnose.py` or `python3 -I -` on the fixed host.
No issuance, renewal, reload, state changes, DB access, or ACME file creation.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import time

DOMAIN = 'run-a-b.com'
NGINX = '/etc/nginx/conf.d/runab.conf'
CERTS = '/etc/letsencrypt/live/run-a-b.com'
MAINTENANCE = ('server {\n    listen 80;\n    server_name run-a-b.com;\n'
               '    location ^~ /.well-known/acme-challenge/ { root /var/lib/runab/acme; }\n'
               '    location / { add_header Cache-Control no-store always; return 503; }\n}\n').encode()
PIN = re.compile(r'(?:docker\.io/)?certbot/certbot@sha256:[0-9a-f]{64}')
SHA = re.compile(r'[0-9a-f]{64}')


def classify_log(data):
    # The only allowed log-derived outputs. No lines, captures, values, or fragments escape.
    text = data.decode('utf-8', errors='replace').lower()
    return {
        'dryrun_success_seen': 'all simulated renewals succeeded' in text,
        'dns_error_seen': any(p in text for p in ('urn:ietf:params:acme:error:dns', 'dns problem:')),
        'challenge_error_seen': any(p in text for p in ('some challenges have failed', 'challenge failed', 'urn:ietf:params:acme:error:unauthorized')),
        'rate_limit_seen': any(p in text for p in ('urn:ietf:params:acme:error:ratelimited', 'too many certificates', 'too many failed authorizations')),
        'network_error_seen': any(p in text for p in ('connectionerror', 'connecttimeout', 'readtimeout', 'timeout during connect', 'network is unreachable', 'temporary failure in name resolution')),
    }


def command(args, *, timeout):
    try:
        result = subprocess.run(args, text=True, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, timeout=timeout)
        return result.returncode, result.stdout.strip()
    except (OSError, subprocess.SubprocessError):
        return 1, ''


def diagnose(root=Path('/'), runner=command):
    root = root.resolve()
    deadline = time.monotonic() + 90
    report = {'overall_readonly': True}

    def flag(name, action):
        try:
            report[name] = bool(action())
        except Exception:
            report[name] = False
        return report[name]

    def require(value):
        if not value:
            raise ValueError('diagnostic check failed')

    def path(name):
        value = root / name.lstrip('/')
        for parent in (value, *value.parents):
            require(not parent.is_symlink())
            if parent == root:
                break
        return value

    def read(name, mode=None, owner=True):
        value = path(name)
        info = value.stat()
        require(stat.S_ISREG(info.st_mode) and info.st_size <= 1024 * 1024)
        require(not owner or info.st_uid == os.geteuid())
        require(stat.S_IMODE(info.st_mode) == mode if mode else not info.st_mode & 0o022)
        return value.read_bytes()

    def directory(name, mode=None):
        value = path(name)
        info = value.stat()
        require(stat.S_ISDIR(info.st_mode) and info.st_uid == os.geteuid())
        require(stat.S_IMODE(info.st_mode) == mode if mode else not info.st_mode & 0o022)
        return True

    def run(args):
        remaining = deadline - time.monotonic()
        require(remaining > 0)
        return runner(args, timeout=min(10, remaining))

    def ok(args):
        return run(args)[0] == 0

    def digest(data):
        return hashlib.sha256(data).hexdigest()

    state, bootstrap, config = {}, {}, b''
    flag('private_runab_directory_ok', lambda: directory('/etc/runab', 0o700))
    if flag('tls_state_file_secure', lambda: bool(read('/etc/runab/tls.json', 0o600))):
        try:
            state = json.loads(read('/etc/runab/tls.json', 0o600))
            require(isinstance(state, dict))
        except Exception:
            state = {}
    flag('tls_state_valid', lambda: state.get('version') == 1 and state.get('domain') == DOMAIN
         and isinstance(state.get('image'), str) and PIN.fullmatch(state['image'])
         and state.get('status') in ('pending', 'ready') and isinstance(state.get('files'), dict))
    flag('tls_state_pending', lambda: state.get('status') == 'pending')
    flag('tls_state_ready', lambda: state.get('status') == 'ready')
    flag('loaded_fingerprint_recorded', lambda: bool(SHA.fullmatch(state.get('loaded_fullchain_sha256', ''))))
    try:
        bootstrap = json.loads(read('/etc/runab/bootstrap.json', 0o600))
        config = read(NGINX, 0o644)
    except Exception:
        pass
    flag('bootstrap_nginx_hash_matches', lambda: bool(config) and digest(config) == bootstrap['files'][NGINX])
    flag('nginx_disk_original_maintenance', lambda: config == MAINTENANCE)
    flag('timer_unit_present', lambda: bool(read('/etc/systemd/system/runab-certbot.timer', 0o644)))
    flag('renewal_configuration_hash_matches', lambda: bool(SHA.fullmatch(state.get('renewal_sha256', ''))) and
         digest(read('/etc/letsencrypt/renewal/run-a-b.com.conf')) == state['renewal_sha256'])
    flag('application_current_absent', lambda: not path('/srv/runab/current').exists())
    flag('api_unit_inactive', lambda: run(['systemctl', 'show', 'runab-api', '--property=ActiveState', '--value']) in ((0, 'inactive'), (0, 'failed')))
    flag('nginx_active', lambda: run(['systemctl', 'is-active', 'nginx']) == (0, 'active'))
    flag('renewal_timer_active', lambda: run(['systemctl', 'is-active', 'runab-certbot.timer']) == (0, 'active'))
    flag('nginx_syntax_ok', lambda: ok(['nginx', '-t']))

    def certificate_paths():
        directory(CERTS)
        archive = path('/etc/letsencrypt/archive/run-a-b.com')
        directory('/etc/letsencrypt/archive/run-a-b.com')
        for stem in ('cert', 'chain', 'fullchain', 'privkey'):
            value = root / CERTS.lstrip('/') / (stem + '.pem')
            resolved = value.resolve(strict=True)
            require(value.is_symlink() and resolved.parent == archive
                    and re.fullmatch(stem + r'[1-9][0-9]*\.pem', resolved.name))
            read(str(resolved.relative_to(root)), 0o600 if stem == 'privkey' else None)
        return True
    cert_paths_ok = flag('certificate_paths_safe', certificate_paths)
    if cert_paths_ok:
        flag('certificate_not_expiring', lambda: ok(['openssl', 'x509', '-in', CERTS + '/cert.pem', '-noout', '-checkend', '86400']))
        for host, name in ((DOMAIN, 'certificate_canonical_trusted'), ('www.' + DOMAIN, 'certificate_alias_trusted')):
            flag(name, lambda host=host: ok(['openssl', 'verify', '-verify_hostname', host, '-purpose', 'sslserver',
                 '-CAfile', '/etc/pki/tls/certs/ca-bundle.crt', '-untrusted', CERTS + '/chain.pem', CERTS + '/cert.pem']))
        flag('certificate_key_matches', lambda: (lambda public, key: public[0] == key[0] == 0 and bool(public[1]) and public[1] == key[1])(
             run(['openssl', 'x509', '-in', CERTS + '/cert.pem', '-pubkey', '-noout']),
             run(['openssl', 'pkey', '-in', CERTS + '/privkey.pem', '-pubout'])))
        flag('fullchain_leaf_matches', lambda: (lambda leaf, full: leaf[0] == full[0] == 0 and bool(leaf[1]) and leaf[1] == full[1])(
             run(['openssl', 'x509', '-in', CERTS + '/cert.pem', '-noout', '-fingerprint', '-sha256']),
             run(['openssl', 'x509', '-in', CERTS + '/fullchain.pem', '-noout', '-fingerprint', '-sha256'])))
        flag('loaded_fingerprint_matches_disk', lambda: digest((root / CERTS.lstrip('/') / 'fullchain.pem').read_bytes()) == state.get('loaded_fullchain_sha256'))
    else:
        for name in ('certificate_not_expiring', 'certificate_canonical_trusted', 'certificate_alias_trusted',
                     'certificate_key_matches', 'fullchain_leaf_matches', 'loaded_fingerprint_matches_disk'):
            report[name] = False

    def http(host, tls, suffix, expected):
        port = '443' if tls else '80'
        return run(['curl', '--silent', '--show-error', '--noproxy', '*', '--max-time', '5',
                    '--connect-timeout', '3', '--resolve', host + ':' + port + ':127.0.0.1',
                    '--output', '/dev/null', '--write-out', '%{http_code}',
                    ('https://' if tls else 'http://') + host + suffix]) == (0, expected)
    flag('http_canonical_maintenance', lambda: http(DOMAIN, False, '/', '503'))
    flag('http_canonical_redirect', lambda: http(DOMAIN, False, '/', '308'))
    flag('https_canonical_maintenance', lambda: http(DOMAIN, True, '/', '503'))
    flag('https_alias_redirect', lambda: http('www.' + DOMAIN, True, '/', '308'))
    missing = '/.well-known/acme-challenge/runab-diagnostic-missing-token-20261019'
    for host, label in ((DOMAIN, 'acme_canonical_missing_token_404'), ('www.' + DOMAIN, 'acme_alias_missing_token_404')):
        flag(label, lambda host=host: not path('/var/lib/runab/acme' + missing).exists() and http(host, False, missing, '404'))

    # Read at most the last 512 KiB of this one approved Certbot log, privately.
    report.update(classify_log(b''))
    try:
        log = path('/var/log/letsencrypt/letsencrypt.log')
        info = log.stat()
        require(stat.S_ISREG(info.st_mode) and info.st_uid == os.geteuid() and not info.st_mode & 0o022)
        with log.open('rb') as handle:
            handle.seek(max(0, info.st_size - 512 * 1024))
            report.update(classify_log(handle.read(512 * 1024)))
    except Exception:
        pass
    report['diagnostic_complete'] = time.monotonic() < deadline
    return report


if __name__ == '__main__':
    try:
        if os.geteuid() != 0 or len(sys.argv) != 1:
            raise ValueError('diagnostic requires root and no arguments')
        result = diagnose()
    except Exception:
        result = {'overall_readonly': True, 'diagnostic_complete': False}
    print(json.dumps(result, sort_keys=True))
