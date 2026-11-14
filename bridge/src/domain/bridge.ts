import { AGENT_LABELS, type CodexSandbox, isTestedBinary, modeIdFor } from './agents.ts';
import { SessionCompensations } from './compensations.ts';
import { EventJournal, type EventListener } from './event-journal.ts';
import {
  type AcpUpdate,
  type AgentKind,
  type AgentVersions,
  type ApprovalMode,
  type ApprovalRequest,
  BridgeError,
  type BridgeEvent,
  type Delivery,
  type NewEvent,
  type PermissionOutcome,
  type PermissionRequest,
  type ProjectInfo,
  type SessionRecord,
  type SessionStatus,
  type SessionSummary,
} from './model.ts';
import { ModifiedFiles, withModifiedFiles } from './modified-files.ts';
import type {
  AgentConnection,
  AgentConnector,
  AgentHandlers,
  Clock,
  EventStore,
  IdGenerator,
  Logger,
  SessionStore,
  ShellParser,
  Workspace,
} from './ports.ts';
import { projectFolderName } from './project-name.ts';
import { ReadOnlyCommands } from './read-only-command.ts';
import { editableOptions, type SessionOption, sessionOptions } from './session-options.ts';
import { withSummaryRequest } from './written-summary.ts';

export interface BridgeDeps {
  /** Native sandbox blocks writes until approval; PRoot needs the Thor workaround. */
  readonly codexSandbox?: CodexSandbox;
  readonly connectors: readonly AgentConnector[];
  readonly events: EventStore;
  readonly sessions: SessionStore;
  readonly workspace: Workspace;
  readonly clock: Clock;
  readonly ids: IdGenerator;
  readonly logger: Logger;
  /** Splits the commands the agents ask to run, to approve the read-only ones. */
  readonly shell: ShellParser;
  /** How many days the events of a session are kept after its last change (30). */
  readonly retentionDays?: number;
  /** How long `shutdown` waits for the cancelled turns to end. */
  readonly shutdownGraceMs?: number;
}

/** What `purge` removed. */
export interface PurgeReport {
  readonly sessions: number;
  readonly events: number;
}

export const DEFAULT_RETENTION_DAYS = 30;
const DAY_MS = 24 * 60 * 60 * 1000;

export interface CreateSessionRequest {
  readonly agent: AgentKind;
  readonly project: string;
  readonly newProject?: boolean | undefined;
  readonly branch?: string | undefined;
  readonly approvalMode: ApprovalMode;
  readonly prompt?: string | undefined;
  readonly configValues?: Readonly<Record<string, string>> | undefined;
}

/** What the bridge knows of an agent's process, beyond its versions. */
export interface AgentState {
  readonly agent: AgentKind;
  readonly running: boolean;
  readonly authenticated: boolean | null;
}

/** A session loaded in a running agent process. */
interface LiveSession {
  readonly compensations: SessionCompensations;
  readonly files: ModifiedFiles;
  /** `execute` tool calls still running: the branch is read again when one ends. */
  readonly commands: Set<string>;
  /** `session/prompt` requests not answered yet: a message sent during a turn is queued. */
  inFlight: number;
}

interface PendingApproval {
  readonly request: ApprovalRequest;
  readonly resolve: (outcome: PermissionOutcome) => void;
}

interface AgentRuntime {
  readonly connector: AgentConnector;
  connection: Promise<AgentConnection> | null;
  authenticated: boolean | null;
}

/** Updates received for a session before `session/new` answered, kept at most this many. */
const EARLY_UPDATES_LIMIT = 200;
const TITLE_LENGTH = 80;

/**
 * The bridge: it starts, keeps and exposes the sessions of Claude Code and Codex.
 *
 * It relays what the agents send, numbered and stored by the journal, and applies the
 * compensations of docs/adr/02. Sessions go on whether a client is connected or not.
 */
export class Bridge {
  private readonly deps: BridgeDeps;
  private readonly journal: EventJournal;
  private readonly records = new Map<string, SessionRecord>();
  private readonly live = new Map<string, LiveSession>();
  private readonly loading = new Map<string, Promise<LiveSession>>();
  private readonly approvals = new Map<string, PendingApproval>();
  private readonly runtimes = new Map<AgentKind, AgentRuntime>();
  private readonly early = new Map<string, AcpUpdate[]>();
  /** Sessions whose `session/load` is replaying the history, already in the journal. */
  private readonly replaying = new Set<string>();
  /** The last message sent to each session: the next one waits for it, order is kept. */
  private readonly sending = new Map<string, Promise<unknown>>();
  /** Bumped by each interruption: a message queued before it is not sent. */
  private readonly interruptions = new Map<string, number>();
  private readonly turns = new Set<Promise<void>>();
  /** Bumped by each branch read: an older read that ends later is dropped. */
  private readonly branchReads = new Map<string, number>();
  private readonly readOnly: ReadOnlyCommands;
  private readonly options = new Map<string, readonly SessionOption[]>();
  private readonly probes = new Map<AgentKind, Promise<string>>();
  private readonly configuring = new Map<string, Promise<unknown>>();
  private stopping: Promise<void> | null = null;

  constructor(deps: BridgeDeps) {
    this.deps = deps;
    this.journal = new EventJournal(deps.events, deps.clock);
    this.readOnly = new ReadOnlyCommands(deps.shell);
    this.loadRecords(deps.sessions.readAll());
    for (const connector of deps.connectors) {
      this.runtimes.set(connector.agent, { connector, connection: null, authenticated: null });
    }
  }

