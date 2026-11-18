import { execFile, spawn } from 'node:child_process';
import { stripVTControlCharacters } from 'node:util';
import type { AuthenticationDriver, LoginTask } from '../../domain/authentication.ts';
import { DEVICE_URL } from '../../domain/authentication.ts';
import type { AgentKind } from '../../domain/model.ts';
import { OpenCodeApiCredentials, type OpenCodeCredentials } from './opencode-credentials.ts';

interface CommandResult {
  readonly code: number | null;
  readonly text: string;
}
interface CommandTask {
  readonly done: Promise<number | null>;
  cancel(): Promise<void>;
}
export interface AuthenticationCommands {
  run(binary: string, args: readonly string[]): Promise<CommandResult>;
  login(binary: string, args: readonly string[], output: (chunk: string) => void): CommandTask;
}

/** Raw stdout/stderr is deliberately neither logged nor returned through the public port. */
export class CliAuthentication implements AuthenticationDriver {
  private readonly binaries: Readonly<Record<AgentKind, string>>;
  private readonly commands: AuthenticationCommands;
  private readonly opencode: OpenCodeCredentials;
  constructor(
    binaries: Readonly<Record<AgentKind, string>>,
    commands: AuthenticationCommands = new NodeAuthenticationCommands(),
    opencode?: OpenCodeCredentials,
  ) {
    this.binaries = binaries;
    this.commands = commands;
    this.opencode = opencode ?? new OpenCodeApiCredentials(binaries.opencode ?? 'opencode');
  }

  async status(agent: AgentKind) {
    const result = await this.commands.run(
      this.binaries[agent] ?? agent,
      agent === 'opencode'
        ? ['auth', 'list', '--format', 'json', '--standalone']
        : agent === 'codex'
          ? ['login', 'status']
          : ['auth', 'status', '--json'],
    );
    if (agent === 'opencode') {
      try {
        const integrations: unknown = JSON.parse(result.text);
        if (result.code !== 0 || !Array.isArray(integrations))
          return { connected: null, account: null };
        return {
          connected: integrations.some(
            (item) =>
              item?.id === 'opencode-go' &&
              Array.isArray(item.connections) &&
              item.connections.length > 0,
          ),
          account: null,
        };
      } catch {
        return { connected: null, account: null };
      }
    }
    if (agent === 'claude') {
      try {
        const json = JSON.parse(result.text) as Record<string, unknown>;
        const connected = typeof json.loggedIn === 'boolean' ? json.loggedIn : null;
        const account =
          connected &&
          typeof json.email === 'string' &&
          /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(json.email)
            ? json.email
            : null;
        return { connected, account };
      } catch {
        return { connected: null, account: null };
      }
    }
    const connected =
      result.code === 0 && /^Logged in using (ChatGPT|an API key)/m.test(result.text)
        ? true
        : result.code === 1 && /Not logged in/i.test(result.text)
          ? false
          : null;
    return { connected, account: null };
  }

  start(challenge: (url: string, code: string) => void): LoginTask {
    let text = '';
    let emitted = false;
    const process = this.commands.login(
      this.binaries.codex ?? 'codex',
      ['login', '--device-auth'],
      (chunk) => {
        if (emitted) return;
        text = (text + chunk).slice(-16_384);
        const plain = stripVTControlCharacters(text);
        const lines = plain
          .slice(0, plain.lastIndexOf('\n') + 1)
          .split(/\r?\n/)
          .map((line) => line.trim());
        const code = lines.find((line) => /^[A-Z0-9]{4}-[A-Z0-9]{4,5}$/.test(line));
        if (lines.includes(DEVICE_URL) && code) {
          emitted = true;
          text = '';
          challenge(DEVICE_URL, code);
        }
      },
    );
    return { done: process.done.then((code) => code === 0), cancel: () => process.cancel() };
  }

  async saveKey(agent: AgentKind, provider: string, key: string): Promise<void> {
    if (agent !== 'opencode') throw new Error('Unsupported login');
    await this.opencode.save(provider, key);
  }

  async logout(agent: AgentKind): Promise<void> {
    if (agent === 'opencode') return this.opencode.logout('opencode-go');
    const result = await this.commands.run(
      this.binaries[agent] ?? agent,
      agent === 'codex' ? ['logout'] : ['auth', 'logout'],
    );
    if (result.code !== 0) throw new Error('logout not confirmed');
  }
}

export class NodeAuthenticationCommands implements AuthenticationCommands {
  private readonly loginTimeoutMs: number;
  private readonly killGraceMs: number;

  constructor(options: { loginTimeoutMs?: number; killGraceMs?: number } = {}) {
    this.loginTimeoutMs = options.loginTimeoutMs ?? 15 * 60 * 1000;
    this.killGraceMs = options.killGraceMs ?? 1000;
  }

  run(binary: string, args: readonly string[]): Promise<CommandResult> {
    return new Promise((resolve) => {
      execFile(
        binary,
        [...args],
        { timeout: 15_000, maxBuffer: 65_536, env: process.env },
        (error, stdout, stderr) => {
          resolve({
            code: error === null ? 0 : typeof error.code === 'number' ? error.code : null,
            text: stdout + stderr,
          });
        },
      );
    });
  }

  login(binary: string, args: readonly string[], output: (chunk: string) => void): CommandTask {
    const child = spawn(binary, [...args], {
      env: { ...process.env, NO_COLOR: '1', BROWSER: 'true' },
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    child.stdout.setEncoding('utf8').on('data', output);
    child.stderr.setEncoding('utf8').on('data', output);
    let killTimer: ReturnType<typeof setTimeout> | undefined;
    let finished = false;
    const stop = () => {
      if (finished) return;
      child.kill('SIGTERM');
      killTimer ??= setTimeout(() => child.kill('SIGKILL'), this.killGraceMs);
    };
    const expiry = setTimeout(stop, this.loginTimeoutMs);
    const done = new Promise<number | null>((resolve) => {
      const finish = (code: number | null) => {
        finished = true;
        clearTimeout(expiry);
        clearTimeout(killTimer);
        resolve(code);
      };
      child.once('close', finish);
      child.once('error', () => finish(null));
    });
    return {
      done,
      cancel: async () => {
        stop();
        await done;
      },
    };
  }
}
