import { execFile, spawn } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { Readable, Writable } from 'node:stream';
import { promisify } from 'node:util';
import * as acp from '@agentclientprotocol/sdk/unstable';
import { modeIdFor, TESTED_VERSIONS } from '../../domain/agents.ts';
import type { AgentKind } from '../../domain/model.ts';
import type { AgentConnector, Logger } from '../../domain/ports.ts';
import { redact } from '../../domain/redact.ts';
import { AcpConnector, type AcpTransport, type Exit } from './acp-connector.ts';

const require = createRequire(import.meta.url);
const run = promisify(execFile);

/**
 * The consigne that keeps Codex out of its sandbox, which cannot work under proot
 * (docs/adr/02, « Modes de validation de Codex »).
 */
const CODEX_DEVELOPER_INSTRUCTIONS =
  'Sur cette machine, le sandbox Linux (bubblewrap) est indisponible : toute commande lancée ' +
  'dans le sandbox échoue. Lance donc chaque commande shell directement avec ' +
  'sandbox_permissions="require_escalated" et une justification courte, sans essai préalable ' +
  'dans le sandbox.';

export interface AgentBinaries {
  /** Claude Code of the console, not the one embedded in the adapter. */
  readonly claude: string;
  /** Codex of the console, not the one embedded in the adapter. */
  readonly codex: string;
}

interface AdapterSpec {
  readonly packageName: string;
  readonly binary: string;
  readonly env: Readonly<Record<string, string>>;
}

export function adapterSpec(
  agent: AgentKind,
  binaries: AgentBinaries,
  platform: NodeJS.Platform = process.platform,
): AdapterSpec {
  if (agent === 'claude') {
    return {
      packageName: '@agentclientprotocol/claude-agent-acp',
      binary: binaries.claude,
      env: { CLAUDE_CODE_EXECUTABLE: binaries.claude, NO_BROWSER: '1' },
    };
  }
  return {
    packageName: '@agentclientprotocol/codex-acp',
    binary: binaries.codex,
    env: {
      CODEX_PATH: binaries.codex,
      NO_BROWSER: '1',
      // Mode of the sessions this process loads, until the bridge sets theirs.
      INITIAL_AGENT_MODE: modeIdFor('codex', 'ask', platform === 'darwin' ? 'native' : 'proot'),
      CODEX_CONFIG: JSON.stringify(
        platform === 'darwin' ? {} : { developer_instructions: CODEX_DEVELOPER_INSTRUCTIONS },
      ),
    },
  };
}

/** The ACP connector of an agent, its adapter run as a child process of the bridge. */
export function processConnector(
  agent: AgentKind,
  binaries: AgentBinaries,
  options: { readonly cwd: string; readonly logger: Logger },
): AgentConnector {
  const spec = adapterSpec(agent, binaries);
  const packageJson = require.resolve(`${spec.packageName}/package.json`);
  const manifest = JSON.parse(readFileSync(packageJson, 'utf8')) as {
    version: string;
    bin: Record<string, string>;
  };
  const entry = join(dirname(packageJson), Object.values(manifest.bin)[0] ?? 'dist/index.js');
  let binaryVersion: Promise<string | null> | null = null;
  return new AcpConnector({
    agent,
    logger: options.logger,
    open: () => spawnAdapter(agent, entry, spec.env, options),
    versions: async () => {
      binaryVersion ??= run(spec.binary, ['--version'], { timeout: 30_000 }).then(
        ({ stdout }) => stdout.trim(),
        () => null,
      );
      const binary = await binaryVersion;
      if (binary === null) binaryVersion = null;
      return {
        agent,
        adapter: { name: spec.packageName, version: manifest.version },
        binary,
        tested: TESTED_VERSIONS[agent],
        isTested: false,
      };
    },
  });
}

function spawnAdapter(
  agent: AgentKind,
  entry: string,
  env: Readonly<Record<string, string>>,
  options: { readonly cwd: string; readonly logger: Logger },
): AcpTransport {
  const { logger } = options;
  const child = spawn(process.execPath, [entry], {
    cwd: options.cwd,
    env: { ...process.env, ...env },
    stdio: ['pipe', 'pipe', 'pipe'],
  });
  logger.info(`${agent}: adapter started, pid ${child.pid}`);
  let pending = '';
  child.stderr.setEncoding('utf8').on('data', (chunk: string) => {
    pending += chunk;
    const lines = pending.split('\n');
    pending = lines.pop() ?? '';
    for (const line of lines) if (line.trim()) logger.info(`${agent} stderr: ${redact(line)}`);
  });
  const exited = new Promise<Exit>((resolve) => {
    child.once('exit', (code, signal) => resolve({ code, signal }));
    child.once('error', (error) => {
      logger.error(`${agent}: adapter failed: ${error.message}`);
      resolve({ code: null, signal: null });
    });
  });
  const alive = () => child.exitCode === null && child.signalCode === null;
  const waitExit = (ms: number) =>
    Promise.race([exited, new Promise((resolve) => setTimeout(resolve, ms))]);
  const stream = acp.ndJsonStream(
    Writable.toWeb(child.stdin) as WritableStream<Uint8Array>,
    Readable.toWeb(child.stdout) as ReadableStream<Uint8Array>,
  );
  return {
    stream,
    exited,
    close: async () => {
      if (!alive()) return;
      child.stdin.end();
      await waitExit(4000);
      if (alive()) child.kill('SIGTERM');
      await waitExit(3000);
      if (alive()) child.kill('SIGKILL');
      await exited;
    },
  };
}
