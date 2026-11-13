import { parse } from 'shell-quote';
import type { ShellParser, ShellToken } from '../../domain/ports.ts';

/**
 * Splits a command line with `shell-quote` 1.12 (docs/adr/02, ticket 13). It runs nothing.
 *
 * `shell-quote` is lenient: it replaces a variable by an empty word, keeps backticks inside
 * words, joins lines and reads everything after `#` as a comment. The domain checks the raw
 * command line for all of these before trusting the words. Here, a variable still comes out as
 * `other`, a comment too, and a line `shell-quote` cannot read (`${ x }`) is a single `other`:
 * in doubt, the command goes to the user.
 */
export class ShellQuoteParser implements ShellParser {
  parse(script: string): readonly ShellToken[] {
    let expanded = false;
    let entries: ReturnType<typeof parse>;
    try {
      entries = parse(script, () => {
        expanded = true;
        return '';
      });
    } catch {
      return [{ kind: 'other' }];
    }
    const tokens: ShellToken[] = entries.map((entry): ShellToken => {
      if (typeof entry === 'string') return { kind: 'word', value: entry };
      // An object an `env` function returned, or a comment: never trusted.
      if (!('op' in entry)) return { kind: 'other' };
      if ('pattern' in entry) return { kind: 'glob', value: entry.pattern };
      return { kind: 'operator', value: entry.op };
    });
    return expanded ? [...tokens, { kind: 'other' }] : tokens;
  }
}
