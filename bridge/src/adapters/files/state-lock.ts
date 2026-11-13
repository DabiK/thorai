import { linkSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * One bridge per state folder: two bridges on the same journal would write the same sequence
 * numbers. The lock is `bridge.lock`, holding the pid of its owner. It is taken before the
 * journal is read; a lock whose process is gone is taken over.
 *
 * Returns the release function. Throws when another live bridge holds the lock.
 */
export function lockStateDir(stateDir: string, pid = process.pid): () => void {
  mkdirSync(stateDir, { recursive: true, mode: 0o700 });
  const lock = join(stateDir, 'bridge.lock');
  const mine = join(stateDir, `bridge.lock.${pid}`);
  writeFileSync(mine, `${pid}\n`, { mode: 0o600 });
  try {
    for (let attempt = 0; attempt < 2; attempt += 1) {
      try {
        // link() is atomic: only one process creates the lock.
        linkSync(mine, lock);
        return () => {
          if (ownerOf(lock) === pid) rmSync(lock, { force: true });
        };
      } catch (error) {
        if ((error as NodeJS.ErrnoException).code !== 'EEXIST') throw error;
      }
      const owner = ownerOf(lock);
      if (owner !== null && owner !== pid && isAlive(owner)) {
        throw new Error(`another bridge (pid ${owner}) uses ${stateDir}`);
      }
      rmSync(lock, { force: true });
    }
    throw new Error(`cannot lock ${stateDir}`);
  } finally {
    rmSync(mine, { force: true });
  }
}

function ownerOf(lock: string): number | null {
  try {
    const pid = Number.parseInt(readFileSync(lock, 'utf8'), 10);
    return Number.isInteger(pid) ? pid : null;
  } catch {
    return null;
  }
}

function isAlive(pid: number): boolean {
  try {
    process.kill(pid, 0);
    return true;
  } catch (error) {
    return (error as NodeJS.ErrnoException).code === 'EPERM';
  }
}