  /**
   * Reads the stored sessions. Those stored before ticket 13 get the new fields; their modified
   * files are found in the journal.
   */
  private loadRecords(stored: readonly SessionRecord[]): void {
    const missing = new Map<string, ModifiedFiles>();
    const found = new Map<string, string[]>();
    for (const record of stored as readonly StoredRecord[]) {
      if (record.modifiedFiles === undefined) missing.set(record.sessionId, new ModifiedFiles());
    }
    if (missing.size > 0) {
      for (const event of this.journal.since(0)) {
        const files = event.sessionId === null ? undefined : missing.get(event.sessionId);
        if (files === undefined || event.type !== 'session_update') continue;
        const changed = files.observe(event.data as AcpUpdate);
        if (changed.length > 0 && event.sessionId !== null) {
          found.set(event.sessionId, [...(found.get(event.sessionId) ?? []), ...changed]);
        }
      }
    }
    for (const record of stored as readonly StoredRecord[]) {
      this.records.set(record.sessionId, {
        ...record,
        requestedBranch: record.requestedBranch ?? null,
        branchInstructionPending: record.branchInstructionPending ?? false,
        modifiedFiles:
          record.modifiedFiles ??
          withModifiedFiles([], found.get(record.sessionId) ?? [], record.cwd),
        purgedAt: record.purgedAt ?? null,
      });
    }
  }

  /**
   * Starts the bridge. Sessions that were loaded when it last stopped are no longer: they become
   * `stopped`, and can be resumed.
   */
  start(): void {
    for (const record of this.records.values()) {
      if (record.status !== 'stopped' && record.status !== 'failed') {
        // Not an activity of the session: `updatedAt` keeps its last one (retention).
        this.update(record.sessionId, { status: 'stopped' }, false);
      }
    }
    this.emit({ sessionId: null, type: 'bridge', data: { state: 'started' } });
  }

  get lastSeq(): number {
    return this.journal.lastSeq;
  }

  get journalId(): string {
    return this.journal.journalId;
  }

  /** Every event after `since`, then the live ones. Returns the unsubscribe function. */
  subscribe(since: number, listener: EventListener): () => void {
    return this.journal.subscribe(since, listener);
  }

  async sessionVocabulary(sessionId: string): Promise<readonly string[]> {
    const record = this.recordOf(sessionId);
    const project = await this.deps.workspace.resolveProject(record.project);
    return this.deps.workspace.vocabulary(project.cwd);
  }

  sessionEvents(sessionId: string, since = 0): readonly BridgeEvent[] {
    this.recordOf(sessionId);
    return this.journal.forSession(sessionId, since);
  }

  /** Current and past sessions, the most recently updated first. */
  listSessions(): readonly SessionSummary[] {
    return [...this.records.values()]
      .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
      .map((record) => this.summaryOf(record));
  }

  pendingApprovals(): readonly ApprovalRequest[] {
    return [...this.approvals.values()].map((pending) => pending.request);
  }

  agentStates(): readonly AgentState[] {
    return [...this.runtimes.values()].map((runtime) => this.stateOf(runtime));
  }

  async versions(): Promise<readonly AgentVersions[]> {
    return Promise.all(
      [...this.runtimes.values()].map(async (runtime) => {
        const versions = await runtime.connector.versions();
        const state = this.stateOf(runtime);
        return {
          ...versions,
          isTested:
            versions.adapter?.version === versions.tested.adapter &&
            isTestedBinary(runtime.connector.agent, versions.binary),
          running: state.running,
          authenticated: state.authenticated,
        };
      }),
    );
  }

  listProjects(): Promise<readonly ProjectInfo[]> {
    return this.deps.workspace.listProjects();
  }

  /**
   * Creates a session. The bridge never checks a branch out: a requested branch is given to the
   * agent with its first instruction, and the agent goes to it its own way (ticket 13).
   */
  async createSession(request: CreateSessionRequest): Promise<SessionSummary> {
    this.assertRunning();
    const { workspace } = this.deps;
    const projectName = request.newProject ? projectFolderName(request.project) : request.project;
    const requestedBranch = request.branch ? request.branch : null;
    if (requestedBranch !== null && !(await workspace.isValidBranchName(requestedBranch))) {
      throw new BridgeError('bad_request', `Nom de branche invalide : ${requestedBranch}.`);
    }
    const project = request.newProject
      ? await workspace.createProject(projectName, 'main')
      : await workspace.resolveProject(projectName);
    const branch = await workspace.currentBranch(project.cwd);
    const connection = await this.connection(request.agent);
    const sessionId = await this.agentCall(request.agent, () => connection.newSession(project.cwd));
    const now = this.now();
    let record: SessionRecord = {
      sessionId,
      agent: request.agent,
      project: project.name,
      cwd: project.cwd,
      branch,
      requestedBranch,
      branchInstructionPending: requestedBranch !== null,
      approvalMode: request.approvalMode,
      status: 'idle',
      title: null,
      createdAt: now,
      updatedAt: now,
      lastError: null,
      modifiedFiles: [],
      purgedAt: null,
    };
    // The session is known, live and usable only once its mode is forced: nothing reaches the
    // agent before (Claude would run in the user's `auto`).
    let failure = await this.applyMode(record, connection);
    if (failure === null) {
      try {
        await this.applySelections(sessionId, request.configValues ?? {}, connection);
      } catch (error) {
        failure = new BridgeError('agent_failed', messageOf(error));
      }
    }
    record = { ...record, configOptions: this.options.get(sessionId) ?? [] };
    if (failure !== null) record = { ...record, status: 'failed', lastError: failure.message };
    this.records.set(sessionId, record);
    if (failure === null) this.goLive(sessionId);
    this.save();
    this.emitSession(sessionId);
    if (failure !== null) {
      this.early.delete(sessionId);
      throw failure;
    }
    this.flushEarly(sessionId);
    if (request.prompt) this.startTurn(sessionId, connection, request.prompt, 'prompt');
    return this.summaryOf(this.recordOf(sessionId));
  }

