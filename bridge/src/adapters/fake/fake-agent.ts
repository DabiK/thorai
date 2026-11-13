import type {
  AcpUpdate,
  AgentKind,
  PermissionOption,
  PermissionOutcome,
  TurnResult,
} from '../../domain/model.ts';
import type { AgentConnection, AgentConnector, AgentHandlers } from '../../domain/ports.ts';

type Exit = { readonly code: number | null; readonly signal: string | null };

interface Deferred<T> {
  readonly promise: Promise<T>;
  resolve(value: T): void;
  reject(error: unknown): void;
}

function deferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

/**
 * A fake agent the tests drive by hand: it records the calls, and the test decides when a turn
 * ends, what the agent sends and when its process stops.
 */
export class FakeAgentConnector implements AgentConnector {
  readonly agent: AgentKind;
  readonly calls: Array<{ readonly method: string; readonly args: readonly unknown[] }> = [];
  /** Whether `_session/steering` is accepted. */
  acceptsSteering = true;
  /** When set, `connect` fails with it. */
  connectError: Error | null = null;
  /** When set, `setMode` fails with it. */
  setModeError: Error | null = null;
  /** Updates replayed by `loadSession`, as an agent does. */
  history: AcpUpdate[] = [];
  connections = 0;
  private handlers: AgentHandlers | null = null;
  private exit: Deferred<Exit> | null = null;
  private nextSession = 1;
  private readonly turns = new Map<string, Deferred<TurnResult>[]>();

  constructor(agent: AgentKind) {
    this.agent = agent;
  }

  versions() {
    return Promise.resolve({
      agent: this.agent,
      adapter: { name: `fake-${this.agent}-acp`, version: '0.0.0' },
      binary: '0.0.0 (fake)',
      tested: { adapter: '0.0.0', binary: '0.0.0' },
      isTested: true,
    });
  }

  connect(handlers: AgentHandlers): Promise<AgentConnection> {
    if (this.connectError) return Promise.reject(this.connectError);
    this.connections += 1;
    this.handlers = handlers;
    const exit = deferred<Exit>();
    this.exit = exit;
    const record = (method: string, ...args: unknown[]) => this.calls.push({ method, args });
    const connection: AgentConnection = {
      newSession: async (cwd) => {
        record('newSession', cwd);
        return `${this.agent}-session-${this.nextSession++}`;
      },
      loadSession: async (sessionId, cwd) => {
        record('loadSession', sessionId, cwd);
        for (const update of this.history) handlers.update(sessionId, update);
      },
      setMode: async (sessionId, modeId) => {
        record('setMode', sessionId, modeId);
        if (this.setModeError) throw this.setModeError;
      },
      prompt: (sessionId, text) => {
        record('prompt', sessionId, text);
        const turn = deferred<TurnResult>();
        this.turns.set(sessionId, [...(this.turns.get(sessionId) ?? []), turn]);
        return turn.promise;
      },
      steer: async (sessionId, text) => {
        record('steer', sessionId, text);
        return this.acceptsSteering;
      },
      cancel: async (sessionId) => {
        record('cancel', sessionId);
        for (const turn of this.turns.get(sessionId) ?? []) {
          turn.resolve({ stopReason: 'cancelled', usage: null });
        }
        this.turns.delete(sessionId);
      },
      close: async () => {
        record('close');
        this.stop({ code: 0, signal: null });
      },
      exited: exit.promise,
    };
    return Promise.resolve(connection);
  }

  /** The agent sends a session update. */
  send(sessionId: string, update: AcpUpdate): void {
    this.connected().update(sessionId, update);
  }

  /** The agent asks for a permission. */
  ask(
    sessionId: string,
    toolCall: Record<string, unknown>,
    options: readonly PermissionOption[],
  ): Promise<PermissionOutcome> {
    return this.connected().permission({ sessionId, toolCall, options });
  }

  reportAuth(authenticated: boolean): void {
    this.connected().authenticated(authenticated);
  }

  /** Ends the oldest running turn of the session. */
  endTurn(sessionId: string, stopReason = 'end_turn'): void {
    const [turn, ...rest] = this.turns.get(sessionId) ?? [];
    if (turn === undefined) throw new Error(`no running turn for ${sessionId}`);
    this.turns.set(sessionId, rest);
    turn.resolve({ stopReason, usage: null });
  }

  failTurn(sessionId: string, error: Error): void {
    const [turn, ...rest] = this.turns.get(sessionId) ?? [];
    if (turn === undefined) throw new Error(`no running turn for ${sessionId}`);
    this.turns.set(sessionId, rest);
    turn.reject(error);
  }

  runningTurns(sessionId: string): number {
    return this.turns.get(sessionId)?.length ?? 0;
  }

  /** The adapter process stops on its own. */
  crash(code = 1): void {
    this.stop({ code, signal: null });
  }

  callsOf(method: string): ReadonlyArray<readonly unknown[]> {
    return this.calls.filter((call) => call.method === method).map((call) => call.args);
  }

  private stop(exit: Exit): void {
    for (const [, turns] of this.turns) {
      for (const turn of turns) turn.reject(new Error('connection closed'));
    }
    this.turns.clear();
    this.exit?.resolve(exit);
  }

  private connected(): AgentHandlers {
    if (this.handlers === null) throw new Error('the fake agent is not connected');
    return this.handlers;
  }
}
