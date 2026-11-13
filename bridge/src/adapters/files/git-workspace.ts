import { execFile } from 'node:child_process';
import { readdir, realpath, stat } from 'node:fs/promises';
import { relative, resolve, sep } from 'node:path';
import { promisify } from 'node:util';
import { BridgeError, type ProjectInfo } from '../../domain/model.ts';
import type { Workspace } from '../../domain/ports.ts';

const run = promisify(execFile);

/** The project folders of `~/projects`, and their git branch. */
export class GitWorkspace implements Workspace {
  private readonly root: string;

  constructor(root: string) {
    this.root = root;
  }

  async resolveProject(project: string): Promise<{ name: string; cwd: string }> {
    const refuse = () =>
      new BridgeError('bad_project', `Projet introuvable dans ~/projects : ${project}.`);
    if (project.trim() === '' || project.includes('\0')) throw refuse();
    const root = await realpath(this.root);
    const cwd = await realpath(resolve(root, project)).catch(() => {
      throw refuse();
    });
    const name = relative(root, cwd);
    // Symbolic links included: the real path must stay inside the projects root.
    if (
      name === '' ||
      name.startsWith('..') ||
      name.startsWith(sep) ||
      cwd !== resolve(root, name)
    ) {
      throw refuse();
    }
    if (!(await stat(cwd)).isDirectory()) throw refuse();
    return { name, cwd };
  }

  async switchBranch(cwd: string, branch: string): Promise<void> {
    const failed = (detail: string) =>
      new BridgeError('branch_failed', `Impossible de passer sur la branche ${branch} : ${detail}`);
    try {
      await git(cwd, ['check-ref-format', '--branch', branch]);
    } catch {
      throw failed('nom de branche invalide.');
    }
    const exists = await git(cwd, [
      'rev-parse',
      '--verify',
      '--quiet',
      `refs/heads/${branch}`,
    ]).then(
      () => true,
      () => false,
    );
    try {
      await git(cwd, exists ? ['switch', branch] : ['switch', '-c', branch]);
    } catch (error) {
      throw failed(firstLine(error));
    }
  }

  async currentBranch(cwd: string): Promise<string | null> {
    try {
      const name = (await git(cwd, ['symbolic-ref', '--quiet', '--short', 'HEAD'])).trim();
      return name === '' ? null : name;
    } catch {
      return null;
    }
  }

  async listProjects(): Promise<readonly ProjectInfo[]> {
    const entries = await readdir(this.root, { withFileTypes: true }).catch(() => []);
    const projects = entries
      .filter((entry) => entry.isDirectory() && !entry.name.startsWith('.'))
      .map((entry) => entry.name)
      .sort();
    return Promise.all(
      projects.map(async (name) => ({
        name,
        branch: await this.currentBranch(resolve(this.root, name)),
      })),
    );
  }
}

async function git(cwd: string, args: readonly string[]): Promise<string> {
  const { stdout } = await run('git', ['-C', cwd, ...args], { timeout: 15_000 });
  return stdout;
}

function firstLine(error: unknown): string {
  const stderr = (error as { stderr?: unknown }).stderr;
  const text = typeof stderr === 'string' && stderr.trim() !== '' ? stderr : String(error);
  return (
    text
      .trim()
      .split('\n')
      .find((line) => line.trim() !== '') ?? text
  );
}