  /**
   * Loads a session of the history into its agent, so that it can go on. Allowed for a purged
   * session too: the agent keeps its own history.
   */
  async resume(sessionId: string): Promise<SessionSummary> {
    this.assertRunning();
    await this.ensureLive(sessionId);
    return this.summaryOf(this.recordOf(sessionId));
  }

  /** One no-prompt discovery session per agent; closed with the adapter, never in Régie's history. */
  async agentOptions(
    agent: AgentKind,
    values: Readonly<Record<string, string>> = {},
  ): Promise<readonly SessionOption[]> {
    this.assertRunning();
    let probe = this.probes.get(agent);
    if (probe === undefined) {
      probe = (async () => {
        const cwd = await this.deps.workspace.configurationDirectory();
        const connection = await this.connection(agent);
        const id = await this.agentCall(agent, () => connection.newSession(cwd));
        this.early.delete(id);
        return id;
      })();
      this.probes.set(agent, probe);
      void probe.catch(() => this.probes.delete(agent));
    }
    const id = await probe;
    return this.serialSettings(id, async () => {
      await this.applySelections(id, values, await this.connection(agent));
      this.early.delete(id);
      return editableOptions(this.options.get(id) ?? []);
    });
  }

  private serialSettings<T>(id: string, action: () => Promise<T>): Promise<T> {
    const previous = this.configuring.get(id) ?? Promise.resolve();
    const next = previous.catch(() => {}).then(action);
    this.configuring.set(id, next);
    void next
      .finally(() => {
        if (this.configuring.get(id) === next) this.configuring.delete(id);
      })
      .catch(() => {});
    return next;
  }

  private async applySelections(
    id: string,
    values: Readonly<Record<string, string>>,
    connection: AgentConnection,
  ): Promise<void> {
    // Models may replace the effort choices; validate each against the latest acknowledgement.
    const entries = Object.entries(values).sort(([a], [b]) => {
      const options = this.options.get(id) ?? [];
      return (
        Number(options.find((o) => o.id === b)?.category === 'model') -
        Number(options.find((o) => o.id === a)?.category === 'model')
      );
    });
    for (const [configId, value] of entries) {
      const option = editableOptions(this.options.get(id) ?? []).find((o) => o.id === configId);
      if (!option?.options.some((o) => o.value === value))
        throw new BridgeError('unknown_option', `Option indisponible : ${configId} / ${value}.`);
      if (option.currentValue !== value) {
        await connection.setConfig(id, configId, value);
        if (this.options.get(id)?.find((o) => o.id === configId)?.currentValue !== value) {
          throw new BridgeError('agent_failed', `L’agent n’a pas confirmé ${configId} / ${value}.`);
        }
      }
    }
  }

  /** Changes acknowledged by ACP, serialized per session. No optimistic value is published. */
  configureSession(
    sessionId: string,
    settings: {
      approvalMode?: ApprovalMode | undefined;
      configId?: string | undefined;
      value?: string | undefined;
      configValues?: Readonly<Record<string, string>> | undefined;
    },
  ): Promise<SessionSummary> {
    return this.serialSettings(sessionId, async () => {
      this.assertRunning();
      const selections =
        settings.configValues ??
        (settings.configId !== undefined && settings.value !== undefined
          ? { [settings.configId]: settings.value }
          : undefined);
      if (
        settings.approvalMode === undefined &&
        selections !== undefined &&
        !this.live.has(sessionId)
      ) {
        // A concurrent resume may still be restoring the old choices. Let it settle
        // before retrying with the explicit replacement, never send a prompt here.
        await this.loading.get(sessionId)?.catch(() => {});
        if (!this.live.has(sessionId)) {
          await this.ensureLive(sessionId, selections);
          return this.summaryOf(this.recordOf(sessionId));
        }
      }
      await this.ensureLive(sessionId);
      const record = this.recordOf(sessionId);
      const connection = await this.connection(record.agent);
      if (settings.approvalMode !== undefined) {
        const failure = await this.applyMode(
          { ...record, approvalMode: settings.approvalMode },
          connection,
        );
        if (failure !== null) {
          await this.restoreMode(record, connection, failure);
          throw failure;
        }
        if (
          record.agent === 'codex' &&
          record.approvalMode === 'plan' &&
          settings.approvalMode !== 'plan'
        ) {
          try {
            await connection.setConfig(sessionId, 'collaboration_mode', 'default');
          } catch (error) {
            await this.restoreMode(record, connection, error);
            throw error;
          }
        }
        this.update(sessionId, { approvalMode: settings.approvalMode });
        for (const pending of [...this.approvals.values()]) {
          if (pending.request.sessionId !== sessionId) continue;
          const outcome = this.automaticApproval(pending.request);
          if (outcome !== null) this.resolveApproval(pending, outcome, 'mode_change');
        }
      } else if (selections !== undefined) {
        await this.applySelections(sessionId, selections, connection);
      } else throw new BridgeError('bad_request', 'Choisis un réglage.');
      return this.summaryOf(this.recordOf(sessionId));
    });
  }

