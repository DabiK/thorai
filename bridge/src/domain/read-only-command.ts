import type { ShellParser, ShellToken } from './ports.ts';

/**
 * Which commands only read, so that the bridge approves them on its own in « valider »
 * (ticket 13, docs/adr/02). The rule is strict: in doubt, the command goes to the user.
 *
 * - Known read-only programs only (see `RULES`), each with the options that would make it write
 *   or run another program refused. An unknown program goes to the user.
 * - Commands may be chained by `&&`, `||`, `;`, `|` or a new line: each one must read only.
 * - Anything else the shell would interpret goes to the user: a redirection (`>`, `<`, `2>&1`),
 *   a substitution (`$(…)`, backticks, `<(…)`), a variable (`$X`), a background job (`&`), a
 *   subshell or a group (`(…)`, `{ …; }`), an assignment (`X=1 cmd`), a comment, a zsh `=cmd`
 *   expansion or glob qualifier (`*(…)`).
 * - A glob (`*`, `?`, `[…]`, a zsh `^…`) could expand to a file named like an option
 *   (`-[e]xec` is `-exec` when such a file exists): refused for the programs whose options can
 *   write or run something (`globs: false`).
 * - `bash -c script`, `bash -lc script`, `sh -c script`: the script is checked.
 */
export class ReadOnlyCommands {
  private readonly shell: ShellParser;

  constructor(shell: ShellParser) {
    this.shell = shell;
  }

  /**
   * Whether the tool call of an ACP permission request only runs a read-only command. Claude
   * (`Bash`) and Codex (exec) both send `kind: "execute"` and the command line in
   * `rawInput.command`. Anything else goes to the user: another kind (an edit, a plan, an MCP
   * tool), no command, or an input with more than the command (a network access, more
   * permissions asked by Codex).
   */
  isReadOnlyToolCall(toolCall: Readonly<Record<string, unknown>>): boolean {
    return this.readOnlyCommandOf(toolCall) !== null;
  }

  /**
   * The command judged read-only, as analysed (`rawInput.command`, its words joined when it is
   * an argv), or `null` when the request goes to the user. Only `rawInput.command` is judged:
   * `title` is never read, and `kind` can only send the request to the user.
   */
  readOnlyCommandOf(toolCall: Readonly<Record<string, unknown>>): string | null {
    if (toolCall.kind !== 'execute') return null;
    const input = toolCall.rawInput;
    if (typeof input !== 'object' || input === null || Array.isArray(input)) return null;
    if (!Object.keys(input).every((key) => COMMAND_INPUT_KEYS.has(key))) return null;
    const command = (input as Readonly<Record<string, unknown>>).command;
    if (!this.isReadOnly(command)) return null;
    return Array.isArray(command) ? command.join(' ') : String(command);
  }

  /** `command` is `rawInput.command` of an ACP tool call: a command line, or an argv. */
  isReadOnly(command: unknown): boolean {
    if (typeof command === 'string') return this.script(command, 0);
    if (!Array.isArray(command) || command.length === 0) return false;
    if (!command.every((word): word is string => typeof word === 'string')) return false;
    // An argv runs without a shell: its words are taken as they are.
    return this.command(
      command.map((value) => ({ kind: 'word', value })),
      0,
    );
  }

  private script(script: string, depth: number): boolean {
    if (depth > MAX_DEPTH) return false;
    const checked = plainScript(script);
    if (checked === null) return false;
    const { plain, globbing } = checked;
    const segments: ShellToken[][] = [[]];
    for (const token of this.shell.parse(plain)) {
      if (token.kind === 'other') return false;
      if (token.kind !== 'operator') {
        segments.at(-1)?.push(token);
        continue;
      }
      if (!CHAINS.has(token.value)) return false;
      segments.push([]);
    }
    // `cat a;` is fine; `cat a &&` or `; cat a` are not.
    if (segments.length > 1 && segments.at(-1)?.length === 0 && plain.trimEnd().endsWith(';')) {
      segments.pop();
    }
    return segments.every((segment) => this.command(segment, depth, globbing));
  }

