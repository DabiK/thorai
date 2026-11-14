/**
 * The bridge's model. Pure TypeScript: no Node module, no library.
 *
 * What the agents send (ACP session updates, tool calls, permission options) is kept as the
 * JSON it arrived as: the bridge relays it, it does not re-model it (docs/adr/02).
 */

export const AGENT_KINDS = ['claude', 'codex'] as const;
export type AgentKind = (typeof AGENT_KINDS)[number];

/** Product levels (ADR 08). `allow-all` remains compatible with recorded sessions. */
export const APPROVAL_MODES = ['plan', 'auto-edit', 'ask', 'allow-all'] as const;
export type ApprovalMode = (typeof APPROVAL_MODES)[number];

/**
 * - `working`: a turn is running (« en cours ») ;
 * - `awaiting_approval`: the agent waits for an answer (« attend ta validation ») ;
 * - `idle`: the turn is over, the agent waits for an instruction (« en attente de consigne ») ;
 * - `stopped`: the session is no longer loaded in an agent (« arrêtée »), it can be resumed ;
 * - `failed`: the last turn ended on an error, or the agent stopped (« échec »).
 */
export const SESSION_STATUSES = [
  'working',
  'awaiting_approval',
  'idle',
  'stopped',
  'failed',
] as const;
export type SessionStatus = (typeof SESSION_STATUSES)[number];

/** A session as it is stored: it survives a restart of the bridge and of the console. */
export interface SessionRecord {
  /** The ACP session id, given by the agent. */
  readonly sessionId: string;
  readonly agent: AgentKind;
  /** The project folder, relative to the projects root (`~/projects`). */
  readonly project: string;
  /** Absolute path of the project, as the agent sees it. */
  readonly cwd: string;
  /**
   * Branch checked out in `cwd`, read again at the end of each turn and of each command:
   * `null` outside a git repository. The bridge never checks a branch out (ticket 13).
   */
  readonly branch: string | null;
  /** Branch asked at creation, given to the agent with its first instruction. */
  readonly requestedBranch: string | null;
  /** Whether the branch instruction still has to go with the next instruction. Not relayed. */
  readonly branchInstructionPending: boolean;
  readonly approvalMode: ApprovalMode;
  readonly status: SessionStatus;
  /** Title given by the agent, or the start of the first instruction. */
  readonly title: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
  /** What went wrong, when `status` is `failed`. */
  readonly lastError: string | null;
  /** Files the agent changed (its completed edits), relative to `cwd` when inside it. */
  readonly modifiedFiles: readonly string[];
  /** When the events of the session were last purged (retention), `null` if never. */
  readonly purgedAt: string | null;
}

/** A session as the client sees it. */
export interface SessionSummary extends Omit<SessionRecord, 'branchInstructionPending'> {
  /** Loaded in a running agent process. `false` for the history. */
  readonly active: boolean;
  /** Ids of the approvals waiting for an answer. */
  readonly pendingApprovals: readonly string[];
}

export const EVENT_TYPES = [
  'session',
  'session_update',
  'user_message',
  'approval_request',
  'approval_resolved',
  'turn_end',
  'session_error',
  'agent',
  'bridge',
] as const;
export type EventType = (typeof EVENT_TYPES)[number];

/** Every event the bridge relays carries a global, monotonic and persisted sequence number. */
export interface BridgeEvent {
  readonly seq: number;
  /** ISO 8601 date the bridge recorded the event. */
  readonly at: string;
  /** `null` for the events about an agent or the bridge itself. */
  readonly sessionId: string | null;
  readonly type: EventType;
  readonly data: unknown;
}

export type NewEvent = Omit<BridgeEvent, 'seq' | 'at'>;

/** An ACP `session/update` payload (the `update` field), relayed as is. */
export type AcpUpdate = { readonly sessionUpdate: string } & Readonly<Record<string, unknown>>;

/** An ACP permission option. */
export interface PermissionOption {
  readonly optionId: string;
  readonly name: string;
  readonly kind: string;
}

/** An ACP `session/request_permission` request. */
export interface PermissionRequest {
  readonly sessionId: string;
  readonly toolCall: Readonly<Record<string, unknown>>;
  readonly options: readonly PermissionOption[];
}

export type PermissionOutcome =
  | { readonly outcome: 'selected'; readonly optionId: string }
  | { readonly outcome: 'cancelled' };

/** What the app is asked to approve. */
export interface ApprovalRequest {
  readonly approvalId: string;
  readonly sessionId: string;
  /** The ACP tool call, completed by the bridge (Codex diff). */
  readonly toolCall: Readonly<Record<string, unknown>>;
  readonly options: readonly PermissionOption[];
  /** The plan to approve, in markdown, when the agent asks to leave its plan mode. */
  readonly plan: string | null;
}

/**
 * How a message reached the agent. `cancelled`: it never did, the session was interrupted while
 * the message waited (session loading, previous message).
 */
export type Delivery = 'prompt' | 'steering' | 'queued' | 'cancelled';

export interface TurnResult {
  readonly stopReason: string;
  readonly usage: unknown;
}

export interface AgentVersions {
  readonly agent: AgentKind;
  /** ACP adapter package and version, `null` when it cannot be read. */
  readonly adapter: { readonly name: string; readonly version: string } | null;
  /** Output of `claude --version` or `codex --version`, `null` when it fails. */
  readonly binary: string | null;
  /** The versions the bridge was tested with (docs/adr/02). */
  readonly tested: { readonly adapter: string; readonly binary: string };
  /** Whether the installed versions are the tested ones. */
  readonly isTested: boolean;
  /** Logged in, as last reported by the agent. `null` until its process has started. */
  readonly authenticated: boolean | null;
  /** Whether its adapter process is running. */
  readonly running: boolean;
}

export interface ProjectInfo {
  readonly branches?: readonly string[];
  readonly name: string;
  readonly branch: string | null;
}

export type BridgeErrorCode =
  | 'bad_request'
  | 'unknown_session'
  | 'unknown_approval'
  | 'unknown_option'
  | 'bad_project'
  | 'bad_project_name'
  | 'project_exists'
  | 'project_init_failed'
  | 'agent_failed'
  | 'shutting_down';

/** An error the client can act on. Its message is written for the user, in French. */
export class BridgeError extends Error {
  readonly code: BridgeErrorCode;

  constructor(code: BridgeErrorCode, message: string) {
    super(message);
    this.name = 'BridgeError';
    this.code = code;
  }
}
