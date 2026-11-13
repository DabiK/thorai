import { readdirSync, readFileSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * The hexagonal rules of CLAUDE.md, for the bridge:
 * - the domain imports only the domain (no Node module, no library);
 * - an adapter imports the domain, Node and libraries, never another adapter;
 * - `src/main.ts` is the only composition root.
 * Test files are left out: a test may use a fake adapter.
 */
const SRC = resolve(import.meta.dirname, '../src');

function sources(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) return sources(path);
    return entry.name.endsWith('.ts') && !entry.name.endsWith('.test.ts') ? [path] : [];
  });
}

function importsOf(file: string): string[] {
  const text = readFileSync(file, 'utf8');
  return [...text.matchAll(/^\s*(?:import|export)[^'"]*from\s+['"]([^'"]+)['"]/gm)].map(
    (match) => match[1] ?? '',
  );
}

const area = (path: string) => relative(SRC, path).split('/').slice(0, 2).join('/');

describe('architecture of the bridge', () => {
  const files = sources(SRC);

  it('keeps the domain pure', () => {
    const violations = files
      .filter((file) => area(file).startsWith('domain'))
      .flatMap((file) =>
        importsOf(file)
          .filter((spec) => !spec.startsWith('./'))
          .map((spec) => `${relative(SRC, file)} -> ${spec}`),
      );
    expect(violations).toEqual([]);
  });

  it('keeps each adapter away from the others', () => {
    const violations = files
      .filter((file) => area(file).startsWith('adapters/'))
      .flatMap((file) =>
        importsOf(file)
          .filter((spec) => spec.startsWith('.'))
          .map((spec) => resolve(dirname(file), spec))
          .filter((target) => {
            const targetArea = area(target);
            return targetArea.startsWith('adapters/') && targetArea !== area(file);
          })
          .map((target) => `${relative(SRC, file)} -> ${relative(SRC, target)}`),
      );
    expect(violations).toEqual([]);
  });
});
