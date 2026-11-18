/**
 * Demo client of the bridge, run from the Mac (thor/bridge/demo.sh sets it up):
 *
 *   adb -s $THOR_SERIAL forward tcp:47600 tcp:47600
 *   REGIE_BRIDGE_TOKEN=… node scripts/demo-client.ts
 *
 * It starts a Claude session and a Codex session on ~/projects/regie-demo, answers « allow once »
 * to their approvals, disconnects, reconnects with the last sequence number and checks it gets
 * the missed events without gap or duplicate, then lists the sessions. It records what the
 * bridge sent in fixtures/bridge-ws/ (e-mails hidden): the app's fake gateway replays it.
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import WebSocket from 'ws';
import { redact } from '../src/domain/redact.ts';

type Frame = Record<string, unknown>;
interface Event {
  seq: number;
  at: string;
  sessionId: string | null;
  type: string;
  data: Record<string, unknown>;
}

const url = process.env.REGIE_BRIDGE_URL ?? 'ws://127.0.0.1:47600';
const token = process.env.REGIE_BRIDGE_TOKEN ?? '';
const project = process.env.REGIE_DEMO_PROJECT ?? 'regie-demo';
const fixtures =
  process.env.REGIE_FIXTURES_DIR ?? join(import.meta.dirname, '../fixtures/bridge-ws');
const PROMPT =
  'Lis notes.md et ajoute son résumé en une seule ligne à la fin de todo.md. ' +
  'Ne modifie aucun autre fichier.';
const t0 = Date.now();

if (token.length < 32) {
  console.error('REGIE_BRIDGE_TOKEN manquant : lancer thor/bridge/demo.sh.');
  process.exit(2);
}

const recording: Array<{ t: number; dir: string; frame: Frame }> = [];
const events: Event[] = [];
const sessions = new Map<string, string>(); // sessionId → agent, the sessions of this run
const agentOf = new Map<string, string>(); // sessionId → agent, from the `session` events
const answered = new Set<string>(); // agents that had an approval answered
const ended = new Set<string>();
const texts = new Map<string, string>();
let failures = 0;

const clock = () => `${((Date.now() - t0) / 1000).toFixed(1).padStart(6)}s`;
const say = (line: string) => console.log(`${clock()}  ${line}`);
const check = (ok: boolean, line: string) => {
  if (!ok) failures += 1;
  say(`${ok ? 'OK   ' : 'ÉCHEC'} ${line}`);
};

class Connection {
  private readonly ws: WebSocket;
  private readonly waiting = new Map<number, (frame: Frame) => void>();
  private nextId = 1;
  readonly opened: Promise<void>;
  onEvent: (event: Event) => void = () => {};

  constructor() {
    this.ws = new WebSocket(url, { headers: { authorization: `Bearer ${token}` } });
    this.ws.on('message', (data) => {
      const frame = JSON.parse(data.toString()) as Frame;
      recording.push({ t: Date.now() - t0, dir: 'bridge->client', frame });
      if ('event' in frame) this.onEvent(frame.event as Event);
      else this.waiting.get(frame.id as number)?.(frame);
    });
    this.opened = new Promise((resolve, reject) => {
      this.ws.once('open', () => resolve());
      this.ws.once('unexpected-response', (_q, r) => reject(new Error(`HTTP ${r.statusCode}`)));
      this.ws.once('error', reject);
    });
  }

  async call(method: string, params: Frame = {}): Promise<Frame> {
    await this.opened;
    const id = this.nextId++;
    const frame = { id, method, params };
    recording.push({ t: Date.now() - t0, dir: 'client->bridge', frame });
    const answer = new Promise<Frame>((resolve) => this.waiting.set(id, resolve));
    this.ws.send(JSON.stringify(frame));
    const response = await answer;
    if ('error' in response)
      throw Object.assign(new Error(JSON.stringify(response.error)), response);
    return response.result as Frame;
  }

  close(): Promise<void> {
    this.ws.close();
    return new Promise((resolve) => this.ws.once('close', () => resolve()));
  }
}

const short = (id: string | null) => (id === null ? 'pont' : `${agentOf.get(id) ?? '?'}`);
const clip = (text: string, n = 90) => {
  const flat = text.replace(/\s+/g, ' ').trim();
  return flat.length > n ? `${flat.slice(0, n - 1)}…` : flat;
};

function flushText(sessionId: string) {
  const text = texts.get(sessionId);
  if (text) say(`        ${short(sessionId)} dit : « ${clip(text, 140)} »`);
  texts.delete(sessionId);
}

function describe(event: Event) {
  const who = short(event.sessionId);
  const { data } = event;
  const head = `#${String(event.seq).padStart(5)} ${who.padEnd(6)}`;
  switch (event.type) {
    case 'session_update': {
      const kind = data.sessionUpdate as string;
      if (kind === 'agent_message_chunk' && event.sessionId) {
        const content = data.content as { text?: string } | undefined;
        texts.set(event.sessionId, (texts.get(event.sessionId) ?? '') + (content?.text ?? ''));
        return;
      }
      if (kind === 'tool_call' || (kind === 'tool_call_update' && data.status)) {
        if (event.sessionId) flushText(event.sessionId);
        const hidden = (data._meta as { regie?: { hidden?: string } } | undefined)?.regie?.hidden;
        const title = typeof data.title === 'string' ? ` ${clip(data.title, 70)}` : '';
        say(
          `${head} outil ${kind === 'tool_call' ? 'lancé' : String(data.status)}${title}${hidden ? ` (masqué : ${hidden})` : ''}`,
        );
      }
      return;
    }
    case 'approval_request': {
      if (event.sessionId) flushText(event.sessionId);
      const toolCall = data.toolCall as { title?: string; content?: unknown[] };
      const options = (data.options as Array<{ optionId: string; kind: string }>)
        .map((o) => `${o.optionId}(${o.kind})`)
        .join(', ');
      const diff = toolCall.content?.some((c) => (c as { type?: string }).type === 'diff');
      say(
        `${head} VALIDATION « ${clip(toolCall.title ?? '', 60)} »${diff ? ' avec diff' : ''} : ${options}`,
      );
      return;
    }
    case 'approval_resolved':
      say(`${head} validation réglée : ${JSON.stringify(data.outcome)} par ${String(data.by)}`);
      return;
    case 'turn_end':
      if (event.sessionId) flushText(event.sessionId);
      say(`${head} fin du tour : ${String(data.stopReason)}`);
      return;
    case 'session':
      say(
        `${head} statut ${String(data.status)}${data.title ? ` — ${clip(String(data.title), 50)}` : ''}`,
      );
      return;
    case 'user_message':
      say(`${head} consigne (${String(data.delivery)}) : « ${clip(String(data.text), 60)} »`);
      return;
    default:
      say(`${head} ${event.type} ${clip(JSON.stringify(data), 100)}`);
  }
}

function pickAllowOnce(options: Array<{ optionId: string; kind: string }>) {
  return (
    options.find((o) => o.kind === 'allow_once') ?? options.find((o) => o.kind.startsWith('allow'))
  );
}

function handle(connection: Connection) {
  connection.onEvent = (event) => {
    events.push(event);
    if (event.type === 'session' && event.sessionId) {
      agentOf.set(event.sessionId, String(event.data.agent));
    }
    describe(event);
    if (event.type === 'turn_end' && event.sessionId) ended.add(event.sessionId);
    if (event.type === 'approval_request' && event.sessionId) {
      const option = pickAllowOnce(event.data.options as Array<{ optionId: string; kind: string }>);
      if (!option) return;
      const agent = sessions.get(event.sessionId) ?? '?';
      connection
        .call('approval.answer', { approvalId: event.data.approvalId, optionId: option.optionId })
        .then(
          () => {
            say(`        → réponse ${option.optionId} (autoriser une fois) à ${agent}`);
            answered.add(agent);
          },
          () => say(`        → validation déjà réglée (${agent})`),
        );
    }
  };
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));
async function until(condition: () => boolean, ms: number, label: string) {
  const deadline = Date.now() + ms;
  while (!condition()) {
    if (Date.now() > deadline) throw new Error(`délai dépassé : ${label}`);
    await sleep(200);
  }
}

function contiguous(list: Event[]): boolean {
  return list.every((event, i) => i === 0 || event.seq === (list[i - 1]?.seq ?? 0) + 1);
}

/** `--list`: only lists the sessions of the bridge, current and past. */
async function listOnly(connection: Connection) {
  const { sessions: listed } = (await connection.call('sessions.list')) as {
    sessions: Array<Record<string, unknown>>;
  };
  say(`${listed.length} sessions :`);
  for (const s of listed) {
    say(
      `  ${String(s.agent).padEnd(6)} ${String(s.status).padEnd(18)} ${String(s.project)}@${s.branch} actif=${s.active} « ${clip(String(s.title), 50)} » ${s.updatedAt}`,
    );
  }
  await connection.close();
  process.exit(0);
}

