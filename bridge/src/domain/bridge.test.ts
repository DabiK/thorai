import { beforeEach, describe, expect, it } from 'vitest';
import { FakeAgentConnector } from '../adapters/fake/fake-agent.ts';
import {
  counterIds,
  FakeClock,
  FakeWorkspace,
  MemoryEventStore,
  MemoryLogger,
  MemorySessionStore,
  WordsShellParser,
} from '../adapters/fake/memory.ts';
import { Bridge, branchInstruction } from './bridge.ts';
import type { BridgeEvent, SessionRecord } from './model.ts';
import { withSummaryRequest } from './written-summary.ts';

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
  readonly clock = new FakeClock();
  readonly logger = new MemoryLogger();
  codexSandbox: 'native' | 'proot' = 'proot';
  retentionDays: number | undefined = undefined;
  bridge = this.newBridge();

  newBridge(): Bridge {
    const bridge = new Bridge({
      connectors: [this.claude, this.codex],
      events: this.events,
      sessions: this.sessions,
      workspace: this.workspace,
      clock: this.clock,
      ids: counterIds('approval'),
      logger: this.logger,
      shell: new WordsShellParser(),
      codexSandbox: this.codexSandbox,
      ...(this.retentionDays === undefined ? {} : { retentionDays: this.retentionDays }),
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
  it.each(['', '   ', '../escape', '/tmp/new', 'a/b', 'x;pwd', '--', '😸', 'a'.repeat(81)])(
    'refuses invalid new name %s before any agent or folder',
    async (project) => {
      await expect(
        world.bridge.createSession({
          agent: 'codex',
          project,
          newProject: true,
          approvalMode: 'ask',
        }),
      ).rejects.toMatchObject({ code: 'bad_project_name' });
      expect(world.codex.callsOf('newSession')).toHaveLength(0);
      expect(await world.bridge.listProjects()).toEqual([{ name: 'regie-demo', branch: 'main' }]);
    },
  );

  it('creates a normalized new project on main and starts Codex inside it', async () => {
    const session = await world.bridge.createSession({
      agent: 'codex',
      project: ' Météo du Jour ',
      newProject: true,
      approvalMode: 'ask',
      prompt: 'Lis le dossier',
    });
    expect(session.project).toBe('meteo-du-jour');
    expect(session.branch).toBe('main');
    expect(world.codex.callsOf('newSession')).toEqual([['/home/dev/projects/meteo-du-jour']]);
    await expect(world.bridge.listProjects()).resolves.toContainEqual({
      name: 'meteo-du-jour',
      branch: 'main',
    });
    await expect(
      world.bridge.createSession({
        agent: 'codex',
        project: 'Météo du Jour',
        newProject: true,
        approvalMode: 'ask',
      }),
    ).rejects.toMatchObject({ code: 'project_exists' });
    expect(world.codex.callsOf('newSession')).toHaveLength(1);
  });

  it('applies the three product levels on creation and restores plan on resume', async () => {
    for (const agent of ['claude', 'codex'] as const) {
      for (const approvalMode of ['plan', 'auto-edit', 'ask'] as const) {
        await world.bridge.createSession({ agent, project: 'regie-demo', approvalMode });
      }
    }
    expect(world.claude.callsOf('setMode').map(([, mode]) => mode)).toEqual([
      'plan',
      'acceptEdits',
      'default',
    ]);
    expect(world.codex.callsOf('setMode').map(([, mode]) => mode)).toEqual([
      'workspace-write',
      'workspace-write',
      'workspace-write',
    ]);
    expect(world.codex.callsOf('setConfig')).toEqual([
      ['codex-session-1', 'collaboration_mode', 'plan'],
    ]);
    await world.bridge.shutdown();
    world.restart();
    await world.bridge.resume('codex-session-1');
    expect(world.codex.callsOf('setConfig')).toHaveLength(2);
  });

  it('keeps native sandbox writes behind approval on creation and resume in every product mode', async () => {
    world.codexSandbox = 'native';
    world.restart();
    for (const approvalMode of ['ask', 'auto-edit', 'plan', 'allow-all'] as const) {
      const session = await world.bridge.createSession({
        agent: 'codex',
        project: 'regie-demo',
        approvalMode,
      });
      const expected = approvalMode === 'allow-all' ? 'agent-full-access' : 'read-only';
      expect(world.codex.callsOf('setMode').at(-1)).toEqual([session.sessionId, expected]);
      await world.bridge.shutdown();
      world.restart();
      const beforeResume = world.codex.callsOf('setMode').length;
      await world.bridge.resume(session.sessionId);
      expect(world.codex.callsOf('setMode').slice(beforeResume)).toEqual([
        [session.sessionId, expected],
      ]);
    }
    expect(world.codex.callsOf('setConfig')).toEqual([
      ['codex-session-3', 'collaboration_mode', 'plan'],
      ['codex-session-3', 'collaboration_mode', 'plan'],
    ]);
  });

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
      approvalMode: 'ask',
      prompt: 'Lis notes.md',
    });

    expect(world.claude.callsOf('prompt')).toEqual([
      [session.sessionId, withSummaryRequest('Lis notes.md')],
    ]);
    expect(world.bridge.listSessions()[0]).toEqual({
      configOptions: [],
      sessionId: session.sessionId,
      agent: 'claude',
      project: 'regie-demo',
      cwd: '/home/dev/projects/regie-demo',
      branch: 'main',
      requestedBranch: null,
      approvalMode: 'ask',
      status: 'working',
      title: 'Lis notes.md',
      createdAt: expect.any(String),
      updatedAt: expect.any(String),
      lastError: null,
      modifiedFiles: [],
      purgedAt: null,
      active: true,
      pendingApprovals: [],
    });
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
  it('auto-edit accepts only file edits and safe reads, still asks for shell writes', async () => {
    const session = await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'auto-edit',
    });
    const edit = world.codex.ask(session.sessionId, { toolCallId: 'e', kind: 'edit' }, [
      ALLOW_ONCE,
      REJECT,
    ]);
    await flush();
    expect(world.bridge.pendingApprovals()).toHaveLength(0);
    expect(await edit).toEqual({ outcome: 'selected', optionId: 'allow_once' });
    const command = world.codex.ask(
      session.sessionId,
      { toolCallId: 'c', kind: 'execute', rawInput: { command: 'touch x' } },
      [ALLOW_ONCE, REJECT],
    );
    await flush();
    const pending = world.bridge.pendingApprovals()[0];
    expect(pending).toBeDefined();
    if (pending === undefined) throw new Error('missing approval');
    world.bridge.answerApproval(pending.approvalId, 'cancel');
    expect(await command).toEqual({ outcome: 'selected', optionId: 'cancel' });
  });

  async function codexAsking() {
    const { sessionId } = await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'ask',
      prompt: 'Lis notes.md',
    });
    const answer = world.codex.ask(
      sessionId,
      {
        toolCallId: 'exec-1',
        title: 'rm notes.md',
        kind: 'execute',
        rawInput: { command: 'rm notes.md', cwd: '/home/dev/projects/regie-demo' },
      },
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

  it('drops the messages still waiting when the session is interrupted while it loads', async () => {
    const { sessionId } = await world.bridge.createSession({
      agent: 'claude',
      project: 'regie-demo',
      approvalMode: 'ask',
    });
    world.restart();
    world.claude.loadDelayMs = 30;
    world.claude.calls.length = 0;

    const first = world.bridge.prompt(sessionId, 'premier');
    const second = world.bridge.prompt(sessionId, 'second');
    await world.bridge.cancel(sessionId);
    const after = world.bridge.prompt(sessionId, 'après');

    await expect(first).resolves.toBe('cancelled');
    await expect(second).resolves.toBe('cancelled');
    await expect(after).resolves.toBe('prompt');
    expect(world.claude.callsOf('prompt')).toEqual([[sessionId, withSummaryRequest('après')]]);
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

describe('read-only commands in « valider » (ticket 13)', () => {
  const CWD = '/home/dev/projects/regie-demo';

  async function session(agent: 'claude' | 'codex', approvalMode: 'ask' | 'allow-all' = 'ask') {
    const { sessionId } = await world.bridge.createSession({
      agent,
      project: 'regie-demo',
      approvalMode,
      prompt: 'Lis notes.md',
    });
    return sessionId;
  }

  it('approves a Codex read once, without asking the app, and keeps it in the feed', async () => {
    const sessionId = await session('codex');
    const command = 'cat notes.md todo.md';

    const answer = world.codex.ask(
      sessionId,
      { toolCallId: 'exec-1', title: command, kind: 'execute', rawInput: { command, cwd: CWD } },
      [ALLOW_ONCE, ALWAYS, REJECT],
    );
    world.codex.send(sessionId, {
      sessionUpdate: 'tool_call',
      toolCallId: 'exec-1',
      kind: 'execute',
      title: command,
      status: 'in_progress',
    });

    await expect(answer).resolves.toEqual({ outcome: 'selected', optionId: 'allow_once' });
    expect(world.ofType('approval_request')).toEqual([]);
    expect(world.bridge.listSessions()[0]).toMatchObject({
      status: 'working',
      pendingApprovals: [],
    });
    expect(world.ofType('session_update').map((e) => e.data)).toContainEqual(
      expect.objectContaining({ sessionUpdate: 'tool_call', toolCallId: 'exec-1', title: command }),
    );
    expect(world.logger.lines).toContainEqual(
      expect.stringContaining('read-only command approved by the bridge: cat notes.md todo.md'),
    );
  });

  it('logs the command it judged, not the title of the call', async () => {
    const sessionId = await session('codex');

    await world.codex.ask(
      sessionId,
      {
        toolCallId: 'exec-2',
        title: 'List files',
        kind: 'execute',
        rawInput: { command: 'ls -la', cwd: CWD },
      },
      [ALLOW_ONCE, REJECT],
    );

    expect(world.logger.lines.filter((l) => l.includes('approved by the bridge'))).toEqual([
      expect.stringMatching(/approved by the bridge: ls -la$/),
    ]);
  });

  it('approves a Claude Bash read once, with Claude’s own option', async () => {
    const sessionId = await session('claude');

    const answer = world.claude.ask(
      sessionId,
      {
        toolCallId: 'toolu_1',
        name: 'Bash',
        title: 'git status',
        kind: 'execute',
        rawInput: { command: 'git status', description: 'Show the status' },
      },
      [
        { optionId: 'allow-once', name: 'Yes', kind: 'allow_once' },
        { optionId: 'allow-with-updates', name: 'Yes, always', kind: 'allow_always' },
        { optionId: 'reject', name: 'No', kind: 'reject_once' },
      ],
    );

    await expect(answer).resolves.toEqual({ outcome: 'selected', optionId: 'allow-once' });
    expect(world.ofType('approval_request')).toEqual([]);
  });

  it('asks the app for a write, a chain with a write, or an edit', async () => {
    const sessionId = await session('codex');
    const asked = [
      { toolCallId: 'exec-1', kind: 'execute', rawInput: { command: 'rm notes.md', cwd: CWD } },
      {
        toolCallId: 'exec-2',
        kind: 'execute',
        rawInput: { command: 'cat notes.md && touch x', cwd: CWD },
      },
      { toolCallId: 'exec-3', kind: 'execute', rawInput: { command: 'cat a | tee b', cwd: CWD } },
      { toolCallId: 'edit-1', kind: 'edit', title: 'Edit files', locations: [{ path: CWD }] },
    ];
    for (const toolCall of asked) void world.codex.ask(sessionId, toolCall, [ALLOW_ONCE, REJECT]);

    expect(world.bridge.pendingApprovals().map((a) => a.toolCall.toolCallId)).toEqual([
      'exec-1',
      'exec-2',
      'exec-3',
      'edit-1',
    ]);
  });

  it('asks the app when the agent offers no « allow once »', async () => {
    const sessionId = await session('codex');

    void world.codex.ask(
      sessionId,
      { toolCallId: 'exec-1', kind: 'execute', rawInput: { command: 'cat a', cwd: CWD } },
      [ALWAYS, REJECT],
    );

    expect(world.bridge.pendingApprovals()).toHaveLength(1);
  });

  it('automatically answers any residual request in « tout autoriser »', async () => {
    const sessionId = await session('claude', 'allow-all');

    const answer = await world.claude.ask(
      sessionId,
      { toolCallId: 'toolu_1', kind: 'execute', rawInput: { command: 'cat a' } },
      [{ optionId: 'allow-once', name: 'Yes', kind: 'allow_once' }],
    );
    expect(answer).toEqual({ outcome: 'selected', optionId: 'allow-once' });

    expect(world.bridge.pendingApprovals()).toHaveLength(0);
  });
});

describe('written summaries (ticket 12)', () => {
  it('requests a report in the actual turn without changing the user message or starting another turn', async () => {
    const { sessionId } = await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'ask',
      prompt: 'Lis notes.md',
    });
    expect(world.codex.callsOf('prompt')).toEqual([
      [sessionId, withSummaryRequest('Lis notes.md')],
    ]);
    expect(world.ofType('user_message').at(-1)?.data).toEqual({
      text: 'Lis notes.md',
      delivery: 'prompt',
    });
    world.codex.endTurn(sessionId);
    await flush();
    expect(world.codex.callsOf('prompt')).toHaveLength(1);
  });
});

describe('branches, left to the agent (ticket 13)', () => {
  const CWD = '/home/dev/projects/regie-demo';
  const userMessages = () => world.ofType('user_message').map((e) => e.data);

  it('never checks the branch out: it puts it before the first instruction only', async () => {
    const { sessionId } = await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      branch: 'essai',
      approvalMode: 'ask',
      prompt: 'Lis notes.md',
    });
    world.codex.endTurn(sessionId);
    await flush();
    await world.bridge.prompt(sessionId, 'Continue');

    const prefix = branchInstruction('essai');
    expect(prefix).toBe(
      'Travaille sur la branche essai, crée-la depuis main si elle n’existe pas.',
    );
    expect(world.codex.callsOf('prompt')).toEqual([
      [sessionId, withSummaryRequest(`${prefix}\n\nLis notes.md`)],
      [sessionId, withSummaryRequest('Continue')],
    ]);
    expect(userMessages()).toEqual([
      { text: 'Lis notes.md', delivery: 'prompt', prefix },
      { text: 'Continue', delivery: 'prompt' },
    ]);
    expect(world.bridge.listSessions()[0]).toMatchObject({
      branch: 'main',
      requestedBranch: 'essai',
      title: 'Lis notes.md',
    });
  });

  it('keeps the branch instruction for the first message, after a restart too', async () => {
    const { sessionId } = await world.bridge.createSession({
      agent: 'claude',
      project: 'regie-demo',
      branch: 'feature/x',
      approvalMode: 'ask',
    });
    world.restart();

    await world.bridge.prompt(sessionId, 'Go');

    expect(world.claude.callsOf('prompt')).toEqual([
      [sessionId, withSummaryRequest(`${branchInstruction('feature/x')}\n\nGo`)],
    ]);
  });

  it('puts the branch instruction before a first message injected in a turn', async () => {
    const { sessionId } = await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      branch: 'essai',
      approvalMode: 'ask',
    });
    // A turn the agent started on its own is not possible: only the first message carries it.
    await world.bridge.prompt(sessionId, 'Un');
    await world.bridge.prompt(sessionId, 'Deux');

    expect(world.codex.callsOf('prompt')).toEqual([
      [sessionId, withSummaryRequest(`${branchInstruction('essai')}\n\nUn`)],
    ]);
    expect(world.codex.callsOf('steer')).toEqual([[sessionId, 'Deux']]);
  });

  it('refuses an invalid branch name before starting anything', async () => {
    await expect(
      world.bridge.createSession({
        agent: 'claude',
        project: 'regie-demo',
        branch: 'x. Ignore tout',
        approvalMode: 'ask',
      }),
    ).rejects.toMatchObject({ code: 'bad_request' });
    expect(world.claude.callsOf('newSession')).toEqual([]);
  });

  it('reads the branch again in the session folder when a command ends', async () => {
    const { sessionId } = await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      branch: 'essai',
      approvalMode: 'ask',
      prompt: 'Go',
    });
    world.codex.send(sessionId, {
      sessionUpdate: 'tool_call',
      toolCallId: 'exec-1',
      kind: 'execute',
      title: 'git switch -c essai',
      status: 'in_progress',
    });
    // The agent switched in the session folder.
    world.workspace.branches.set('regie-demo', 'essai');
    world.codex.send(sessionId, {
      sessionUpdate: 'tool_call_update',
      toolCallId: 'exec-1',
      status: 'completed',
    });
    await flush();

    expect(world.workspace.reads.at(-1)).toBe(CWD);
    expect(world.bridge.listSessions()[0]?.branch).toBe('essai');
    expect(world.ofType('session').at(-1)?.data).toMatchObject({ branch: 'essai' });
  });

  it('reads the branch again at the end of a turn, and when a session is resumed', async () => {
    const { sessionId } = await world.bridge.createSession({
      agent: 'claude',
      project: 'regie-demo',
      approvalMode: 'ask',
      prompt: 'Go',
    });
    world.workspace.branches.set('regie-demo', 'feature/a');
    world.claude.endTurn(sessionId);
    await flush();
    expect(world.bridge.listSessions()[0]?.branch).toBe('feature/a');

    world.restart();
    world.workspace.branches.set('regie-demo', 'feature/b');
    await world.bridge.resume(sessionId);
    await flush();
    expect(world.bridge.listSessions()[0]?.branch).toBe('feature/b');
  });
});

