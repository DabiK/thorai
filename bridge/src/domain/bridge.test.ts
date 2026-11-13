import { beforeEach, describe, expect, it } from 'vitest';
import { FakeAgentConnector } from '../adapters/fake/fake-agent.ts';
import {
  counterIds,
  FakeClock,
  FakeWorkspace,
  MemoryEventStore,
  MemoryLogger,
  MemorySessionStore,
} from '../adapters/fake/memory.ts';
import { Bridge } from './bridge.ts';
import type { BridgeEvent } from './model.ts';

const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

const ALLOW_ONCE = { optionId: 'allow_once', name: 'Yes, proceed', kind: 'allow_once' };
const ALWAYS = {
  optionId: 'accept_execpolicy_amendment',
  name: 'Yes, and don’t ask again',
  kind: 'allow_always',
};
const REJECT = { optionId: 'cancel', name: 'No', kind: 'reject_once' };

class World {
  readonly claude = new FakeAgentConnector('claude');
  readonly codex = new FakeAgentConnector('codex');
  readonly events = new MemoryEventStore();
  readonly sessions = new MemorySessionStore();
  readonly workspace = new FakeWorkspace();
  bridge = this.newBridge();

  newBridge(): Bridge {
    const bridge = new Bridge({
      connectors: [this.claude, this.codex],
      events: this.events,
      sessions: this.sessions,
      workspace: this.workspace,
      clock: new FakeClock(),
      ids: counterIds('approval'),
      logger: new MemoryLogger(),
      shutdownGraceMs: 10,
    });
    bridge.start();
    return bridge;
  }

  /** Simulates a restart of the bridge on the same stored data. */
  restart(): Bridge {
    this.bridge = this.newBridge();
    return this.bridge;
  }

  received(since = 0): BridgeEvent[] {
    const received: BridgeEvent[] = [];
    this.bridge.subscribe(since, (event) => received.push(event))();
    return received;
  }

  ofType(type: string): BridgeEvent[] {
    return this.received().filter((event) => event.type === type);
  }
}

let world: World;
beforeEach(() => {
  world = new World();
});

describe('creating a session', () => {
  it('forces the mode of the approval level after session/new, for each agent', async () => {
    await world.bridge.createSession({
      agent: 'claude',
      project: 'regie-demo',
      approvalMode: 'ask',
    });
    await world.bridge.createSession({
      agent: 'claude',
      project: 'regie-demo',
      approvalMode: 'allow-all',
    });
    await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'ask',
    });
    await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'allow-all',
    });

    expect(world.claude.callsOf('setMode').map(([, mode]) => mode)).toEqual([
      'default',
      'bypassPermissions',
    ]);
    expect(world.codex.callsOf('setMode').map(([, mode]) => mode)).toEqual([
      'workspace-write',
      'agent-full-access',
    ]);
  });

  it('keeps one adapter process per agent for all its sessions', async () => {
    await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'ask',
    });
    await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'ask',
    });

    expect(world.codex.connections).toBe(1);
    expect(world.bridge.listSessions()).toHaveLength(2);
  });

  it('records agent, project, branch and status, and sends the first instruction', async () => {
    const session = await world.bridge.createSession({
      agent: 'claude',
      project: 'regie-demo',
      branch: 'essai',
      approvalMode: 'ask',
      prompt: 'Lis notes.md',
    });

    expect(world.workspace.switches).toEqual([
      { cwd: '/home/dev/projects/regie-demo', branch: 'essai' },
    ]);
    expect(world.claude.callsOf('prompt')).toEqual([[session.sessionId, 'Lis notes.md']]);
    expect(world.bridge.listSessions()[0]).toMatchObject({
      sessionId: session.sessionId,
      agent: 'claude',
      project: 'regie-demo',
      cwd: '/home/dev/projects/regie-demo',
      branch: 'essai',
      approvalMode: 'ask',
      status: 'working',
      title: 'Lis notes.md',
      active: true,
    });
  });

  it('fails clearly when the branch cannot be checked out, without starting a session', async () => {
    world.workspace.refused.add('cassee');

    await expect(
      world.bridge.createSession({
        agent: 'claude',
        project: 'regie-demo',
        branch: 'cassee',
        approvalMode: 'ask',
      }),
    ).rejects.toMatchObject({ code: 'branch_failed' });
    expect(world.claude.callsOf('newSession')).toEqual([]);
  });

  it('refuses a project outside the projects root', async () => {
    await expect(
      world.bridge.createSession({ agent: 'codex', project: '../etc', approvalMode: 'ask' }),
    ).rejects.toMatchObject({ code: 'bad_project' });
  });

  it('marks the session failed when the agent refuses the mode', async () => {
    world.claude.setModeError = new Error('mode inconnu');

    await expect(
      world.bridge.createSession({ agent: 'claude', project: 'regie-demo', approvalMode: 'ask' }),
    ).rejects.toMatchObject({ code: 'agent_failed' });
    expect(world.bridge.listSessions()[0]).toMatchObject({ status: 'failed', active: false });
  });

  it('reports an agent that does not start', async () => {
    world.codex.connectError = new Error('spawn ENOENT');

    await expect(
      world.bridge.createSession({ agent: 'codex', project: 'regie-demo', approvalMode: 'ask' }),
    ).rejects.toMatchObject({ code: 'agent_failed', message: expect.stringContaining('ENOENT') });
  });
});