  /**
   * `globbing`: the command line has a glob `shell-quote` does not mark (`[…]`, `^…`). Taken
   * for the whole line: in doubt, the command goes to the user.
   */
  private command(tokens: readonly ShellToken[], depth: number, globbing = false): boolean {
    const [first, ...rest] = tokens;
    if (first?.kind !== 'word' || ASSIGNMENT.test(first.value)) return false;
    const args = rest.map((token) => (token.kind === 'other' ? null : token.value));
    if (args.includes(null)) return false;
    const words = args as string[];
    const script = shellScript(first.value, words);
    if (script !== null) return this.script(script, depth + 1);
    const rule = RULES[first.value];
    if (rule === undefined) return false;
    // A glob could expand to a file named like an option (`--pre=…`, `-delete`).
    if (!rule.globs && (globbing || rest.some((token) => token.kind === 'glob'))) return false;
    return rule.check(words);
  }
}

/**
 * The keys of `rawInput` that only describe the command: Codex sends `command` and `cwd`;
 * Claude's `Bash` sends `command`, `description` (or `command_description`), `timeout`,
 * `run_in_background`. Any other key (Codex `url`, `additionalPermissions`…) goes to the user.
 */
const COMMAND_INPUT_KEYS = new Set([
  'command',
  'cwd',
  'workdir',
  'description',
  'command_description',
  'timeout',
  'run_in_background',
]);
const MAX_DEPTH = 2;
const CHAINS = new Set(['&&', '||', ';', '|']);
const ASSIGNMENT = /^[A-Za-z_][A-Za-z0-9_]*=/;
const SHELLS = new Set(['bash', 'sh', '/bin/bash', '/bin/sh', '/usr/bin/bash', '/usr/bin/sh']);

/** The script of `bash -c script` or `bash -lc script`, `null` for another command. */
function shellScript(program: string, args: readonly string[]): string | null {
  const [flag, script, ...more] = args;
  if (!SHELLS.has(program) || script === undefined || more.length > 0) return null;
  return flag === '-c' || flag === '-lc' ? script : null;
}

/**
 * The command line, checked outside quotes: `null` when the shell would do more than run
 * commands in a row. Unquoted new lines become `;`, line continuations are removed: the parser
 * would otherwise join two lines into one command. `globbing`: an unquoted `[`, or `^` at the
 * start of a word (zsh), which `shell-quote` does not mark as a glob.
 */
function plainScript(script: string): { plain: string; globbing: boolean } | null {
  let out = '';
  let globbing = false;
  let quote: "'" | '"' | null = null;
  let wordStart = true;
  for (let i = 0; i < script.length; i += 1) {
    const c = script.charAt(i);
    const next = script.charAt(i + 1);
    if (quote === "'") {
      if (c === "'") quote = null;
      out += c;
      continue;
    }
    if (quote === '"') {
      if (c === '$' || c === '`') return null;
      if (c === '\\') {
        out += c + next;
        i += 1;
        continue;
      }
      if (c === '"') quote = null;
      out += c;
      continue;
    }
    if (c === '\\') {
      if (next === '') return null;
      i += 1;
      if (next !== '\n') out += c + next;
      wordStart = false;
      continue;
    }
    if (c === "'" || c === '"') {
      quote = c;
      out += c;
      wordStart = false;
      continue;
    }
    if (FORBIDDEN.includes(c)) return null;
    if (c === '#' && wordStart) return null;
    // zsh: `=cmd` expands to the path of `cmd`.
    if (c === '=' && wordStart) return null;
    if (c === '[' || (c === '^' && wordStart)) globbing = true;
    if (c === '&') {
      if (next !== '&') return null;
      out += '&&';
      i += 1;
      wordStart = true;
      continue;
    }
    if (c === '|') {
      if (next === '&') return null;
      if (next === '|') i += 1;
      out += next === '|' ? '||' : '|';
      wordStart = true;
      continue;
    }
    if (c === '\n') {
      // A blank line, or a line ending with `;`, `&&`, `||`, `|`, adds no command.
      const before = out.trimEnd();
      out += before === '' || /(;|&&|\|\||\|)$/.test(before) ? ' ' : ';';
      wordStart = true;
      continue;
    }
    out += c;
    wordStart = c === ' ' || c === '\t' || c === ';';
  }
  return quote === null ? { plain: out, globbing } : null;
}

