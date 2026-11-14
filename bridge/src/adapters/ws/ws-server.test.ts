import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import WebSocket from 'ws';
import { Bridge } from '../../domain/bridge.ts';
import type { BridgeEvent } from '../../domain/model.ts';
import { FakeAgentConnector } from '../fake/fake-agent.ts';
import {
  counterIds,
  FakeClock,
  FakeWorkspace,
  MemoryEventStore,
  MemoryLogger,
  MemorySessionStore,
  WordsShellParser,
} from '../fake/memory.ts';
import { HOST, type RunningServer, startWsServer } from './ws-server.ts';

const TOKEN = 'a'.repeat(64);

let codex: FakeAgentConnector;
let bridge: Bridge;
let server: RunningServer;
let shutdowns: number;

beforeEach(async () => {
  codex = new FakeAgentConnector('codex');
  bridge = new Bridge({
    connectors: [new FakeAgentConnector('claude'), codex],
    events: new MemoryEventStore(),
    sessions: new MemorySessionStore(),
    workspace: new FakeWorkspace(),
    clock: new FakeClock(),
    ids: counterIds('approval'),
    logger: new MemoryLogger(),
    shell: new WordsShellParser(),
  });
  bridge.start();
  shutdowns = 0;
  server = await startWsServer({
    port: 0,
    token: TOKEN,
    bridge,
    logger: new MemoryLogger(),
    info: { version: 'test', pid: 1, startedAt: '2026-11-13T20:00:00.000Z' },
    onShutdown: () => {
      shutdowns += 1;
    },
  });
});

afterEach(async () => {
  await server.close();
});

/** A test client: requests by id, and the events it received. */
class Client {
  readonly events: BridgeEvent[] = [];
  private readonly ws: WebSocket;
  private readonly waiting = new Map<number, (frame: Record<string, unknown>) => void>();
  private nextId = 1;
  readonly open: Promise<void>;

  constructor(token: string | null = TOKEN) {
    this.ws = new WebSocket(`ws://${HOST}:${server.port}`, {
      headers: token === null ? {} : { authorization: `Bearer ${token}` },
    });
    this.ws.on('message', (data) => {
      const frame = JSON.parse(data.toString()) as Record<string, unknown>;
      if ('event' in frame) this.events.push(frame.event as BridgeEvent);
      else this.waiting.get(frame.id as number)?.(frame);
    });
    this.open = new Promise((resolve, reject) => {
      this.ws.once('open', () => resolve());
      this.ws.once('unexpected-response', (_req, res) =>
        reject(new Error(`HTTP ${res.statusCode}`)),
      );
      this.ws.once('error', reject);
    });
  }

  async call(method: string, params: Record<string, unknown> = {}) {
    await this.open;
    const id = this.nextId++;
    const answer = new Promise<Record<string, unknown>>((resolve) => this.waiting.set(id, resolve));
    this.ws.send(JSON.stringify({ id, method, params }));
    return answer;
  }

  async close() {
    this.ws.close();
    await new Promise((resolve) => this.ws.once('close', resolve));
  }
}

const settle = () => new Promise((resolve) => setTimeout(resolve, 30));

