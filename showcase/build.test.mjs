import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync, readdirSync } from 'node:fs';
const build = (url = '', verified = '', sourcePublic = '') => spawnSync(process.execPath, ['showcase/build.mjs'], { env: { ...process.env, BELSEN_APP_URL: url, BELSEN_APP_VERIFIED: verified, BELSEN_SOURCE_PUBLIC: sourcePublic }, encoding: 'utf8' });
test('default makes no live claim or Launch App link', () => {
  assert.equal(build().status, 0);
  const html = readFileSync('showcase/dist/index.html', 'utf8');
  assert.match(html, /Interactive app not deployed/);
  assert.doesNotMatch(html, />Launch application/);
  assert.match(html, /Oracle ARM hosting is a proposal/);
  assert.match(html, /Source links are not enabled for this showcase release/);
  assert.doesNotMatch(html, /repository remains private/i);
});
test('URL alone cannot enable a launch link', () => {
  for (const flag of ['', 'false', 'yes']) assert.notEqual(build('https://demo.example.org', flag).status, 0);
  assert.notEqual(build('', 'true').status, 0);
});
test('explicitly attested future origin creates navigation only', () => {
  assert.equal(build('https://demo.example.org', 'true').status, 0);
  const html = readFileSync('showcase/dist/index.html', 'utf8');
  assert.match(html, /href="https:\/\/demo.example.org\/auth"/);
  assert.doesNotMatch(html, /has no verified public URL/);
  assert.equal(build().status, 0);
});
test('rejects unsafe, ambiguous, internal and unapproved Render origins before writing', () => {
  for (const url of ['http://demo.example.org', 'https://demo.example.org/', 'https://demo.example.org?token=x', 'https://user:secret@demo.example.org', 'https://demo.example.org:8443', 'https://localhost', 'https://127.0.0.1', 'https://fixture.internal', 'https://fixture.onrender.com', 'https://onrender.com', 'javascript:alert(1)', 'https://demo.example.org/"<script>'])
    assert.notEqual(build(url, 'true').status, 0);
  assert.match(readFileSync('showcase/dist/index.html', 'utf8'), /Interactive app not deployed/);
});
test('static files have no scripts, API connections or implicit SPA fallback', () => {
  assert.doesNotMatch(readFileSync('showcase/dist/index.html', 'utf8'), /<script|<form|iframe/i);
  assert.match(readFileSync('showcase/dist/_headers', 'utf8'), /connect-src 'none'; script-src 'none'/);
  assert.match(readFileSync('showcase/dist/404.html', 'utf8'), /Page not found/);
});

test('malformed input is rejected without echoing the supplied value', () => {
  const result = build('accidentally-pasted-sensitive-value', 'true');
  assert.notEqual(result.status, 0);
  assert.doesNotMatch(result.stderr, /accidentally-pasted-sensitive-value/);
});

test('source links stay hidden pending approval; explicit approval retains the configured repository', () => {
  assert.equal(build().status, 0);
  assert.doesNotMatch(readFileSync('showcase/dist/index.html', 'utf8'), /github\.com/);
  assert.equal(build('', '', 'true').status, 0);
  const html = readFileSync('showcase/dist/index.html', 'utf8');
  assert.match(html, /href="https:\/\/github\.com\/Aqyn97\/Belsen-AI"/);
  assert.equal(build().status, 0);
});

test('bundle includes only the six approved public files and unchanged genuine PNGs', () => {
  assert.equal(build().status, 0);
  assert.deepEqual(readdirSync('showcase/dist').sort(), ['404.html', '_headers', 'assets', 'index.html', 'styles.css']);
  assert.deepEqual(readdirSync('showcase/dist/assets').sort(), ['analytics.png', 'dashboard.png']);
  for (const file of ['dashboard.png', 'analytics.png']) {
    const image = readFileSync(`showcase/assets/${file}`);
    assert.deepEqual(readFileSync(`showcase/dist/assets/${file}`), image);
    // No textual/EXIF metadata or extra payload chunks enter the public screenshots.
    assert.equal(image.subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
    let offset = 8;
    while (offset < image.length) {
      assert.ok(['IHDR', 'IDAT', 'IEND'].includes(image.toString('ascii', offset + 4, offset + 8)));
      offset += image.readUInt32BE(offset) + 12;
    }
    assert.equal(offset, image.length);
  }
});
