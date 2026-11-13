import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { HIDDEN_SANDBOX_FAILURE, OUTPUT_LIMIT, SessionCompensations } from './compensations.ts';
import type { AcpUpdate, PermissionRequest } from './model.ts';

/** Replays a raw ACP recording of the Thor (bridge/fixtures/acp-raw) through the compensations. */
function replay(fixture: string) {
  const path = join(import.meta.dirname, '../../fixtures/acp-raw', fixture);
  const compensations = new SessionCompensations();
  const updates: AcpUpdate[] = [];
  const approvals: ReturnType<SessionCompensations['approval']>[] = [];
  const raw: AcpUpdate[] = [];
  for (const line of readFileSync(path, 'utf8').split('\n')) {
    if (!line.trim()) continue;
    const record = JSON.parse(line) as { dir: string; msg?: Record<string, unknown> };
    if (record.dir !== 'agent->client' || record.msg === undefined) continue;
    const params = record.msg.params as Record<string, unknown> | undefined;
    if (record.msg.method === 'session/update') {
      const update = params?.update as AcpUpdate;
      raw.push(update);
      updates.push(compensations.update(update));
    } else if (record.msg.method === 'session/request_permission') {
      approvals.push(compensations.approval(params as unknown as PermissionRequest));
    }
  }
  return { updates, approvals, raw };
}

const toolUpdates = (updates: AcpUpdate[]) =>
  updates.filter((u) => u.sessionUpdate === 'tool_call' || u.sessionUpdate === 'tool_call_update');

describe('Codex compensations, on the recordings of the Thor', () => {
  it('hides the calls that failed in the bubblewrap sandbox', () => {
    const { updates } = replay('codex-session.jsonl');
    const failed = updates.filter((u) => u.status === 'failed');

    expect(failed.length).toBeGreaterThan(0);
    for (const update of failed) {
      expect(update).toEqual({
        sessionUpdate: 'tool_call_update',
        toolCallId: update.toolCallId,
        status: 'failed',
        content: [],
        _meta: { regie: { hidden: HIDDEN_SANDBOX_FAILURE } },
      });
    }
    expect(JSON.stringify(updates)).not.toContain('bwrap:');
  });

  it('puts the diff of the edit in its approval', () => {
    const { approvals } = replay('codex-session.jsonl');
    const edit = approvals.find((a) => a.toolCall.kind === 'edit');

    expect(edit?.toolCall.content).toEqual([
      expect.objectContaining({ type: 'diff', path: '/home/dev/projects/regie-demo/todo.md' }),
    ]);
  });

  it('puts the command output back in the tool call content', () => {
    const { updates } = replay('codex-session-untrusted.jsonl');
    const cat = toolUpdates(updates).find(
      (u) =>
        u.sessionUpdate === 'tool_call_update' &&
        Array.isArray(u.content) &&
        u.status === 'completed' &&
        JSON.stringify(u.content).includes('Notes de démo'),
    );

    expect(cat?.content).toEqual([
      { type: 'content', content: { type: 'text', text: expect.stringContaining('# À faire') } },
    ]);
  });

  it('relays no terminal the client never created', () => {
    const { updates } = replay('codex-session-untrusted.jsonl');

    expect(JSON.stringify(toolUpdates(updates))).not.toContain('"type":"terminal"');
  });

  it('does not offer the « always allow » that writes Codex rules', () => {
    const { approvals } = replay('codex-session-untrusted.jsonl');

    expect(approvals).toHaveLength(4);
    for (const approval of approvals) {
      expect(approval.options.map((o) => o.optionId)).toEqual(['allow_once', 'cancel']);
    }
  });

  it('lifts the plan to approve out of the plan mode request', () => {
    const { approvals } = replay('codex-session-planmode.jsonl');
    const plan = approvals.find((a) => a.toolCall.kind === 'switch_mode');

    expect(plan?.plan).toEqual(expect.any(String));
    expect(plan?.plan?.length).toBeGreaterThan(20);
  });
});

describe('a long command output', () => {
  it('is relayed chunk by chunk, then once capped at the end: linear, not quadratic', () => {
    const compensations = new SessionCompensations();
    const chunk = `${'x'.repeat(4095)}\n`;
    const chunks = 512; // 2 Mo of output
    const relayed: AcpUpdate[] = [];
    relayed.push(
      compensations.update({ sessionUpdate: 'tool_call', toolCallId: 't', kind: 'execute' }),
    );
    for (let i = 0; i < chunks; i += 1) {
      relayed.push(
        compensations.update({
          sessionUpdate: 'tool_call_update',
          toolCallId: 't',
          _meta: { terminal_output_delta: { data: chunk, terminal_id: 't' } },
        }),
      );
    }
    const end = compensations.update({
      sessionUpdate: 'tool_call_update',
      toolCallId: 't',
      status: 'completed',
      _meta: { terminal_exit: { exit_code: 0, signal: null, terminal_id: 't' } },
    });
    relayed.push(end);

    const total = relayed.reduce((sum, update) => sum + JSON.stringify(update).length, 0);
    expect(total).toBeLessThan(chunk.length * chunks * 1.2 + OUTPUT_LIMIT * 1.2);
    expect(relayed[1]?._meta).toEqual({ regie: { output: 'delta', truncated: false } });
    const text = (end.content as Array<{ content: { text: string } }>)[0]?.content.text ?? '';
    expect(text.length).toBe(OUTPUT_LIMIT);
    expect(end._meta).toMatchObject({ regie: { output: 'final', truncated: true } });
  });
});

describe('Claude compensations, on the recordings of the Thor', () => {
  it('relays its updates untouched', () => {
    const { updates, raw } = replay('claude-session.jsonl');

    expect(updates).toEqual(raw);
  });

  it('keeps the diff of its edit approval and all its options', () => {
    const { approvals } = replay('claude-session.jsonl');

    expect(approvals).toHaveLength(1);
    expect(approvals[0]?.toolCall.content).toEqual([expect.objectContaining({ type: 'diff' })]);
    expect(approvals[0]?.options.map((o) => o.optionId)).toEqual([
      'allow-once',
      'allow-with-updates',
      'reject',
    ]);
    expect(approvals[0]?.plan).toBeNull();
  });

  it('lifts the plan to approve out of ExitPlanMode', () => {
    const { approvals } = replay('claude-session-planmode.jsonl');

    expect(approvals[0]?.plan).toContain('# Ajouter une tâche à todo.md');
  });
});
