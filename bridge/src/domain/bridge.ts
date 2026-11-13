import { AGENT_LABELS, isTestedBinary, modeIdFor } from './agents.ts';
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
import type {
  AgentConnection,
  AgentConnector,
  AgentHandlers,
  Clock,
  EventStore,
  IdGenerator,
  Logger,
  SessionStore,
  Workspace,
} from './ports.ts';

export interface BridgeDeps {
  readonly connectors: readonly AgentConnector[];
  readonly events: EventStore;
  readonly sessions: SessionStore;
  readonly workspace: Workspace;
  readonly clock: Clock;
  readonly ids: IdGenerator;
  readonly logger: Logger;
  /** How long `shutdown` waits for the cancelled turns to end. */
  readonly shutdownGraceMs?: number;
}

export interface CreateSessionRequest {
  readonly agent: AgentKind;
  readonly project: string;
  readonly branch?: string | undefined;
  readonly approvalMode: ApprovalMode;
  readonly prompt?: string | undefined;
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
  private readonly turns = new Set<Promise<void>>();
  private stopping: Promise<void> | null = null;

  constructor(deps: BridgeDeps) {
    this.deps = deps;
    this.journal = new EventJournal(deps.events, deps.clock);
    for (const record of deps.sessions.readAll()) this.records.set(record.sessionId, record);
    for (const connector of deps.connectors) {
      this.runtimes.set(connector.agent, { connector, connection: null, authenticated: null });
    }
  }

  /**
   * Starts the bridge. Sessions that were loaded when it last stopped are no longer: they become
   * `stopped`, and can be resumed.
   */
  start(): void {
    for (const record of this.records.values()) {
      if (record.status !== 'stopped' && record.status !== 'failed') {
        this.update(record.sessionId, { status: 'stopped' });
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

  async createSession(request: CreateSessionRequest): Promise<SessionSummary> {
    this.assertRunning();
    const { workspace } = this.deps;
    const project = await workspace.resolveProject(request.project);
    if (request.branch) await workspace.switchBranch(project.cwd, request.branch);
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
      approvalMode: request.approvalMode,
      status: 'idle',
      title: null,
      createdAt: now,
      updatedAt: now,
      lastError: null,
    };
    // The session is known, live and usable only once its mode is forced: nothing reaches the
    // agent before (Claude would run in the user's `auto`).
    const failure = await this.applyMode(record, connection);
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

  /** Loads a session of the history into its agent, so that it can go on. */
  async resume(sessionId: string): Promise<SessionSummary> {
    this.assertRunning();
    await this.ensureLive(sessionId);
    return this.summaryOf(this.recordOf(sessionId));
  }

  /**
   * Sends a message. During a turn, it is injected in the turn when the agent accepts it
   * (`_session/steering`), otherwise it waits for its turn.
   */
  prompt(sessionId: string, text: string): Promise<Delivery> {
    this.assertRunning();
    this.recordOf(sessionId);
    const previous = this.sending.get(sessionId) ?? Promise.resolve();
    const next = previous.then(
      () => this.send(sessionId, text),
      () => this.send(sessionId, text),
    );
    this.sending.set(sessionId, next);
    const forget = () => {
      if (this.sending.get(sessionId) === next) this.sending.delete(sessionId);
    };
    next.then(forget, forget);
    return next;
  }

  private async send(sessionId: string, text: string): Promise<Delivery> {
    this.assertRunning();
    const record = this.recordOf(sessionId);
    const live = await this.ensureLive(sessionId);
    const connection = await this.connection(record.agent);
    if (live.inFlight > 0) {
      const injected = await connection.steer(sessionId, text).catch(() => false);
      if (injected) {
        this.emit({ sessionId, type: 'user_message', data: { text, delivery: 'steering' } });
        return 'steering';
      }
    }
    const delivery: Delivery = live.inFlight > 0 ? 'queued' : 'prompt';
    this.startTurn(sessionId, connection, text, delivery);
    return delivery;
  }

  /** Interrupts the running turn. The session stays usable. */
  async cancel(sessionId: string): Promise<void> {
    const record = this.recordOf(sessionId);
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
    this.emit({ sessionId, type: 'user_message', data: { text, delivery } });
    live.inFlight += 1;
    const record = this.recordOf(sessionId);
    this.update(sessionId, {
      title: record.title ?? titleFrom(text),
      status: this.statusOf(sessionId),
      lastError: null,
    });
    const turn = connection.prompt(sessionId, text).then(
      (result) => {
        if (this.live.get(sessionId) !== live) return;
        live.inFlight -= 1;
        this.emit({ sessionId, type: 'turn_end', data: result });
        this.update(sessionId, { status: this.statusOf(sessionId) });
      },
      (error: unknown) => {
        if (this.live.get(sessionId) !== live) return;
        live.inFlight -= 1;
        // The agent process is being closed: the session is stopped, not failed.
        if (this.stopping !== null) return;
        const message = `${AGENT_LABELS[record.agent]} : ${messageOf(error)}`;
        this.emit({ sessionId, type: 'session_error', data: { message } });
        this.update(sessionId, { status: 'failed', lastError: message });
      },
    );
    this.turns.add(turn);
    void turn.finally(() => this.turns.delete(turn));
  }

  private ensureLive(sessionId: string): Promise<LiveSession> {
    const live = this.live.get(sessionId);
    if (live !== undefined) return Promise.resolve(live);
    let loading = this.loading.get(sessionId);
    if (loading === undefined) {
      loading = this.load(sessionId).finally(() => this.loading.delete(sessionId));
      this.loading.set(sessionId, loading);
    }
    return loading;
  }

  /**
   * Loads a session into its agent, then forces its mode. It goes live only then: until both
   * are done, no message reaches the agent and the session is not `active`.
   */
  private async load(sessionId: string): Promise<LiveSession> {
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
    const failure = await this.applyMode(record, connection);
    if (failure !== null || this.stopping !== null) {
      this.early.delete(sessionId);
      if (failure === null) throw new BridgeError('shutting_down', 'Le pont s’arrête.');
      this.update(sessionId, { status: 'failed', lastError: failure.message });
      throw failure;
    }
    const live = this.goLive(sessionId);
    this.update(sessionId, { status: 'idle', lastError: null });
    this.flushEarly(sessionId);
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
    const modeId = modeIdFor(record.agent, record.approvalMode);
    try {
      await connection.setMode(record.sessionId, modeId);
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
    const live = this.live.get(sessionId);
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
    const live: LiveSession = { compensations: new SessionCompensations(), inFlight: 0 };
    this.live.set(sessionId, live);
    return live;
  }

  private goOffline(sessionId: string, status: SessionStatus, lastError: string | null): void {
    this.live.delete(sessionId);
    this.update(sessionId, { status, lastError });
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

  private update(
    sessionId: string,
    changes: Partial<Pick<SessionRecord, 'status' | 'title' | 'lastError'>>,
  ): void {
    const record = this.recordOf(sessionId);
    const changed = (Object.keys(changes) as (keyof typeof changes)[]).some(
      (key) => changes[key] !== record[key],
    );
    if (!changed) {
      return;
    }
    this.records.set(sessionId, { ...record, ...changes, updatedAt: this.now() });
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
    return {
      ...record,
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
