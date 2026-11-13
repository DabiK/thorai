import { execFileSync } from 'node:child_process';
import { linkSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

/** What the lock records of its owner. */
interface Owner {
  readonly pid: number;
  /** `/proc/sys/kernel/random/boot_id`: a lock of an earlier boot is stale. */
  readonly boot: string | null;
  /** The main script of the bridge: a pid given since to another process is not a bridge. */
  readonly script: string;
}

export interface LockSystem {
  readonly pid: number;
  readonly script: string;
  bootId(): string | null;
  /** The command line of a process, `null` when it is gone or cannot be read. */
  commandLine(pid: number): string | null;
  /** Whether the process exists and is ours to signal. `EPERM` (another user) is not ours. */
  signalable(pid: number): boolean;
}

/**
 * One bridge per state folder: two bridges on the same journal would write the same sequence
 * numbers. The lock is `bridge.lock`, taken before the journal is read.
 *
 * The lock is held only if its owner is still a bridge of this boot: same boot id, a process
 * we can signal, whose command line runs the bridge's main script. A pid reused after a
 * restart of the console, or after Android killed the bridge, does not hold it.
 *
 * Returns the release function. Throws when another live bridge holds the lock.
 */
export function lockStateDir(stateDir: string, system: LockSystem = realSystem()): () => void {
  mkdirSync(stateDir, { recursive: true, mode: 0o700 });
  const lock = join(stateDir, 'bridge.lock');
  const mine = join(stateDir, `bridge.lock.${system.pid}`);
  const me: Owner = { pid: system.pid, boot: system.bootId(), script: system.script };
  writeFileSync(mine, `${JSON.stringify(me)}\n`, { mode: 0o600 });
  try {
    for (let attempt = 0; attempt < 2; attempt += 1) {
      try {
        // link() is atomic: only one process creates the lock.
        linkSync(mine, lock);
        return () => {
          if (ownerOf(lock)?.pid === system.pid) rmSync(lock, { force: true });
        };
      } catch (error) {
        if ((error as NodeJS.ErrnoException).code !== 'EEXIST') throw error;
      }
      const owner = ownerOf(lock);
      if (owner !== null && owner.pid !== system.pid && isLiveBridge(owner, system)) {
        throw new Error(`another bridge (pid ${owner.pid}) uses ${stateDir}`);
      }
      rmSync(lock, { force: true });
    }
    throw new Error(`cannot lock ${stateDir}`);
  } finally {
    rmSync(mine, { force: true });
  }
}

function isLiveBridge(owner: Owner, system: LockSystem): boolean {
  const boot = system.bootId();
  if (owner.boot !== null && boot !== null && owner.boot !== boot) return false;
  if (!system.signalable(owner.pid)) return false;
  return system.commandLine(owner.pid)?.includes(owner.script) ?? false;
}

function ownerOf(lock: string): Owner | null {
  try {
    const owner = JSON.parse(readFileSync(lock, 'utf8')) as Partial<Owner>;
    if (!Number.isInteger(owner.pid) || typeof owner.script !== 'string') return null;
    return { pid: owner.pid as number, boot: owner.boot ?? null, script: owner.script };
  } catch {
    // An unreadable lock, or one in an older format: not held.
    return null;
  }
}

function realSystem(): LockSystem {
  return {
    pid: process.pid,
    script: resolve(process.argv[1] ?? ''),
    bootId: () => {
      try {
        return readFileSync('/proc/sys/kernel/random/boot_id', 'utf8').trim() || null;
      } catch {
        return null;
      }
    },
    commandLine: (pid) => {
      try {
        return readFileSync(`/proc/${pid}/cmdline`, 'utf8').replaceAll('\0', ' ');
      } catch {
        // No /proc (macOS, for the tests): ask ps.
        try {
          return execFileSync('ps', ['-p', String(pid), '-o', 'command='], {
            encoding: 'utf8',
          }).trim();
        } catch {
          return null;
        }
      }
    },
    signalable: (pid) => {
      try {
        process.kill(pid, 0);
        return true;
      } catch {
        return false;
      }
    },
  };
}
