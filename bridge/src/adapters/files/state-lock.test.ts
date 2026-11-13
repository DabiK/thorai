import { existsSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { lockStateDir } from './state-lock.ts';

let dir: string;
beforeEach(() => {
  dir = mkdtempSync(join(tmpdir(), 'regie-lock-'));
});
afterEach(() => {
  rmSync(dir, { recursive: true, force: true });
});

describe('lockStateDir', () => {
  it('refuses a second bridge while the first one lives', () => {
    const release = lockStateDir(dir);

    // process.ppid: a pid that is alive and is not ours.
    expect(() => lockStateDir(dir, process.ppid)).toThrow(/another bridge/);
    release();
    expect(existsSync(join(dir, 'bridge.lock'))).toBe(false);
    lockStateDir(dir, process.ppid)();
  });

  it('takes over the lock of a bridge that is gone', () => {
    writeFileSync(join(dir, 'bridge.lock'), '999999999\n');

    const release = lockStateDir(dir);

    expect(() => lockStateDir(dir, process.ppid)).toThrow(/another bridge/);
    release();
  });
});
