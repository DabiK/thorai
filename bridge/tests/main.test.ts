import { type ChildProcess, spawn } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

/**
 * The bridge process itself (src/main.ts), without any agent: none is started until a session
 * is created. Two bridges must never write the same journal.
 */
const MAIN = resolve(import.meta.dirname, '../src/main.ts');
let base: string;
const running: ChildProcess[] = [];

beforeEach(() => {
  base = mkdtempSync(join(tmpdir(), 'regie-main-'));
  writeFileSync(join(base, 'token'), 'b'.repeat(64), { mode: 0o600 });
  mkdirSync(join(base, 'projects'));
});
afterEach(() => {
  for (const child of running.splice(0)) child.kill('SIGKILL');
  rmSync(base, { recursive: true, force: true });
});

function bridge(state: string, port: number, settings: Record<string, string> = {}) {
  const child = spawn(process.execPath, [MAIN], {
    env: {
      ...process.env,
      REGIE_BRIDGE_PORT: String(port),
      REGIE_BRIDGE_TOKEN_FILE: join(base, 'token'),
      REGIE_BRIDGE_STATE_DIR: join(base, state),
      REGIE_PROJECTS_DIR: join(base, 'projects'),
      ...settings,
    },
    stdio: ['ignore', 'ignore', 'pipe'],
  });
  running.push(child);
  let log = '';
  child.stderr?.on('data', (chunk: Buffer) => {
    log += chunk.toString();
  });
  const exited = new Promise<number | null>((done) => child.once('exit', (code) => done(code)));
  const ready = new Promise<number>((done, fail) => {
    child.stderr?.on('data', () => {
      const port = /listening on ws:\/\/127\.0\.0\.1:(\d+)/.exec(log)?.[1];
      if (port && log.includes(' ready, ')) done(Number(port));
    });
    void exited.then(() => fail(new Error(`exited: ${log}`)));
  });
  // A bridge expected to fail is never awaited for `ready`.
  ready.catch(() => {});
  return { ready, exited, log: () => log };
}

describe('two bridges', () => {
  it('on the same state folder: the second leaves without writing anything', async () => {
    const port = await bridge('state', 0).ready;
    const journal = readFileSync(join(base, 'state/events.jsonl'), 'utf8');

    const second = bridge('state', 0);

    expect(await second.exited).toBe(1);
    expect(second.log()).toContain('another bridge');
    expect(readFileSync(join(base, 'state/events.jsonl'), 'utf8')).toBe(journal);
    expect(port).toBeGreaterThan(0);
  });

  it('on the same port: the second leaves before touching its journal', async () => {
    const port = await bridge('state', 0).ready;

    const second = bridge('other-state', port);

    expect(await second.exited).toBe(1);
    expect(second.log()).toContain('EADDRINUSE');
    expect(existsSync(join(base, 'other-state/events.jsonl'))).toBe(false);
  });
});

describe('retention of the history', () => {
  const OLD = '2020-01-01T00:00:00.000Z';

  function oldSession(sessionId: string) {
    return {
      sessionId,
      agent: 'codex',
      project: 'demo',
      cwd: join(base, 'projects/demo'),
      branch: null,
      approvalMode: 'ask',
      status: 'idle',
      title: 'Ancienne',
      createdAt: OLD,
      updatedAt: OLD,
      lastError: null,
    };
  }

  it('purges the old events at start, and keeps the session listed', async () => {
    const state = join(base, 'state');
    mkdirSync(state);
    writeFileSync(
      join(state, 'sessions.json'),
      JSON.stringify({ version: 1, sessions: [oldSession('old-1')] }),
    );
    const event = { seq: 1, at: OLD, sessionId: 'old-1', type: 'user_message', data: {} };
    writeFileSync(
      join(state, 'events.jsonl'),
      `${JSON.stringify({ journal: 'j', version: 1 })}\n${JSON.stringify(event)}\n`,
    );

    const started = bridge('state', 0);
    await started.ready;

    const journal = readFileSync(join(state, 'events.jsonl'), 'utf8');
    expect(journal).not.toContain('"old-1"');
    // The header keeps the journal name and the last number: numbering never goes back.
    const header = JSON.parse(journal.split('\n')[0] ?? '');
    expect(header.journal).toBe('j');
    expect(header.lastSeq).toBeGreaterThanOrEqual(2);
    const sessions = JSON.parse(readFileSync(join(state, 'sessions.json'), 'utf8'));
    expect(sessions.sessions[0]).toMatchObject({ sessionId: 'old-1', title: 'Ancienne' });
    expect(sessions.sessions[0].purgedAt).toEqual(expect.any(String));
    expect(started.log()).toContain('purge: 1 sessions');
  });

  it('refuses a retention that is not a positive number of days', async () => {
    const started = bridge('state', 0, { REGIE_BRIDGE_RETENTION_DAYS: 'trente' });

    expect(await started.exited).toBe(1);
    expect(started.log()).toContain('REGIE_BRIDGE_RETENTION_DAYS');
    expect(existsSync(join(base, 'state/events.jsonl'))).toBe(false);
  });
});