  private async restoreMode(
    record: SessionRecord,
    connection: AgentConnection,
    failure: unknown,
  ): Promise<void> {
    if ((await this.applyMode(record, connection)) === null) return;
    // No further prompt may use an uncertain policy. A later load must force it again.
    await this.cancel(record.sessionId).catch(() => {});
    this.cancelApprovals(record.sessionId, 'mode_failed');
    this.goOffline(
      record.sessionId,
      'failed',
      `Changement de mode incertain : ${messageOf(failure)}`,
    );
  }

  /**
   * Sends a message. During a turn, it is injected in the turn when the agent accepts it
   * (`_session/steering`), otherwise it waits for its turn.
   */
  prompt(sessionId: string, text: string): Promise<Delivery> {
    this.assertRunning();
    this.recordOf(sessionId);
    const previous = this.sending.get(sessionId) ?? Promise.resolve();
    const interruption = this.interruptions.get(sessionId) ?? 0;
    const next = previous.then(
      () => this.send(sessionId, text, interruption),
      () => this.send(sessionId, text, interruption),
    );
    this.sending.set(sessionId, next);
    const forget = () => {
      if (this.sending.get(sessionId) === next) this.sending.delete(sessionId);
    };
    next.then(forget, forget);
    return next;
  }

  private async send(sessionId: string, text: string, interruption: number): Promise<Delivery> {
    const interrupted = () => (this.interruptions.get(sessionId) ?? 0) !== interruption;
    if (interrupted()) return 'cancelled';
    this.assertRunning();
    await this.configuring.get(sessionId);
    const record = this.recordOf(sessionId);
    const live = await this.ensureLive(sessionId);
    const connection = await this.connection(record.agent);
    if (interrupted()) return 'cancelled';
    if (live.inFlight > 0) {
      const prefix = this.branchPrefixOf(sessionId);
      const sent = prefix === null ? text : `${prefix}\n\n${text}`;
      const injected = await connection.steer(sessionId, sent).catch(() => false);
      if (injected) {
        this.branchInstructed(sessionId);
        this.emit({ sessionId, type: 'user_message', data: messageData(text, 'steering', prefix) });
        return 'steering';
      }
    }
    const delivery: Delivery = live.inFlight > 0 ? 'queued' : 'prompt';
    this.startTurn(sessionId, connection, text, delivery);
    return delivery;
  }

  /**
   * Interrupts the session: the running turn, and the messages still waiting to be sent (while
   * the session loads, for instance). The session stays usable.
   */
  async cancel(sessionId: string): Promise<void> {
    const record = this.recordOf(sessionId);
    this.interruptions.set(sessionId, (this.interruptions.get(sessionId) ?? 0) + 1);
    if (!this.live.has(sessionId)) return;
    const connection = await this.connection(record.agent);
    await connection.cancel(sessionId);
    this.cancelApprovals(sessionId, 'cancel');
  }

  answerApproval(approvalId: string, optionId: string): void {
    const pending = this.approvals.get(approvalId);
    if (pending === undefined) {
      throw new BridgeError('unknown_approval', 'Cette validation n’attend plus de réponse.');
    }
    if (!pending.request.options.some((option) => option.optionId === optionId)) {
      throw new BridgeError(
        'unknown_option',
        `Option inconnue pour cette validation : ${optionId}.`,
      );
    }
    this.resolveApproval(pending, { outcome: 'selected', optionId }, 'client');
  }

  /**
   * Stops everything: running turns are cancelled, then the agent processes are closed. The
   * sessions stay in the history.
   */
  shutdown(): Promise<void> {
    this.stopping ??= this.stopAll();
    return this.stopping;
  }

  private async stopAll(): Promise<void> {
    this.emit({ sessionId: null, type: 'bridge', data: { state: 'stopping' } });
    for (const [sessionId, live] of this.live) {
      if (live.inFlight > 0) await this.cancel(sessionId).catch(() => {});
      this.cancelApprovals(sessionId, 'shutdown');
    }
    await Promise.race([
      Promise.allSettled([...this.turns]),
      new Promise((resolve) => setTimeout(resolve, this.deps.shutdownGraceMs ?? 3000)),
    ]);
    await Promise.allSettled(
      [...this.runtimes.values()].map(async (runtime) => {
        const connection = await runtime.connection?.catch(() => null);
        await connection?.close();
      }),
    );
    for (const sessionId of [...this.live.keys()]) this.goOffline(sessionId, 'stopped', null);
    this.emit({ sessionId: null, type: 'bridge', data: { state: 'stopped' } });
  }

