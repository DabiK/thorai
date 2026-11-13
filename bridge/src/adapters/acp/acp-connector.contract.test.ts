import { execFileSync } from 'node:child_process';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { modeIdFor } from '../../domain/agents.ts';
import type { AcpUpdate, AgentKind, PermissionRequest } from '../../domain/model.ts';
import type { AgentHandlers } from '../../domain/ports.ts';
import { MemoryLogger } from '../fake/memory.ts';
import { processConnector } from './adapter-process.ts';

/**
 * Contract test: the real ACP adapters, with the real Claude Code and Codex of the Thor.
 * On demand only, in the Debian (thor/bridge/contract-test.sh): `npm run test:contract`.
 * The demo project is put back as it was at the end.
 */
const project = process.env.REGIE_CONTRACT_PROJECT ?? '/home/dev/projects/regie-demo';
const binaries = {
  claude: process.env.CLAUDE_CODE_EXECUTABLE ?? '/home/dev/.local/bin/claude',
  codex: process.env.CODEX_PATH ?? '/usr/local/bin/codex',
};
const git = (...args: string[]) =>
  execFileSync('git', ['-C', project, ...args], { encoding: 'utf8' }).trim();

let head: string;
beforeAll(() => {
  head = git('rev-parse', 'HEAD');
});
afterAll(() => {
  git('reset', '-q', '--hard', head);
  git('clean', '-qfd');
});

function recorder() {
  const updates: AcpUpdate[] = [];
  const permissions: PermissionRequest[] = [];
  const auth: boolean[] = [];
  const handlers: AgentHandlers = {
    update: (_id, update) => updates.push(update),
    permission: async (request) => {
      permissions.push(request);
      const allow = request.options.find((option) => option.kind === 'allow_once');
      return allow ? { outcome: 'selected', optionId: allow.optionId } : { outcome: 'cancelled' };
    },
    authenticated: (value) => auth.push(value),
  };
  return { handlers, updates, permissions, auth };
}

describe.each(['claude', 'codex'] as AgentKind[])('%s through its ACP adapter', (agent) => {
  const connector = processConnector(agent, binaries, { cwd: project, logger: new MemoryLogger() });

  it('reports the versions tested in docs/adr/02', async () => {
    const versions = await connector.versions();

    expect(versions.adapter?.version).toBe(versions.tested.adapter);
    expect(versions.binary).toContain(versions.tested.binary);
  });

  it('asks before writing in the « ask » mode, then goes on once allowed, and resumes', async () => {
    const first = recorder();
    const connection = await connector.connect(first.handlers);
    const sessionId = await connection.newSession(project);
    await connection.setMode(sessionId, modeIdFor(agent, 'ask'));

    const result = await connection.prompt(
      sessionId,
      `Ajoute la ligne « - [ ] contrat ${agent} » à la fin de todo.md. Ne modifie aucun autre fichier.`,
    );

    expect(result.stopReason).toBe('end_turn');
    expect(first.permissions.length).toBeGreaterThan(0);
    expect(first.updates.some((u) => u.sessionUpdate === 'agent_message_chunk')).toBe(true);
    expect(first.auth).toContain(true);
    expect(JSON.stringify(first)).not.toMatch(/@[A-Za-z0-9-]+\.[A-Za-z]{2,}/);
    await connection.close();

    // A new adapter process, as after a restart of the bridge.
    const second = recorder();
    const again = await connector.connect(second.handlers);
    await again.loadSession(sessionId, project);
    await again.setMode(sessionId, modeIdFor(agent, 'ask'));
    const answer = await again.prompt(sessionId, 'Réponds seulement « repris », sans outil.');

    expect(answer.stopReason).toBe('end_turn');
    expect(second.updates.some((u) => u.sessionUpdate === 'user_message_chunk')).toBe(true);
    await again.close();
  });
});