describe('the WebSocket server', () => {
  it('refuses a connection without the token, before any data', async () => {
    await expect(new Client(null).open).rejects.toThrow('HTTP 401');
    await expect(new Client('b'.repeat(64)).open).rejects.toThrow('HTTP 401');
  });

  it('listens on the loopback only', () => {
    expect(server.address).toBe('127.0.0.1');
  });

  it('runs a session and its approval for a client', async () => {
    const client = new Client();
    await client.call('hello', {});
    const created = await client.call('session.create', {
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'ask',
      prompt: 'Lis notes.md',
    });
    const { sessionId } = (created.result as { session: { sessionId: string } }).session;
    void codex.ask(sessionId, { toolCallId: 't1', kind: 'execute', title: 'cat' }, [
      { optionId: 'allow_once', name: 'Yes', kind: 'allow_once' },
    ]);
    await settle();
    const request = client.events.find((e) => e.type === 'approval_request');
    const { approvalId } = (request?.data ?? {}) as { approvalId: string };

    expect(await client.call('approval.answer', { approvalId, optionId: 'allow_once' })).toEqual({
      id: 3,
      result: {},
    });
    await client.close();
  });

  it('catches up with the events missed while disconnected, without gap or duplicate', async () => {
    const first = new Client();
    await first.call('hello', { since: 0 });
    const created = await first.call('session.create', {
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'ask',
      prompt: 'Go',
    });
    const { sessionId } = (created.result as { session: { sessionId: string } }).session;
    await settle();
    const last = first.events.at(-1)?.seq ?? 0;
    await first.close();

    codex.send(sessionId, {
      sessionUpdate: 'agent_message_chunk',
      content: { type: 'text', text: 'a' },
    });
    codex.endTurn(sessionId);
    await settle();

    const second = new Client();
    const hello = await second.call('hello', { since: last });
    codex.send(sessionId, {
      sessionUpdate: 'agent_message_chunk',
      content: { type: 'text', text: 'b' },
    });
    await settle();

    const seqs = [...first.events, ...second.events].map((e) => e.seq);
    expect(seqs).toEqual(Array.from({ length: bridge.lastSeq }, (_, i) => i + 1));
    expect(hello.result).toMatchObject({ resync: false, sessions: [expect.anything()] });
    expect(second.events.map((e) => e.type)).toContain('turn_end');
    await second.close();
  });

  it('refuses a second hello on the same connection, even sent right after the first', async () => {
    const client = new Client();
    const [first, second] = await Promise.all([
      client.call('hello', { since: 0 }),
      client.call('hello', { since: 0 }),
    ]);
    await settle();

    expect(first).toMatchObject({ result: { lastSeq: 1 } });
    expect(second).toMatchObject({ error: { code: 'bad_request' } });
    expect(client.events.map((e) => e.seq)).toEqual([1]);
    await client.close();
  });

  it('tells a client of another journal to start again', async () => {
    const client = new Client();
    const hello = await client.call('hello', { since: 1, journal: 'an-old-journal' });

    expect(hello.result).toMatchObject({ resync: true, journal: 'memory-journal' });
    await client.close();
  });

  it('tells a client ahead of the journal to start again', async () => {
    const client = new Client();
    const hello = await client.call('hello', { since: 10_000 });

    expect(hello.result).toMatchObject({ resync: true });
    await client.close();
  });

  it('passes new project intent through the public protocol', async () => {
    const client = new Client();
    expect(
      await client.call('session.create', {
        agent: 'codex',
        project: 'Météo',
        newProject: true,
        approvalMode: 'ask',
      }),
    ).toMatchObject({ result: { session: { project: 'meteo', branch: 'main' } } });
    expect(
      await client.call('session.create', {
        agent: 'codex',
        project: 'Météo',
        newProject: true,
        approvalMode: 'ask',
      }),
    ).toMatchObject({ error: { code: 'project_exists' } });
    await client.close();
  });

  it('answers clear errors', async () => {
    const client = new Client();

    expect(await client.call('nope')).toMatchObject({ error: { code: 'bad_request' } });
    expect(await client.call('session.prompt', { sessionId: 'x' })).toMatchObject({
      error: { code: 'bad_request' },
    });
    expect(await client.call('session.prompt', { sessionId: 'x', text: 'hi' })).toMatchObject({
      error: { code: 'unknown_session' },
    });
    expect(
      await client.call('session.create', { agent: 'codex', project: 'nope', approvalMode: 'ask' }),
    ).toMatchObject({ error: { code: 'bad_project' } });
    await client.close();
  });

  it('answers bridge.shutdown, then stops the bridge', async () => {
    const client = new Client();

    expect(await client.call('bridge.shutdown')).toMatchObject({ result: {} });
    expect(shutdowns).toBe(1);
    await client.close();
  });
});

