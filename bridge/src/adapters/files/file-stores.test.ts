import {
  appendFileSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  statSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { EventJournal } from '../../domain/event-journal.ts';
import type { SessionRecord } from '../../domain/model.ts';
import { FakeClock } from '../fake/memory.ts';
import { FileEventStore, FileSessionStore } from './file-stores.ts';

let dir: string;
beforeEach(() => {
  dir = mkdtempSync(join(tmpdir(), 'regie-bridge-'));
});
afterEach(() => {
  rmSync(dir, { recursive: true, force: true });
});

describe('FileEventStore', () => {
  it('goes on numbering after a restart, without gap or duplicate', () => {
    const first = new EventJournal(new FileEventStore(dir), new FakeClock());
    first.append({ sessionId: null, type: 'bridge', data: { state: 'started' } });
    first.append({ sessionId: 's1', type: 'user_message', data: { text: 'a' } });

    const second = new EventJournal(new FileEventStore(dir), new FakeClock());
    second.append({ sessionId: 's1', type: 'user_message', data: { text: 'b' } });

    expect(second.since(0).map((e) => e.seq)).toEqual([1, 2, 3]);
    expect(second.since(1).map((e) => e.data)).toEqual([{ text: 'a' }, { text: 'b' }]);
  });

  it('drops a last line cut by a crash, and numbers the next event after the last whole one', () => {
    const journal = new EventJournal(new FileEventStore(dir), new FakeClock());
    journal.append({ sessionId: null, type: 'bridge', data: {} });
    appendFileSync(join(dir, 'events.jsonl'), '{"seq":2,"at":"2026-11');

    const restarted = new EventJournal(new FileEventStore(dir), new FakeClock());
    const next = restarted.append({ sessionId: null, type: 'bridge', data: {} });

    expect(next.seq).toBe(2);
    // The journal name, then the two whole events.
    expect(readFileSync(join(dir, 'events.jsonl'), 'utf8').trim().split('\n')).toHaveLength(3);
  });

  it('names the journal, keeps its name across restarts, and gives a new journal a new one', () => {
    const ids = ['journal-a', 'journal-b'];
    const next = () => ids.shift() ?? 'none';
    expect(new FileEventStore(dir, next).journalId).toBe('journal-a');
    expect(new FileEventStore(dir, next).journalId).toBe('journal-a');

    rmSync(join(dir, 'events.jsonl'));
    expect(new FileEventStore(dir, next).journalId).toBe('journal-b');
  });

  it('gives a name to a journal written without one, and keeps its events', () => {
    writeFileSync(
      join(dir, 'events.jsonl'),
      '{"seq":1,"at":"x","sessionId":null,"type":"bridge","data":{}}\n',
    );

    const store = new FileEventStore(dir, () => 'journal-c');

    expect(store.journalId).toBe('journal-c');
    expect(store.readAll().map((e) => e.seq)).toEqual([1]);
    expect(new FileEventStore(dir, () => 'other').journalId).toBe('journal-c');
  });

  it('keeps the file private', () => {
    new FileEventStore(dir).append({ seq: 1, at: '', sessionId: null, type: 'bridge', data: {} });

    expect(statSync(join(dir, 'events.jsonl')).mode & 0o777).toBe(0o600);
  });
});

describe('FileSessionStore', () => {
  it('keeps the sessions across a restart', () => {
    const record: SessionRecord = {
      sessionId: 's1',
      agent: 'codex',
      project: 'regie-demo',
      cwd: '/home/dev/projects/regie-demo',
      branch: 'main',
      approvalMode: 'ask',
      status: 'idle',
      title: 'Résumé',
      createdAt: '2026-11-13T20:00:00.000Z',
      updatedAt: '2026-11-13T20:00:01.000Z',
      lastError: null,
    };
    new FileSessionStore(dir).saveAll([record]);

    expect(new FileSessionStore(dir).readAll()).toEqual([record]);
  });

  it('starts empty', () => {
    expect(new FileSessionStore(dir).readAll()).toEqual([]);
  });
});
