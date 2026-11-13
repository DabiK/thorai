import { describe, expect, it } from 'vitest';
import { MemoryLogger } from '../fake/memory.ts';
import { DAY_MS, type Repeat, runNowThenDaily } from './daily.ts';

class FakeRepeat {
  task: (() => void) | null = null;
  everyMs: number | null = null;
  unrefs = 0;

  readonly repeat: Repeat = (task, everyMs) => {
    this.task = task;
    this.everyMs = everyMs;
    return {
      unref: () => {
        this.unrefs += 1;
      },
    };
  };

  /** One day goes by. */
  tick(): void {
    this.task?.();
  }
}

describe('runNowThenDaily', () => {
  it('runs the task at once, then once a day, on a timer that does not keep the process alive', () => {
    const timers = new FakeRepeat();
    let runs = 0;

    runNowThenDaily('purge', () => runs++, new MemoryLogger(), timers.repeat);
    expect(runs).toBe(1);
    expect(timers.everyMs).toBe(DAY_MS);
    expect(timers.unrefs).toBe(1);

    timers.tick();
    timers.tick();
    expect(runs).toBe(3);
  });

  it('logs a failed run and goes on with the next ones', () => {
    const timers = new FakeRepeat();
    const logger = new MemoryLogger();
    let runs = 0;

    runNowThenDaily(
      'purge',
      () => {
        runs += 1;
        if (runs === 1) throw new Error('disque plein');
      },
      logger,
      timers.repeat,
    );
    timers.tick();

    expect(runs).toBe(2);
    expect(logger.lines).toEqual([expect.stringContaining('purge failed: disque plein')]);
  });

  it('does not keep Node running with its real timer', () => {
    const timer = runNowThenDaily('purge', () => {}, new MemoryLogger()) as NodeJS.Timeout;

    expect(timer.hasRef()).toBe(false);
    clearInterval(timer);
  });
});
