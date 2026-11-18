import { describe, expect, it } from 'vitest';
import {
  type AuthenticationCommands,
  CliAuthentication,
  NodeAuthenticationCommands,
} from './cli-authentication.ts';

describe('CLI authentication adapter without any real agent', () => {
  it('extracts only the Codex public URL and code from fragmented ANSI output', async () => {
    const challenges: unknown[] = [];
    let output!: (chunk: string) => void;
    let exit!: (code: number | null) => void;
    const calls: unknown[] = [];
    const commands: AuthenticationCommands = {
      run: async (_binary, args) => {
        calls.push(args);
        return { code: 0, text: 'Logged in using ChatGPT\naccess_token=secret' };
      },
      login: (binary, args, data) => {
        calls.push([binary, args]);
        output = data;
        return {
          done: new Promise((resolve) => {
            exit = resolve;
          }),
          cancel: async () => exit(null),
        };
      },
    };
    const driver = new CliAuthentication({ codex: '/codex', claude: '/claude' }, commands);
    const login = driver.start((url, code) => challenges.push({ url, code }));
    output('\u001b[32mhttps://auth.openai.com/codex/de');
    output('vice\u001b[0m\nEnter this one-time code:\nABCD-');
    output('EFGHI\nrefresh_token=secret');
    expect(challenges).toEqual([
      { url: 'https://auth.openai.com/codex/device', code: 'ABCD-EFGHI' },
    ]);
    expect(await driver.status('codex')).toEqual({ connected: true, account: null });
    expect(calls[0]).toEqual(['/codex', ['login', '--device-auth']]);
    exit(0);
    expect(await login.done).toBe(true);
  });

  it('does not relay Claude secrets, unknown output, or an OAuth URL pretending to be a device URL', async () => {
    let emit!: (chunk: string) => void;
    const commands: AuthenticationCommands = {
      run: async () => ({
        code: 0,
        text: JSON.stringify({
          loggedIn: true,
          email: 'person@example.org',
          accessToken: 'secret',
        }),
      }),
      login: (_binary, _args, data) => {
        emit = data;
        return { done: Promise.resolve(null), cancel: async () => {} };
      },
    };
    const driver = new CliAuthentication({ codex: '/codex', claude: '/claude' }, commands);
    const challenges: unknown[] = [];
    driver.start((...challenge) => challenges.push(challenge));
    emit('https://auth.openai.com/codex/device?token=secret\nABCD-EFGHI\n');
    expect(challenges).toEqual([]);
    expect(await driver.status('claude')).toEqual({
      connected: true,
      account: 'person@example.org',
    });
    expect(await driver.status('codex')).toEqual({ connected: null, account: null });
  });
});

it('expires and reaps a synthetic CLI process without contacting a service', async () => {
  const commands = new NodeAuthenticationCommands({ loginTimeoutMs: 50, killGraceMs: 20 });
  const task = commands.login(process.execPath, ['-e', 'setInterval(() => {}, 1000)'], () => {});
  try {
    expect(
      await Promise.race([
        task.done,
        new Promise((resolve) => setTimeout(() => resolve('still-running'), 300)),
      ]),
    ).toBeNull();
  } finally {
    await task.cancel();
  }
});
