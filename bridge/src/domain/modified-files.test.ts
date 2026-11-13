import { describe, expect, it } from 'vitest';
import { ModifiedFiles, withModifiedFiles } from './modified-files.ts';

const CWD = '/home/dev/projects/regie-demo';

describe('modified files', () => {
  it('takes the diff of a Codex edit once it completes', () => {
    const files = new ModifiedFiles();

    expect(
      files.observe({
        sessionUpdate: 'tool_call',
        toolCallId: 'exec-1',
        kind: 'edit',
        title: 'Editing files',
        status: 'in_progress',
        content: [{ type: 'diff', path: `${CWD}/todo.md`, oldText: 'a', newText: 'b' }],
      }),
    ).toEqual([]);
    expect(
      files.observe({
        sessionUpdate: 'tool_call_update',
        toolCallId: 'exec-1',
        status: 'completed',
      }),
    ).toEqual([`${CWD}/todo.md`]);
  });

  it('takes the locations of a Claude edit', () => {
    const files = new ModifiedFiles();
    files.observe({
      sessionUpdate: 'tool_call',
      toolCallId: 'toolu_1',
      kind: 'edit',
      status: 'pending',
      content: [],
      locations: [],
    });
    files.observe({
      sessionUpdate: 'tool_call_update',
      toolCallId: 'toolu_1',
      locations: [{ path: `${CWD}/notes.md` }],
    });

    expect(
      files.observe({
        sessionUpdate: 'tool_call_update',
        toolCallId: 'toolu_1',
        status: 'completed',
      }),
    ).toEqual([`${CWD}/notes.md`]);
  });

  it('ignores a refused or failed edit, a read and a command', () => {
    const files = new ModifiedFiles();
    files.observe({
      sessionUpdate: 'tool_call',
      toolCallId: 'e',
      kind: 'edit',
      locations: [{ path: `${CWD}/a.md` }],
    });
    files.observe({ sessionUpdate: 'tool_call_update', toolCallId: 'e', status: 'failed' });
    files.observe({
      sessionUpdate: 'tool_call',
      toolCallId: 'r',
      kind: 'read',
      locations: [{ path: `${CWD}/b.md` }],
    });

    expect(
      files.observe({ sessionUpdate: 'tool_call_update', toolCallId: 'r', status: 'completed' }),
    ).toEqual([]);
    expect(
      files.observe({ sessionUpdate: 'tool_call_update', toolCallId: 'e', status: 'completed' }),
    ).toEqual([]);
  });

  it('keeps each file once, relative to the project when inside it', () => {
    expect(
      withModifiedFiles(['todo.md'], [`${CWD}/todo.md`, `${CWD}/src/a.ts`, '/etc/hosts'], CWD),
    ).toEqual(['todo.md', 'src/a.ts', '/etc/hosts']);
  });
});
