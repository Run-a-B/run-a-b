#!/usr/bin/env python3
"""One-host TLS preparation on the already bootstrapped AL2023 server.

sudo -n python3 -I runab-prepare-tls.py apply --template nginx.conf.template
The installed systemd timer invokes `renew`; it also supports `check-renewal`.
No application deployment, activation, secret output, or unknown-file adoption.
"""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile

DOMAIN = 'run-a-b.com'
EMAIL = 'jaeseongwi48@gmail.com'
NGINX = '/etc/nginx/conf.d/runab.conf'
MARKER = '/etc/runab/bootstrap.json'
STATE = '/etc/runab/tls.json'
BACKUP = '/etc/runab/nginx-maintenance.conf'
HELPER = '/srv/runab/ops/prepare_tls.py'
CERTS = '/etc/letsencrypt/live/' + DOMAIN
IMAGE = 'certbot/certbot:latest'
DIGEST = re.compile(r'(?:docker\.io/)?certbot/certbot@sha256:[0-9a-f]{64}')
TEMPLATE_SHA256 = 'e291af5501f5fa456d91dbcafdeda632bc66451fbe07d5dfcffcb8bbb7a1b610'
MAINTENANCE = ('server {\n    listen 80;\n    server_name run-a-b.com;\n'
               '    location ^~ /.well-known/acme-challenge/ { root /var/lib/runab/acme; }\n'
               '    location / { add_header Cache-Control no-store always; return 503; }\n}\n').encode()
SERVICE = '''[Unit]
Description=RunAB certificate renewal
After=network-online.target docker.service nginx.service
Wants=network-online.target
Requires=docker.service

[Service]
Type=oneshot
ExecStart=/usr/bin/python3 -I /srv/runab/ops/prepare_tls.py renew
UMask=0077
TimeoutStartSec=1800
'''
TIMER = '''[Unit]
Description=RunAB twice daily certificate renewal

[Timer]
OnCalendar=*-*-* 00,12:00:00
RandomizedDelaySec=3600
Persistent=true
Unit=runab-certbot.service

[Install]
WantedBy=timers.target
'''
UNITS = {'/etc/systemd/system/runab-certbot.service': SERVICE.encode(),
         '/etc/systemd/system/runab-certbot.timer': TIMER.encode()}


def require(value):
    if not value:
        raise RuntimeError('TLS prerequisite or verification failed; inspect host locally')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def command(args, *, input=None, allow_failure=False):
    result = subprocess.run(args, input=input, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, timeout=1200)
    require(result.returncode == 0 or allow_failure)
    return result.stdout.strip() if result.returncode == 0 else ''


