import { describe, expect, it } from 'vitest';
import { ReadOnlyCommands } from '../../domain/read-only-command.ts';
import { ShellQuoteParser } from './shell-quote-parser.ts';

/**
 * The read-only rule of the domain (ticket 13) with the real parser: the rule depends on how
 * `shell-quote` actually splits a command line. Each command of the list, and each case that
 * must go to the user, one by one.
 */
const commands = new ReadOnlyCommands(new ShellQuoteParser());

describe('ShellQuoteParser', () => {
  const parser = new ShellQuoteParser();

  it('gives words without their quotes, operators and globs', () => {
    expect(parser.parse("cat 'a b' && ls *.md | wc")).toEqual([
      { kind: 'word', value: 'cat' },
      { kind: 'word', value: 'a b' },
      { kind: 'operator', value: '&&' },
      { kind: 'word', value: 'ls' },
      { kind: 'glob', value: '*.md' },
      { kind: 'operator', value: '|' },
      { kind: 'word', value: 'wc' },
    ]);
  });

  it('gives `other` for a variable, a comment, or a line it cannot read', () => {
    expect(parser.parse('cat $HOME')).toContainEqual({ kind: 'other' });
    expect(parser.parse('cat a # b')).toContainEqual({ kind: 'other' });
    // biome-ignore lint/suspicious/noTemplateCurlyInString: a shell substitution, not a template
    expect(parser.parse('cat ${ x }')).toEqual([{ kind: 'other' }]);
  });
});

describe('read-only commands, approved by the bridge', () => {
  it.each([
    ['pwd', 'pwd'],
    ['ls', 'ls -la src'],
    ['ls with a glob', 'ls *.md'],
    ['cat', 'cat notes.md todo.md'],
    ['cat with a quoted name', 'cat \'mes notes.md\' "todo list.md"'],
    ['head', 'head -n 20 notes.md'],
    ['tail', 'tail -n 5 notes.md'],
    ['wc', 'wc -l notes.md'],
    ['nl', 'nl -ba notes.md'],
    ['rg', "rg --files -g 'AGENTS.md' -g 'notes.md'"],
    ['rg with a pattern ending with $', "rg -n 'fin$' src"],
    ['grep', "grep -c '[^[:space:]]' notes.md"],
    ['grep -r', 'grep -rn TODO src'],
    ['find', "find . -name '*.md' -type f"],
    ['git status', 'git status --short'],
    ['git log', 'git log --oneline -n 5'],
    ['git diff', 'git diff --check'],
    ['git diff of a file', 'git diff -- notes.md'],
    ['git show', 'git show HEAD~1 --stat'],
    ['git branch', 'git branch'],
    ['git branch --show-current', 'git branch --show-current'],
    ['git branch -a', 'git branch -a'],
    ['git rev-parse', 'git rev-parse --abbrev-ref HEAD'],
    ['sed -n Np', "sed -n '12p' notes.md"],
    ['sed -n N,Mp', 'sed -n 1,40p notes.md'],
    ['a bracket glob for a program without dangerous options', 'cat [n]otes.md'],
    ['a caret inside a word', 'git show HEAD^'],
    ['echo', 'echo bonjour'],
    ['which', 'which rg'],
    ['stat', 'stat notes.md'],
    ['cd, then a read', 'cd src && ls'],
    ['a chain of reads (Codex)', "pwd && rg --files -g 'notes.md' && cat notes.md todo.md"],
    ['reads separated by ;', 'cat a; cat b;'],
    ['reads separated by ||', 'cat a || ls'],
    ['a pipe between reads', 'cat notes.md | head -n 3'],
    ['reads on several lines', 'cat notes.md\n\ncat todo.md\n'],
    ['a line ending with &&', 'cat notes.md &&\n  cat todo.md'],
    ['a line continuation', 'cat \\\n notes.md'],
    ['an escaped dollar', 'cat \\$HOME'],
    ['bash -lc', "bash -lc 'cat notes.md && git status'"],
    ['sh -c', 'sh -c "ls -la"'],
  ])('%s: %s', (_case, command) => {
    expect(commands.isReadOnly(command)).toBe(true);
  });

  it('reads an argv, and the script of a bash -lc argv', () => {
    expect(commands.isReadOnly(['cat', 'notes.md'])).toBe(true);
    expect(commands.isReadOnly(['bash', '-lc', 'git diff && cat a'])).toBe(true);
    expect(commands.isReadOnly(['/bin/sh', '-c', 'ls'])).toBe(true);
  });
});