it('relays advertised options, creation selections and acknowledged live changes', async () => {
  codex.initialOptions = [
    {
      id: 'model',
      name: 'Model',
      type: 'select',
      category: 'model',
      currentValue: 'one',
      options: [
        { value: 'one', name: 'One' },
        { value: 'two', name: 'Two' },
      ],
    },
  ];
  const client = new Client();
  const catalogue = await client.call('agent.options', { agent: 'codex' });
  expect(catalogue).toMatchObject({
    result: { configOptions: [{ id: 'model', currentValue: 'one' }] },
  });
  const created = await client.call('session.create', {
    agent: 'codex',
    project: 'regie-demo',
    approvalMode: 'allow-all',
    configValues: { model: 'two' },
  });
  const { sessionId } = (created.result as { session: { sessionId: string } }).session;
  expect(created).toMatchObject({
    result: { session: { configOptions: [{ currentValue: 'two' }] } },
  });
  expect(
    await client.call('session.configure', { sessionId, configId: 'model', value: 'one' }),
  ).toMatchObject({ result: { session: { configOptions: [{ currentValue: 'one' }] } } });
  expect(await client.call('session.configure', { sessionId, approvalMode: 'plan' })).toMatchObject(
    { result: { session: { approvalMode: 'plan' } } },
  );
  expect(
    await client.call('session.configure', { sessionId, configId: 'model', value: 'unknown' }),
  ).toMatchObject({ error: { code: 'unknown_option' } });
  await client.close();
});

it('publishes refreshed choices on repeated failed resumes and accepts an explicit replacement', async () => {
  const model = {
    id: 'model',
    name: 'Model',
    type: 'select',
    category: 'model',
    currentValue: 'large',
    options: [{ value: 'large', name: 'Large' }],
  };
  codex.initialOptions = [model];
  const client = new Client();
  await client.call('hello');
  const created = await client.call('session.create', {
    agent: 'codex',
    project: 'regie-demo',
    approvalMode: 'allow-all',
  });
  const { sessionId } = (created.result as { session: { sessionId: string } }).session;
  codex.crash();
  await settle();
  for (const replacement of ['small', 'new']) {
    codex.initialOptions = [
      { ...model, currentValue: replacement, options: [{ value: replacement, name: replacement }] },
    ];
    expect(await client.call('session.resume', { sessionId })).toMatchObject({
      error: { code: 'agent_failed' },
    });
    expect(client.events.filter((e) => e.type === 'session').at(-1)?.data).toMatchObject({
      active: false,
      configOptions: [{ currentValue: 'large', options: [{ value: replacement }] }],
    });
  }
  expect(
    await client.call('session.configure', { sessionId, configId: 'model', value: 'new' }),
  ).toMatchObject({
    result: { session: { active: true, configOptions: [{ currentValue: 'new' }] } },
  });
  expect(codex.callsOf('prompt')).toEqual([]);
  await client.close();
});

it('accepts joint resume replacements and rejects mixed or empty configuration payloads', async () => {
  const option = (id: string, value: string) => ({
    id,
    name: id,
    type: 'select',
    category: id === 'model' ? 'model' : 'thought_level',
    currentValue: value,
    options: [{ value, name: value }],
  });
  codex.initialOptions = [option('model', 'old'), option('effort', 'high')];
  const client = new Client();
  await client.call('hello');
  const created = await client.call('session.create', {
    agent: 'codex',
    project: 'regie-demo',
    approvalMode: 'allow-all',
  });
  const { sessionId } = (created.result as { session: { sessionId: string } }).session;
  codex.crash();
  await settle();
  codex.initialOptions = [option('model', 'new'), option('effort', 'low')];
  expect(await client.call('session.resume', { sessionId })).toMatchObject({
    error: { code: 'agent_failed' },
  });
  for (const invalid of [
    { configValues: {} },
    { configValues: { model: 'new' }, configId: 'effort', value: 'low' },
    { configValues: { model: 'new' }, approvalMode: 'ask' },
  ])
    expect(await client.call('session.configure', { sessionId, ...invalid })).toHaveProperty(
      'error',
    );
  expect(
    await client.call('session.configure', {
      sessionId,
      configValues: { model: 'new', effort: 'low' },
    }),
  ).toMatchObject({
    result: {
      session: {
        active: true,
        approvalMode: 'allow-all',
        configOptions: [{ currentValue: 'new' }, { currentValue: 'low' }],
      },
    },
  });
  expect(codex.callsOf('prompt')).toEqual([]);
  await client.close();
});
