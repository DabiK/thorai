import { AGENT_KINDS, type AgentKind } from './model.ts';

export interface LoginTask {
  readonly done: Promise<boolean>;
  cancel(): Promise<void>;
}

/** Only public metadata crosses this port. No credential files are read by Régie. */
export interface AuthenticationDriver {
  status(agent: AgentKind): Promise<{ connected: boolean | null; account: string | null }>;
  start(challenge: (url: string, code: string) => void): LoginTask;
  logout(agent: AgentKind): Promise<void>;
}

export interface AuthenticationState {
  readonly agent: AgentKind;
  readonly connected: boolean | null;
  readonly account: string | null;
  readonly phase:
    | 'unknown'
    | 'disconnected'
    | 'starting'
    | 'waiting'
    | 'connected'
    | 'failed'
    | 'manual';
  readonly url: string | null;
  readonly code: string | null;
  readonly message: string | null;
}

export const CLAUDE_MANUAL =
  'Claude ne propose pas de code d’appareil. Sur la console, ouvre Termux et lance claude auth login. Ouvre l’URL affichée sur ton téléphone et recopie le code du navigateur dans Termux. Reviens ici puis choisis Actualiser.';
export const DEVICE_URL = 'https://auth.openai.com/codex/device';

/** Ephemeral state: challenges never enter the durable session journal. */
export class Authentication {
  private readonly states = new Map<AgentKind, AuthenticationState>();
  private readonly tasks = new Map<AgentKind, { task: LoginTask; release: () => void }>();
  private readonly changing = new Set<AgentKind>();
  private readonly attempts = new Map<AgentKind, symbol>();
  private stopped = false;

  private readonly driver: AuthenticationDriver;
  private readonly acquire: (agent: AgentKind) => () => void;
  constructor(
    driver: AuthenticationDriver,
    acquire: (agent: AgentKind) => () => void = () => () => {},
  ) {
    this.driver = driver;
    this.acquire = acquire;
  }

  private state(agent: AgentKind): AuthenticationState {
    return (
      this.states.get(agent) ?? {
        agent,
        connected: null,
        account: null,
        phase: 'unknown',
        url: null,
        code: null,
        message: null,
      }
    );
  }

  private save(agent: AgentKind, patch: Partial<AuthenticationState>): AuthenticationState {
    const state = { ...this.state(agent), ...patch };
    this.states.set(agent, state);
    return state;
  }

  async status(agent: AgentKind): Promise<AuthenticationState> {
    if (this.tasks.has(agent) || this.changing.has(agent)) return this.state(agent);
    const before = this.state(agent);
    try {
      const status = await this.driver.status(agent);
      // A login may have started while the CLI was answering.
      if (this.state(agent) !== before && this.states.has(agent)) return this.state(agent);
      const phase =
        status.connected === true
          ? 'connected'
          : status.connected === null
            ? 'unknown'
            : ['failed', 'manual'].includes(before.phase)
              ? before.phase
              : 'disconnected';
      return this.save(agent, {
        connected: status.connected,
        account: status.account,
        phase,
        url: null,
        code: null,
        message: phase === 'connected' ? null : before.message,
      });
    } catch {
      if (this.state(agent) !== before && this.states.has(agent)) return this.state(agent);
      return this.save(agent, {
        connected: null,
        account: null,
        phase: 'unknown',
        url: null,
        code: null,
        message: 'État de connexion indisponible. Actualise pour réessayer.',
      });
    }
  }

  async list(): Promise<readonly AuthenticationState[]> {
    return Promise.all(AGENT_KINDS.map((agent) => this.status(agent)));
  }

  async start(agent: AgentKind): Promise<AuthenticationState> {
    if (this.tasks.has(agent) || this.changing.has(agent) || this.stopped) return this.state(agent);
    if (agent === 'claude')
      return this.save(agent, { phase: 'manual', url: null, code: null, message: CLAUDE_MANUAL });
    const release = this.acquire(agent);
    const attempt = Symbol();
    this.attempts.set(agent, attempt);
    this.save(agent, {
      phase: 'starting',
      connected: false,
      account: null,
      url: null,
      code: null,
      message: null,
    });
    try {
      const task = this.driver.start((url, code) => {
        // Allowlist, never relay arbitrary CLI output or OAuth query parameters.
        if (this.stopped || this.attempts.get(agent) !== attempt) return;
        if (url === DEVICE_URL && /^[A-Z0-9]{4}-[A-Z0-9]{4,5}$/.test(code)) {
          this.save(agent, { phase: 'waiting', url, code });
        }
      });
      this.tasks.set(agent, { task, release });
      void task.done.then(
        (success) => this.completed(agent, task, success),
        () => this.completed(agent, task, false),
      );
    } catch {
      this.attempts.delete(agent);
      release();
      this.save(agent, {
        phase: 'failed',
        message: 'Connexion impossible. Réessaie depuis Régie.',
      });
    }
    return this.state(agent);
  }

  private async completed(agent: AgentKind, task: LoginTask, success: boolean): Promise<void> {
    const pending = this.tasks.get(agent);
    if (pending?.task !== task) return;
    this.tasks.delete(agent);
    this.attempts.delete(agent);
    pending.release();
    this.save(agent, {
      phase: success ? 'unknown' : 'failed',
      url: null,
      code: null,
      message: success ? null : 'Connexion annulée, expirée ou refusée. Réessaie.',
    });
    await this.status(agent);
  }

  async cancel(agent: AgentKind): Promise<AuthenticationState> {
    const pending = this.tasks.get(agent);
    this.tasks.delete(agent);
    this.attempts.delete(agent);
    this.save(agent, { phase: 'disconnected', url: null, code: null, message: null });
    if (pending) {
      try {
        await pending.task.cancel();
      } finally {
        pending.release();
      }
    }
    return this.stopped ? this.state(agent) : this.status(agent);
  }

  async logout(agent: AgentKind): Promise<AuthenticationState> {
    await this.cancel(agent);
    if (this.changing.has(agent) || this.stopped) return this.state(agent);
    const release = this.acquire(agent);
    this.changing.add(agent);
    try {
      await this.driver.logout(agent);
      this.save(agent, {
        connected: false,
        account: null,
        phase: 'disconnected',
        url: null,
        code: null,
        message: null,
      });
    } catch {
      this.save(agent, {
        connected: null,
        account: null,
        phase: 'failed',
        message: 'Déconnexion non confirmée. Actualise avant de réessayer.',
      });
    } finally {
      this.changing.delete(agent);
      release();
    }
    return this.status(agent);
  }

  async close(): Promise<void> {
    this.stopped = true;
    await Promise.all(AGENT_KINDS.map((agent) => this.cancel(agent)));
  }
}
