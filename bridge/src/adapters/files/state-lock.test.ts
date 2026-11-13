import { existsSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { type LockSystem, lockStateDir } from './state-lock.ts';

const SCRIPT = '/home/dev/.local/share/regie/bridge/src/main.ts';

let dir: string;
beforeEach(() => {
  dir = mkdtempSync(join(tmpdir(), 'regie-lock-'));
});
afterEach(() => {
  rmSync(dir, { recursive: true, force: true });
});

/** A console where `processes` run, by pid and command line. */
function system(pid: number, processes: Record<number, string>, boot = 'boot-1'): LockSystem {
  return {
    pid,
    script: SCRIPT,
    bootId: () => boot,
    commandLine: (other) => processes[other] ?? null,
    signalable: (other) => other in processes,
  };
}

const lockedBy = (owner: object) =>
  writeFileSync(join(dir, 'bridge.lock'), `${JSON.stringify(owner)}\n`);

describe('lockStateDir', () => {
  it('refuses a second bridge while the first one lives, and frees the lock on release', () => {
    const processes = { 10: `node ${SCRIPT}`, 20: `node ${SCRIPT}` };
    const release = lockStateDir(dir, system(10, processes));

    expect(() => lockStateDir(dir, system(20, processes))).toThrow(/another bridge \(pid 10\)/);
    release();
    expect(existsSync(join(dir, 'bridge.lock'))).toBe(false);
    lockStateDir(dir, system(20, processes))();
  });

  it('takes over the lock of a bridge that is gone', () => {
    lockedBy({ pid: 10, boot: 'boot-1', script: SCRIPT });

    expect(() => lockStateDir(dir, system(20, { 20: `node ${SCRIPT}` }))).not.toThrow();
  });

  it('takes over a lock whose pid now runs another program', () => {
    lockedBy({ pid: 1, boot: 'boot-1', script: SCRIPT });

    expect(() => lockStateDir(dir, system(20, { 1: 'init', 20: `node ${SCRIPT}` }))).not.toThrow();
  });

  it('takes over a lock of an earlier boot, even if its pid runs a bridge again', () => {
    lockedBy({ pid: 10, boot: 'boot-0', script: SCRIPT });

    expect(() =>
      lockStateDir(dir, system(20, { 10: `node ${SCRIPT}`, 20: `node ${SCRIPT}` })),
    ).not.toThrow();
  });

  it('takes over a lock whose owner cannot be signalled (another user)', () => {
    lockedBy({ pid: 10, boot: 'boot-1', script: SCRIPT });
    const other: LockSystem = { ...system(20, { 10: `node ${SCRIPT}` }), signalable: () => false };

    expect(() => lockStateDir(dir, other)).not.toThrow();
  });

  it('takes over a lock it cannot read, or in the older format', () => {
    writeFileSync(join(dir, 'bridge.lock'), '1\n');

    expect(() => lockStateDir(dir, system(20, { 1: `node ${SCRIPT}` }))).not.toThrow();
  });
});
