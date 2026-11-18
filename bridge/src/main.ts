/**
 * The bridge process: the only composition root. Run in the Debian of the Thor, as `dev`:
 *
 *   node src/main.ts
 *
 * Settings, all optional (docs/protocole-pont.md, « Démarrage et arrêt »):
 * - REGIE_BRIDGE_PORT (47600), the bridge listens on 127.0.0.1 only;
 * - REGIE_BRIDGE_TOKEN_FILE (~/.config/regie/bridge-token);
 * - REGIE_BRIDGE_STATE_DIR (~/.local/state/regie/bridge): sessions, events, pid;
 * - REGIE_PROJECTS_DIR (~/projects);
 * - REGIE_BRIDGE_RETENTION_DAYS (30): days the events of a session are kept after its last
 *   activity; then only its summary stays. A value that is not a positive number stops the
 *   bridge at start;
 * - CLAUDE_CODE_EXECUTABLE (~/.local/bin/claude), CODEX_PATH (/usr/local/bin/codex), OPENCODE_PATH (~/.opencode/bin/opencode).
 */

import { randomUUID } from 'node:crypto';
import { readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { processConnector } from './adapters/acp/adapter-process.ts';
import { CliAuthentication } from './adapters/auth/cli-authentication.ts';
import { FileEventStore, FileSessionStore } from './adapters/files/file-stores.ts';
import { GitWorkspace } from './adapters/files/git-workspace.ts';
import { lockStateDir } from './adapters/files/state-lock.ts';
import { StderrLogger } from './adapters/logging/logger.ts';
import { runNowThenDaily } from './adapters/schedule/daily.ts';
import { ShellQuoteParser } from './adapters/shell/shell-quote-parser.ts';
import { listenWs } from './adapters/ws/ws-server.ts';
import { Authentication } from './domain/authentication.ts';
import { Bridge, DEFAULT_RETENTION_DAYS } from './domain/bridge.ts';
import { AGENT_KINDS } from './domain/model.ts';

export const DEFAULT_PORT = 47600;

const env = process.env;
const home = homedir();
const port = Number(env.REGIE_BRIDGE_PORT ?? DEFAULT_PORT);
const tokenFile = env.REGIE_BRIDGE_TOKEN_FILE ?? join(home, '.config/regie/bridge-token');
const stateDir = env.REGIE_BRIDGE_STATE_DIR ?? join(home, '.local/state/regie/bridge');
const projectsDir = env.REGIE_PROJECTS_DIR ?? join(home, 'projects');
const retentionSetting = env.REGIE_BRIDGE_RETENTION_DAYS ?? String(DEFAULT_RETENTION_DAYS);
const binaries = {
  claude: env.CLAUDE_CODE_EXECUTABLE ?? join(home, '.local/bin/claude'),
  codex: env.CODEX_PATH ?? '/usr/local/bin/codex',
  opencode: env.OPENCODE_PATH ?? join(home, '.opencode/bin/opencode'),
};
const version = (
  JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8')) as {
    version: string;
  }
).version;

const logger = new StderrLogger();

function readToken(): string {
  let token: string;
  try {
    if ((statSync(tokenFile).mode & 0o077) !== 0) {
      throw new Error(`${tokenFile} is readable by others: chmod 600 it`);
    }
    token = readFileSync(tokenFile, 'utf8').trim();
  } catch (error) {
    throw new Error(`no token: ${(error as Error).message}. Run thor/bridge/install.sh.`);
  }
  if (token.length < 32) throw new Error(`token too short in ${tokenFile}`);
  return token;
}

function readRetentionDays(): number {
  const days = Number(retentionSetting);
  if (retentionSetting.trim() === '' || !Number.isFinite(days) || days <= 0) {
    throw new Error(`REGIE_BRIDGE_RETENTION_DAYS must be a positive number of days`);
  }
  return days;
}

async function main(): Promise<void> {
  const token = readToken();
  const retentionDays = readRetentionDays();
  // A second bridge stops here, before reading or writing the journal: first the state
  // folder, then the port.
  const unlock = lockStateDir(stateDir);
  process.on('exit', unlock);
  const server = await listenWs({
    port,
    token,
    logger,
    info: { version, pid: process.pid, startedAt: new Date().toISOString() },
  });
  const bridge = new Bridge({
    codexSandbox: process.platform === 'darwin' ? 'native' : 'proot',
    connectors: AGENT_KINDS.map((agent) =>
      processConnector(agent, binaries, { cwd: projectsDir, logger }),
    ),
    events: new FileEventStore(stateDir),
    sessions: new FileSessionStore(stateDir),
    workspace: new GitWorkspace(projectsDir),
    clock: { now: () => new Date() },
    ids: () => randomUUID(),
    logger,
    shell: new ShellQuoteParser(),
    retentionDays,
  });
  bridge.start();
  const authentication = new Authentication(
    new CliAuthentication(binaries),
    (agent) => bridge.acquireAuthentication(agent),
    AGENT_KINDS,
  );
  // The old events are purged at start, then once a day.
  runNowThenDaily('purge', () => bridge.purge(), logger);

  let stopping = false;
  const pidFile = join(stateDir, 'bridge.pid');
  const stop = async (reason: string) => {
    if (stopping) return;
    stopping = true;
    logger.info(`stopping (${reason})`);
    await authentication.close();
    await bridge.shutdown();
    await server.close();
    rmSync(pidFile, { force: true });
    logger.info('stopped');
    process.exit(0);
  };

  server.attach({ bridge, authentication, onShutdown: () => void stop('bridge.shutdown') });
  writeFileSync(pidFile, `${process.pid}\n`, { mode: 0o600 });
  logger.info(`bridge ${version} ready, pid ${process.pid}, last seq ${bridge.lastSeq}`);
  for (const signal of ['SIGTERM', 'SIGINT', 'SIGHUP'] as const) {
    process.on(signal, () => void stop(signal));
  }
}

main().catch((error: unknown) => {
  logger.error(`bridge failed: ${error instanceof Error ? error.stack : String(error)}`);
  process.exit(1);
});
