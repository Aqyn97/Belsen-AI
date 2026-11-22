#!/usr/bin/env python3
"""Archived experimental Render rehearsal; NOT approved for production.
No further Render optimization is authorized. See docs/RENDER_EXPERIMENT.md.
Disposable local production topology. No ACME, DNS, R2 or existing DB access.
Requires a built local image, Docker Compose, age/age-keygen and htpasswd.
Uses the existing VPS topology only as a disposable local HTTPS/PG fixture;
never edits it or contacts Render, Neon, Cloudflare or another database.
Tests the JVM/pool budget, not real platform ingress or Neon suspension.
"""
import argparse, http.cookiejar, json, os, pathlib, secrets, ssl, subprocess, tempfile, time, shutil, urllib.request, urllib.error, uuid
ROOT = pathlib.Path(__file__).resolve().parents[2]

def run(a, **kw):
    return subprocess.run(a, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kw).stdout
p = argparse.ArgumentParser()
p.add_argument('--image', required=True)
p.add_argument('--render-profile', action='store_true', help='Use production,render with a local Neon-shaped TLS endpoint; no cloud calls')
p.add_argument('--startup-only', action='store_true', help='Explicit cold-start/workload assessment only; omits restart and outage checks')
p.add_argument('--cpus', type=float, default=0.1)
p.add_argument('--age-dir', required=True)
a = p.parse_args()
if not 0.1 <= a.cpus <= 1:
    p.error('--cpus must be between 0.1 and 1')
