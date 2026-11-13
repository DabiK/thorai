import type { Logger } from '../../domain/ports.ts';

export const DAY_MS = 24 * 60 * 60 * 1000;

/** `setInterval` of Node: its timer can be told not to keep the process alive. */
export type Repeat = (task: () => void, everyMs: number) => { unref(): unknown };

/**
 * Runs `task` now, then once a day (ticket 13: the purge of old events). The timer does not keep
 * the process alive (`unref`), and a failed run is logged without stopping the next ones.
 * Returns the timer, for `clearInterval`.
 */
export function runNowThenDaily(
  name: string,
  task: () => void,
  logger: Logger,
  repeat: Repeat = setInterval,
): { unref(): unknown } {
  const run = () => {
    try {
      task();
    } catch (error) {
      logger.error(`${name} failed: ${error instanceof Error ? error.message : String(error)}`);
    }
  };
  run();
  const timer = repeat(run, DAY_MS);
  timer.unref();
  return timer;
}