describe('a turn', () => {
  it('goes from working to idle, with the agent updates relayed in between', async () => {
    const { sessionId } = await world.bridge.createSession({
      agent: 'claude',
      project: 'regie-demo',
      approvalMode: 'ask',
      prompt: 'Bonjour',
    });
    world.claude.send(sessionId, {
      sessionUpdate: 'agent_message_chunk',
      content: { type: 'text', text: 'Salut' },
    });
    world.claude.endTurn(sessionId);
    await flush();

    const types = world.received().filter((e) => e.sessionId === sessionId);
    expect(types.map((e) => e.type)).toEqual([
      'session',
      'user_message',
      'session',
      'session_update',
      'turn_end',
      'session',
    ]);
    expect(world.bridge.listSessions()[0]?.status).toBe('idle');
  });

  it('injects a message sent during a turn when the agent accepts steering', async () => {
    const { sessionId } = await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'ask',
      prompt: 'Compte',
    });

    await expect(world.bridge.prompt(sessionId, 'Arrête')).resolves.toBe('steering');
    expect(world.codex.callsOf('steer')).toEqual([[sessionId, 'Arrête']]);
    expect(world.codex.runningTurns(sessionId)).toBe(1);
  });

  it('queues a message sent during a turn when the agent refuses steering', async () => {
    world.claude.acceptsSteering = false;
    const { sessionId } = await world.bridge.createSession({
      agent: 'claude',
      project: 'regie-demo',
      approvalMode: 'ask',
      prompt: 'Compte',
    });

    await expect(world.bridge.prompt(sessionId, 'Ensuite')).resolves.toBe('queued');
    expect(world.claude.runningTurns(sessionId)).toBe(2);
    world.claude.endTurn(sessionId);
    await flush();
    expect(world.bridge.listSessions()[0]?.status).toBe('working');
    world.claude.endTurn(sessionId);
    await flush();
    expect(world.bridge.listSessions()[0]?.status).toBe('idle');
  });

  it('sends a plain prompt when no turn is running', async () => {
    const { sessionId } = await world.bridge.createSession({
      agent: 'claude',
      project: 'regie-demo',
      approvalMode: 'ask',
    });

    await expect(world.bridge.prompt(sessionId, 'Go')).resolves.toBe('prompt');
    expect(world.claude.callsOf('steer')).toEqual([]);
  });

  it('marks the session failed when the turn ends on an error', async () => {
    const { sessionId } = await world.bridge.createSession({
      agent: 'claude',
      project: 'regie-demo',
      approvalMode: 'ask',
      prompt: 'Go',
    });
    world.claude.failTurn(sessionId, new Error('quota dépassé'));
    await flush();

    expect(world.bridge.listSessions()[0]).toMatchObject({
      status: 'failed',
      lastError: 'Claude : quota dépassé',
    });
    expect(world.ofType('session_error')).toHaveLength(1);
  });

  it('interrupts a turn and keeps the session usable', async () => {
    const { sessionId } = await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'ask',
      prompt: 'Long',
    });
    await world.bridge.cancel(sessionId);
    await flush();

    expect(world.ofType('turn_end').at(-1)?.data).toMatchObject({ stopReason: 'cancelled' });
    expect(world.bridge.listSessions()[0]).toMatchObject({ status: 'idle', active: true });
  });
});

