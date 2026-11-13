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
 * One agent (Claude or Codex), reached through its ACP adapter.
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
  append(event: BridgeEvent): void;
}

/** The sessions, current and past. */
export interface SessionStore {
  readAll(): readonly SessionRecord[];
  saveAll(records: readonly SessionRecord[]): void;
}

/** The project folders the agents may work in. */
export interface Workspace {
  /** Resolves a project of the projects root. Rejects any path that leaves it. */
  resolveProject(project: string): Promise<{ readonly name: string; readonly cwd: string }>;
  /** Checks out `branch`, creating it when it does not exist. */
  switchBranch(cwd: string, branch: string): Promise<void>;
  currentBranch(cwd: string): Promise<string | null>;
  listProjects(): Promise<readonly ProjectInfo[]>;
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