  private startTurn(
    sessionId: string,
    connection: AgentConnection,
    text: string,
    delivery: Delivery,
  ): void {
    const live = this.liveOf(sessionId);
    const prefix = this.branchPrefixOf(sessionId);
    this.branchInstructed(sessionId);
    this.emit({ sessionId, type: 'user_message', data: messageData(text, delivery, prefix) });
    live.inFlight += 1;
    const record = this.recordOf(sessionId);
    this.update(sessionId, {
      title: record.title ?? titleFrom(text),
      status: this.statusOf(sessionId),
      lastError: null,
    });
    const sent = prefix === null ? text : `${prefix}\n\n${text}`;
    const turn = connection.prompt(sessionId, withSummaryRequest(sent)).then(
      (result) => {
        if (this.live.get(sessionId) !== live) return;
        live.inFlight -= 1;
        this.emit({ sessionId, type: 'turn_end', data: result });
        this.update(sessionId, { status: this.statusOf(sessionId) });
        this.readBranch(sessionId);
      },
      (error: unknown) => {
        if (this.live.get(sessionId) !== live) return;
        live.inFlight -= 1;
        // The agent process is being closed: the session is stopped, not failed.
        if (this.stopping !== null) return;
        const message = `${AGENT_LABELS[record.agent]} : ${messageOf(error)}`;
        this.emit({ sessionId, type: 'session_error', data: { message } });
        this.update(sessionId, { status: 'failed', lastError: message });
        this.readBranch(sessionId);
      },
    );
    this.turns.add(turn);
    void turn.finally(() => this.turns.delete(turn));
  }

  private ensureLive(
    sessionId: string,
    replacements: Readonly<Record<string, string>> = {},
  ): Promise<LiveSession> {
    const live = this.live.get(sessionId);
    if (live !== undefined) return Promise.resolve(live);
    let loading = this.loading.get(sessionId);
    if (loading === undefined) {
      loading = this.load(sessionId, replacements).finally(() => this.loading.delete(sessionId));
      this.loading.set(sessionId, loading);
    }
    return loading;
  }

  /**
   * Loads a session into its agent, then forces its mode. It goes live only then: until both
   * are done, no message reaches the agent and the session is not `active`.
   */
  private async load(
    sessionId: string,
    replacements: Readonly<Record<string, string>>,
  ): Promise<LiveSession> {
    const record = this.recordOf(sessionId);
    const connection = await this.connection(record.agent);
    this.early.delete(sessionId);
    this.replaying.add(sessionId);
    try {
      await this.agentCall(record.agent, () => connection.loadSession(sessionId, record.cwd));
    } catch (error) {
      this.update(sessionId, { status: 'failed', lastError: messageOf(error) });
      throw error;
    } finally {
      this.replaying.delete(sessionId);
    }
    let failure = await this.applyMode(record, connection);
    if (failure === null) {
      try {
        await this.applySelections(
          sessionId,
          {
            ...Object.fromEntries(
              editableOptions(record.configOptions ?? []).flatMap((o) =>
                o.currentValue === null ? [] : [[o.id, o.currentValue]],
              ),
            ),
            ...replacements,
          },
          connection,
        );
      } catch (error) {
        failure = new BridgeError('agent_failed', messageOf(error));
      }
    }
    if (failure !== null || this.stopping !== null) {
      this.early.delete(sessionId);
      if (failure === null) throw new BridgeError('shutting_down', 'Le pont s’arrête.');
      this.update(sessionId, { status: 'failed', lastError: failure.message });
      throw failure;
    }
    const live = this.goLive(sessionId);
    this.update(sessionId, {
      status: 'idle',
      lastError: null,
      configOptions: this.options.get(sessionId) ?? [],
    });
    this.flushEarly(sessionId);
    // The agent may have changed the branch since the session was last live.
    this.readBranch(sessionId);
    return live;
  }

  /**
   * Forces the session mode: Claude starts in the user's `auto`, Codex in its process mode.
   * Returns the error when the agent refuses it.
   */
  private async applyMode(
    record: SessionRecord,
    connection: AgentConnection,
  ): Promise<BridgeError | null> {
    const modeId = modeIdFor(record.agent, record.approvalMode, this.deps.codexSandbox);
    try {
      await connection.setMode(record.sessionId, modeId);
      if (record.agent === 'codex' && record.approvalMode === 'plan') {
        await connection.setConfig(record.sessionId, 'collaboration_mode', 'plan');
      }
      return null;
    } catch (error) {
      const message = `Mode ${modeId} refusé par ${AGENT_LABELS[record.agent]} : ${messageOf(error)}`;
      return new BridgeError('agent_failed', message);
    }
  }

  private connection(agent: AgentKind): Promise<AgentConnection> {
    const runtime = this.runtimes.get(agent);
    if (runtime === undefined) {
      throw new BridgeError('bad_request', `Agent non disponible : ${agent}.`);
    }
    runtime.connection ??= runtime.connector.connect(this.handlersFor(runtime)).then(
      (connection) => {
        void connection.exited.then((exit) => this.onAgentExit(runtime, exit));
        this.emitAgent(runtime);
        return connection;
      },
      (error: unknown) => {
        runtime.connection = null;
        throw new BridgeError(
          'agent_failed',
          `${AGENT_LABELS[agent]} n’a pas démarré : ${messageOf(error)}`,
        );
      },
    );
    return runtime.connection;
  }

  private handlersFor(runtime: AgentRuntime): AgentHandlers {
    return {
      update: (sessionId, update) => this.onUpdate(sessionId, update),
      permission: (request) => this.onPermission(request),
      authenticated: (authenticated) => {
        if (runtime.authenticated === authenticated) return;
        runtime.authenticated = authenticated;
        this.emitAgent(runtime);
      },
    };
  }

