import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, expect, it } from 'vitest';
import { OpenCodeApiCredentials } from './opencode-credentials.ts';

const dirs: string[] = [];
afterEach(() => {
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});
function synthetic(fail = false) {
  const dir = mkdtempSync(join(tmpdir(), 'regie-key-test-'));
  dirs.push(dir);
  const script = join(dir, 'server.cjs');
  const binary = join(dir, 'opencode');
  const log = join(dir, 'requests.jsonl');
  writeFileSync(
    script,
    `
    const http = require('node:http'), fs = require('node:fs');
    const log = ${JSON.stringify(log)};
    const server = http.createServer(async (req, res) => {
      let body = ''; for await (const chunk of req) body += chunk;
      const authorized = req.headers.authorization === 'Basic ' + Buffer.from('opencode:' + process.env.OPENCODE_PASSWORD).toString('base64');
      fs.appendFileSync(log, JSON.stringify({ path: req.url.split('?')[0], method: req.method, body, authorized, args: process.argv }) + '\\n');
      if (${fail} && req.method === 'POST') { res.writeHead(400).end('test-only-key'); return; }
      if (req.url.startsWith('/api/model/default')) { res.end(JSON.stringify({data:{}})); return; }
      if (req.method === 'GET') res.end(JSON.stringify({ data: { connections: [{ type: 'credential', id: 'go-credential' }, { type: 'environment', name: 'EXAMPLE' }] } }));
      else res.writeHead(204).end();
    });
    server.listen(0, '127.0.0.1', () => console.log(JSON.stringify({url:'http://127.0.0.1:' + server.address().port})));
    process.stdin.resume(); process.stdin.on('end', () => { fs.appendFileSync(log, JSON.stringify({closed:true}) + '\\n'); server.close(); server.closeAllConnections(); });
  `,
  );
  const quote = (s: string) => `'${s.replaceAll("'", "'\\''")}'`;
  writeFileSync(binary, `#!/bin/sh\nexec ${quote(process.execPath)} ${quote(script)} "$@"\n`, {
    mode: 0o700,
  });
  return {
    driver: new OpenCodeApiCredentials(binary),
    requests: () =>
      readFileSync(log, 'utf8')
        .trim()
        .split('\n')
        .map((line) => JSON.parse(line)),
  };
}

it('writes a key through the authenticated loopback API body, then closes its private server', async () => {
  const { driver, requests } = synthetic();
  await driver.save('opencode-go', 'test-only-key');
  expect(requests()).toMatchObject([
    { path: '/api/model/default', method: 'GET', authorized: true },
    {
      path: '/api/integration/opencode-go/connect/key',
      method: 'POST',
      authorized: true,
      body: '{"key":"test-only-key"}',
    },
    { closed: true },
  ]);
  expect(JSON.stringify(requests()[1].args)).not.toContain('test-only-key');
});

it('removes only Go credential ids discovered through the public integration endpoint', async () => {
  const { driver, requests } = synthetic();
  await driver.logout('opencode-go');
  expect(
    requests()
      .filter((r) => r.path)
      .map((r) => [r.method, r.path]),
  ).toEqual([
    ['GET', '/api/model/default'],
    ['GET', '/api/integration/opencode-go'],
    ['DELETE', '/api/credential/go-credential'],
  ]);
});

it('drops provider error bodies that might echo the key and still reaps the private server', async () => {
  const { driver, requests } = synthetic(true);
  await expect(driver.save('opencode-go', 'test-only-key')).rejects.toThrow(
    'OpenCode credential operation failed',
  );
  expect(requests().at(-1)).toEqual({ closed: true });
});
