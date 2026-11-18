import { describe, expect, it } from 'vitest';
import { WordsShellParser } from '../adapters/fake/memory.ts';
import { ReadOnlyCommands } from './read-only-command.ts';

/**
 * Which tool call of a permission request carries a command to judge. The shapes come from
 * `fixtures/acp-raw/` (Codex) and from claude-agent-acp 0.107 (`Bash`). The rule itself, with
 * the real parser, is tested in `adapters/shell/shell-quote-parser.test.ts`.
 */
const commands = new ReadOnlyCommands(new WordsShellParser());

const CWD = '/home/dev/projects/regie-demo';

describe('the tool call of a permission request', () => {
  it('judges the command of a Codex exec request', () => {
    expect(
      commands.isReadOnlyToolCall({
        toolCallId: 'exec-f4b8d155',
        title: 'cat notes.md todo.md',
        rawInput: { command: 'cat notes.md todo.md', cwd: CWD },
        kind: 'execute',
        status: 'pending',
      }),
    ).toBe(true);
    expect(
      commands.isReadOnlyToolCall({
        toolCallId: 'exec-304f8ce4',
        title: 'touch .probe && rm .probe',
        rawInput: { command: 'touch .probe && rm .probe', cwd: CWD },
        kind: 'execute',
      }),
    ).toBe(false);
  });

  it('judges the command of a Claude Bash request', () => {
    expect(
      commands.isReadOnlyToolCall({
        toolCallId: 'toolu_016Z',
        name: 'Bash',
        rawInput: {
          command: 'git status',
          description: 'Show the working tree status',
          timeout: 120000,
          run_in_background: false,
        },
        title: 'git status',
        kind: 'execute',
      }),
    ).toBe(true);
  });

  it('sends anything that is not a shell command to the user', () => {
    // A Claude edit.
    expect(
      commands.isReadOnlyToolCall({
        kind: 'edit',
        title: 'Edit todo.md',
        rawInput: { file_path: `${CWD}/todo.md`, old_string: 'a', new_string: 'b' },
      }),
    ).toBe(false);
    // A Codex edit: only its locations.
    expect(
      commands.isReadOnlyToolCall({
        kind: 'edit',
        title: 'Edit files',
        locations: [{ path: CWD }],
      }),
    ).toBe(false);
    // A plan to approve.
    expect(commands.isReadOnlyToolCall({ kind: 'switch_mode', rawInput: { plan: '# Plan' } })).toBe(
      false,
    );
    // A command named in a read kind: only `execute` is judged.
    expect(commands.isReadOnlyToolCall({ kind: 'read', rawInput: { command: 'cat a' } })).toBe(
      false,
    );
    // An MCP tool approval from Codex, without a command.
    expect(
      commands.isReadOnlyToolCall({
        kind: 'execute',
        rawInput: { serverName: 'x', description: 'y', schema: {} },
      }),
    ).toBe(false);
    // No input.
    expect(commands.isReadOnlyToolCall({ kind: 'execute', title: 'cat a' })).toBe(false);
  });

  it('judges only rawInput.command: never the title, and a kind other than execute goes up', () => {
    // How Codex shows its reads in the feed: `read` or `search`, a title, no command.
    expect(
      commands.isReadOnlyToolCall({ kind: 'read', title: "Read file '/home/dev/notes.md'" }),
    ).toBe(false);
    expect(commands.isReadOnlyToolCall({ kind: 'search', title: 'List files' })).toBe(false);
    // A reading title does not hide a write.
    expect(
      commands.isReadOnlyToolCall({
        kind: 'execute',
        title: "Read file 'notes.md'",
        rawInput: { command: 'rm notes.md', cwd: CWD },
      }),
    ).toBe(false);
    expect(
      commands.isReadOnlyToolCall({ kind: 'execute', title: 'List files', rawInput: { cwd: CWD } }),
    ).toBe(false);
  });

  it('gives the command it analysed, an argv joined', () => {
    expect(
      commands.readOnlyCommandOf({
        kind: 'execute',
        title: "Read file 'notes.md'",
        rawInput: { command: 'cat notes.md', cwd: CWD },
      }),
    ).toBe('cat notes.md');
    expect(
      commands.readOnlyCommandOf({ kind: 'execute', rawInput: { command: ['ls', '-la'] } }),
    ).toBe('ls -la');
    expect(commands.readOnlyCommandOf({ kind: 'execute', rawInput: { command: 'rm a' } })).toBe(
      null,
    );
  });

  it('sends a command that asks for more than running to the user', () => {
    // Codex asks for a network access, or more permissions, with the command.
    expect(
      commands.isReadOnlyToolCall({
        kind: 'execute',
        rawInput: { command: 'cat a', cwd: CWD, url: 'https://example.org' },
      }),
    ).toBe(false);
    expect(
      commands.isReadOnlyToolCall({
        kind: 'execute',
        rawInput: { command: 'cat a', cwd: CWD, additionalPermissions: { network: true } },
      }),
    ).toBe(false);
    // Claude asks to run outside its sandbox.
    expect(
      commands.isReadOnlyToolCall({
        kind: 'execute',
        rawInput: { command: 'cat a', dangerouslyDisableSandbox: true },
      }),
    ).toBe(false);
  });

  it('refuses before splitting what the shell would interpret', () => {
    for (const command of ['cat a > b', 'cat $(rm x)', 'cat `rm x`', 'cat a & rm b', '(cat a)']) {
      expect(commands.isReadOnly(command)).toBe(false);
    }
  });
});

it('recognizes OpenCode shell workdir metadata while retaining the strict command rule', () => {
  const reads = new ReadOnlyCommands(new WordsShellParser());
  expect(
    reads.readOnlyCommandOf({
      kind: 'execute',
      rawInput: { command: 'cat README.md', workdir: '/project' },
    }),
  ).toBe('cat README.md');
  expect(
    reads.readOnlyCommandOf({
      kind: 'execute',
      rawInput: { command: 'cat README.md > copy', workdir: '/project' },
    }),
  ).toBeNull();
  expect(
    reads.readOnlyCommandOf({
      kind: 'execute',
      rawInput: {
        command: 'cat README.md',
        workdir: '/project',
        additionalPermissions: { network: true },
      },
    }),
  ).toBeNull();
});
