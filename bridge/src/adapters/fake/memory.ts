import { BridgeError, type BridgeEvent, type SessionRecord } from '../../domain/model.ts';
import type {
  Clock,
  EventStore,
  IdGenerator,
  Logger,
  SessionStore,
  Workspace,
} from '../../domain/ports.ts';

/** Events kept in memory. Share one instance between two bridges to simulate a restart. */
export class MemoryEventStore implements EventStore {
  readonly events: BridgeEvent[] = [];

  readAll(): readonly BridgeEvent[] {
    return [...this.events];
  }

  append(event: BridgeEvent): void {
    this.events.push(event);
  }
}

export class MemorySessionStore implements SessionStore {
  records: SessionRecord[] = [];

  readAll(): readonly SessionRecord[] {
    return [...this.records];
  }

  saveAll(records: readonly SessionRecord[]): void {
    this.records = [...records];
  }
}

/** A clock that moves forward one second at each reading. */
export class FakeClock implements Clock {
  private time: number;

  constructor(start = Date.parse('2026-11-13T20:00:00.000Z')) {
    this.time = start;
  }

  now(): Date {
    const now = new Date(this.time);
    this.time += 1000;
    return now;
  }
}

export function counterIds(prefix = 'id'): IdGenerator {
  let next = 1;
  return () => `${prefix}-${next++}`;
}

/** Projects and branches in memory. */
export class FakeWorkspace implements Workspace {
  readonly branches = new Map<string, string | null>([['regie-demo', 'main']]);
  readonly switches: Array<{ readonly cwd: string; readonly branch: string }> = [];
  /** Branches `switchBranch` refuses. */
  readonly refused = new Set<string>();

  async resolveProject(project: string) {
    if (!this.branches.has(project)) {
      throw new BridgeError('bad_project', `Projet introuvable : ${project}.`);
    }
    return { name: project, cwd: `/home/dev/projects/${project}` };
  }

  async switchBranch(cwd: string, branch: string): Promise<void> {
    if (this.refused.has(branch)) {
      throw new BridgeError('branch_failed', `Impossible de passer sur la branche ${branch}.`);
    }
    this.switches.push({ cwd, branch });
    this.branches.set(cwd.split('/').at(-1) ?? cwd, branch);
  }

  async currentBranch(cwd: string): Promise<string | null> {
    return this.branches.get(cwd.split('/').at(-1) ?? cwd) ?? null;
  }

  async listProjects() {
    return [...this.branches].map(([name, branch]) => ({ name, branch }));
  }
}

export class MemoryLogger implements Logger {
  readonly lines: string[] = [];

  info(message: string): void {
    this.lines.push(`info ${message}`);
  }

  warn(message: string): void {
    this.lines.push(`warn ${message}`);
  }

  error(message: string): void {
    this.lines.push(`error ${message}`);
  }
}
