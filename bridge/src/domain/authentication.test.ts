import { describe, expect, it } from 'vitest';
import { Authentication, type AuthenticationDriver, type LoginTask } from './authentication.ts';

class FakeAuthentication implements AuthenticationDriver {
  connected = false;
  reads = 0;
  starts = 0;
  cancelled = 0;
  resolve!: (success: boolean) => void;
  challenge!: (url: string, code: string) => void;
  async status() {
    this.reads++;
    return { connected: this.connected, account: null };
  }
  start(challenge: (url: string, code: string) => void): LoginTask {
    this.starts++;
    this.challenge = challenge;
    return {
      done: new Promise((resolve) => {
        this.resolve = resolve;
      }),
      cancel: async () => {
        this.cancelled++;
        this.resolve(false);
      },
    };
  }
  async logout() {
    this.connected = false;
  }
}

describe('agent authentication', () => {
  it('shows a device challenge once, then verifies the saved login before reporting success', async () => {
    const driver = new FakeAuthentication();
    const auth = new Authentication(driver);
    expect((await auth.status('codex')).connected).toBe(false);
    await auth.start('codex');
    await auth.start('codex');
    expect(driver.starts).toBe(1);
    driver.challenge('https://auth.openai.com/codex/device', 'ABCD-EFGHI');
    expect(await auth.status('codex')).toMatchObject({ phase: 'waiting', code: 'ABCD-EFGHI' });
    driver.connected = true;
    driver.resolve(true);
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(await auth.status('codex')).toMatchObject({
      connected: true,
      phase: 'connected',
      code: null,
      url: null,
    });
    await auth.logout('codex');
    expect((await auth.status('codex')).connected).toBe(false);
  });
});

it('manual Claude login needs no task and tells the user where to paste the OAuth code', async () => {
  const driver = new FakeAuthentication();
  const auth = new Authentication(driver);
  const state = await auth.start('claude');
  expect(state.phase).toBe('manual');
  expect(state.message).toContain('claude auth login');
  expect(state.message).toContain('proot-distro login debian --user dev');
  expect(state.message).toContain('--work-dir /home/dev -- bash -l');
  expect(state.message).toContain('--bind "$HOME/.codex:/home/dev/.codex"');
  expect(state.message?.indexOf('proot-distro')).toBeLessThan(
    state.message?.indexOf('claude auth login') ?? 0,
  );
  expect(state.message).toContain('Termux');
  expect(state.url).toBeNull();
  expect(driver.starts).toBe(0);
});

it('cancellation clears the challenge, releases the lease and ignores late callbacks', async () => {
  const driver = new FakeAuthentication();
  let leases = 0;
  const auth = new Authentication(driver, () => {
    leases++;
    return () => {
      leases--;
    };
  });
  await auth.start('codex');
  const late = driver.challenge;
  driver.challenge('https://auth.openai.com/codex/device', 'ABCD-EFGHI');
  expect(leases).toBe(1);
  await auth.cancel('codex');
  late('https://auth.openai.com/codex/device', 'WXYZ-12345');
  expect(await auth.status('codex')).toMatchObject({
    phase: 'disconnected',
    code: null,
    url: null,
  });
  expect(leases).toBe(0);
  expect(driver.cancelled).toBe(1);
  await auth.start('codex');
  driver.resolve(false);
  await new Promise((resolve) => setTimeout(resolve, 0));
  expect(await auth.status('codex')).toMatchObject({ phase: 'failed', code: null });
  expect(leases).toBe(0);
});

it('arbitrary output and an invalid challenge never reach the public state', async () => {
  const driver = new FakeAuthentication();
  const auth = new Authentication(driver);
  await auth.start('codex');
  driver.challenge('https://auth.openai.com/codex/device?access_token=secret', 'ABCD-EFGHI');
  driver.challenge('https://evil.invalid', 'ABCD-EFGHI');
  driver.challenge('https://auth.openai.com/codex/device', 'access_token=secret');
  expect(JSON.stringify(await auth.list())).not.toContain('secret');
  await auth.close();
  expect(driver.cancelled).toBe(1);
  await auth.start('codex');
  expect(driver.starts).toBe(1);
});

