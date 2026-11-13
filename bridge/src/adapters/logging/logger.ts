import type { Logger } from '../../domain/ports.ts';
import { redact } from '../../domain/redact.ts';

/** Writes timestamped lines on stderr; the start script sends them to the log file. */
export class StderrLogger implements Logger {
  info(message: string): void {
    this.write('info', message);
  }

  warn(message: string): void {
    this.write('warn', message);
  }

  error(message: string): void {
    this.write('error', message);
  }

  private write(level: string, message: string): void {
    process.stderr.write(`${new Date().toISOString()} ${level} ${redact(message)}\n`);
  }
}
