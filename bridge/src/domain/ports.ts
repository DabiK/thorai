import type {
  AcpUpdate,
  AgentKind,
  AgentVersions,
  BridgeEvent,
  PermissionOutcome,
  PermissionRequest,
  ProjectInfo,
  SessionRecord,
  TurnResult,
} from './model.ts';

/**
 * One registered agent, reached through its ACP adapter.
 *
 * `connect` starts the adapter process. One process holds every session of its agent.
 */
export interface AgentConnector {
  readonly agent: AgentKind;
  /** Versions of the adapter and of the agent binary. Does not start the adapter. */
  versions(): Promise<Omit<AgentVersions, 'authenticated' | 'running'>>;
  connect(handlers: AgentHandlers): Promise<AgentConnection>;
}

/** What the agent sends on its own. */
export interface AgentHandlers {
  update(sessionId: string, update: AcpUpdate): void;
  permission(request: PermissionRequest): Promise<PermissionOutcome>;
  /** The agent reported its login state. The account details are never passed on. */
  authenticated(authenticated: boolean): void;
}

export interface AgentConnection {
  newSession(cwd: string): Promise<string>;
  loadSession(sessionId: string, cwd: string): Promise<void>;
  setMode(sessionId: string, modeId: string): Promise<void>;
  setConfig(sessionId: string, configId: string, value: string): Promise<void>;
  /** Resolves when the turn ends. */
  prompt(sessionId: string, text: string): Promise<TurnResult>;
  /** Injects a message in the running turn. `false` when the agent does not accept it. */
  steer(sessionId: string, text: string): Promise<boolean>;
  cancel(sessionId: string): Promise<void>;
  /** Stops the adapter process: stdin closed, then SIGTERM, then SIGKILL. */
  close(): Promise<void>;
  /** Resolves when the adapter process is gone, whatever the reason. */
  readonly exited: Promise<{ readonly code: number | null; readonly signal: string | null }>;
}

/** The events, kept for the client to catch up. Appends must be durable once they return. */
export interface EventStore {
  /**
   * Identifies this journal. A new journal (the old one deleted) has a new id: a client that
   * kept numbers of another journal starts again.
   */
  readonly journalId: string;
  readAll(): readonly BridgeEvent[];
  /** The highest number ever stored, even when its event has been purged since. */
  lastSeq(): number;
  append(event: BridgeEvent): void;
  /**
   * Replaces the stored events at once, keeping the journal id and the numbers: used to purge
   * old events. `lastSeq` is kept, so that numbering never goes back.
   */
  replaceAll(events: readonly BridgeEvent[], lastSeq: number): void;
}

/** The sessions, current and past. */
export interface SessionStore {
  readAll(): readonly SessionRecord[];
  saveAll(records: readonly SessionRecord[]): void;
}

/** The project folders the agents may work in. */
export interface Workspace {
  configurationDirectory(): Promise<string>;
  createProject(
    name: string,
    initialBranch: string,
  ): Promise<{ readonly name: string; readonly cwd: string }>;
  /** Bounded source identifiers and git refs; never file contents. */
  vocabulary(cwd: string): Promise<readonly string[]>;
  /** Resolves a project of the projects root. Rejects any path that leaves it. */
  resolveProject(project: string): Promise<{ readonly name: string; readonly cwd: string }>;
  /** Whether `branch` is a valid branch name. The bridge never checks a branch out. */
  isValidBranchName(branch: string): Promise<boolean>;
  /** The branch checked out in `cwd`, `null` outside a git repository or on a detached HEAD. */
  currentBranch(cwd: string): Promise<string | null>;
  listProjects(): Promise<readonly ProjectInfo[]>;
}

/**
 * A word, a glob (an unquoted word with `*`, `?` or `[`), an operator (`&&`, `|`, `>`…), or
 * anything else the shell would read (a comment, a variable): `other`, never trusted.
 */
export type ShellToken =
  | { readonly kind: 'word'; readonly value: string }
  | { readonly kind: 'glob'; readonly value: string }
  | { readonly kind: 'operator'; readonly value: string }
  | { readonly kind: 'other' };

/** Splits a shell command line into words and operators, quotes removed. Runs nothing. */
export interface ShellParser {
  parse(script: string): readonly ShellToken[];
}

export interface Clock {
  now(): Date;
}

export type IdGenerator = () => string;

export interface Logger {
  info(message: string): void;
  warn(message: string): void;
  error(message: string): void;
}
