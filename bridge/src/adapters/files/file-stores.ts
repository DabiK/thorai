import { randomUUID } from 'node:crypto';
import {
  closeSync,
  existsSync,
  fdatasyncSync,
  fsyncSync,
  mkdirSync,
  openSync,
  readFileSync,
  readSync,
  renameSync,
  statSync,
  truncateSync,
  writeSync,
} from 'node:fs';
import { dirname, join } from 'node:path';
import type { BridgeEvent, SessionRecord } from '../../domain/model.ts';
import type { EventStore, SessionStore } from '../../domain/ports.ts';

/**
 * The events, one JSON line each, in `events.jsonl` of the state folder.
 *
 * - The first line names the journal: `{"journal": "<id>", "version": 1}`. A new file gets a
 *   new id.
 * - Each append is written and synced to the disk (`fdatasync`) before the event goes out: an
 *   event a client received survives a crash of the bridge or of the console.
 * - A line cut by a crash is dropped at the next start: it was never sent.
 */
export class FileEventStore implements EventStore {
  readonly journalId: string;
  private readonly path: string;
  private readonly events: readonly BridgeEvent[];
  private readonly fd: number;

  constructor(stateDir: string, newId: () => string = randomUUID) {
    mkdirSync(stateDir, { recursive: true, mode: 0o700 });
    this.path = join(stateDir, 'events.jsonl');
    const { journalId, events } = this.load(newId);
    this.journalId = journalId;
    this.events = events;
    this.fd = openSync(this.path, 'a', 0o600);
  }

  readAll(): readonly BridgeEvent[] {
    return this.events;
  }

  append(event: BridgeEvent): void {
    writeSync(this.fd, `${JSON.stringify(event)}\n`);
    fdatasyncSync(this.fd);
  }

  close(): void {
    closeSync(this.fd);
  }

  private load(newId: () => string): { journalId: string; events: BridgeEvent[] } {
    let header: string | null = null;
    let first = true;
    const events: BridgeEvent[] = [];
    if (existsSync(this.path)) {
      const complete = readLines(this.path, (line) => {
        if (first) {
          first = false;
          header = parseHeader(line);
          if (header !== null) return;
        }
        const event = parse(line);
        if (event !== null) events.push(event);
      });
      // The last line was cut by a crash: it was never sent, it is dropped.
      if (complete < statSync(this.path).size) truncateSync(this.path, complete);
    }
    if (header !== null) return { journalId: header, events };
    // A new journal, or one written before the header existed: it gets its id.
    const journalId = newId();
    const temporary = `${this.path}.tmp`;
    const fd = openSync(temporary, 'w', 0o600);
    try {
      writeSync(fd, `${JSON.stringify({ journal: journalId, version: 1 })}\n`);
      for (const event of events) writeSync(fd, `${JSON.stringify(event)}\n`);
      fsyncSync(fd);
    } finally {
      closeSync(fd);
    }
    renameSync(temporary, this.path);
    return { journalId, events };
  }
}

/**
 * Reads a file line by line, by blocks: a big journal is never held in one string. Returns the
 * size in bytes of the whole lines, those ending with a newline.
 */
function readLines(path: string, onLine: (line: string) => void): number {
  const fd = openSync(path, 'r');
  const buffer = Buffer.alloc(1024 * 1024);
  let pending: Buffer[] = [];
  let complete = 0;
  let read = 0;
  try {
    for (;;) {
      const size = readSync(fd, buffer, 0, buffer.length, read);
      if (size === 0) break;
      let start = 0;
      for (let i = 0; i < size; i += 1) {
        if (buffer[i] !== 0x0a) continue;
        pending.push(buffer.subarray(start, i));
        const line = Buffer.concat(pending).toString('utf8');
        pending = [];
        if (line.trim() !== '') onLine(line);
        start = i + 1;
        complete = read + start;
      }
      pending.push(Buffer.from(buffer.subarray(start, size)));
      read += size;
    }
  } finally {
    closeSync(fd);
  }
  return complete;
}

function parseHeader(line: string): string | null {
  try {
    const header = JSON.parse(line) as { journal?: unknown };
    return typeof header.journal === 'string' ? header.journal : null;
  } catch {
    return null;
  }
}

function parse(line: string): BridgeEvent | null {
  try {
    const event = JSON.parse(line) as BridgeEvent;
    return Number.isInteger(event.seq) ? event : null;
  } catch {
    return null;
  }
}

/** The sessions, in `sessions.json`, replaced atomically at each change. */
export class FileSessionStore implements SessionStore {
  private readonly path: string;

  constructor(stateDir: string) {
    mkdirSync(stateDir, { recursive: true, mode: 0o700 });
    this.path = join(stateDir, 'sessions.json');
  }

  readAll(): readonly SessionRecord[] {
    if (!existsSync(this.path)) return [];
    const parsed = JSON.parse(readFileSync(this.path, 'utf8')) as { sessions?: SessionRecord[] };
    return parsed.sessions ?? [];
  }

  saveAll(records: readonly SessionRecord[]): void {
    writeAtomically(this.path, `${JSON.stringify({ version: 1, sessions: records }, null, 1)}\n`);
  }
}

export function writeAtomically(path: string, content: string): void {
  mkdirSync(dirname(path), { recursive: true });
  const temporary = `${path}.tmp`;
  const fd = openSync(temporary, 'w', 0o600);
  try {
    writeSync(fd, content);
    fsyncSync(fd);
  } finally {
    closeSync(fd);
  }
  renameSync(temporary, path);
}
