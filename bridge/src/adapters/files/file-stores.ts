import {
  appendFileSync,
  closeSync,
  existsSync,
  fsyncSync,
  mkdirSync,
  openSync,
  readFileSync,
  renameSync,
  truncateSync,
  writeSync,
} from 'node:fs';
import { dirname, join } from 'node:path';
import type { BridgeEvent, SessionRecord } from '../../domain/model.ts';
import type { EventStore, SessionStore } from '../../domain/ports.ts';

/**
 * The events, one JSON line each, in `events.jsonl` of the state folder.
 *
 * Each append is written synchronously before the event goes out: an event a client received
 * is on disk. A line cut by a crash is dropped at the next start (it was never sent).
 */
export class FileEventStore implements EventStore {
  private readonly path: string;

  constructor(stateDir: string) {
    mkdirSync(stateDir, { recursive: true, mode: 0o700 });
    this.path = join(stateDir, 'events.jsonl');
  }

  readAll(): readonly BridgeEvent[] {
    if (!existsSync(this.path)) return [];
    let text = readFileSync(this.path, 'utf8');
    if (text !== '' && !text.endsWith('\n')) {
      // The last line was cut by a crash: it was never sent, it is dropped.
      text = text.slice(0, text.lastIndexOf('\n') + 1);
      truncateSync(this.path, Buffer.byteLength(text, 'utf8'));
    }
    const events: BridgeEvent[] = [];
    for (const line of text.split('\n')) {
      const event = line.trim() === '' ? null : parse(line);
      if (event !== null) events.push(event);
    }
    return events;
  }

  append(event: BridgeEvent): void {
    appendFileSync(this.path, `${JSON.stringify(event)}\n`, { mode: 0o600 });
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
