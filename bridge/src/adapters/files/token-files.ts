import { lstatSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import type { Logger } from '../../domain/ports.ts';

const MIN_LENGTH = 32;
/** An Android package name: `app.regie.jeu`. Files being written start with a dot. */
const CLIENT = /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/;

/**
 * The tokens the bridge accepts (docs/protocole-pont.md, « Le jeton », ADR 22):
 * - the historical file, written for an app that names no client;
 * - one file per app in `dir`, named by its package.
 *
 * Read again at each connection: an app adds or replaces its token without restarting the
 * bridge, so the other apps keep their sessions. A file readable by others, too short or not a
 * regular file is refused, never accepted. The logs name the file, never the token, and only
 * once per file and per problem: a refused file does not fill the log at each connection.
 */
export class TokenFiles {
  private readonly file: string;
  private readonly dir: string;
  private readonly logger: Logger;
  /** The problem last reported for each path, until it changes or goes away. */
  private readonly reported = new Map<string, string>();
  private current = new Set<string>();

  constructor(file: string, dir: string, logger: Logger) {
    this.file = file;
    this.dir = dir;
    this.logger = logger;
  }

  read(): string[] {
    this.current = new Set();
    const tokens: string[] = [];
    const legacy = this.legacy();
    if (legacy !== null) tokens.push(legacy);
    for (const name of this.clients()) {
      const token = this.client(name);
      if (token !== null) tokens.push(token);
    }
    // A path repaired or removed is forgotten: if its problem comes back, it is reported again.
    for (const path of this.reported.keys()) {
      if (!this.current.has(path)) this.reported.delete(path);
    }
    return tokens;
  }

  private refuse(path: string, problem: string): null {
    this.current.add(path);
    if (this.reported.get(path) !== problem) {
      this.reported.set(path, problem);
      this.logger.warn(`tokens: ${path} ${problem}`);
    }
    return null;
  }

  private legacy(): string | null {
    let mode: number;
    try {
      // Followed links, as before ticket 50: the historical file keeps its rules.
      mode = statSync(this.file).mode;
    } catch {
      return null;
    }
    return this.accept(this.file, mode);
  }

  private clients(): string[] {
    try {
      return readdirSync(this.dir).filter((name) => CLIENT.test(name));
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== 'ENOENT') {
        this.refuse(this.dir, `unreadable (${(error as Error).message})`);
      }
      return [];
    }
  }

  private client(name: string): string | null {
    const path = join(this.dir, name);
    try {
      const stats = lstatSync(path);
      if (!stats.isFile()) {
        return this.refuse(path, 'refused, not a regular file');
      }
      return this.accept(path, stats.mode);
    } catch {
      return null;
    }
  }

  private accept(path: string, mode: number): string | null {
    if ((mode & 0o077) !== 0) {
      return this.refuse(path, 'refused, readable by others: chmod 600 it');
    }
    let token: string;
    try {
      token = readFileSync(path, 'utf8').trim();
    } catch (error) {
      return this.refuse(path, `unreadable (${(error as Error).message})`);
    }
    if (token.length < MIN_LENGTH) {
      return this.refuse(path, 'refused, token too short');
    }
    return token;
  }
}