  private onAgentExit(
    runtime: AgentRuntime,
    exit: { readonly code: number | null; readonly signal: string | null },
  ): void {
    runtime.connection = null;
    this.probes.delete(runtime.connector.agent);
    const agent = runtime.connector.agent;
    const expected = this.stopping !== null;
    if (!expected) {
      this.deps.logger.warn(`${agent} adapter exited: code=${exit.code} signal=${exit.signal}`);
    }
    this.emitAgent(runtime, exit);
    const message = expected ? null : `${AGENT_LABELS[agent]} s’est arrêté.`;
    for (const sessionId of [...this.live.keys()]) {
      if (this.records.get(sessionId)?.agent !== agent) continue;
      this.cancelApprovals(sessionId, 'agent_exit');
      this.goOffline(sessionId, expected ? 'stopped' : 'failed', message);
    }
  }

  private onUpdate(sessionId: string, update: AcpUpdate): void {
    if (update.sessionUpdate === 'config_option_update') {
      const options = sessionOptions(update.configOptions);
      this.options.set(sessionId, options);
      const record = this.records.get(sessionId);
      // Offline updates are provisional: a failed load must keep every saved selection
      // for the next attempt, including after a bridge restart.
      if (
        this.live.has(sessionId) &&
        record &&
        JSON.stringify(record.configOptions) !== JSON.stringify(options)
      )
        this.update(sessionId, { configOptions: options });
      else if (record && !this.live.has(sessionId)) this.emitSession(sessionId);
    }
    const live = this.live.get(sessionId);
    if (live !== undefined && !this.configuring.has(sessionId)) {
      const record = this.recordOf(sessionId);
      const options = this.options.get(sessionId) ?? [];
      const nativeMode =
        update.sessionUpdate === 'current_mode_update'
          ? update.currentModeId
          : update.sessionUpdate === 'config_option_update'
            ? options.find((o) => o.category === 'mode' || o.id === 'mode')?.currentValue
            : undefined;
      let approvalMode = record.approvalMode;
      if (
        typeof nativeMode === 'string' &&
        nativeMode !== modeIdFor(record.agent, approvalMode, this.deps.codexSandbox)
      ) {
        const match = (['ask', 'auto-edit', 'allow-all', 'plan'] as const).find(
          (mode) => modeIdFor(record.agent, mode, this.deps.codexSandbox) === nativeMode,
        );
        if (match !== undefined) approvalMode = match;
      }
      if (record.agent === 'codex' && update.sessionUpdate === 'config_option_update') {
        const collaboration = options.find((o) => o.id === 'collaboration_mode')?.currentValue;
        if (collaboration === 'plan') approvalMode = 'plan';
        else if (collaboration === 'default' && approvalMode === 'plan') approvalMode = 'ask';
      }
      if (approvalMode !== record.approvalMode) this.update(sessionId, { approvalMode });
    }
    if (live === undefined) {
      // The replayed history is already in the journal. Anything else waits for the session
      // to go live.
      if (this.replaying.has(sessionId)) return;
      const early = this.early.get(sessionId) ?? [];
      if (early.length < EARLY_UPDATES_LIMIT) early.push(update);
      this.early.set(sessionId, early);
      return;
    }
    const relayed = live.compensations.update(update);
    this.emit({ sessionId, type: 'session_update', data: relayed });
    if (update.sessionUpdate === 'session_info_update' && typeof update.title === 'string') {
      this.update(sessionId, { title: update.title });
    }
    const changed = live.files.observe(relayed);
    if (changed.length > 0) {
      const record = this.recordOf(sessionId);
      const modifiedFiles = withModifiedFiles(record.modifiedFiles, changed, record.cwd);
      if (modifiedFiles.length > record.modifiedFiles.length)
        this.update(sessionId, { modifiedFiles });
    }
    if (this.commandEnded(live, update)) this.readBranch(sessionId);
  }

  /** Whether the update ends an `execute` tool call: it may have changed the branch. */
  private commandEnded(live: LiveSession, update: AcpUpdate): boolean {
    const id = update.toolCallId;
    if (typeof id !== 'string') return false;
    if (update.sessionUpdate === 'tool_call' && update.kind === 'execute') live.commands.add(id);
    const ended = update.status === 'completed' || update.status === 'failed';
    if (!ended || !(live.commands.has(id) || update.kind === 'execute')) return false;
    live.commands.delete(id);
    return true;
  }

  private flushEarly(sessionId: string): void {
    const early = this.early.get(sessionId);
    this.early.delete(sessionId);
    for (const update of early ?? []) this.onUpdate(sessionId, update);
  }

  private onPermission(request: PermissionRequest): Promise<PermissionOutcome> {
    const live = this.live.get(request.sessionId);
    if (live === undefined || this.stopping !== null) {
      return Promise.resolve({ outcome: 'cancelled' });
    }
    const automatic = this.automaticApproval(request);
    if (automatic !== null) return Promise.resolve(automatic);
    const normalized = live.compensations.approval(request);
    const approval: ApprovalRequest = {
      approvalId: this.deps.ids(),
      sessionId: request.sessionId,
      ...normalized,
    };
    return new Promise((resolve) => {
      this.approvals.set(approval.approvalId, { request: approval, resolve });
      this.emit({ sessionId: request.sessionId, type: 'approval_request', data: approval });
      this.update(request.sessionId, { status: this.statusOf(request.sessionId) });
    });
  }