describe('commands that go to the user', () => {
  it.each([
    // Programs that write, or that are not known.
    ['an unknown program', 'awk 1 notes.md'],
    ['a write', 'touch .probe && rm .probe'],
    ['rm', 'rm notes.md'],
    ['printf', "printf '%s' x"],
    ['a program by its path', '/bin/cat notes.md'],
    ['an empty command', ''],
    ['a loop', 'for p in a b; do cat "$p"; done'],
    ['a test', '[ -f notes.md ]'],
    // Redirections.
    ['a redirection', 'cat a > b'],
    ['an append', 'echo x >> todo.md'],
    ['stderr to /dev/null', 'cat a 2>/dev/null'],
    ['stderr to stdout', 'cat a 2>&1'],
    ['an input redirection', 'cat < a'],
    ['a here-document', 'cat <<EOF\nx\nEOF'],
    ['a redirection without spaces', 'cat a>b'],
    // Substitutions and expansions.
    ['a command substitution', 'cat $(rm -rf x)'],
    ['a substitution in double quotes', 'echo "$(rm x)"'],
    ['backticks', 'cat `rm x`'],
    ['backticks in double quotes', 'echo "`rm x`"'],
    ['a process substitution', 'cat <(rm x)'],
    ['an output process substitution', 'ls >(cat)'],
    ['a variable', 'cat $HOME/notes.md'],
    ['a variable in double quotes', 'cat "$HOME"'],
    // biome-ignore lint/suspicious/noTemplateCurlyInString: a shell variable, not a template
    ['a variable in braces', 'cat ${HOME}'],
    // Chains with a write.
    ['a chain with a write', 'cat a && rm b'],
    ['a write after ;', 'ls; rm -rf src'],
    ['a write after ||', 'cat a || touch a'],
    ['a pipe to a writer', 'cat a | tee b'],
    ['a pipe to a shell', 'cat script.sh | sh'],
    ['a write on the next line', 'cat a\nrm b'],
    ['a comment that hides a line', 'cat a # lecture\nrm b'],
    ['a comment', 'cat a # lecture'],
    // Shell syntax.
    ['a background job', 'cat a & rm b'],
    ['a trailing &', 'cat a &'],
    ['|&', 'cat a |& cat'],
    ['a subshell', '(cat a)'],
    ['a group', '{ cat a; }'],
    ['an assignment', 'FOO=1 cat a'],
    ['an assignment alone', 'PAGER=x'],
    [';;', 'cat a;; ls'],
    ['a leading ;', '; cat a'],
    ['a trailing &&', 'cat a &&'],
    ['an unclosed quote', "cat 'a"],
    ['a trailing backslash', 'cat a \\'],
    // Dangerous options of known programs.
    ['find -exec', "find . -name '*.md' -exec rm {} \\;"],
    ['find -execdir', 'find . -execdir ls \\;'],
    ['find -ok', 'find . -ok rm \\;'],
    ['find -okdir', 'find . -okdir rm \\;'],
    ['find -delete', 'find . -name x -delete'],
    ['find -fprint', 'find . -fprint out.txt'],
    ['find -fprint0', 'find . -fprint0 out.txt'],
    ['find -fprintf', "find . -fprintf out.txt '%p'"],
    ['find -fls', 'find . -fls out.txt'],
    ['find with an unquoted glob', 'find *'],
    ['rg --pre', 'rg --pre ./script x'],
    ['rg --pre=', 'rg --pre=./script x'],
    ['rg --hostname-bin', 'rg --hostname-bin=./x foo'],
    ['rg --search-zip', 'rg --search-zip foo'],
    ['rg -z', 'rg -z foo'],
    ['rg -z in a cluster', 'rg -nz foo'],
    ['rg with an unquoted glob', 'rg foo *'],
    ['git with -c', 'git -c core.pager=x log'],
    ['git with -C', 'git -C /tmp status'],
    ['git with --git-dir', 'git --git-dir=/tmp/x status'],
    ['git diff --output', 'git diff --output=patch.txt'],
    ['git log --output abbreviated', 'git log -p --out=patch.txt'],
    ['git diff --ext-diff', 'git diff --ext-diff'],
    ['git show --textconv', 'git show --textconv HEAD'],
    ['git branch with a name', 'git branch nouvelle'],
    ['git branch -d', 'git branch -d main'],
    ['git branch --list with a pattern', "git branch --list 'f*'"],
    ['git checkout', 'git checkout main'],
    ['git switch', 'git switch -c x'],
    ['git commit', 'git commit -m x'],
    ['git alone', 'git'],
    ['sed -i', "sed -i 's/a/b/' notes.md"],
    ['sed -n -i', 'sed -n -i 1p notes.md'],
    ['sed without -n', 'sed 1p notes.md'],
    ['sed with a write command', "sed -n 'w out.txt' notes.md"],
    ['sed with an execute command', "sed -n '1e rm x' notes.md"],
    // Programs out of the list: their writing options have abbreviations (`file --comp`).
    ['file', 'file notes.md'],
    ['file --comp', 'file --comp -m foo'],
    ['file --compil', 'file --compil -m foo'],
    ['tree', 'tree -L 2'],
    ['tree -o', 'tree -o out.txt'],
    ['tree --output', 'tree --output=out.txt'],
    // Globs shell-quote does not mark: `[…]` can expand to an option when such a file exists.
    ['find -[d]elete', 'find . -[d]elete'],
    ['find -[e]xec', 'find . -maxdepth 1 -name victim -[e]xec touch pwned \\;'],
    ['git -[-]output', 'git log -[-]output=x'],
    ['rg -[-]pre', 'rg x -[-]pre=sh'],
    ['sed with a bracket glob', 'sed -n 1p [n]otes.md'],
    ['a bracket glob anywhere in the line', 'ls [a]* && git status'],
    ['a brace expansion', 'find . -{d,}elete'],
    // zsh, the shell of Claude's Bash tool on some machines.
    ['zsh =cmd', 'ls =rm'],
    ['zsh negation glob', 'git log ^x'],
    ['zsh glob qualifier', 'ls *(.)'],
    // Shells.
    ['bash -lc with a write', "bash -lc 'cat a > b'"],
    ['bash running a file', 'bash script.sh'],
    ['bash -c with more arguments', "bash -c 'cat a' x"],
    ['nested shells too deep', `bash -c "bash -c 'bash -c ls'"`],
  ])('%s: %s', (_case, command) => {
    expect(commands.isReadOnly(command)).toBe(false);
  });

  it('refuses an argv with a write, a bash -lc argv with a write, or no command', () => {
    expect(commands.isReadOnly(['rm', 'notes.md'])).toBe(false);
    expect(commands.isReadOnly(['bash', '-lc', 'cat a > b'])).toBe(false);
    expect(commands.isReadOnly(['FOO=1', 'cat'])).toBe(false);
    expect(commands.isReadOnly([])).toBe(false);
    expect(commands.isReadOnly(undefined)).toBe(false);
    expect(commands.isReadOnly({ command: 'cat a' })).toBe(false);
  });
});