describe('approvals', () => {
  async function codexAsking() {
    const { sessionId } = await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'ask',
      prompt: 'Lis notes.md',
    });
    const answer = world.codex.ask(
      sessionId,
      { toolCallId: 'exec-1', title: 'cat notes.md', kind: 'execute' },
      [ALLOW_ONCE, ALWAYS, REJECT],
    );
    const request = world.ofType('approval_request').at(-1)?.data as {
      approvalId: string;
      options: Array<{ optionId: string }>;
    };
    return { sessionId, answer, request };
  }

  it('relays the request without Codex’s « always allow » and waits for the answer', async () => {
    const { request } = await codexAsking();

    expect(request.options.map((o) => o.optionId)).toEqual(['allow_once', 'cancel']);
    expect(world.bridge.listSessions()[0]).toMatchObject({
      status: 'awaiting_approval',
      pendingApprovals: [request.approvalId],
    });
  });

  it('answers with the option the client picked', async () => {
    const { answer, request } = await codexAsking();
    world.bridge.answerApproval(request.approvalId, 'allow_once');

    await expect(answer).resolves.toEqual({ outcome: 'selected', optionId: 'allow_once' });
    expect(world.bridge.listSessions()[0]).toMatchObject({
      status: 'working',
      pendingApprovals: [],
    });
    expect(world.ofType('approval_resolved').at(-1)?.data).toMatchObject({
      approvalId: request.approvalId,
      by: 'client',
    });
  });

  it('refuses an option the agent did not offer, or a withheld one', async () => {
    const { request } = await codexAsking();

    expect(() =>
      world.bridge.answerApproval(request.approvalId, 'accept_execpolicy_amendment'),
    ).toThrow(expect.objectContaining({ code: 'unknown_option' }));
    expect(() => world.bridge.answerApproval('nope', 'allow_once')).toThrow(
      expect.objectContaining({ code: 'unknown_approval' }),
    );
  });

  it('answers cancelled to the pending approvals when the turn is interrupted', async () => {
    const { sessionId, answer } = await codexAsking();
    await world.bridge.cancel(sessionId);

    await expect(answer).resolves.toEqual({ outcome: 'cancelled' });
    expect(world.bridge.pendingApprovals()).toEqual([]);
  });

  it('completes a Codex edit approval with the diff of the matching tool call', async () => {
    const { sessionId } = await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'ask',
      prompt: 'Édite',
    });
    const diff = { type: 'diff', path: '/p/todo.md', oldText: 'a', newText: 'b' };
    world.codex.send(sessionId, {
      sessionUpdate: 'tool_call',
      toolCallId: 'edit-1',
      kind: 'edit',
      title: 'Editing files',
      content: [diff],
    });
    void world.codex.ask(
      sessionId,
      { toolCallId: 'edit-1', title: 'Edit files', kind: 'edit', locations: [{ path: '/p' }] },
      [ALLOW_ONCE],
    );

    expect(world.bridge.pendingApprovals()[0]?.toolCall.content).toEqual([diff]);
  });
});

