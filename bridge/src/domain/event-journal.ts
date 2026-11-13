import type { BridgeEvent, NewEvent } from './model.ts';
import type { Clock, EventStore } from './ports.ts';

export type EventListener = (event: BridgeEvent) => void;

/**
 * Numbers the events, stores them, then hands them to the listeners.
 *
 * The sequence number is global and monotonic, and it goes on from the stored events after a
 * restart: a client that kept its last number gets everything after it, without duplicate. An
 * event is stored before any listener sees it.
 *
 * Old events can be purged (ticket 13): the numbers then have gaps, never go back, and a client
 * catching up from an old number gets what is left.
 */
export class EventJournal {
  private events: BridgeEvent[];
  private last: number;
  private readonly listeners = new Set<EventListener>();
  private readonly store: EventStore;
  private readonly clock: Clock;

  constructor(store: EventStore, clock: Clock) {
    this.store = store;
    this.clock = clock;
    this.events = [...store.readAll()];
    this.last = Math.max(store.lastSeq(), this.events.at(-1)?.seq ?? 0);
  }

  get journalId(): string {
    return this.store.journalId;
  }

  get lastSeq(): number {
    return this.last;
  }

  append(event: NewEvent): BridgeEvent {
    const recorded: BridgeEvent = {
      seq: this.last + 1,
      at: this.clock.now().toISOString(),
      sessionId: event.sessionId,
      type: event.type,
      data: event.data,
    };
    this.store.append(recorded);
    this.last = recorded.seq;
    this.events.push(recorded);
    for (const listener of this.listeners) listener(recorded);
    return recorded;
  }

  /** The events after `seq`, oldest first. */
  since(seq: number): readonly BridgeEvent[] {
    return this.events.slice(this.indexAfter(seq));
  }

  forSession(sessionId: string, since = 0): readonly BridgeEvent[] {
    return this.since(since).filter((event) => event.sessionId === sessionId);
  }

  /**
   * Hands the events after `seq` to `listener`, then every new event, in order. Nothing is
   * appended in between: the catch-up and the live stream join without gap.
   */
  subscribe(seq: number, listener: EventListener): () => void {
    for (const event of this.since(seq)) listener(event);
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  /**
   * Removes the events `expired` picks, from the disk and from memory. The others keep their
   * numbers. Returns the removed events.
   */
  remove(expired: (event: BridgeEvent) => boolean): readonly BridgeEvent[] {
    const removed: BridgeEvent[] = [];
    const kept: BridgeEvent[] = [];
    for (const event of this.events) (expired(event) ? removed : kept).push(event);
    if (removed.length === 0) return removed;
    this.store.replaceAll(kept, this.last);
    this.events = kept;
    return removed;
  }

  /** Index of the first event numbered after `seq`. Numbers grow, with gaps after a purge. */
  private indexAfter(seq: number): number {
    let low = 0;
    let high = this.events.length;
    while (low < high) {
      const middle = (low + high) >>> 1;
      if ((this.events[middle]?.seq ?? 0) <= seq) low = middle + 1;
      else high = middle;
    }
    return low;
  }
}