project = 'belsen-free-' + uuid.uuid4().hex[:10]
password = secrets.token_urlsafe(24)
with tempfile.TemporaryDirectory(prefix=project, dir='/tmp') as tmp:
    t = pathlib.Path(tmp)
    os.chmod(t, 448)
    for n in ['admin', 'app', 'backup']:
        (t / n).write_text(secrets.token_hex(32))
        os.chmod(t / n, 292)
    env = t / 'stack.env'
    env.write_text('\n'.join([f'BELSEN_APP_IMAGE={a.image}', 'BELSEN_DOMAIN=localhost', 'BELSEN_ACME_EMAIL=fixture@example.invalid', f'BELSEN_POSTGRES_ADMIN_PASSWORD_FILE={t}/admin', f'BELSEN_DB_PASSWORD_FILE={t}/app', f'BELSEN_POSTGRES_BACKUP_PASSWORD_FILE={t}/backup']))
    base = ['docker', 'compose', '-p', project, '--env-file', str(env), '-f', str(ROOT / 'infra/production/compose.yaml')]
    config = json.loads(run(base + ['config', '--format', 'json']))
    config.pop('name', None)
    caddy = t / 'Caddyfile.local'
    caddy.write_text('{\n admin off\n persist_config off\n auto_https disable_redirects\n}\nhttps://localhost {\n tls internal\n import /etc/caddy/proxy.caddy\n}\nhttp://localhost {\n redir https://localhost:18445{uri} 308\n}\n')
    for service in config['services'].values():
        for mount in service.get('volumes', []):
            if mount.get('type') == 'bind':
                source = pathlib.Path(mount['source'])
                dest = t / source.name
                shutil.copyfile(source, dest)
                os.chmod(dest, 292)
                mount['source'] = str(dest)
    c = config['services']['caddy']
    c['ports'] = [{'target': 443, 'published': '18445', 'host_ip': '127.0.0.1'}, {'target': 80, 'published': '18085', 'host_ip': '127.0.0.1'}]
    for mount in c['volumes']:
        if mount['target'] == '/etc/caddy/Caddyfile':
            mount['source'] = str(caddy)
    c['environment']['BELSEN_PUBLIC_HOST'] = 'localhost:18445'
    config['services']['app']['environment']['BELSEN_PUBLIC_BASE_URL'] = 'https://localhost:18445'
    config['services']['app'].update(mem_limit='512m', memswap_limit='512m', cpus=a.cpus)
    config['services']['app']['environment'].update(JAVA_TOOL_OPTIONS='-Xms32m -Xmx160m -Xss512k -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=32m -XX:ActiveProcessorCount=1 -XX:+ExitOnOutOfMemoryError', SERVER_TOMCAT_THREADS_MAX='20', SERVER_TOMCAT_THREADS_MIN_SPARE='2', BELSEN_DB_POOL_MAX='3', BELSEN_DB_POOL_MIN='0', SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT='30000', SPRING_DATASOURCE_HIKARI_MAX_LIFETIME='300000', SPRING_DATASOURCE_HIKARI_KEEPALIVE_TIME='0', SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT='30000', SPRING_SESSION_JDBC_CLEANUP_CRON='0 0 3 * * *')
    config['services']['app']['healthcheck'].update(start_period='600s', retries=60)
    if a.render_profile:
        # Synthetic DNS alias inside this disposable Docker network, never public DNS.
        endpoint = 'ep-fixture.us-east-2.aws.neon.tech'
        certificate = t / 'pg.crt'
        private_key = t / 'pg.key'
        certificate_config = t / 'certificate.cnf'
        certificate_config.write_text('[req]\ndistinguished_name=dn\nx509_extensions=ext\nprompt=no\n[dn]\nCN=' + endpoint + '\n[ext]\nsubjectAltName=DNS:' + endpoint + '\n')
        run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '2', '-config', str(certificate_config), '-keyout', str(private_key), '-out', str(certificate)])
        trust = t / 'trust.p12'
        # This store contains ONLY a public test certificate; its password is not a credential.
        run(['keytool', '-importcert', '-noprompt', '-alias', 'fixture', '-file', str(certificate), '-keystore', str(trust), '-storetype', 'PKCS12', '-storepass', 'fixture-trust-only'])
        for file in [certificate, private_key, trust]:
            os.chmod(file, 0o444)
        db = config['services']['postgres']
        db['networks']['data'] = {'aliases': [endpoint]}
        db['volumes'].extend([{'type': 'bind', 'source': str(certificate), 'target': '/tmp/pg.crt', 'read_only': True}, {'type': 'bind', 'source': str(private_key), 'target': '/tmp/pg.key', 'read_only': True}])
        init = t / 'postgres-init.sql'
        os.chmod(init, 0o600)
        init.write_text(init.read_text() + r"""
COPY (SELECT regexp_split_to_table(pg_read_file('/tmp/pg.crt'), E'\n')) TO '/var/lib/postgresql/data/server.crt';
COPY (SELECT regexp_split_to_table(pg_read_file('/tmp/pg.key'), E'\n')) TO '/var/lib/postgresql/data/server.key';
COPY (SELECT 'fixture') TO PROGRAM 'cat >/dev/null; chmod 600 /var/lib/postgresql/data/server.key';
ALTER SYSTEM SET ssl_cert_file='/var/lib/postgresql/data/server.crt';
ALTER SYSTEM SET ssl_key_file='/var/lib/postgresql/data/server.key';
ALTER SYSTEM SET ssl='on';
""")
        os.chmod(init, 0o444)
        app = config['services']['app']
        app.setdefault('volumes', []).append({'type': 'bind', 'source': str(trust), 'target': '/tmp/trust.p12', 'read_only': True})
        app['environment'].pop('BELSEN_PUBLIC_BASE_URL', None)
        app['environment'].update(SPRING_PROFILES_ACTIVE='production,render', RENDER_EXTERNAL_URL='https://fixture.onrender.com', PORT='8081', SPRING_DATASOURCE_URL='jdbc:postgresql://' + endpoint + ':5432/belsen?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&connectTimeout=15&socketTimeout=30&tcpKeepAlive=true')
        app['environment']['JAVA_TOOL_OPTIONS'] += ' -Djavax.net.ssl.trustStore=/tmp/trust.p12 -Djavax.net.ssl.trustStorePassword=fixture-trust-only'
        c['environment']['BELSEN_PUBLIC_HOST'] = 'fixture.onrender.com'
    cfg = t / 'compose.json'
    cfg.write_text(json.dumps(config))
    base = ['docker', 'compose', '-p', project, '--env-file', str(env), '-f', str(cfg)]

    def sql(query, db='belsen'):
        return run(base + ['exec', '-T', '--user', 'postgres', 'postgres', 'psql', '-U', 'postgres', '-d', db, '-v', 'ON_ERROR_STOP=1', '-At'], input=query.encode()).decode().strip()
    try:
        run(['docker', 'volume', 'create', project + '_caddy_data'])
        run(['docker', 'run', '--rm', '--network', 'none', '--user', '0:0', '--cap-drop', 'ALL', '--cap-add', 'CHOWN', '-v', project + '_caddy_data:/data', c['image'], 'chown', '10001:10001', '/data'])
        started = time.monotonic()
        run(base + ['up', '-d', '--wait', '--wait-timeout', '900'])
        print(f'Cold topology healthy in {time.monotonic() - started:.1f}s at {a.cpus} CPU / 512 MiB', flush=True)

        def memory(label):
            ident = run(base + ['ps', '-q', 'app']).decode().strip()
            values = {n: int(run(['docker', 'exec', ident, 'cat', '/sys/fs/cgroup/' + n])) for n in ['memory.current', 'memory.peak']}
            state = json.loads(run(['docker', 'inspect', ident]))[0]['State']
            assert not state['OOMKilled']
            print(label + ': ' + json.dumps(values) + '; OOMKilled=false', flush=True)
        memory('Cold memory bytes')
        cid = run(base + ['ps', '-q', 'caddy']).decode().strip()
        run(['docker', 'cp', cid + ':/data/caddy/pki/authorities/local/root.crt', str(t / 'ca.crt')])
        import http.client
        redirect_client = http.client.HTTPConnection('localhost', 18085, timeout=10)
        redirect_client.request('GET', '/')
        redirect = redirect_client.getresponse()
        assert redirect.status == 308 and redirect.getheader('Location') == 'https://localhost:18445/'
        ctx = ssl.create_default_context(cafile=str(t / 'ca.crt'))
        cookies = http.cookiejar.CookieJar()
        client = urllib.request.build_opener(urllib.request.HTTPSHandler(context=ctx), urllib.request.HTTPCookieProcessor(cookies))

        def req(path, body=None, method=None, token=None, extra=None):
            headers = {'Content-Type': 'application/json'}
            headers.update(extra or {})
            if token:
                headers[token['headerName']] = token['token']
            r = urllib.request.Request('https://localhost:18445' + path, data=None if body is None else json.dumps(body).encode(), method=method, headers=headers)
            try:
                with client.open(r, timeout=50) as res:
                    return (res.status, res.read(), res.headers)
            except urllib.error.HTTPError as e:
                return (e.code, e.read(), e.headers)
        assert req('/api/v1/health', extra={'Forwarded': 'proto=http;host=attacker.invalid', 'X-Forwarded-Proto': 'http', 'X-Forwarded-Host': 'attacker.invalid', 'X-Forwarded-For': '203.0.113.1'})[0] == 200
        assert json.loads(req('/api/v1/auth/policy')[1]) == {'registrationEnabled': False}
        token = json.loads(req('/api/v1/auth/csrf')[1])
        assert req('/api/v1/auth/register', {'email': 'fixture@example.test', 'password': password, 'displayName': 'Fixture', 'timezone': 'UTC'}, token=token)[0] == 403
        assert sql('SELECT count(*) FROM users;') == '0'
        hashed = run(['htpasswd', '-niBC', '10', 'fixture'], input=(password + '\n').encode()).decode().strip().split(':', 1)[1]
        uid = str(uuid.uuid4())
        sql(f"INSERT INTO users VALUES ('{uid}','fixture@example.test','{{bcrypt}}{hashed}','Fixture','UTC',now(),now());")
        assert req('/api/v1/auth/login', {'email': 'fixture@example.test', 'password': password}, token=token)[0] == 200
        assert req('/api/v1/me')[0] == 200
        token = json.loads(req('/api/v1/auth/csrf')[1])
        assert req('/api/v1/goals', {'title': 'Synthetic', 'target': 10, 'unit': 'pages'})[0] == 403
        assert req('/api/v1/goals', {'title': 'Synthetic', 'target': 10, 'unit': 'pages'}, token=token)[0] == 201
        assert any((x.secure for x in cookies))
        assert sql('SELECT count(*) FROM flyway_schema_history WHERE success;') == '7'
        if a.render_profile:
            assert int(sql("SELECT count(*) FROM pg_stat_ssl s JOIN pg_stat_activity a USING(pid) WHERE a.usename='belsen_app' AND s.ssl;")) > 0
            print('Actual production,render profile + verified local PostgreSQL TLS: passed', flush=True)
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
        for path in ['/api/v1/goals', '/api/v1/tasks', '/api/v1/habits', '/api/v1/coach/status']:
            assert req(path)[0] == 200
        for _ in range(5):
            assert req('/api/v1/analytics/dashboard')[0] == 200
        memory('After authenticated dashboard reads')
        if a.startup_only:
            print('Startup-only run complete; restart/outage/expiry are NOT verified by this run', flush=True)
            raise SystemExit(0)
        started = time.monotonic()
        run(base + ['restart', 'app'])
        deadline = time.monotonic() + 900
        while req('/api/v1/health')[0] != 200:
            if time.monotonic() > deadline:
                raise AssertionError('Restart readiness deadline exceeded')
            time.sleep(1)
        assert req('/api/v1/me')[0] == 200
        assert sql('SELECT count(*) FROM goals;') == '1'
        print(f'Restart healthy in {time.monotonic() - started:.1f}s; same JDBC session valid', flush=True)
        memory('Restart memory bytes')
        run(base + ['stop', 'postgres'])
        failure = req('/api/v1/me')
        assert failure[0] >= 500
        assert all((word not in failure[1].lower() for word in [b'jdbc:', b'password', b'psqlexception', b'select ']))
        print('Database outage: safe 5xx, no private SQL/credential details', flush=True)
        run(base + ['start', 'postgres'])
        deadline = time.monotonic() + 90
        while req('/api/v1/me')[0] != 200:
            if time.monotonic() > deadline:
                raise AssertionError('Database recovery deadline exceeded')
            time.sleep(0.25)
        assert sql('SELECT count(*) FROM goals;') == '1'
        print('Local database stop/start recovery with persistent session and data: passed', flush=True)
        print(run(['docker', 'stats', '--no-stream', '--format', '{{.Name}} {{.MemUsage}}'] + run(base + ['ps', '-q']).decode().split()).decode(), flush=True)
        assert req('/api/v1/auth/logout', {}, token=token)[0] == 204
        assert req('/api/v1/me')[0] == 401
        token = json.loads(req('/api/v1/auth/csrf')[1])
        assert req('/api/v1/auth/login', {'email': 'fixture@example.test', 'password': password}, token=token)[0] == 200
        sql('UPDATE spring_session SET last_access_time=0, expiry_time=0;')
        assert req('/api/v1/me')[0] == 401
        assert req('/api/v1/auth/csrf')[0] == 200
        memory('Final memory bytes')
        print('App restart persistence, JDBC session, logout and deterministic expiry: passed', flush=True)
    except Exception:
        print(run(base + ['logs', '--tail', '25']).decode(), flush=True)
        raise
    finally:
        run(base + ['down', '-v', '--remove-orphans'])
