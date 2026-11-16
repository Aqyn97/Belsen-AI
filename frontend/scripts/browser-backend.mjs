// Disposable PostgreSQL + the already verified application JAR for browser tests.
// Requires Docker, Java 21 and a successful backend Maven verify. No persistent volume.
import { execFileSync, spawn } from 'node:child_process';
import { randomUUID, randomBytes } from 'node:crypto';
import { existsSync, copyFileSync, mkdtempSync, rmSync, cpSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
const packaged = process.argv.includes('--packaged');
const sourceJar = fileURLToPath(new URL('../../backend/target/belsen-ai-0.0.1-SNAPSHOT.jar', import.meta.url));
if (!existsSync(sourceJar)) throw new Error('Run backend Maven verify before real-browser tests.');
const temporary = mkdtempSync(join(tmpdir(), 'belsen-browser-'));
const jar = join(temporary, 'backend.jar');
copyFileSync(sourceJar, jar);
const fixtureSource = fileURLToPath(new URL('../../backend/target/test-classes/org/belsenfixture', import.meta.url));
if (!packaged && !existsSync(fixtureSource)) { rmSync(temporary, {recursive:true,force:true}); throw new Error('Run backend Maven verify to compile the deterministic browser fixture.'); }
const fixturePath = join(temporary, 'fixture-classes');
if (!packaged) {
  cpSync(fixtureSource, join(fixturePath, 'org/belsenfixture'), {recursive:true});
  copyFileSync(fileURLToPath(new URL('../../backend/src/main/resources/application-local.yml', import.meta.url)), join(fixturePath, 'application-local.yml'));
}
const tlsPassword = randomBytes(32).toString('hex');
const keyStore = join(temporary, 'browser.p12');
function createTestCertificate() { execFileSync('keytool', ['-genkeypair','-alias','belsen-browser','-keyalg','RSA','-keysize','2048','-validity','2','-dname','CN=localhost','-ext','SAN=dns:localhost,ip:127.0.0.1','-storetype','PKCS12','-keystore',keyStore,'-storepass:env','BELSEN_TEST_TLS_PASSWORD'], {env:{...process.env,BELSEN_TEST_TLS_PASSWORD:tlsPassword},stdio:'pipe'}); }
const name = `belsen-browser-${randomUUID()}`;
const password = randomBytes(32).toString('hex');
let backend;
let stopping = false;
async function stop(code = 0) {
  if (stopping) return; stopping = true;
  if (backend && backend.exitCode === null) {
    backend.kill('SIGTERM');
    await new Promise(resolve => {
      const timeout = setTimeout(resolve, 5000);
      backend.once('exit', () => { clearTimeout(timeout); resolve(); });
    });
  }
  try { execFileSync('docker', ['rm', '-f', name], {stdio:'ignore'}); } catch { /* Already removed or daemon stopped. */ }
  rmSync(temporary, {recursive:true,force:true});
  process.exit(code);
}
process.on('SIGTERM', () => stop()); process.on('SIGINT', () => stop());
try {
  if (packaged) createTestCertificate();
  execFileSync('docker', ['run', '--rm', '-d', '--name', name, '-p', '127.0.0.1::5432', '-e', 'POSTGRES_PASSWORD', '-e', 'POSTGRES_DB=belsen_browser', 'postgres:17.11'], {env:{...process.env,POSTGRES_PASSWORD:password},stdio:['ignore','ignore','pipe']});
  const binding = execFileSync('docker',['port', name, '5432/tcp'],{encoding:'utf8'}).trim();
  let ready = false;
  for (let attempt = 0; attempt < 60; attempt++) {
    try { execFileSync('docker',['exec',name,'pg_isready','-U','postgres'],{stdio:'ignore'}); ready = true; break; }
    catch { await new Promise(resolve => setTimeout(resolve, 500)); }
  }
  if (!ready) throw new Error('Disposable PostgreSQL did not become ready.');
  const javaArgs = packaged ? ['-jar',jar] : [`-Dloader.path=${fixturePath}`, '-Dloader.main=org.belsenfixture.BrowserFixtureApplication', '-cp', jar, 'org.springframework.boot.loader.launch.PropertiesLauncher'];
  backend = spawn('java',javaArgs,{stdio:'inherit', env:{...process.env,
    SPRING_PROFILES_ACTIVE:packaged?'production':'local', SPRING_DATASOURCE_URL:`jdbc:postgresql://${binding}/belsen_browser`,
    SPRING_DATASOURCE_USERNAME:'postgres', SPRING_DATASOURCE_PASSWORD:password, BELSEN_SERVER_PORT:packaged?'18443':'18081', BELSEN_GOOGLE_ENABLED:'false',
    ...(packaged ? {BELSEN_PUBLIC_BASE_URL:'https://localhost:18443',BELSEN_TLS_KEY_STORE:keyStore,BELSEN_TLS_KEY_STORE_PASSWORD:tlsPassword} : {}),
  }});
  backend.on('error', () => stop(1)); backend.on('exit',code => stop(code ?? 1));
} catch (error) { console.error(error.message); stop(1); }