async function main() {
  say(`connexion à ${url}`);
  let connection = new Connection();
  handle(connection);
  const hello = await connection.call('hello', {});
  const start = hello.lastSeq as number;
  say(`pont ${JSON.stringify(hello.bridge)}, dernier numéro ${start}`);
  if (process.argv.includes('--list')) await listOnly(connection);

  const { agents } = (await connection.call('agents.versions')) as {
    agents: Array<Record<string, unknown>>;
  };
  for (const agent of agents) {
    const adapter = agent.adapter as { name: string; version: string } | null;
    say(
      `version ${agent.agent} : adaptateur ${adapter?.name}@${adapter?.version}, binaire ${agent.binary}, testée : ${agent.isTested}`,
    );
  }
  const { projects } = (await connection.call('projects.list')) as { projects: unknown[] };
  say(`projets : ${JSON.stringify(projects)}`);

  for (const agent of ['claude', 'codex']) {
    const { session } = (await connection.call('session.create', {
      agent,
      project,
      approvalMode: 'ask',
      prompt: PROMPT,
    })) as { session: { sessionId: string; branch: string | null; status: string } };
    sessions.set(session.sessionId, agent);
    say(
      `session ${agent} créée : ${session.sessionId} (branche ${session.branch}, ${session.status})`,
    );
  }

  await until(
    () => answered.has('claude') && answered.has('codex'),
    5 * 60_000,
    'une validation de chaque agent',
  );
  const lastBefore = events.at(-1)?.seq ?? start;
  say(`--- déconnexion volontaire, dernier numéro reçu ${lastBefore} ---`);
  await connection.close();
  await sleep(10_000);

  say(`--- reconnexion avec since=${lastBefore}, journal ${String(hello.journal)} ---`);
  connection = new Connection();
  const beforeReconnect = events.length;
  handle(connection);
  const again = await connection.call('hello', { since: lastBefore, journal: hello.journal });
  await sleep(500);
  const caughtUp = events.slice(beforeReconnect);
  const missed = caughtUp.filter((e) => e.seq <= (again.lastSeq as number));
  check(again.resync === false, 'le pont reconnaît le numéro envoyé');
  check(
    caughtUp[0]?.seq === lastBefore + 1,
    `le rattrapage commence à ${lastBefore + 1} (reçu ${caughtUp[0]?.seq})`,
  );
  check(
    contiguous(events),
    `${missed.length} événements manqués rattrapés, numéros ${lastBefore + 1} à ${again.lastSeq}, sans trou ni doublon`,
  );

  await until(
    () => [...sessions.keys()].every((id) => ended.has(id)),
    6 * 60_000,
    'fin des deux tours',
  );
  await sleep(1000);
  check(
    contiguous(events),
    `${events.length} événements reçus au total, numéros contigus de ${events[0]?.seq} à ${events.at(-1)?.seq}`,
  );

  const { sessions: listed } = (await connection.call('sessions.list')) as {
    sessions: Array<Record<string, unknown>>;
  };
  say('sessions du pont :');
  for (const s of listed.slice(0, 8)) {
    say(
      `  ${String(s.agent).padEnd(6)} ${String(s.status).padEnd(18)} ${String(s.project)}@${s.branch} actif=${s.active} « ${clip(String(s.title), 50)} » ${s.updatedAt}`,
    );
  }
  for (const [sessionId, agent] of sessions) {
    const { events: stored } = (await connection.call('session.events', { sessionId })) as {
      events: Event[];
    };
    const received = events.filter((e) => e.sessionId === sessionId);
    check(
      JSON.stringify(stored.map((e) => e.seq)) === JSON.stringify(received.map((e) => e.seq)),
      `${agent} : le journal du pont et le client ont les mêmes ${received.length} événements`,
    );
    check(
      received.some((e) => e.type === 'approval_request') &&
        received.some((e) => e.type === 'approval_resolved'),
      `${agent} : une validation demandée et réglée`,
    );
  }
  await connection.close();

  mkdirSync(fixtures, { recursive: true });
  const write = (name: string, lines: unknown[]) =>
    writeFileSync(
      join(fixtures, name),
      `${lines.map((l) => redact(JSON.stringify(l))).join('\n')}\n`,
    );
  write('demo-run.jsonl', recording);
  for (const [sessionId, agent] of sessions) {
    write(
      `${agent}-session.jsonl`,
      events.filter((e) => e.sessionId === sessionId),
    );
  }
  say(`enregistrements écrits dans ${fixtures}`);
  say(failures === 0 ? 'DÉMO RÉUSSIE' : `DÉMO EN ÉCHEC (${failures})`);
  process.exit(failures === 0 ? 0 : 1);
}

main().catch((error: unknown) => {
  console.error(error);
  process.exit(1);
});
