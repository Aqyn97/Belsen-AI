#!/usr/bin/env python3
"""Disposable local Oracle ARM compatibility rehearsal, derived from the existing production fixture. No ACME, DNS, R2 or existing DB access.
Requires built local image, Docker Compose, age/age-keygen and htpasswd (synthetic hash only).
"""
import argparse, http.cookiejar, json, os, pathlib, secrets, ssl, subprocess, tempfile, time, shutil, urllib.request, urllib.error, uuid
ROOT = pathlib.Path(__file__).resolve().parents[2]

def run(a, **kw):
    return subprocess.run(a, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kw).stdout
p = argparse.ArgumentParser()
p.add_argument('--image', required=True)
p.add_argument('--age-dir', required=True)
a = p.parse_args()
project = 'belsen-oracle-check-' + uuid.uuid4().hex[:10]
password = secrets.token_urlsafe(24)
with tempfile.TemporaryDirectory(prefix=project, dir='/tmp') as tmp:
    t = pathlib.Path(tmp)
    os.chmod(t, 448)
    for n in ['admin', 'app', 'backup']:
        (t / n).write_text(secrets.token_hex(32))
        os.chmod(t / n, 292)
    env = t / 'stack.env'
    env.write_text('\n'.join([f'BELSEN_APP_IMAGE={a.image}', 'BELSEN_DOMAIN=localhost', 'BELSEN_ACME_EMAIL=fixture@example.invalid', f'BELSEN_POSTGRES_ADMIN_PASSWORD_FILE={t}/admin', f'BELSEN_DB_PASSWORD_FILE={t}/app', f'BELSEN_POSTGRES_BACKUP_PASSWORD_FILE={t}/backup']))
    base = ['docker', 'compose', '-p', project, '--env-file', str(env), '-f', str(ROOT / 'infra/production/compose.yaml'), '-f', str(ROOT / 'infra/oracle/compose.yaml')]
    config = json.loads(run(base + ['config', '--format', 'json']))
    config.pop('name', None)
    assert config['networks']['proxy']['ipam']['config']==[{'subnet':'172.30.50.0/28'}]
    assert config['networks']['proxy']['internal'] and config['networks']['data']['internal']
    for service in ['app','postgres']:
        assert not config['services'][service].get('ports')
    caddy = t / 'Caddyfile.local'
    caddy.write_text('{\n admin off\n persist_config off\n auto_https disable_redirects\n}\nhttps://localhost {\n tls internal\n import /etc/caddy/proxy.caddy\n}\nhttp://localhost {\n redir https://localhost:18447{uri} 308\n}\n')
    for service in config['services'].values():
        for mount in service.get('volumes', []):
            if mount.get('type') == 'bind':
                source = pathlib.Path(mount['source'])
                dest = t / source.name
                shutil.copyfile(source, dest)
                os.chmod(dest, 292)
                mount['source'] = str(dest)
    c = config['services']['caddy']
    c['ports'] = [{'target': 443, 'published': '18447', 'host_ip': '127.0.0.1'}, {'target': 80, 'published': '18087', 'host_ip': '127.0.0.1'}]
    for mount in c['volumes']:
        if mount['target'] == '/etc/caddy/Caddyfile':
            mount['source'] = str(caddy)
    c['environment']['BELSEN_PUBLIC_HOST'] = 'localhost:18447'
    config['services']['app']['environment']['BELSEN_PUBLIC_BASE_URL'] = 'https://localhost:18447'
    cfg = t / 'compose.json'
    cfg.write_text(json.dumps(config))
    base = ['docker', 'compose', '-p', project, '--env-file', str(env), '-f', str(cfg)]

    def sql(query, db='belsen'):
        return run(base + ['exec', '-T', '--user', 'postgres', 'postgres', 'psql', '-U', 'postgres', '-d', db, '-v', 'ON_ERROR_STOP=1', '-At'], input=query.encode()).decode().strip()
    try:
        run(['docker', 'volume', 'create', project + '_caddy_data'])
        run(['docker', 'run', '--rm', '--network', 'none', '--user', '0:0', '--cap-drop', 'ALL', '--cap-add', 'CHOWN', '-v', project + '_caddy_data:/data', c['image'], 'chown', '10001:10001', '/data'])
        started = time.monotonic()
        assert config['services']['app']['environment']['SPRING_PROFILES_ACTIVE'] == 'production'
        assert sum((float(v['cpus']) for v in config['services'].values())) <= 2
        try:
            run(base + ['up', '-d', '--wait', '--wait-timeout', '180'])
        except subprocess.CalledProcessError as failure:
            print(failure.stderr.decode(), flush=True)
            print(run(base + ['logs', '--tail', '25']).decode(), flush=True)
            raise
        print(f'ARM topology healthy in {time.monotonic() - started:.1f}s', flush=True)

        def evidence(label):
            for service in ['app', 'postgres', 'caddy']:
                ident = run(base + ['ps', '-q', service]).decode().strip()
                state = json.loads(run(['docker', 'inspect', ident]))[0]
                image = json.loads(run(['docker', 'image', 'inspect', state['Image']]))[0]
                assert image['Architecture'] == 'arm64'
                assert not state['State']['OOMKilled'] and state['RestartCount'] == 0
                peak = int(run(['docker', 'exec', ident, 'cat', '/sys/fs/cgroup/memory.peak']))
                print(f'{label}: {service} arm64, peak={peak} bytes, no OOM/unexpected restart', flush=True)
        evidence('Cold start')
        cid = run(base + ['ps', '-q', 'caddy']).decode().strip()
        run(['docker', 'cp', cid + ':/data/caddy/pki/authorities/local/root.crt', str(t / 'ca.crt')])
        import http.client
        redirect_client = http.client.HTTPConnection('localhost', 18087, timeout=10)
        redirect_client.request('GET', '/')
        redirect = redirect_client.getresponse()
        assert redirect.status == 308 and redirect.getheader('Location') == 'https://localhost:18447/'
        ctx = ssl.create_default_context(cafile=str(t / 'ca.crt'))
        cookies = http.cookiejar.CookieJar()
        client = urllib.request.build_opener(urllib.request.HTTPSHandler(context=ctx), urllib.request.HTTPCookieProcessor(cookies))

        def req(path, body=None, method=None, token=None, extra=None):
            headers = {'Content-Type': 'application/json'}
            headers.update(extra or {})
            if token:
                headers[token['headerName']] = token['token']
            r = urllib.request.Request('https://localhost:18447' + path, data=None if body is None else json.dumps(body).encode(), method=method, headers=headers)
            try:
                with client.open(r, timeout=20) as res:
                    return (res.status, res.read(), res.headers)
            except urllib.error.HTTPError as e:
                return (e.code, e.read(), e.headers)
        assert req('/api/v1/health', extra={'Forwarded': 'proto=http;host=attacker.invalid', 'X-Forwarded-Proto': 'http', 'X-Forwarded-Host': 'attacker.invalid', 'X-Forwarded-For': '203.0.113.1'})[0] == 200
        assert json.loads(req('/api/v1/auth/policy')[1]) == {'registrationEnabled': False}
        token = json.loads(req('/api/v1/auth/csrf')[1])
        assert req('/api/v1/auth/register', {'email': 'fixture@example.test', 'password': password, 'displayName': 'Fixture', 'timezone': 'UTC'}, token=token)[0] == 403
        assert sql('SELECT count(*) FROM users;') == '0'
        hashed = run(['htpasswd', '-niBC', '10', 'fixture'], input=(password + chr(10)).encode()).decode().strip().split(':', 1)[1]
        uid = str(uuid.uuid4())
        sql(f"INSERT INTO users VALUES ('{uid}','fixture@example.test','{{bcrypt}}{hashed}','Fixture','UTC',now(),now());")
        assert req('/api/v1/auth/login', {'email': 'fixture@example.test', 'password': password}, token=token)[0] == 200
        assert req('/api/v1/me')[0] == 200
        token = json.loads(req('/api/v1/auth/csrf')[1])
        assert req('/api/v1/goals', {'title': 'Synthetic', 'target': 10, 'unit': 'pages'})[0] == 403
        assert req('/api/v1/goals', {'title': 'Synthetic', 'target': 10, 'unit': 'pages'}, token=token)[0] == 201
        assert any((x.secure for x in cookies))
        assert sql('SELECT count(*) FROM flyway_schema_history WHERE success;') == '7'
        assert sql("SELECT rolsuper FROM pg_roles WHERE rolname='belsen_app';") == 'f'
        for service in ['app', 'postgres']:
            ident = run(base + ['ps', '-q', service]).decode().strip()
            d = json.loads(run(['docker', 'inspect', ident]))[0]
            assert not d['HostConfig']['PortBindings']
        print('TLS trusted CA/hostname, disabled signup, login, CSRF, private ports, role and V1-V7: passed', flush=True)
        age = pathlib.Path(a.age_dir)
        identity = t / 'identity'
        run([str(age / 'age-keygen'), '-o', str(identity)])
        recipient = run([str(age / 'age-keygen'), '-y', str(identity)])
        (t / 'recipients').write_bytes(recipient)
        out = t / 'backups'
        cmd = ['python3', str(ROOT / 'scripts/production/backup.py'), 'create', '--out-dir', str(out), '--compose-file', str(cfg), '--env-file', str(env), '--project', project, '--recipients-file', str(t / 'recipients'), '--age-bin', str(age / 'age')]
        artifact = pathlib.Path(run(cmd).decode().strip())
        dump = run([str(age / 'age'), '--decrypt', '-i', str(identity), str(artifact)])
        sql('CREATE DATABASE belsen_restore;', 'postgres')
        run(base + ['exec', '-T', '--user', 'postgres', 'postgres', 'pg_restore', '-U', 'postgres', '-d', 'belsen_restore', '--no-owner', '--no-acl', '--exit-on-error'], input=dump)
        assert sql('SELECT count(*) FROM goals;', 'belsen_restore') == '1'
        assert sql('SELECT count(*) FROM flyway_schema_history WHERE success;', 'belsen_restore') == '7'
        bad = t / 'corrupt.age'
        data = bytearray(artifact.read_bytes())
        data[-1] ^= 1
        bad.write_bytes(data)
        assert subprocess.run([str(age / 'age'), '--decrypt', '-i', str(identity), str(bad)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode != 0
        print('Encrypted streaming backup + real PG17 restore + corruption rejection: passed', flush=True)
        started = time.monotonic()
        run(base + ['restart', 'app'])
        deadline = time.monotonic() + 180
        while req('/api/v1/health')[0] != 200:
            if time.monotonic() > deadline:
                raise AssertionError('ARM restart readiness deadline exceeded')
            time.sleep(1)
        assert req('/api/v1/me')[0] == 200
        assert sql('SELECT count(*) FROM goals;') == '1'
        print(f'ARM restart healthy in {time.monotonic() - started:.1f}s', flush=True)
        evidence('Restart')
        print(run(['docker', 'stats', '--no-stream', '--format', '{{.Name}} {{.MemUsage}}'] + run(base + ['ps', '-q']).decode().split()).decode(), flush=True)
        assert req('/api/v1/auth/logout', {}, token=token)[0] == 204
        assert req('/api/v1/me')[0] == 401
        print('App restart persistence, JDBC session and logout: passed', flush=True)
    finally:
        run(base + ['down', '-v', '--remove-orphans'])
