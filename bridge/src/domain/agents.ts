import type { AgentKind, ApprovalMode } from './model.ts';

/**
 * The ACP mode each approval level maps to (docs/adr/02).
 *
 * - Claude starts in `auto`, the user's own setting: the mode is forced after every
 *   `session/new` and `session/load`.
 * - Codex `ask` is `workspace-write`, with the instruction that avoids its sandbox (set by the
 *   ACP adapter). Its `agent` mode is never used.
 */
const MODE_IDS: Record<AgentKind, Record<ApprovalMode, string>> = {
  claude: { ask: 'default', 'allow-all': 'bypassPermissions' },
  codex: { ask: 'workspace-write', 'allow-all': 'agent-full-access' },
};

export function modeIdFor(agent: AgentKind, mode: ApprovalMode): string {
  return MODE_IDS[agent][mode];
}

/** Versions the bridge was tested with on the Thor (docs/adr/02). */
export const TESTED_VERSIONS: Record<AgentKind, { adapter: string; binary: string }> = {
  claude: { adapter: '0.107.0', binary: '2.1.322' },
  codex: { adapter: '2.6.0', binary: '0.167.2' },
};

/** Whether `--version` output names the tested version. */
export function isTestedBinary(agent: AgentKind, versionOutput: string | null): boolean {
  if (versionOutput === null) return false;
  const found = /\d+\.\d+\.\d+/.exec(versionOutput);
  return found?.[0] === TESTED_VERSIONS[agent].binary;
}

export const AGENT_LABELS: Record<AgentKind, string> = { claude: 'Claude', codex: 'Codex' };