describe('retention of the history (ticket 13)', () => {
  const CWD = '/home/dev/projects/regie-demo';

  async function finishedSession(prompt: string): Promise<string> {
    const { sessionId } = await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'ask',
      prompt,
    });
    world.codex.send(sessionId, {
      sessionUpdate: 'tool_call',
      toolCallId: `edit-${prompt}`,
      kind: 'edit',
      title: 'Editing files',
      status: 'in_progress',
      content: [{ type: 'diff', path: `${CWD}/todo.md`, oldText: 'a', newText: 'b' }],
    });
    world.codex.send(sessionId, {
      sessionUpdate: 'tool_call_update',
      toolCallId: `edit-${prompt}`,
      status: 'completed',
    });
    world.codex.endTurn(sessionId);
    await flush();
    return sessionId;
  }

  it('keeps the files a session changed in its summary', async () => {
    await finishedSession('Édite');

    expect(world.bridge.listSessions()[0]?.modifiedFiles).toEqual(['todo.md']);
  });

  it('keeps every event for 30 days', async () => {
    const sessionId = await finishedSession('Édite');
    const events = world.bridge.sessionEvents(sessionId).length;
    world.clock.advanceDays(29);

    expect(world.bridge.purge()).toEqual({ sessions: 0, events: 0 });
    expect(world.bridge.sessionEvents(sessionId)).toHaveLength(events);
  });

  it('then purges the events of the session, from memory and from the store, and keeps its summary', async () => {
    const old = await finishedSession('Ancienne');
    const summary = world.bridge.listSessions()[0];
    world.clock.advanceDays(31);
    const recent = await finishedSession('Récente');
    const lastSeq = world.bridge.lastSeq;

    const report = world.bridge.purge();

    expect(report.sessions).toBe(1);
    expect(report.events).toBeGreaterThan(0);
    expect(world.bridge.sessionEvents(old)).toEqual([]);
    expect(world.events.events.some((e) => e.sessionId === old)).toBe(false);
    expect(world.bridge.sessionEvents(recent).length).toBeGreaterThan(0);
    expect(world.bridge.listSessions().find((s) => s.sessionId === old)).toEqual({
      ...summary,
      purgedAt: expect.any(String),
    });
    expect(world.bridge.listSessions().find((s) => s.sessionId === old)).toMatchObject({
      title: 'Ancienne',
      agent: 'codex',
      project: 'regie-demo',
      status: 'idle',
      modifiedFiles: ['todo.md'],
    });
    // Numbers never go back: the next event follows the last one, purged or not.
    expect(world.bridge.lastSeq).toBe(lastSeq);
    expect(world.bridge.purge()).toEqual({ sessions: 0, events: 0 });
  });

  it('purges the old events of no session, and keeps the numbering after a restart', async () => {
    await finishedSession('Ancienne');
    world.clock.advanceDays(31);
    const lastSeq = world.bridge.lastSeq;
    world.bridge.purge();

    world.restart();

    const seqs = world.received().map((e) => e.seq);
    expect(seqs[0]).toBe(lastSeq + 1);
    expect(world.received().every((e) => e.at > '2026-11-20')).toBe(true);
    // A client catching up from a purged number gets what is left, in order.
    expect(world.received(3).map((e) => e.seq)).toEqual(seqs);
  });

  it('never purges a session that is running', async () => {
    const { sessionId } = await world.bridge.createSession({
      agent: 'claude',
      project: 'regie-demo',
      approvalMode: 'ask',
      prompt: 'Long',
    });
    world.clock.advanceDays(40);

    expect(world.bridge.purge().sessions).toBe(0);
    expect(world.bridge.sessionEvents(sessionId).length).toBeGreaterThan(0);
  });

  it('follows the retention setting', async () => {
    world.retentionDays = 7;
    world.restart();
    const sessionId = await finishedSession('Courte');
    world.clock.advanceDays(8);

    expect(world.bridge.purge().sessions).toBe(1);
    expect(world.bridge.sessionEvents(sessionId)).toEqual([]);
  });

  it('can resume a purged session: the agent keeps its own history', async () => {
    const sessionId = await finishedSession('Ancienne');
    world.restart();
    world.clock.advanceDays(31);
    world.bridge.purge();

    await world.bridge.prompt(sessionId, 'Reprends');

    expect(world.codex.callsOf('loadSession').at(-1)).toEqual([sessionId, CWD]);
    expect(world.bridge.sessionEvents(sessionId).map((e) => e.type)).toContain('user_message');
  });

  it('finds the modified files of a session stored before ticket 13 in the journal', async () => {
    const sessionId = await finishedSession('Édite');
    const stored = world.sessions.readAll().map((record) => {
      const { modifiedFiles: _files, purgedAt: _purged, ...before } = record;
      return before as unknown as SessionRecord;
    });
    world.sessions.saveAll(stored);

    world.restart();

    expect(world.bridge.listSessions().find((s) => s.sessionId === sessionId)).toMatchObject({
      modifiedFiles: ['todo.md'],
      purgedAt: null,
      requestedBranch: null,
    });
  });
});