  /**
   * Safe shell reads are automatic (ticket 13). In auto-edit, structured file edits are
   * automatic too; shell writes still ask. Only a single-use choice is ever selected.
   */
  private automaticApproval(request: PermissionRequest): PermissionOutcome | null {
    const record = this.records.get(request.sessionId);
    if (record === undefined) return null;
    if (record.approvalMode === 'allow-all') {
      const once = request.options.find((option) => option.kind === 'allow_once');
      return once ? { outcome: 'selected', optionId: once.optionId } : { outcome: 'cancelled' };
    }
    // « Autoriser une fois », never « toujours » : the next command is judged again.
    const allow = request.options.find((option) => option.kind === 'allow_once');
    if (allow === undefined) return null;
    if (record.approvalMode === 'auto-edit' && request.toolCall.kind === 'edit') {
      return { outcome: 'selected', optionId: allow.optionId };
    }
    const command = this.readOnly.readOnlyCommandOf(request.toolCall);
    if (command === null) return null;
    this.deps.logger.info(
      `${record.agent} ${request.sessionId}: read-only command approved by the bridge: ${clip(command)}`,
    );
    return { outcome: 'selected', optionId: allow.optionId };
  }

  private cancelApprovals(sessionId: string, by: string): void {
    for (const pending of [...this.approvals.values()]) {
      if (pending.request.sessionId === sessionId) {
        this.resolveApproval(pending, { outcome: 'cancelled' }, by);
      }
    }
  }

  private resolveApproval(pending: PendingApproval, outcome: PermissionOutcome, by: string): void {
    const { approvalId, sessionId } = pending.request;
    if (!this.approvals.delete(approvalId)) return;
    pending.resolve(outcome);
    this.emit({ sessionId, type: 'approval_resolved', data: { approvalId, outcome, by } });
    if (this.live.has(sessionId)) this.update(sessionId, { status: this.statusOf(sessionId) });
  }

  private goLive(sessionId: string): LiveSession {
    const live: LiveSession = {
      compensations: new SessionCompensations(),
      files: new ModifiedFiles(),
      commands: new Set(),
      inFlight: 0,
    };
    this.live.set(sessionId, live);
    return live;
  }

  private goOffline(sessionId: string, status: SessionStatus, lastError: string | null): void {
    this.live.delete(sessionId);
    // Stopped with the bridge: not an activity of the session, `updatedAt` stays (retention).
    this.update(sessionId, { status, lastError }, status !== 'stopped');
  }

  /** The status of a live session, from its turns and its approvals. */
  private statusOf(sessionId: string): SessionStatus {
    if (this.pendingOf(sessionId).length > 0) return 'awaiting_approval';
    return (this.live.get(sessionId)?.inFlight ?? 0) > 0 ? 'working' : 'idle';
  }

  private pendingOf(sessionId: string): string[] {
    return [...this.approvals.values()]
      .filter((pending) => pending.request.sessionId === sessionId)
      .map((pending) => pending.request.approvalId);
  }

  /**
   * The branch prefix of the next instruction, when the session was created with a branch and
   * nothing was sent to its agent yet.
   */
  private branchPrefixOf(sessionId: string): string | null {
    const record = this.recordOf(sessionId);
    if (!record.branchInstructionPending || record.requestedBranch === null) return null;
    return branchInstruction(record.requestedBranch);
  }

  private branchInstructed(sessionId: string): void {
    const record = this.recordOf(sessionId);
    if (!record.branchInstructionPending) return;
    this.records.set(sessionId, { ...record, branchInstructionPending: false });
    this.save();
  }

  /** Reads the branch again in the session folder: the agent may have changed it. */
  private readBranch(sessionId: string): void {
    const ticket = (this.branchReads.get(sessionId) ?? 0) + 1;
    this.branchReads.set(sessionId, ticket);
    const { cwd } = this.recordOf(sessionId);
    this.deps.workspace.currentBranch(cwd).then(
      (branch) => {
        if (this.branchReads.get(sessionId) !== ticket || this.stopping !== null) return;
        this.update(sessionId, { branch });
      },
      () => {},
    );
  }

  /**
   * Purges the events of the sessions left alone for longer than the retention (30 days by
   * default, from their last activity): their summary stays listed, with `purgedAt`. A session
   * that is loading, running or waiting for an approval is never purged; an idle one still
   * loaded is, its live state is not in the journal. Events of no session (bridge, agents)
   * older than the retention go too. Run at start, then once a day.
   */
  purge(): PurgeReport {
    if (this.stopping !== null) return { sessions: 0, events: 0 };
    const now = this.deps.clock.now();
    const days = this.deps.retentionDays ?? DEFAULT_RETENTION_DAYS;
    const cutoff = new Date(now.getTime() - days * DAY_MS).toISOString();
    const expired = new Set<string>();
    for (const record of this.records.values()) {
      const busy =
        this.loading.has(record.sessionId) ||
        record.status === 'working' ||
        record.status === 'awaiting_approval';
      if (!busy && record.updatedAt < cutoff) expired.add(record.sessionId);
    }
    const removed = this.journal.remove((event) =>
      event.sessionId !== null && this.records.has(event.sessionId)
        ? expired.has(event.sessionId)
        : event.at < cutoff,
    );
    const purgedAt = now.toISOString();
    const emptied = new Set(removed.map((event) => event.sessionId));
    let sessions = 0;
    for (const sessionId of expired) {
      const record = this.recordOf(sessionId);
      if (record.purgedAt !== null && !emptied.has(sessionId)) continue;
      // Not a change of the session: no event, `updatedAt` stays its last activity.
      this.records.set(sessionId, { ...record, purgedAt });
      sessions += 1;
    }
    if (sessions > 0) this.save();
    if (removed.length > 0 || sessions > 0) {
      this.deps.logger.info(
        `purge: ${sessions} sessions, ${removed.length} events older than ${days} days`,
      );
    }
    return { sessions, events: removed.length };
  }

