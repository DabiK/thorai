import type { AgentKind, ApprovalMode } from './model.ts';

/**
 * The ACP mode each approval level maps to (docs/adr/02).
 *
 * - Claude starts in `auto`, the user's own setting: the mode is forced after every
 *   `session/new` and `session/load`.
 * - Under PRoot, Codex uses `workspace-write` and the adapter's sandbox workaround.
 *   With a native sandbox, product modes use `read-only` so writes reach the bridge.
 *   Its `agent` mode is never used.
 */
const MODE_IDS: Record<AgentKind, Record<ApprovalMode, string>> = {
  claude: {
    plan: 'plan',
    'auto-edit': 'acceptEdits',
    ask: 'default',
    'allow-all': 'bypassPermissions',
  },
  opencode: { plan: 'plan', 'auto-edit': 'build', ask: 'build', 'allow-all': 'build' },
  codex: {
    plan: 'workspace-write',
    'auto-edit': 'workspace-write',
    ask: 'workspace-write',
    'allow-all': 'agent-full-access',
  },
};

export type CodexSandbox = 'native' | 'proot';

export function modeIdFor(
  agent: AgentKind,
  mode: ApprovalMode,
  codexSandbox: CodexSandbox = 'proot',
): string {
  // Native workspace-write bypasses the bridge for both shell writes and edits.
  // Auto-edit still starts read-only: only the domain may approve structured edits.
  if (agent === 'codex' && codexSandbox === 'native' && mode !== 'allow-all') return 'read-only';
  return MODE_IDS[agent]?.[mode] ?? mode;
}

/** Versions the bridge was tested with on the Thor (docs/adr/02). */
export const TESTED_VERSIONS: Record<AgentKind, { adapter: string; binary: string }> = {
  claude: { adapter: '0.107.0', binary: '2.1.322' },
  codex: { adapter: '2.6.0', binary: '0.167.2' },
  opencode: { adapter: '2.1.2', binary: '2.1.2' },
};

/** Whether `--version` output names the tested version. */
export function isTestedBinary(agent: AgentKind, versionOutput: string | null): boolean {
  if (versionOutput === null) return false;
  const found = /\d+\.\d+\.\d+/.exec(versionOutput);
  return found?.[0] === TESTED_VERSIONS[agent]?.binary;
}

export const AGENT_LABELS: Record<AgentKind, string> = {
  claude: 'Claude',
  codex: 'Codex',
  opencode: 'opencode',
};

export interface LoginDescription {
  readonly method: 'device_code' | 'manual' | 'api_key';
  readonly label: string;
  readonly help: string;
  readonly provider?: string;
}
export function loginDescription(agent: AgentKind): LoginDescription {
  if (agent === 'opencode')
    return {
      method: 'api_key',
      provider: 'opencode-go',
      label: 'Clé OpenCode Go',
      help: 'Saisis ta clé OpenCode Go. Opencode la conserve sur cet appareil. Les autres fournisseurs se configurent avec opencode auth login dans Termux.',
    };
  if (agent === 'codex')
    return {
      method: 'device_code',
      label: 'Connecter',
      help: 'Connecte ton compte ChatGPT depuis ton téléphone. Si le site le demande, active la connexion par code d’appareil dans les réglages de sécurité ChatGPT.',
    };
  return {
    method: 'manual',
    label: 'Marche à suivre',
    help: 'Connecte cet agent dans Termux, puis actualise.',
  };
}

/** OpenCode must request writes; the common domain decides ask/auto-edit/allow-all per session. */
export const OPENCODE_BRIDGE_CONFIGURATION = {
  permissions: [
    { action: 'shell', resource: '*', effect: 'ask' },
    { action: 'edit', resource: '*', effect: 'ask' },
    { action: 'thor_android_*', resource: '*', effect: 'allow' },
    { action: 'shell', resource: 'adb *', effect: 'allow' },
    { action: 'shell', resource: 'thor-screen *', effect: 'allow' },
  ],
};