it('loads vocabulary from the session project and refuses an unknown session', async () => {
  const session = await world.bridge.createSession({
    agent: 'codex',
    project: 'regie-demo',
    approvalMode: 'ask',
  });
  world.workspace.terms.set('/home/dev/projects/regie-demo', [
    'PassageCheck.ts',
    'minPassage',
    'main',
  ]);
  await expect(world.bridge.sessionVocabulary(session.sessionId)).resolves.toEqual([
    'PassageCheck.ts',
    'minPassage',
    'main',
  ]);
  await expect(world.bridge.sessionVocabulary('missing')).rejects.toMatchObject({
    code: 'unknown_session',
  });
});

describe('session settings', () => {
  it.each(['ask', 'plan'] as const)(
    'suspends %s when both the mode change and rollback fail, and restores before another prompt',
    async (approvalMode) => {
      const session = await world.bridge.createSession({
        agent: 'codex',
        project: 'regie-demo',
        approvalMode,
        prompt: 'First turn',
      });
      const pending = world.codex.ask(session.sessionId, { kind: 'edit', title: 'Write' }, [
        ALLOW_ONCE,
        REJECT,
      ]);
      if (approvalMode === 'ask') world.codex.setModeError = new Error('mode refused');
      else world.codex.setConfigError = new Error('collaboration refused');
      await expect(
        world.bridge.configureSession(session.sessionId, { approvalMode: 'allow-all' }),
      ).rejects.toThrow('refused');
      expect(world.bridge.listSessions()[0]).toMatchObject({
        active: false,
        status: 'failed',
        approvalMode,
      });
      await expect(pending).resolves.toEqual({ outcome: 'cancelled' });
      expect(world.codex.runningTurns(session.sessionId)).toBe(0);
      await expect(world.bridge.prompt(session.sessionId, 'Must not run')).rejects.toThrow(
        'refused',
      );
      expect(world.codex.callsOf('prompt')).toHaveLength(1);
      world.codex.setModeError = null;
      world.codex.setConfigError = null;
      await expect(world.bridge.prompt(session.sessionId, 'Now restored')).resolves.toBe('prompt');
      expect(world.codex.callsOf('prompt')).toHaveLength(2);
      expect(world.bridge.listSessions()[0]).toMatchObject({ active: true, approvalMode });
    },
  );
  it('switches plan to full access, releases pending approvals and never asks again', async () => {
    const session = await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'plan',
    });
    const pending = world.codex.ask(session.sessionId, { kind: 'edit', title: 'Write' }, [
      ALLOW_ONCE,
      REJECT,
    ]);
    expect(world.bridge.pendingApprovals()).toHaveLength(1);
    await world.bridge.configureSession(session.sessionId, { approvalMode: 'allow-all' });
    expect(world.codex.callsOf('setMode').at(-1)).toEqual([session.sessionId, 'agent-full-access']);
    expect(world.codex.callsOf('setConfig').at(-1)).toEqual([
      session.sessionId,
      'collaboration_mode',
      'default',
    ]);
    await expect(pending).resolves.toEqual({ outcome: 'selected', optionId: 'allow_once' });
    await expect(
      world.codex.ask(session.sessionId, { kind: 'execute', title: 'Write again' }, [
        ALLOW_ONCE,
        REJECT,
      ]),
    ).resolves.toEqual({ outcome: 'selected', optionId: 'allow_once' });
    expect(world.bridge.pendingApprovals()).toEqual([]);
    expect(world.bridge.listSessions()[0]?.approvalMode).toBe('allow-all');
  });
  it('keeps the previous mode when ACP refuses the change', async () => {
    const session = await world.bridge.createSession({
      agent: 'claude',
      project: 'regie-demo',
      approvalMode: 'ask',
    });
    world.claude.setModeError = new Error('refused');
    await expect(
      world.bridge.configureSession(session.sessionId, { approvalMode: 'allow-all' }),
    ).rejects.toThrow('refused');
    expect(world.bridge.listSessions()[0]?.approvalMode).toBe('ask');
  });
});