describe('agents', () => {
  it('keeps only the login state, never the account', async () => {
    await world.bridge.createSession({
      agent: 'claude',
      project: 'regie-demo',
      approvalMode: 'ask',
    });
    world.claude.reportAuth(true);

    expect(world.bridge.agentStates()).toContainEqual({
      agent: 'claude',
      running: true,
      authenticated: true,
    });
  });

  it('marks the sessions failed when the adapter process stops on its own, then restarts it', async () => {
    const { sessionId } = await world.bridge.createSession({
      agent: 'claude',
      project: 'regie-demo',
      approvalMode: 'ask',
      prompt: 'Go',
    });
    world.claude.crash();
    await flush();

    expect(world.bridge.listSessions()[0]).toMatchObject({
      status: 'failed',
      active: false,
      lastError: 'Claude s’est arrêté.',
    });

    await world.bridge.prompt(sessionId, 'Reprends');
    expect(world.claude.connections).toBe(2);
    expect(world.claude.callsOf('loadSession')).toEqual([
      [sessionId, '/home/dev/projects/regie-demo'],
    ]);
    expect(world.bridge.listSessions()[0]?.status).toBe('working');
  });

  it('exposes the versions of each agent', async () => {
    const versions = await world.bridge.versions();

    expect(versions.map((v) => [v.agent, v.running])).toEqual([
      ['claude', false],
      ['codex', false],
    ]);
  });
});

describe('restart and history', () => {
  it('goes on numbering after a restart, without gap or duplicate', async () => {
    await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'ask',
      prompt: 'Go',
    });
    const before = world.bridge.lastSeq;
    world.restart();

    const seqs = world.received().map((e) => e.seq);
    expect(seqs).toEqual(Array.from({ length: seqs.length }, (_, i) => i + 1));
    expect(world.bridge.lastSeq).toBeGreaterThan(before);
  });

  it('lists the sessions of the history as stopped after a restart', async () => {
    const { sessionId } = await world.bridge.createSession({
      agent: 'claude',
      project: 'regie-demo',
      approvalMode: 'ask',
      prompt: 'Go',
    });
    world.restart();

    expect(world.bridge.listSessions()).toEqual([
      expect.objectContaining({ sessionId, status: 'stopped', active: false, title: 'Go' }),
    ]);
  });

  it('resumes a session of the history with session/load and its mode again', async () => {
    const { sessionId } = await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'ask',
    });
    world.restart();
    world.codex.history = [
      { sessionUpdate: 'user_message_chunk', content: { type: 'text', text: 'ancien' } },
    ];
    const lastBefore = world.bridge.lastSeq;

    const resumed = await world.bridge.resume(sessionId);

    expect(resumed).toMatchObject({ status: 'idle', active: true });
    expect(world.codex.callsOf('setMode').at(-1)).toEqual([sessionId, 'workspace-write']);
    // The replayed history is already in the journal: it is not relayed twice.
    expect(world.received(lastBefore).filter((e) => e.type === 'session_update')).toEqual([]);
  });

  it('gives the events of one session', async () => {
    const { sessionId } = await world.bridge.createSession({
      agent: 'claude',
      project: 'regie-demo',
      approvalMode: 'ask',
      prompt: 'Go',
    });
    await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'ask',
    });

    const events = world.bridge.sessionEvents(sessionId);
    expect(events.length).toBeGreaterThan(0);
    expect(events.every((e) => e.sessionId === sessionId)).toBe(true);
  });
});

