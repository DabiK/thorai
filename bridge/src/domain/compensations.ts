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

type Json = Record<string, unknown>;

export interface NormalizedApproval {
  readonly toolCall: Readonly<Json>;
  readonly options: readonly PermissionOption[];
  readonly plan: string | null;
}

export class SessionCompensations {
  /** Diff content of the tool calls still running, by `toolCallId`. */
  private readonly diffs = new Map<string, unknown[]>();
  /** Terminal output gathered so far, by `toolCallId`. */
  private readonly outputs = new Map<string, string>();

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
    const output = this.outputs.get(id) ?? '';
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

  private withTerminalOutput(update: AcpUpdate): AcpUpdate {
    const id = toolCallIdOf(update);
    const meta = update._meta;
    if (id === null || !isObject(meta)) return update;
    const delta = meta.terminal_output_delta;
    const exit = meta.terminal_exit;
    if (isObject(delta) && typeof delta.data === 'string') {
      this.outputs.set(id, (this.outputs.get(id) ?? '') + delta.data);
    } else if (!isObject(exit) || !this.outputs.has(id)) {
      return update;
    }
    const text = this.outputs.get(id) ?? '';
    return { ...update, content: [{ type: 'content', content: { type: 'text', text } }] };
  }
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
