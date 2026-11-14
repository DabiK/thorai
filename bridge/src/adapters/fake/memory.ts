import { BridgeError, type BridgeEvent, type SessionRecord } from '../../domain/model.ts';
import type {
  Clock,
  EventStore,
  IdGenerator,
  Logger,
  SessionStore,
  ShellParser,
  ShellToken,
  Workspace,
} from '../../domain/ports.ts';

/** Events kept in memory. Share one instance between two bridges to simulate a restart. */
export class MemoryEventStore implements EventStore {
  events: BridgeEvent[] = [];
  readonly journalId: string;
  private last = 0;

  constructor(journalId = 'memory-journal') {
    this.journalId = journalId;
  }

  readAll(): readonly BridgeEvent[] {
    return [...this.events];
  }

  lastSeq(): number {
    return this.last;
  }

  append(event: BridgeEvent): void {
    this.events.push(event);
    this.last = Math.max(this.last, event.seq);
  }

  replaceAll(events: readonly BridgeEvent[], lastSeq: number): void {
    this.events = [...events];
    this.last = lastSeq;
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

  /** Moves the clock forward, by days. */
  advanceDays(days: number): void {
    this.time += days * 24 * 60 * 60 * 1000;
  }
}

export function counterIds(prefix = 'id'): IdGenerator {
  let next = 1;
  return () => `${prefix}-${next++}`;
}

/** Projects and branches in memory. A test moves a branch as an agent would. */
export class FakeWorkspace implements Workspace {
  readonly terms = new Map<string, readonly string[]>();
  async vocabulary(cwd: string): Promise<readonly string[]> {
    return this.terms.get(cwd) ?? [];
  }
  readonly branches = new Map<string, string | null>([['regie-demo', 'main']]);
  /** Folders whose branch was read. */
  readonly reads: string[] = [];

  async resolveProject(project: string) {
    if (!this.branches.has(project)) {
      throw new BridgeError('bad_project', `Projet introuvable : ${project}.`);
    }
    return { name: project, cwd: `/home/dev/projects/${project}` };
  }

  /** Roughly `git check-ref-format --branch`. */
  async isValidBranchName(branch: string): Promise<boolean> {
    return /^(?!-)(?!.*\.\.)(?!.*\/$)[A-Za-z0-9._/-]+$/.test(branch);
  }

  async currentBranch(cwd: string): Promise<string | null> {
    this.reads.push(cwd);
    return this.branches.get(cwd.split('/').at(-1) ?? cwd) ?? null;
  }

  async listProjects() {
    return [...this.branches].map(([name, branch]) => ({ name, branch }));
  }
}

/** Splits on blanks; `&&`, `||`, `;` and `|` alone are operators. No quote, no glob. */
export class WordsShellParser implements ShellParser {
  parse(script: string): readonly ShellToken[] {
    return script
      .split(/\s+/)
      .filter((word) => word !== '')
      .map((word) =>
        ['&&', '||', ';', '|'].includes(word)
          ? { kind: 'operator', value: word }
          : { kind: 'word', value: word },
      );
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
