import type { AcpUpdate, PermissionOption, PermissionRequest } from './model.ts';

/**
 * What the bridge corrects in what the agents send, one session at a time (docs/adr/02,
 * « Ce que le pont doit compenser »). Everything else is relayed untouched.
 *
 * - Codex sends the output of its commands in `_meta.terminal_output_delta`, for a terminal the
 *   client never created: the output goes back into the tool call content.
 * - Codex calls that failed in its sandbox (output starting with `bwrap:`) are hidden.
 * - Codex asks to approve an edit without its diff: the diff comes from the tool call with the
 *   same `toolCallId`.
 * - Codex's « always allow » for commands (`accept_execpolicy_amendment`) is not offered.
 * - The plan of an agent leaving its plan mode is lifted out of `rawInput.plan`.
 */

/** Option ids never offered to the app. */
const WITHHELD_OPTIONS = new Set(['accept_execpolicy_amendment']);

/** Marks a tool call the app must hide. */
export const HIDDEN_SANDBOX_FAILURE = 'sandbox_unavailable';

/**
 * Most of a command's output relayed at once, in characters: the end is kept, as RUN_COMMAND
 * does. A chunk is relayed alone (the app appends it); the whole output, capped, once at the end.
 */
export const OUTPUT_LIMIT = 64 * 1024;

/** The output of a running command: its start (for `bwrap:`) and its last characters. */
interface Output {
  readonly head: string;
  readonly tail: string;
  readonly truncated: boolean;
}

type Json = Record<string, unknown>;

export interface NormalizedApproval {
  readonly toolCall: Readonly<Json>;
  readonly options: readonly PermissionOption[];
  readonly plan: string | null;
}

export class SessionCompensations {
  /** Diff content of the tool calls still running, by `toolCallId`. */
  private readonly diffs = new Map<string, unknown[]>();
  /** Terminal output gathered so far, by `toolCallId`, capped. */
  private readonly outputs = new Map<string, Output>();

  update(update: AcpUpdate): AcpUpdate {
    switch (update.sessionUpdate) {
      case 'tool_call':
        return this.toolCall(update);
      case 'tool_call_update':
        return this.toolCallUpdate(update);
      default:
        return update;
    }
  }

  approval(request: PermissionRequest): NormalizedApproval {
    const toolCall: Json = { ...request.toolCall };
    const id = toolCallIdOf(toolCall);
    const content = toolCall.content;
    const hasContent = Array.isArray(content) && content.length > 0;
    const diff = id === null ? undefined : this.diffs.get(id);
    if (!hasContent && diff !== undefined) toolCall.content = diff;
    const rawInput = toolCall.rawInput;
    const plan =
      toolCall.kind === 'switch_mode' && isObject(rawInput) && typeof rawInput.plan === 'string'
        ? rawInput.plan
        : null;
    return {
      toolCall,
      options: request.options.filter((option) => !WITHHELD_OPTIONS.has(option.optionId)),
      plan,
    };
  }

  private toolCall(update: AcpUpdate): AcpUpdate {
    const id = toolCallIdOf(update);
    const content = withoutTerminals(update.content);
    if (id !== null && content?.some(isDiff)) this.diffs.set(id, content);
    const relayed = content === undefined ? update : { ...update, content };
    return this.withTerminalOutput(relayed);
  }

  private toolCallUpdate(update: AcpUpdate): AcpUpdate {
    const id = toolCallIdOf(update);
    const content = withoutTerminals(update.content);
    if (id !== null && content?.some(isDiff)) this.diffs.set(id, content);
    const relayed = this.withTerminalOutput(
      content === undefined ? update : { ...update, content },
    );
    if (id === null) return relayed;
    const output = this.outputs.get(id)?.head ?? '';
    const finished = update.status === 'completed' || update.status === 'failed';
    if (finished) {
      this.outputs.delete(id);
      this.diffs.delete(id);
    }
    if (update.status === 'failed' && output.startsWith('bwrap:')) {
      return {
        sessionUpdate: 'tool_call_update',
        toolCallId: id,
        status: 'failed',
        content: [],
        _meta: { regie: { hidden: HIDDEN_SANDBOX_FAILURE } },
      };
    }
    return relayed;
  }

  /**
   * Codex sends the output of a command in `_meta.terminal_output_delta`. The relayed update
   * carries in `content`:
   * - the chunk just received, marked `_meta.regie.output = "delta"`: the app appends it;
   * - at the end (`terminal_exit`), the whole output, its last `OUTPUT_LIMIT` characters only,
   *   marked `"final"`: the app replaces what it gathered.
   * The raw chunk is taken out of `_meta`: it is not relayed twice.
   */
  private withTerminalOutput(update: AcpUpdate): AcpUpdate {
    const id = toolCallIdOf(update);
    const meta = update._meta;
    if (id === null || !isObject(meta)) return update;
    const delta = meta.terminal_output_delta;
    const exit = meta.terminal_exit;
    const chunk = isObject(delta) && typeof delta.data === 'string' ? delta.data : null;
    if (chunk === null && !isObject(exit)) return update;
    const output = this.gather(id, chunk ?? '');
    const { terminal_output_delta: _relayedInContent, regie: _ours, ...rest } = meta;
    const final = isObject(exit);
    const text = final ? output.tail : capped(chunk ?? '').text;
    const truncated = final ? output.truncated : capped(chunk ?? '').truncated;
    return {
      ...update,
      content: [{ type: 'content', content: { type: 'text', text } }],
      _meta: { ...rest, regie: { output: final ? 'final' : 'delta', truncated } },
    };
  }

  private gather(id: string, chunk: string): Output {
    const previous = this.outputs.get(id) ?? { head: '', tail: '', truncated: false };
    const head = previous.head.length >= 16 ? previous.head : (previous.head + chunk).slice(0, 16);
    const { text, truncated } = capped(previous.tail + chunk);
    const output = { head, tail: text, truncated: previous.truncated || truncated };
    this.outputs.set(id, output);
    return output;
  }
}

function capped(text: string): { text: string; truncated: boolean } {
  return text.length > OUTPUT_LIMIT
    ? { text: text.slice(-OUTPUT_LIMIT), truncated: true }
    : { text, truncated: false };
}

function toolCallIdOf(value: Readonly<Json>): string | null {
  return typeof value.toolCallId === 'string' ? value.toolCallId : null;
}

function withoutTerminals(content: unknown): unknown[] | undefined {
  if (!Array.isArray(content)) return undefined;
  return content.filter((item) => !(isObject(item) && item.type === 'terminal'));
}

function isDiff(item: unknown): boolean {
  return isObject(item) && item.type === 'diff';
}

function isObject(value: unknown): value is Json {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
