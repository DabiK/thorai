import type { AcpUpdate } from './model.ts';

type Json = Readonly<Record<string, unknown>>;

/** Tool call kinds whose `locations` are the files they change. */
const CHANGING_KINDS = new Set(['edit', 'delete', 'move']);

/**
 * Finds the files a session changed, from the tool calls of its agent (ticket 13): the paths of
 * their diffs (`content[type=diff].path`) and, for an edit, its `locations`. A file counts once
 * its tool call has completed: an edit refused or failed changed nothing.
 */
export class ModifiedFiles {
  private readonly calls = new Map<string, { changing: boolean; paths: Set<string> }>();

  /** The files this update shows changed, as the agent names them. */
  observe(update: AcpUpdate): readonly string[] {
    if (update.sessionUpdate !== 'tool_call' && update.sessionUpdate !== 'tool_call_update') {
      return [];
    }
    const id = update.toolCallId;
    if (typeof id !== 'string') return [];
    const call = this.calls.get(id) ?? { changing: false, paths: new Set<string>() };
    this.calls.set(id, call);
    if (typeof update.kind === 'string') call.changing = CHANGING_KINDS.has(update.kind);
    for (const item of arrayOf(update.content)) {
      if (isObject(item) && item.type === 'diff' && typeof item.path === 'string') {
        call.paths.add(item.path);
      }
    }
    if (call.changing) {
      for (const location of arrayOf(update.locations)) {
        if (isObject(location) && typeof location.path === 'string') call.paths.add(location.path);
      }
    }
    // Only the calls that may change a file are followed until they end.
    if (update.status === 'failed' || (!call.changing && call.paths.size === 0)) {
      this.calls.delete(id);
    }
    if (update.status !== 'completed') return [];
    this.calls.delete(id);
    return [...call.paths];
  }
}

/** `files` then the new ones of `added`, relative to `cwd` when inside it, each once. */
export function withModifiedFiles(
  files: readonly string[],
  added: readonly string[],
  cwd: string,
): readonly string[] {
  const all = new Set(files);
  for (const path of added) all.add(relativeTo(cwd, path));
  return [...all];
}

function relativeTo(cwd: string, path: string): string {
  const root = cwd.endsWith('/') ? cwd : `${cwd}/`;
  return path.startsWith(root) && path.length > root.length ? path.slice(root.length) : path;
}

function arrayOf(value: unknown): readonly unknown[] {
  return Array.isArray(value) ? value : [];
}

function isObject(value: unknown): value is Json {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
