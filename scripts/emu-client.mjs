// Real manual bench client, never part of the offline quality gate.
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { homedir } from 'node:os';
import { join } from 'node:path';
const require = createRequire(new URL('../bridge/package.json', import.meta.url));
const WebSocket = require('ws');
const bench = process.env.REGIE_BENCH_DIR ?? join(homedir(), 'Documents/regie-sandbox');
const action = process.argv[2];
const token = readFileSync(join(bench, 'bridge-token'), 'utf8').trim();
const ws = new WebSocket('ws://127.0.0.1:47600', { headers: { authorization: `Bearer ${token}` } });
const pending = new Map();
let nextId = 0;
const timeout = setTimeout(() => {
  console.error('Délai dépassé : pont ou démo Codex. Consulter le journal du banc.');
  process.exit(1);
}, action === 'demo' ? 180_000 : 8000);
ws.on('message', raw => {
  const frame = JSON.parse(raw.toString());
  const waiter = pending.get(frame.id);
  if (waiter) {
    pending.delete(frame.id);
    if (frame.error) waiter.reject(new Error(JSON.stringify(frame.error)));
    else waiter.resolve(frame.result);
  }
});
function call(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = ++nextId;
    pending.set(id, { resolve, reject });
    ws.send(JSON.stringify({ id, method, params }));
  });
}
try {
  await new Promise((resolve, reject) => {
    ws.once('open', resolve);
    ws.once('error', reject);
    ws.once('unexpected-response', (_, response) => reject(new Error(`HTTP ${response.statusCode}`)));
  });
  await call('hello', { client: 'regie-mac-bench' });
  if (action === 'demo') {
    const { sessions } = await call('sessions.list');
    const saved = join(bench, 'demo-session');
    const ownId = existsSync(saved) ? readFileSync(saved, 'utf8').trim() : null;
    let session = sessions.find(s => s.sessionId === ownId && s.project === 'regie-demo' && s.agent === 'codex' && s.active);
    if (!session) {
      ({ session } = await call('session.create', {
        agent: 'codex', project: 'regie-demo', approvalMode: 'ask',
        prompt: 'Lis README.md dans ce projet de démonstration. Ne modifie aucun fichier. Réponds en français en deux phrases avec le nom du projet et le repère présent dans ce fichier. N’utilise aucun sous-agent.',
      }));
    }
    writeFileSync(join(bench, 'demo-session'), `${session.sessionId}\n`, { mode: 0o600 });
    while (true) {
      const { events } = await call('session.events', { sessionId: session.sessionId });
      const error = events.find(e => e.type === 'session_error');
      if (error) throw new Error(`Codex : ${JSON.stringify(error.data)}`);
      const end = events.find(e => e.type === 'turn_end');
      if (end) {
        const text = events.filter(e => e.type === 'session_update' && e.data.sessionUpdate === 'agent_message_chunk')
          .map(e => e.data.content?.text ?? '').join('');
        if (end.data.stopReason !== 'end_turn' || !text.includes('THOR-DEMO-16')) {
          throw new Error(`Démo non confirmée : ${end.data.stopReason}, réponse : ${text}`);
        }
        console.log(`Session Codex ${session.sessionId}\n${text}`);
        break;
      }
      await new Promise(resolve => setTimeout(resolve, 500));
    }
  } else if (action === 'stop') {
    const closed = new Promise(resolve => ws.once('close', resolve));
    await call('bridge.shutdown');
    await closed;
    console.log('Pont et agents arrêtés (connexion fermée après bridge.shutdown).');
  } else if (action !== 'ping') {
    throw new Error('Usage : emu-client.mjs ping|demo|stop');
  }
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
} finally {
  clearTimeout(timeout);
  ws.terminate();
}