class PrepareTls:
    def __init__(self, root=Path('/'), runner=command):
        self.root, self.run = root.resolve(), runner

    def path(self, name):
        path = self.root / name.lstrip('/')
        for parent in (path, *path.parents):
            require(not parent.is_symlink())
            if parent == self.root:
                break
        return path

    def read(self, name, mode=None):
        path = self.path(name)
        require(path.is_file() and path.stat().st_uid == os.geteuid())
        require(stat.S_IMODE(path.stat().st_mode) == mode if mode else not path.stat().st_mode & 0o022)
        return path.read_bytes()

    def directory(self, name, mode=None):
        path = self.path(name)
        require(path.is_dir() and path.stat().st_uid == os.geteuid())
        require(stat.S_IMODE(path.stat().st_mode) == mode if mode else not path.stat().st_mode & 0o022)
        return path

    def write(self, name, data, mode=0o600, *, new=False):
        path = self.path(name)
        if new:
            with open(path, 'xb', opener=lambda p, flags: os.open(p, flags, mode)) as out:
                out.write(data)
                out.flush()
                os.fsync(out.fileno())
            path.chmod(mode)
            return
        fd, temporary = tempfile.mkstemp(prefix='.runab-tls-', dir=path.parent)
        try:
            with os.fdopen(fd, 'wb') as out:
                out.write(data)
                out.flush()
                os.fsync(out.fileno())
                os.fchmod(out.fileno(), mode)
            os.replace(temporary, path)
        finally:
            Path(temporary).unlink(missing_ok=True)

    def save_state(self, state, *, new=False):
        self.write(STATE, (json.dumps(state, sort_keys=True) + '\n').encode(), new=new)

    def bootstrap(self):
        self.directory('/etc/runab', 0o700)
        state = json.loads(self.read(MARKER, 0o600))
        require(state['version'] == 1 and state['domain'] == DOMAIN)
        for name, expected in state['files'].items():
            require(sha(self.read(name, 0o600 if name.startswith('/etc/runab/') else 0o644)) == expected)
        require(NGINX in state['files'])
        return state

    def inactive(self):
        require(not self.path('/srv/runab/current').exists())
        require(self.run(['systemctl', 'show', 'runab-api', '--property=ActiveState', '--value']) in ('inactive', 'failed'))
        require(not self.run(['ss', '-H', '-ltn', '( sport = :8080 )']))

    def managed_state(self):
        state = json.loads(self.read(STATE, 0o600))
        require(state['version'] == 1 and state['domain'] == DOMAIN and DIGEST.fullmatch(state['image']))
        require(sha(self.read(BACKUP, 0o600)) == state['maintenance_sha256'] == sha(MAINTENANCE))
        for name, expected in state['files'].items():
            require(sha(self.read(name, 0o644)) == expected)
        self.certbot_config(state)
        return state

    def certbot_config(self, state):
        for name in ('/etc/letsencrypt', '/var/lib/letsencrypt', '/var/log/letsencrypt'):
            self.directory(name, 0o700)
        require(not self.path('/etc/letsencrypt/cli.ini').exists())
        renewal = self.path('/etc/letsencrypt/renewal')
        if renewal.exists():
            require({p.name for p in renewal.iterdir()} <= {DOMAIN + '.conf'})
        hooks = self.path('/etc/letsencrypt/renewal-hooks')
        if hooks.exists():
            for folder in hooks.iterdir():
                require(folder.name in ('pre', 'deploy', 'post') and self.directory(str(folder.relative_to(self.root))).is_dir())
                require(not any(folder.iterdir()))
        if state.get('renewal_sha256'):
            require(sha(self.read('/etc/letsencrypt/renewal/' + DOMAIN + '.conf')) == state['renewal_sha256'])

    def certbot(self, image, operation, *extra):
        require(DIGEST.fullmatch(image))
        self.run(['docker', 'run', '--rm', '--log-driver', 'none', '--network', 'host',
                  '--mount', 'type=bind,src=/etc/letsencrypt,dst=/etc/letsencrypt',
                  '--mount', 'type=bind,src=/var/lib/letsencrypt,dst=/var/lib/letsencrypt',
                  '--mount', 'type=bind,src=/var/log/letsencrypt,dst=/var/log/letsencrypt',
                  '--mount', 'type=bind,src=/var/lib/runab/acme,dst=/var/lib/runab/acme',
                  image, operation, '--non-interactive', '--no-directory-hooks',
                  '--cert-name', DOMAIN, '--webroot', '--webroot-path', '/var/lib/runab/acme', *extra])

    def certificate_paths(self):
        self.directory(CERTS)
        archive = self.directory('/etc/letsencrypt/archive/' + DOMAIN)
        for stem in ('cert', 'chain', 'fullchain', 'privkey'):
            path = self.root / CERTS.lstrip('/') / (stem + '.pem')
            resolved = path.resolve(strict=True)
            require(path.is_symlink() and resolved.parent == archive
                    and re.fullmatch(stem + r'[1-9][0-9]*\.pem', resolved.name)
                    and resolved.is_file() and resolved.stat().st_uid == os.geteuid()
                    and not resolved.stat().st_mode & 0o022)
            if stem == 'privkey':
                require(stat.S_IMODE(resolved.stat().st_mode) == 0o600)

    def validate_certificate(self):
        self.certificate_paths()
        self.run(['openssl', 'x509', '-in', CERTS + '/cert.pem', '-noout', '-checkend', '86400'])
        for host in (DOMAIN, 'www.' + DOMAIN):
            self.run(['openssl', 'verify', '-verify_hostname', host, '-purpose', 'sslserver',
                      '-CAfile', '/etc/pki/tls/certs/ca-bundle.crt', '-untrusted',
                      CERTS + '/chain.pem', CERTS + '/cert.pem'])
        public = self.run(['openssl', 'x509', '-in', CERTS + '/cert.pem', '-pubkey', '-noout'])
        require(public and public == self.run(['openssl', 'pkey', '-in', CERTS + '/privkey.pem', '-pubout']))
        leaf = self.run(['openssl', 'x509', '-in', CERTS + '/cert.pem', '-noout', '-fingerprint', '-sha256'])
        require(leaf and leaf == self.run(['openssl', 'x509', '-in', CERTS + '/fullchain.pem', '-noout', '-fingerprint', '-sha256']))

    def switch_nginx(self, config):
        # Validate the candidate without changing any live nginx file first.
        fd, name = tempfile.mkstemp(prefix='.runab-check-', dir=self.path('/etc/nginx'))
        try:
            with os.fdopen(fd, 'w') as out:
                out.write('events {}\nhttp {\n' + config.decode() + '\n}\n')
            self.run(['nginx', '-t', '-c', name])
        finally:
            Path(name).unlink(missing_ok=True)
        self.write(NGINX, config, 0o644)
        self.run(['nginx', '-t'])
        self.run(['systemctl', 'reload', 'nginx'])
        require(config.decode().strip() in self.run(['nginx', '-T']))

    def curl(self, url, status=False):
        host = DOMAIN if 'www.' not in url else 'www.' + DOMAIN
        port = '443' if url.startswith('https:') else '80'
        return self.run(['curl', '--silent', '--show-error', '--noproxy', '*', '--max-time', '15',
                         '--retry', '3', '--retry-delay', '1', '--resolve', host + ':' + port + ':127.0.0.1',
                         *(['--output', '/dev/null', '--write-out', '%{http_code}'] if status else []), url])

    def probe(self, https=False):
        self.directory('/var/lib/runab/acme')
        challenge = self.path('/var/lib/runab/acme/.well-known/acme-challenge')
        challenge.mkdir(parents=True, mode=0o755, exist_ok=True)
        # Public ACME content is deliberately readable despite the private process umask.
        challenge.parent.chmod(0o755)
        challenge.chmod(0o755)
        fd, name = tempfile.mkstemp(prefix='runab-check-', dir=challenge)
        try:
            with os.fdopen(fd, 'w') as out:
                out.write('runab-acme-route-ok')
                os.fchmod(out.fileno(), 0o644)
            for host in (DOMAIN, 'www.' + DOMAIN):
                require(self.curl('http://' + host + '/.well-known/acme-challenge/' + Path(name).name) == 'runab-acme-route-ok')
            if https:
                require(self.curl('http://' + DOMAIN + '/', True) == '308')
                require(self.curl('https://www.' + DOMAIN + '/', True) == '308')
                for suffix in ('/', '/api/actuator/health'):
                    require(self.curl('https://' + DOMAIN + suffix, True) == '503')
        finally:
            Path(name).unlink(missing_ok=True)

    def apply(self, template):
        raw = template.read_bytes()
        require(sha(raw) == TEMPLATE_SHA256)
        config = raw.replace(b'RUNAB_DOMAIN', DOMAIN.encode())
        self.inactive()
        bootstrap = self.bootstrap()
        marker_original = self.read(MARKER, 0o600)
        for name in ('/srv/runab/ops', '/etc/systemd/system', '/etc/nginx/conf.d'):
            self.directory(name)
        require(self.run(['systemctl', 'is-active', 'nginx'], allow_failure=True) == 'active')
        if self.path(STATE).exists():
            state = self.managed_state()
            if state['status'] == 'ready':
                require(self.read(NGINX) == config)
                self.validate_certificate()
                self.run(['nginx', '-t'])
                require(config.decode().strip() in self.run(['nginx', '-T']))
                self.probe(https=True)
                require(self.run(['systemctl', 'is-active', 'runab-certbot.timer'], allow_failure=True) == 'active')
                return
            require(state['status'] == 'pending' and not state['files'] and self.read(NGINX) == MAINTENANCE)
            for name in (*UNITS, HELPER):
                require(not self.path(name).exists())
        else:
            require(self.read(NGINX) == MAINTENANCE)
            for name in (*UNITS, HELPER, BACKUP, '/etc/letsencrypt', '/var/lib/letsencrypt', '/var/log/letsencrypt'):
                require(not self.path(name).exists())
            self.run(['docker', 'pull', IMAGE])
            digests = json.loads(self.run(['docker', 'image', 'inspect', IMAGE, '--format', '{{json .RepoDigests}}']))
            image = next((d for d in digests if DIGEST.fullmatch(d)), None)
            require(image)
            self.write(BACKUP, MAINTENANCE, new=True)
            for name in ('/etc/letsencrypt', '/var/lib/letsencrypt', '/var/log/letsencrypt'):
                self.path(name).mkdir(mode=0o700)
            state = {'version': 1, 'domain': DOMAIN, 'status': 'pending', 'image': image,
                     'maintenance_sha256': sha(MAINTENANCE), 'files': {}}
            self.save_state(state, new=True)
        created = []
        timer_enabled = False
        try:
            self.certbot_config(state)
            self.switch_nginx(MAINTENANCE.replace(b'server_name run-a-b.com;', b'server_name run-a-b.com www.run-a-b.com;'))
            self.probe()
            self.certbot(state['image'], 'certonly', '--agree-tos', '--email', EMAIL,
                         '--keep-until-expiring', '-d', DOMAIN, '-d', 'www.' + DOMAIN)
            state['renewal_sha256'] = sha(self.read('/etc/letsencrypt/renewal/' + DOMAIN + '.conf'))
            self.save_state(state)
            self.validate_certificate()
            self.switch_nginx(config)
            self.probe(https=True)
            self.certbot(state['image'], 'renew', '--dry-run')
            self.validate_certificate()
            payload = {**UNITS, HELPER: Path(__file__).read_bytes()}
            for name, data in payload.items():
                self.write(name, data, 0o644, new=True)
                created.append(name)
            self.run(['systemctl', 'daemon-reload'])
            timer_enabled = True
            self.run(['systemctl', 'enable', '--now', 'runab-certbot.timer'])
            require(self.run(['systemctl', 'is-active', 'runab-certbot.timer'], allow_failure=True) == 'active')
            self.inactive()
            state.update(status='ready', files={name: sha(data) for name, data in payload.items()},
                         loaded_fullchain_sha256=sha((self.root / CERTS.lstrip('/') / 'fullchain.pem').read_bytes()))
            self.save_state(state)
            bootstrap['files'][NGINX] = sha(config)
            self.write(MARKER, (json.dumps(bootstrap, sort_keys=True) + '\n').encode())
        except Exception:
            # Only this invocation's files may be removed; preserve certbot state for safe retry.
            self.write(NGINX, MAINTENANCE, 0o644)
            self.run(['nginx', '-t'])
            self.run(['systemctl', 'reload', 'nginx'])
            if timer_enabled:
                self.run(['systemctl', 'disable', '--now', 'runab-certbot.timer'], allow_failure=True)
            for name in created:
                self.path(name).unlink()
            if created:
                self.run(['systemctl', 'daemon-reload'])
            self.write(MARKER, marker_original)
            state.update(status='pending', files={})
            self.save_state(state)
            raise

    def renew(self, dry_run=False):
        self.bootstrap()
        state = self.managed_state()
        require(state['status'] == 'ready')
        require(re.fullmatch(r'[0-9a-f]{64}', state.get('loaded_fullchain_sha256', '')))
        self.certificate_paths()
        self.certbot(state['image'], 'renew', *(['--dry-run'] if dry_run else []))
        self.validate_certificate()
        disk_sha256 = sha((self.root / CERTS.lstrip('/') / 'fullchain.pem').read_bytes())
        # Checkpoint only validated Certbot changes before reload. Keep the last
        # successfully loaded certificate until reload succeeds, so a later
        # no-op renewal still retries a failed test/reload rather than skipping it.
        state['renewal_sha256'] = sha(self.read('/etc/letsencrypt/renewal/' + DOMAIN + '.conf'))
        self.save_state(state)
        if not dry_run and state['loaded_fullchain_sha256'] != disk_sha256:
            self.run(['nginx', '-t'])
            self.run(['systemctl', 'reload', 'nginx'])
            state['loaded_fullchain_sha256'] = disk_sha256
            self.save_state(state)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=('apply', 'renew', 'check-renewal'))
    parser.add_argument('--template', type=Path)
    args = parser.parse_args()
    try:
        require(os.geteuid() == 0)
        release = Path('/etc/os-release').read_text()
        require(re.search(r'^ID="?amzn"?$', release, re.M) and re.search(r'^VERSION_ID="?2023"?$', release, re.M))
        os.umask(0o077)
        with open('/run/runab-bootstrap.lock', 'a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            tool = PrepareTls()
            if args.operation == 'apply':
                require(args.template is not None)
                tool.apply(args.template)
            else:
                tool.renew(dry_run=args.operation == 'check-renewal')
        print(json.dumps({'tls_operation_ok': True}))
        return 0
    except Exception:
        print(json.dumps({'tls_operation_ok': False}))
        return 1


if __name__ == '__main__':
    sys.exit(main())