describe('agent provided choices', () => {
  const choices = [
    {
      id: 'model',
      name: 'Model',
      type: 'select',
      category: 'model',
      currentValue: 'small',
      options: [
        { value: 'small', name: 'Small' },
        { value: 'large', name: 'Large' },
      ],
    },
    {
      id: 'effort',
      name: 'Effort',
      type: 'select',
      category: 'thought_level',
      currentValue: 'low',
      options: [
        {
          group: 'Thinking',
          options: [
            { value: 'low', name: 'Low' },
            { value: 'high', name: 'High' },
          ],
        },
      ],
    },
  ];
  it('discovers choices without a prompt, applies choices before the first turn, restores after restart', async () => {
    world.codex.initialOptions = choices;
    const options = await world.bridge.agentOptions('codex');
    expect(options.map((o) => o.id)).toEqual(['model', 'effort']);
    expect(options[1]?.options).toEqual([
      { value: 'low', name: 'Low' },
      { value: 'high', name: 'High' },
    ]);
    expect(world.bridge.listSessions()).toEqual([]);
    expect(world.codex.callsOf('prompt')).toEqual([]);
    const session = await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'allow-all',
      configValues: { model: 'large', effort: 'high' },
      prompt: 'Hello',
    });
    expect(session.configOptions?.map((o) => o.currentValue)).toEqual(['large', 'high']);
    const methods = world.codex.calls.map((c) => c.method);
    expect(methods.lastIndexOf('setConfig')).toBeLessThan(methods.indexOf('prompt'));
    world.codex.endTurn(session.sessionId);
    await world.bridge.shutdown();
    world.restart();
    const resumed = await world.bridge.resume(session.sessionId);
    expect(resumed.configOptions?.map((o) => o.currentValue)).toEqual(['large', 'high']);
  });
  it('rejects unadvertised values, replaces lists on update and reports the acknowledged value', async () => {
    world.codex.initialOptions = choices;
    const session = await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'ask',
    });
    await expect(
      world.bridge.configureSession(session.sessionId, { configId: 'model', value: 'invented' }),
    ).rejects.toMatchObject({ code: 'unknown_option' });
    expect(world.codex.callsOf('setConfig')).toEqual([]);
    await world.bridge.configureSession(session.sessionId, { configId: 'model', value: 'large' });
    expect(world.bridge.listSessions()[0]?.configOptions?.[0]?.currentValue).toBe('large');
    world.codex.send(session.sessionId, {
      sessionUpdate: 'config_option_update',
      configOptions: [choices[0]],
    });
    expect(world.bridge.listSessions()[0]?.configOptions).toHaveLength(1);
    await expect(
      world.bridge.configureSession(session.sessionId, { configId: 'effort', value: 'high' }),
    ).rejects.toMatchObject({ code: 'unknown_option' });
  });
});

