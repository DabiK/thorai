import { type ChildProcess, execFileSync, spawn } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import {
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  symlinkSync,
  writeFileSync,
} from 'node:fs';
import { homedir, tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { expect, it } from 'vitest';
import WebSocket from 'ws';
import type { AcpUpdate, BridgeEvent } from '../../domain/model.ts';

// Explicit real-agent contract, excluded from npm run check. Never prompts Claude.
// Run only this file: the older ACP contract DOES prompt both agents.
const root = resolve(import.meta.dirname, '../../..');
const fixture = join(root, 'fixtures/mcp/probe.mjs');
const port = Number(process.env.REGIE_MCP_CONTRACT_PORT ?? '47619');
const binaries = {
  codex: process.env.CODEX_PATH ?? join(homedir(), '.local/bin/codex'),
  claude: process.env.CLAUDE_CODE_EXECUTABLE ?? join(homedir(), '.local/bin/claude'),
};
type ProbeRecord = {
  owner: string;
  pid: number;
  method: string;
  params?: { name: string; arguments: { marker: string } };
  result?: { content?: { text: string }[]; tools?: { name: string }[] };
};
async function until<T>(read: () => T | Promise<T>, ready: (value: T) => boolean, ms = 60_000) {
  const deadline = Date.now() + ms;
  do {
    const value = await read();
    if (ready(value)) return value;
    await delay(100);
  } while (Date.now() < deadline);
  throw new Error(`Contract timed out after ${ms} ms`);
}
async function client(token: string) {
  const ws = new WebSocket(`ws://127.0.0.1:${port}`, {
    headers: { authorization: `Bearer ${token}` },
  });
  let next = 0;
  const pending = new Map<
    number,
    { resolve: (value: unknown) => void; reject: (error: Error) => void }
  >();
  ws.on('message', (raw) => {
    const frame = JSON.parse(raw.toString());
    const waiter = pending.get(frame.id);
    if (!waiter) return;
    pending.delete(frame.id);
    if (frame.error) waiter.reject(new Error(JSON.stringify(frame.error)));
    else waiter.resolve(frame.result);
  });
  await new Promise<void>((resolve, reject) => {
    ws.once('open', resolve);
    ws.once('error', reject);
  });
  async function call<T>(method: string, params = {}): Promise<T> {
    const id = ++next;
    return new Promise<T>((resolve, reject) => {
      const timer = setTimeout(() => {
        pending.delete(id);
        reject(new Error(`${method} timed out`));
      }, 60_000);
      pending.set(id, {
        resolve: (value) => {
          clearTimeout(timer);
          resolve(value as T);
        },
        reject: (error) => {
          clearTimeout(timer);
          reject(error);
        },
      });
      ws.send(JSON.stringify({ id, method, params }));
    });
  }
  await call('hello', { client: 'regie-mcp-contract' });
  return { call, close: () => ws.terminate() };
}

it('discovers user MCPs in Régie; Codex calls one before and after reload, Claude only lists', async () => {
  if (port === 47600) throw new Error('47600 is reserved for the emulator bench');
  const bench = mkdtempSync(join(tmpdir(), 'regie-mcp-19-'));
  const codexDir = join(bench, '.codex');
  const claudeDir = join(bench, '.claude');
  const project = join(bench, 'projects/demo');
  const state = join(bench, 'state');
  const log = join(bench, 'probe.jsonl');
  const token = randomUUID();
  const marker = `GLOBAL-${randomUUID()}`;
  const records = (): ProbeRecord[] =>
    existsSync(log)
      ? readFileSync(log, 'utf8')
          .trim()
          .split('\n')
          .filter(Boolean)
          .map((line) => JSON.parse(line))
      : [];
  for (const dir of [codexDir, claudeDir, project, state]) mkdirSync(dir, { recursive: true });
  const auth = join(process.env.CODEX_HOME ?? join(homedir(), '.codex'), 'auth.json');
  // Reuse login without copying a credential; never edit the user's actual config.
  symlinkSync(auth, join(codexDir, 'auth.json'));
  writeFileSync(
    join(codexDir, 'AGENTS.md'),
    `For every regie_probe call, set marker to ${marker}. Never use subagents.\n`,
  );
  writeFileSync(
    join(codexDir, 'config.toml'),
    `
model_reasoning_effort = "low"
[mcp_servers.regie_contract]
command = ${JSON.stringify(process.execPath)}
args = ${JSON.stringify([fixture, log, 'codex'])}
default_tools_approval_mode = "approve"
startup_timeout_sec = 20
`,
  );
  const env = {
    ...process.env,
    CODEX_HOME: codexDir,
    CLAUDE_CONFIG_DIR: claudeDir,
    // Any accidental Claude model request is impossible, including through stored auth.
    ANTHROPIC_BASE_URL: 'http://127.0.0.1:1',
    ANTHROPIC_API_KEY: 'regie-contract-no-inference',
    CLAUDE_CODE_EXECUTABLE: binaries.claude,
    CODEX_PATH: binaries.codex,
    REGIE_BRIDGE_PORT: String(port),
    REGIE_BRIDGE_STATE_DIR: state,
    REGIE_BRIDGE_TOKEN_FILE: join(bench, 'token'),
    REGIE_PROJECTS_DIR: join(bench, 'projects'),
  };
  writeFileSync(join(bench, 'token'), token, { mode: 0o600 });
  writeFileSync(
    join(claudeDir, 'settings.json'),
    JSON.stringify({ permissions: { allow: ['mcp__regie_contract__*'] } }),
  );
  execFileSync(
    binaries.claude,
    [
      'mcp',
      'add-json',
      '--scope',
      'user',
      'regie_contract',
      JSON.stringify({
        type: 'stdio',
        command: process.execPath,
        args: [fixture, log, 'claude'],
      }),
    ],
    { env, encoding: 'utf8', timeout: 30_000 },
  );
  execFileSync('git', ['init', '-q', '-b', 'main', project]);
  let child: ChildProcess | undefined;
  let rpc: Awaited<ReturnType<typeof client>> | undefined;
  let stderr = '';
  let sessionId = '';
  const evidence: Record<string, unknown> = {
    port,
    profile: 'temporary user config dirs; original configs untouched',
    codex: execFileSync(binaries.codex, ['--version'], { encoding: 'utf8' }).trim(),
    claude: execFileSync(binaries.claude, ['--version'], { encoding: 'utf8' }).trim(),
  };
  async function start() {
    child = spawn(process.execPath, ['src/main.ts'], {
      cwd: root,
      env,
      stdio: ['ignore', 'ignore', 'pipe'],
    });
    child.stderr?.on('data', (chunk) => {
      stderr += chunk.toString();
    });
    await until(() => {
      if (child?.exitCode !== null) throw new Error(`Bridge exited: ${stderr}`);
      return existsSync(join(state, 'bridge.pid'));
    }, Boolean);
    rpc = await client(token);
  }
  async function stop() {
    if (rpc && child?.exitCode === null) await rpc.call('bridge.shutdown');
    await until(() => child?.exitCode !== null, Boolean, 20_000);
    rpc?.close();
    rpc = undefined;
  }
  async function events(since = 0) {
    const result = await rpc?.call<{ events: BridgeEvent[] }>('session.events', {
      sessionId,
      since,
    });
    return result?.events ?? [];
  }
  async function prompt(text: string) {
    const previous = await events();
    const seq = previous.at(-1)?.seq ?? 0;
    await rpc?.call('session.prompt', { sessionId, text });
    return until(
      async () => {
        const next = await events(seq);
        expect(
          next.filter((e) => e.type === 'approval_request' || e.type === 'session_error'),
        ).toEqual([]);
        return next;
      },
      (next) => next.some((e) => e.type === 'turn_end'),
      150_000,
    );
  }
  try {
    await start();
    const claude = await rpc?.call<{ session: { sessionId: string } }>('session.create', {
      agent: 'claude',
      project: 'demo',
      approvalMode: 'ask',
    });
    expect(claude?.session.sessionId).toBeTruthy();
    await until(records, (rows) =>
      rows.some((r) => r.owner === 'claude' && r.method === 'tools/list'),
    );
    evidence.claudeStartup = records().filter((r) => r.owner === 'claude');
    expect(records().filter((r) => r.owner === 'claude' && r.method === 'tools/call')).toEqual([]);

    const created = await rpc?.call<{ session: { sessionId: string } }>('session.create', {
      agent: 'codex',
      project: 'demo',
      approvalMode: 'ask',
    });
    sessionId = created?.session.sessionId ?? '';
    expect(sessionId).not.toBe('');
    await until(records, (rows) =>
      rows.some((r) => r.owner === 'codex' && r.method === 'tools/list'),
    );
    evidence.codexStartup = records().filter((r) => r.owner === 'codex');
    for (const phase of ['new', 'load']) {
      if (phase === 'load') {
        await stop();
        await start();
        await rpc?.call('session.resume', { sessionId });
      }
      const before = records().filter(
        (r) => r.owner === 'codex' && r.method === 'tools/call',
      ).length;
      const turn = await prompt(
        'Call the regie_contract regie_probe MCP tool exactly once with the marker required by your global instructions. Use no other tool and no subagent. Reply with its receipt.',
      );
      const calls = records()
        .filter((r) => r.owner === 'codex' && r.method === 'tools/call')
        .slice(before);
      expect(calls).toHaveLength(1);
      expect(calls[0]?.params).toEqual({ name: 'regie_probe', arguments: { marker } });
      const receipt = calls[0]?.result?.content?.[0]?.text;
      expect(receipt).toMatch(/^RECEIPT-/);
      const updates = turn
        .filter((e) => e.type === 'session_update')
        .map((e) => e.data as AcpUpdate);
      expect(updates.filter((u) => u.sessionUpdate === 'tool_call')).toEqual([
        expect.objectContaining({
          rawInput: { server: 'regie_contract', tool: 'regie_probe', arguments: { marker } },
          _meta: expect.objectContaining({ is_mcp_tool_call: true }),
        }),
      ]);
      const answer = updates
        .filter((u) => u.sessionUpdate === 'agent_message_chunk')
        .map((u) => (u.content as { text?: string }).text ?? '')
        .join('');
      expect(answer).toContain(receipt);
      expect(turn.filter((e) => e.type === 'turn_end').map((e) => e.data)).toEqual([
        expect.objectContaining({ stopReason: 'end_turn' }),
      ]);
      evidence[phase] = {
        calls,
        answer,
        events: turn.filter(
          (e) =>
            e.type !== 'session_update' ||
            ['tool_call', 'tool_call_update', 'agent_message_chunk'].includes(
              (e.data as AcpUpdate).sessionUpdate,
            ),
        ),
      };
    }
    await stop();
    const saved = JSON.parse(readFileSync(join(state, 'sessions.json'), 'utf8'));
    expect(saved.sessions.map((session: { status: string }) => session.status)).toEqual([
      'stopped',
      'stopped',
    ]);
    evidence.stoppedSessions = saved;
    await until(
      () =>
        records()
          .filter((r) => r.method === 'started')
          .every((r) => {
            try {
              process.kill(r.pid, 0);
              return false;
            } catch {
              return true;
            }
          }),
      Boolean,
      10_000,
    );
    evidence.allProbeProcessesStopped = true;
  } finally {
    rpc?.close();
    if (child?.exitCode === null) {
      child.kill('SIGTERM');
      await until(() => child?.exitCode !== null, Boolean, 20_000);
    }
    const output = process.env.REGIE_MCP_CONTRACT_EVIDENCE;
    if (output) {
      mkdirSync(output, { recursive: true });
      writeFileSync(join(output, 'contract.json'), `${JSON.stringify(evidence, null, 2)}\n`);
      writeFileSync(join(output, 'bridge.txt'), stderr);
    }
    rmSync(bench, { recursive: true, force: true });
  }
});
