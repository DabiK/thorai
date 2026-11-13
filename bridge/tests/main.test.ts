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

function bridge(state: string, port: number) {
  const child = spawn(process.execPath, [MAIN], {
    env: {
      ...process.env,
      REGIE_BRIDGE_PORT: String(port),
      REGIE_BRIDGE_TOKEN_FILE: join(base, 'token'),
      REGIE_BRIDGE_STATE_DIR: join(base, state),
      REGIE_PROJECTS_DIR: join(base, 'projects'),
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