describe('agent initiated mode changes', () => {
  it('shows native permission changes and stops using the previous automatic policy', async () => {
    const s = await world.bridge.createSession({
      agent: 'claude',
      project: 'regie-demo',
      approvalMode: 'allow-all',
    });
    world.claude.send(s.sessionId, {
      sessionUpdate: 'current_mode_update',
      currentModeId: 'default',
    });
    expect(world.bridge.listSessions()[0]?.approvalMode).toBe('ask');
    void world.claude.ask(s.sessionId, { kind: 'edit', title: 'write' }, [ALLOW_ONCE, REJECT]);
    expect(world.bridge.pendingApprovals()).toHaveLength(1);
  });
  it('tracks Codex collaboration mode independently of its native sandbox mode', async () => {
    const s = await world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'ask',
    });
    const config = (value: string) => ({
      sessionUpdate: 'config_option_update',
      configOptions: [
        {
          id: 'collaboration_mode',
          name: 'Mode',
          type: 'select',
          currentValue: value,
          options: [],
        },
      ],
    });
    world.codex.send(s.sessionId, config('plan'));
    expect(world.bridge.listSessions()[0]?.approvalMode).toBe('plan');
    world.codex.send(s.sessionId, config('default'));
    expect(world.bridge.listSessions()[0]?.approvalMode).toBe('ask');
  });
});

