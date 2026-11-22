/** Static-only build. Node 24 core modules; no framework, dependency, network or secrets. */
import { mkdir, readFile, writeFile, cp, rm } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const root = path.dirname(fileURLToPath(import.meta.url));
if (Number(process.versions.node.split('.')[0]) !== 24) throw new Error('Use Node.js 24.');
const configured = process.env.BELSEN_APP_URL;
// Preserve the configured repository. Public links require a separate access/privacy review.
const repository = 'https://github.com/Aqyn97/Belsen-AI';
const sourcePublic = process.env.BELSEN_SOURCE_PUBLIC === 'true';
let launch = '<span class="button unavailable">Interactive app not deployed</span>';
let status = 'The full Java application has no verified public URL. Oracle ARM hosting is a proposal, subject to eligibility, available capacity and HTTPS verification. This public showcase works independently and needs no login.';
if (configured) {
  // Operator attestation, not automated verification. No network request is made here.
  if (process.env.BELSEN_APP_VERIFIED !== 'true')
    throw new Error('A launch URL requires BELSEN_APP_VERIFIED=true after separate public HTTPS verification.');
  let url;
  try { url = new URL(configured); } catch { throw new Error('Invalid public HTTPS application origin.'); }
  const dns = /^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/;
  if (url.protocol !== 'https:' || configured !== url.origin || !dns.test(url.hostname)
      || url.port || url.username || url.password
      || /(?:^|\.)(?:localhost|local|internal|test|invalid|example)$/.test(url.hostname)
      || /(?:^|\.)onrender\.com$/.test(url.hostname))
    throw new Error('Use a verified public HTTPS DNS origin without credentials, path, port or query. Render is not approved.');
  launch = `<a class="button" href="${configured}/auth" aria-describedby="app-status">Launch application <span aria-hidden="true">↗</span></a>`;
  status = 'Application access uses approved individual accounts on its separately verified HTTPS origin. This public showcase does not access your account or call authenticated APIs.';
} else if (process.env.BELSEN_APP_VERIFIED === 'true') {
  throw new Error('BELSEN_APP_VERIFIED requires a verified BELSEN_APP_URL.');
}
const out = path.join(root, 'dist');
await rm(out, { recursive: true, force: true });
await mkdir(out);
for (const file of ['styles.css', '404.html', '_headers']) await cp(path.join(root, file), path.join(out, file));
await cp(path.join(root, 'assets'), path.join(out, 'assets'), { recursive: true });
const template = await readFile(path.join(root, 'index.html'), 'utf8');
const replacements = {
  LAUNCH: launch, APP_STATUS: status,
  SOURCE_NAV: sourcePublic ? `<a href="${repository}">GitHub <span aria-hidden="true">↗</span></a>` : '<a href="#release-status">Release status</a>',
  SOURCE_HERO: sourcePublic ? `<a class="text-link" href="${repository}">Explore the source ↗</a>` : '<a class="text-link" href="#engineering">Explore the engineering</a>',
  SOURCE_ARCH: sourcePublic ? `<a class="text-link" href="${repository}/blob/main/docs/ARCHITECTURE.md">Read the architecture ↗</a>` : '<a class="text-link" href="#engineering">Review the engineering</a>',
  SOURCE_CLOSING: sourcePublic ? `<a class="button" href="${repository}">View Belsen-AI on GitHub ↗</a>` : '<a class="button" href="#product">Explore the product</a>',
  SOURCE_FOOTER: sourcePublic ? `<a href="${repository}/blob/main/docs/FREE_HOSTING.md">Hosting status &amp; limitations ↗</a>` : '<a href="#release-status">Release status</a>',
  SOURCE_STATUS: sourcePublic ? 'Public source access has been approved for this build.' : 'Public source access is not enabled. The repository remains private.',
};
let html = template;
for (const [key, value] of Object.entries(replacements)) html = html.replace(`<!-- ${key} -->`, value);
await writeFile(path.join(out, 'index.html'), html);
console.log(`Static showcase built. Launch ${configured ? 'explicitly attested by operator' : 'absent; no live application claimed'}.`);
