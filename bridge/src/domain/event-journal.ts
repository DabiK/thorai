import type { BridgeEvent, NewEvent } from './model.ts';
import type { Clock, EventStore } from './ports.ts';

export type EventListener = (event: BridgeEvent) => void;

/**
 * Numbers the events, stores them, then hands them to the listeners.
 *
 * The sequence number is global and monotonic, and it goes on from the stored events after a
 * restart: a client that kept its last number gets everything after it, without gap or
 * duplicate. An event is stored before any listener sees it.
 */
export class EventJournal {
  private readonly events: BridgeEvent[];
  private readonly listeners = new Set<EventListener>();
  private readonly store: EventStore;
  private readonly clock: Clock;

  constructor(store: EventStore, clock: Clock) {
    this.store = store;
    this.clock = clock;
    this.events = [...store.readAll()];
  }

  get journalId(): string {
    return this.store.journalId;
  }

  get lastSeq(): number {
    return this.events.at(-1)?.seq ?? 0;
  }

  append(event: NewEvent): BridgeEvent {
    const recorded: BridgeEvent = {
      seq: this.lastSeq + 1,
      at: this.clock.now().toISOString(),
      sessionId: event.sessionId,
      type: event.type,
      data: event.data,
    };
    this.store.append(recorded);
    this.events.push(recorded);
    for (const listener of this.listeners) listener(recorded);
    return recorded;
  }

  /** The events after `seq`, oldest first. */
  since(seq: number): readonly BridgeEvent[] {
    const start = this.indexAfter(seq);
    return this.events.slice(start);
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

  private indexAfter(seq: number): number {
    // Sequence numbers are contiguous from the first stored one: index arithmetic, no search.
    const first = this.events[0]?.seq ?? 1;
    return Math.min(Math.max(seq - first + 1, 0), this.events.length);
  }
}
