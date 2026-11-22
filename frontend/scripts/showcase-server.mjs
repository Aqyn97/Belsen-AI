// Local test server only. Match Pages headers and explicit 404; never proxy APIs.
import http from 'node:http';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../../showcase/dist/', import.meta.url));
const headers = Object.fromEntries((await readFile(path.join(root, '_headers'), 'utf8')).trim().split('\n').slice(1).map(line => {
  const i = line.indexOf(':'); return [line.slice(0, i).trim(), line.slice(i + 1).trim()];
}));
const server = http.createServer(async (req, res) => {
  const route = new URL(req.url, 'http://localhost').pathname;
  const files = { '/': 'index.html', '/styles.css': 'styles.css', '/assets/dashboard.png': 'assets/dashboard.png', '/assets/analytics.png': 'assets/analytics.png' };
  const file = files[route] ?? '404.html';
  const type = file.endsWith('.css') ? 'text/css' : file.endsWith('.png') ? 'image/png' : 'text/html; charset=utf-8';
  res.writeHead(files[route] ? 200 : 404, { ...headers, 'Content-Type': type });
  res.end(await readFile(path.join(root, file)));
});
server.listen(4175, '127.0.0.1');
for (const signal of ['SIGTERM', 'SIGINT']) process.once(signal, () => server.close());