/** Outside quotes: variables, substitutions, redirections, subshells and groups. */
const FORBIDDEN = '$`<>(){}';

interface Rule {
  /** Whether unquoted globs (`*.md`) are accepted in the arguments. */
  readonly globs: boolean;
  check(args: readonly string[]): boolean;
}

const anyArgs: Rule = { globs: true, check: () => true };

function refusing(globs: boolean, refused: (arg: string) => boolean): Rule {
  return { globs, check: (args) => !args.some(refused) };
}

/** A cluster of short options (`-abc`) that contains one of `letters`. */
function hasShortOption(arg: string, letters: string): boolean {
  if (!arg.startsWith('-') || arg.startsWith('--')) return false;
  return [...arg.slice(1)].some((letter) => letters.includes(letter));
}

/** A long option, or its value form (`--name=…`). */
function isLongOption(arg: string, ...names: string[]): boolean {
  return names.some((name) => arg === name || arg.startsWith(`${name}=`));
}

/**
 * Git takes any unambiguous start of a long option: `--out` is `--output`. Refused from
 * `minimum` characters on, or with a value.
 */
function abbreviates(arg: string, option: string, minimum: number): boolean {
  const name = arg.split('=')[0] ?? arg;
  return (name.length >= minimum && option.startsWith(name)) || name.startsWith(option);
}

const GIT_READS = new Set(['status', 'log', 'diff', 'show', 'rev-parse']);
const GIT_BRANCH_LISTING = new Set([
  '--show-current',
  '--list',
  '-a',
  '--all',
  '-r',
  '--remotes',
  '-v',
  '-vv',
  '--verbose',
]);

const git: Rule = {
  globs: false,
  check: ([sub, ...args]) => {
    // Options before the subcommand (`-c`, `-C`, `--git-dir`…) change what git runs.
    if (sub === undefined || sub.startsWith('-')) return false;
    const writes = args.some(
      (arg) =>
        abbreviates(arg, '--output', 4) ||
        abbreviates(arg, '--ext-diff', 5) ||
        abbreviates(arg, '--textconv', 7),
    );
    if (writes) return false;
    // `git branch name` creates a branch: only the listing options, without a name.
    if (sub === 'branch') return args.every((arg) => GIT_BRANCH_LISTING.has(arg));
    return GIT_READS.has(sub);
  },
};

const FIND_ACTIONS = new Set([
  '-exec',
  '-execdir',
  '-ok',
  '-okdir',
  '-delete',
  '-fprint',
  '-fprint0',
  '-fprintf',
  '-fls',
]);

/** `sed -n` with a pure print script (`12p`, `1,20p`, `$p`), never `-i`. */
const sed: Rule = {
  globs: false,
  check: ([flag, script, ...files]) =>
    flag === '-n' &&
    script !== undefined &&
    /^(\d+|\$)(,(\d+|\$))?p$/.test(script) &&
    files.every((file) => !file.startsWith('-')),
};

/** The read-only programs. Any other one goes to the user. */
const RULES: Readonly<Record<string, Rule>> = {
  pwd: anyArgs,
  cd: anyArgs,
  ls: anyArgs,
  cat: anyArgs,
  head: anyArgs,
  tail: anyArgs,
  wc: anyArgs,
  nl: anyArgs,
  grep: anyArgs,
  echo: anyArgs,
  which: anyArgs,
  stat: anyArgs,
  // `--pre` and `--hostname-bin` run a program, `-z` runs the decompressors.
  rg: refusing(
    false,
    (arg) =>
      isLongOption(arg, '--pre', '--hostname-bin', '--search-zip') || hasShortOption(arg, 'z'),
  ),
  find: refusing(false, (arg) => FIND_ACTIONS.has(arg)),
  git,
  sed,
};