  /**
   * Changes a session and tells the clients. `activity`: whether it counts as an activity of the
   * session, which moves `updatedAt` (the order of the list, and the start of the retention).
   */
  private update(
    sessionId: string,
    changes: Partial<
      Pick<
        SessionRecord,
        | 'status'
        | 'title'
        | 'lastError'
        | 'branch'
        | 'modifiedFiles'
        | 'approvalMode'
        | 'configOptions'
      >
    >,
    activity = true,
  ): void {
    const record = this.recordOf(sessionId);
    const changed = (Object.keys(changes) as (keyof typeof changes)[]).some(
      (key) => changes[key] !== record[key],
    );
    if (!changed) {
      return;
    }
    const updatedAt = activity ? this.now() : record.updatedAt;
    this.records.set(sessionId, { ...record, ...changes, updatedAt });
    this.save();
    this.emitSession(sessionId);
  }

  private emitSession(sessionId: string): void {
    this.emit({ sessionId, type: 'session', data: this.summaryOf(this.recordOf(sessionId)) });
  }

  private emitAgent(
    runtime: AgentRuntime,
    exit?: { readonly code: number | null; readonly signal: string | null },
  ): void {
    this.emit({
      sessionId: null,
      type: 'agent',
      data: { ...this.stateOf(runtime), ...(exit ? { exit } : {}) },
    });
  }

  private stateOf(runtime: AgentRuntime): AgentState {
    return {
      agent: runtime.connector.agent,
      running: runtime.connection !== null,
      authenticated: runtime.authenticated,
    };
  }

  private emit(event: NewEvent): BridgeEvent {
    return this.journal.append(event);
  }

  private summaryOf(record: SessionRecord): SessionSummary {
    const { branchInstructionPending: _internal, ...summary } = record;
    const latest = this.options.get(record.sessionId);
    const saved = record.configOptions ?? [];
    // An unsuccessful load still discovers choices. Show those choices without
    // replacing the saved intent with the agent's provisional defaults.
    const configOptions =
      !this.live.has(record.sessionId) && latest !== undefined
        ? [
            ...latest.map((option) => {
              const selection = saved.find((o) => o.id === option.id);
              return selection ? { ...option, currentValue: selection.currentValue } : option;
            }),
            ...saved
              .filter((o) => !latest.some((n) => n.id === o.id))
              .map((o) => ({
                ...o,
                options: [],
              })),
          ]
        : saved;
    return {
      ...summary,
      configOptions,
      active: this.live.has(record.sessionId),
      pendingApprovals: this.pendingOf(record.sessionId),
    };
  }

  private recordOf(sessionId: string): SessionRecord {
    const record = this.records.get(sessionId);
    if (record === undefined) {
      throw new BridgeError('unknown_session', `Session inconnue : ${sessionId}.`);
    }
    return record;
  }

  private liveOf(sessionId: string): LiveSession {
    const live = this.live.get(sessionId);
    if (live === undefined) {
      throw new BridgeError('unknown_session', `Session non chargée : ${sessionId}.`);
    }
    return live;
  }

  private async agentCall<T>(agent: AgentKind, call: () => Promise<T>): Promise<T> {
    try {
      return await call();
    } catch (error) {
      if (error instanceof BridgeError) throw error;
      throw new BridgeError('agent_failed', `${AGENT_LABELS[agent]} : ${messageOf(error)}`);
    }
  }

  private assertRunning(): void {
    if (this.stopping !== null) {
      throw new BridgeError('shutting_down', 'Le pont s’arrête.');
    }
  }

  private save(): void {
    this.deps.sessions.saveAll([...this.records.values()]);
  }

  private now(): string {
    return this.deps.clock.now().toISOString();
  }
}

/** A session as stored before ticket 13. */
type StoredRecord = SessionRecord &
  Partial<
    Pick<
      SessionRecord,
      'requestedBranch' | 'branchInstructionPending' | 'modifiedFiles' | 'purgedAt'
    >
  >;

/** What goes before the first instruction of a session created with a branch (ticket 13). */
export function branchInstruction(branch: string): string {
  return `Travaille sur la branche ${branch}, crée-la depuis main si elle n’existe pas.`;
}

function messageData(text: string, delivery: Delivery, prefix: string | null) {
  return prefix === null ? { text, delivery } : { text, delivery, prefix };
}

function clip(text: string): string {
  return text.length > 200 ? `${text.slice(0, 199)}…` : text;
}

function titleFrom(text: string): string {
  const line = text.trim().split('\n')[0] ?? '';
  return line.length > TITLE_LENGTH ? `${line.slice(0, TITLE_LENGTH - 1)}…` : line;
}

function messageOf(error: unknown): string {
  if (error instanceof Error) return error.message;
  if (typeof error === 'object' && error !== null && 'message' in error) {
    return String((error as { message: unknown }).message);
  }
  return String(error);
}