it('does not report an unacknowledged choice as applied or send a first prompt with obsolete effort', async () => {
  world.codex.initialOptions = [
    {
      id: 'model',
      name: 'Model',
      category: 'model',
      type: 'select',
      currentValue: 'one',
      options: [
        { value: 'one', name: 'One' },
        { value: 'two', name: 'Two' },
      ],
    },
    {
      id: 'effort',
      name: 'Effort',
      category: 'thought_level',
      type: 'select',
      currentValue: 'low',
      options: [
        { value: 'low', name: 'Low' },
        { value: 'high', name: 'High' },
      ],
    },
  ];
  world.codex.configResponses = new Map([['model:two', [world.codex.initialOptions[0] ?? {}]]]);
  const s = await world.bridge.createSession({
    agent: 'codex',
    project: 'regie-demo',
    approvalMode: 'ask',
  });
  await expect(
    world.bridge.configureSession(s.sessionId, { configId: 'model', value: 'two' }),
  ).rejects.toMatchObject({ code: 'agent_failed' });
  expect(world.bridge.listSessions()[0]?.configOptions?.[0]?.currentValue).toBe('one');
  world.codex.configResponses = new Map([
    ['model:two', [{ ...(world.codex.initialOptions[0] ?? {}), currentValue: 'two' }]],
  ]);
  await expect(
    world.bridge.createSession({
      agent: 'codex',
      project: 'regie-demo',
      approvalMode: 'allow-all',
      configValues: { model: 'two', effort: 'high' },
      prompt: 'Do work',
    }),
  ).rejects.toThrow('Option indisponible');
  expect(world.codex.callsOf('prompt')).toEqual([]);
});

it('restores the native permission mode when entering plan fails after set_mode', async () => {
  world.codexSandbox = 'native';
  world.restart();
  const s = await world.bridge.createSession({
    agent: 'codex',
    project: 'regie-demo',
    approvalMode: 'allow-all',
  });
  world.codex.setConfigError = new Error('plan refused');
  await expect(
    world.bridge.configureSession(s.sessionId, { approvalMode: 'plan' }),
  ).rejects.toThrow('plan refused');
  expect(world.codex.callsOf('setMode').map(([, mode]) => mode)).toEqual([
    'agent-full-access',
    'read-only',
    'agent-full-access',
  ]);
  expect(world.bridge.listSessions()[0]?.approvalMode).toBe('allow-all');
});