describe('a session goes live only once loaded and in its mode', () => {
  const methods = () => world.claude.calls.map((call) => call.method);

  it('keeps the order of two messages sent at once to a stopped session, after load and mode', async () => {
    const { sessionId } = await world.bridge.createSession({
      agent: 'claude',
      project: 'regie-demo',
      approvalMode: 'ask',
    });
    world.restart();
    world.claude.history = [
      { sessionUpdate: 'user_message_chunk', content: { type: 'text', text: 'ancien' } },
    ];
    world.claude.loadDelayMs = 30;
    world.claude.calls.length = 0;
    const lastBefore = world.bridge.lastSeq;

    const first = world.bridge.prompt(sessionId, 'premier');
    await new Promise((resolve) => setTimeout(resolve, 5));
    expect(world.bridge.listSessions()[0]?.active).toBe(false);
    const second = world.bridge.prompt(sessionId, 'second');

    await expect(first).resolves.toBe('prompt');
    await expect(second).resolves.toBe('steering');
    expect(methods()).toEqual(['loadSession', 'setMode', 'prompt', 'steer']);
    world.claude.send(sessionId, {
      sessionUpdate: 'agent_message_chunk',
      content: { type: 'text', text: 'réponse' },
    });
    const after = world.received(lastBefore);
    expect(
      after.filter((e) => e.type === 'user_message').map((e) => (e.data as { text: string }).text),
    ).toEqual(['premier', 'second']);
    // The replayed history is not relayed; what the agent says afterwards is.
    expect(after.filter((e) => e.type === 'session_update').map((e) => e.data)).toEqual([
      { sessionUpdate: 'agent_message_chunk', content: { type: 'text', text: 'réponse' } },
    ]);
  });

  it('sends nothing to a new session before its mode is forced, and relays what came meanwhile', async () => {
    world.claude.setModeDelayMs = 30;
    world.claude.onSetMode = [{ sessionUpdate: 'config_option_update', configOptions: [] }];

    const creating = world.bridge.createSession({
      agent: 'claude',
      project: 'regie-demo',
      approvalMode: 'ask',
      prompt: 'Go',
    });
    await new Promise((resolve) => setTimeout(resolve, 5));
    expect(world.bridge.listSessions()).toEqual([]);
    const { sessionId } = await creating;

    expect(methods()).toEqual(['newSession', 'setMode', 'prompt']);
    const types = world
      .received()
      .filter((e) => e.sessionId === sessionId)
      .map((e) => e.type);
    expect(types.slice(0, 3)).toEqual(['session', 'session_update', 'user_message']);
  });

  it('does not take a resumed session for live while it loads', async () => {
    const { sessionId } = await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'ask',
    });
    world.restart();
    world.codex.loadDelayMs = 30;

    const resuming = world.bridge.resume(sessionId);
    const prompted = world.bridge.prompt(sessionId, 'Go');
    await Promise.all([resuming, prompted]);

    const codexMethods = world.codex.calls.map((call) => call.method);
    expect(codexMethods.slice(-3)).toEqual(['loadSession', 'setMode', 'prompt']);
    expect(world.codex.callsOf('loadSession')).toHaveLength(1);
  });
});

describe('shutdown', () => {
  it('cancels the running turns, closes the agents and keeps the history', async () => {
    const { sessionId } = await world.bridge.createSession({
      agent: 'claude',
      project: 'regie-demo',
      approvalMode: 'ask',
      prompt: 'Go',
    });
    await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'ask',
    });

    await world.bridge.shutdown();

    expect(world.claude.callsOf('cancel')).toEqual([[sessionId]]);
    expect(world.claude.callsOf('close')).toHaveLength(1);
    expect(world.codex.callsOf('close')).toHaveLength(1);
    expect(world.bridge.listSessions().map((s) => [s.status, s.active])).toEqual([
      ['stopped', false],
      ['stopped', false],
    ]);
    expect(world.ofType('session_error')).toEqual([]);
    expect(world.ofType('bridge').map((e) => e.data)).toEqual([
      { state: 'started' },
      { state: 'stopping' },
      { state: 'stopped' },
    ]);
    await expect(
      world.bridge.createSession({ agent: 'claude', project: 'regie-demo', approvalMode: 'ask' }),
    ).rejects.toMatchObject({ code: 'shutting_down' });
  });
});
