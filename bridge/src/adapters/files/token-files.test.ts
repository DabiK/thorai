import { chmodSync, mkdirSync, mkdtempSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { MemoryLogger } from '../fake/memory.ts';
import { TokenFiles } from './token-files.ts';

const CLASSIC = 'a'.repeat(64);
const GAME = 'b'.repeat(64);
const OTHER = 'c'.repeat(64);

let base: string;
let logger: MemoryLogger;
let files: TokenFiles;

beforeEach(() => {
  base = mkdtempSync(join(tmpdir(), 'regie-tokens-'));
  logger = new MemoryLogger();
  files = new TokenFiles(join(base, 'bridge-token'), join(base, 'bridge-tokens'), logger);
});
afterEach(() => {
  rmSync(base, { recursive: true, force: true });
});

const legacy = (token: string, mode = 0o600) => {
  writeFileSync(join(base, 'bridge-token'), `${token}\n`, { mode });
  chmodSync(join(base, 'bridge-token'), mode);
};
const client = (name: string, token: string, mode = 0o600) => {
  mkdirSync(join(base, 'bridge-tokens'), { recursive: true, mode: 0o700 });
  writeFileSync(join(base, 'bridge-tokens', name), token, { mode });
  chmodSync(join(base, 'bridge-tokens', name), mode);
};
const warnings = () => logger.lines.filter((line) => line.startsWith('warn'));

describe('the tokens the bridge accepts', () => {
  it('keeps the historical single file of the classic app', () => {
    legacy(CLASSIC);

    expect(files.read()).toEqual([CLASSIC]);
  });

  it('adds one token per app, named by its package', () => {
    legacy(CLASSIC);
    client('app.regie.jeu', GAME);
    client('app.thorai.jeu', OTHER);

    expect(files.read().sort()).toEqual([CLASSIC, GAME, OTHER]);
  });

  it('needs no historical file when an app has its own', () => {
    client('app.regie.jeu', GAME);

    expect(files.read()).toEqual([GAME]);
    expect(warnings()).toEqual([]);
  });

  it('is read again each time: an app joins without restarting the bridge', () => {
    legacy(CLASSIC);
    expect(files.read()).toEqual([CLASSIC]);

    client('app.regie.jeu', GAME);

    expect(files.read().sort()).toEqual([CLASSIC, GAME]);
  });

  it('refuses a file readable by others, and names the file, never the token', () => {
    legacy(CLASSIC, 0o644);
    client('app.regie.jeu', GAME, 0o640);

    expect(files.read()).toEqual([]);
    expect(warnings()).toHaveLength(2);
    expect(warnings().join('\n')).toContain('bridge-token');
    expect(warnings().join('\n')).toContain('app.regie.jeu');
    expect(logger.lines.join('\n')).not.toContain(CLASSIC);
    expect(logger.lines.join('\n')).not.toContain(GAME);
  });

  it('refuses a token too short', () => {
    client('app.regie.jeu', 'short');

    expect(files.read()).toEqual([]);
    expect(warnings().join('\n')).toContain('too short');
  });

  it('ignores files being written and names that are not a package', () => {
    client('.app.regie.jeu.Xa81kQ', GAME);
    client('app.regie.jeu~', GAME);
    client('notes', GAME);

    expect(files.read()).toEqual([]);
  });

  it('refuses a link, even to a private file', () => {
    legacy(CLASSIC);
    mkdirSync(join(base, 'bridge-tokens'), { mode: 0o700 });
    symlinkSync(join(base, 'bridge-token'), join(base, 'bridge-tokens', 'app.regie.jeu'));

    expect(files.read()).toEqual([CLASSIC]);
    expect(warnings().join('\n')).toContain('app.regie.jeu');
  });
});