it('accepts a challenge delivered immediately by the authentication port', async () => {
  const driver = new FakeAuthentication();
  const auth = new Authentication({
    status: () => driver.status(),
    logout: () => driver.logout(),
    start: (challenge) => {
      challenge('https://auth.openai.com/codex/device', 'ABCD-EFGHI');
      return driver.start(challenge);
    },
  });
  expect(await auth.start('codex')).toMatchObject({ phase: 'waiting', code: 'ABCD-EFGHI' });
  await auth.close();
});

it('a stale status failure cannot erase a newly issued device challenge', async () => {
  const driver = new FakeAuthentication();
  let fail!: (error: Error) => void;
  const auth = new Authentication({
    status: () =>
      new Promise((_resolve, reject) => {
        fail = reject;
      }),
    logout: () => driver.logout(),
    start: (challenge) => driver.start(challenge),
  });
  const old = auth.status('codex');
  await auth.start('codex');
  driver.challenge('https://auth.openai.com/codex/device', 'ABCD-EFGHI');
  fail(new Error('raw secret'));
  expect(await old).toMatchObject({ phase: 'waiting', code: 'ABCD-EFGHI' });
  // Do not wait for a fresh status on this deliberately hanging fake.
  driver.resolve(false);
});

it('shutdown cancels login tasks without spawning status commands', async () => {
  const driver = new FakeAuthentication();
  const auth = new Authentication(driver);
  await auth.start('codex');
  await auth.close();
  expect(driver.cancelled).toBe(1);
  expect(driver.reads).toBe(0);
});

it('never spreads unexpected credential fields from a driver into the public response', async () => {
  const auth = new Authentication({
    status: async () => ({
      connected: true,
      account: 'person@example.org',
      access_token: 'secret',
    }),
    start: () => {
      throw new Error('not used');
    },
    logout: async () => {},
  });
  const status = await auth.status('claude');
  expect(status.account).toBe('person@example.org');
  expect(JSON.stringify(status)).not.toContain('secret');
});

it('accepts a Go key only through an exclusive ephemeral operation, never in public state', async () => {
  const driver = new FakeAuthentication();
  const saved: unknown[] = [];
  const saveKey = async (...args: string[]) => {
    saved.push(args);
    driver.connected = true;
  };
  const auth = new Authentication({
    status: () => driver.status(),
    start: (f) => driver.start(f),
    logout: () => driver.logout(),
    saveKey,
  });
  expect(await auth.start('opencode')).toMatchObject({
    phase: 'key',
    login: { method: 'api_key', provider: 'opencode-go' },
  });
  await expect(auth.submitKey('opencode', 'other-provider', 'test-only-key')).rejects.toThrow();
  await expect(auth.submitKey('codex', 'opencode-go', 'test-only-key')).rejects.toThrow();
  expect(saved).toEqual([]);
  const result = await auth.submitKey('opencode', 'opencode-go', 'test-only-key');
  expect(result.connected).toBe(true);
  expect(saved).toEqual([['opencode', 'opencode-go', 'test-only-key']]);
  expect(JSON.stringify(await auth.list())).not.toContain('test-only-key');
});

it('never relays a credential-bearing driver exception and releases its lease', async () => {
  let leases = 0;
  const driver = new FakeAuthentication();
  const auth = new Authentication(
    {
      status: () => driver.status(),
      start: (f) => driver.start(f),
      logout: () => driver.logout(),
      saveKey: async () => {
        throw new Error('test-only-key');
      },
    },
    () => {
      leases++;
      return () => {
        leases--;
      };
    },
  );
  const state = await auth.submitKey('opencode', 'opencode-go', 'test-only-key');
  expect(state.phase).toBe('failed');
  expect(JSON.stringify(state)).not.toContain('test-only-key');
  expect(leases).toBe(0);
});
