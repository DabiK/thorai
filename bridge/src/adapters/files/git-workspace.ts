import { execFile } from 'node:child_process';
import { lstat, readdir, readFile, realpath, stat } from 'node:fs/promises';
import { basename, relative, resolve, sep } from 'node:path';
import { promisify } from 'node:util';
import { BridgeError, type ProjectInfo } from '../../domain/model.ts';
import type { Workspace } from '../../domain/ports.ts';

const run = promisify(execFile);

/** The project folders of `~/projects`, and their git branch. It never changes a branch. */
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

  async isValidBranchName(branch: string): Promise<boolean> {
    // `@{-1}` names the previous branch for `--branch`: not a name the agent could create.
    if (branch.includes('@{')) return false;
    try {
      await run('git', ['check-ref-format', '--branch', branch], {
        cwd: this.root,
        timeout: 15_000,
      });
      return true;
    } catch {
      return false;
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

  async vocabulary(cwd: string): Promise<readonly string[]> {
    const root = await realpath(cwd);
    const paths = (
      await git(root, ['ls-files', '-z', '--cached', '--others', '--exclude-standard'])
    )
      .split('\0')
      .filter(Boolean)
      .slice(0, 400);
    const terms = new Set<string>();
    const branches = await git(root, ['for-each-ref', '--format=%(refname:short)', 'refs/heads']);
    for (const branch of branches.split('\n').filter(Boolean).slice(0, 30)) terms.add(branch);
    for (const path of paths) {
      // Skip hidden/config secrets, symlinks (including parent directories), binary and big files.
      if (path.split('/').some((part) => part.startsWith('.'))) continue;
      const full = resolve(root, path);
      const real = await realpath(full).catch(() => null);
      if (real !== full || !real.startsWith(`${root}${sep}`)) continue;
      const info = await lstat(full).catch(() => null);
      if (!info?.isFile()) continue;
      terms.add(basename(path));
      if (info.size > 64_000 || !/\.(?:[cm]?[jt]sx?|kt|java|py|rs|go|swift|c|cpp|h)$/.test(path))
        continue;
      const source = await readFile(full, 'utf8').catch(() => '');
      const declarations =
        /\b(?:function|fun|def|fn|func)\s+([A-Za-z_][\w]*)|\b(?:const|let)\s+([A-Za-z_][\w]*)\s*=\s*(?:async\s*)?(?:\([^)]*\)|[A-Za-z_]\w*)\s*=>/g;
      for (const match of source.matchAll(declarations)) {
        const name = match[1] ?? match[2];
        if (name) terms.add(name);
      }
    }
    return [...terms].filter((term) => term.length <= 80 && !/[\r\n\0]/.test(term)).slice(0, 300);
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
        branches: (
          await git(resolve(this.root, name), [
            'for-each-ref',
            '--format=%(refname:short)',
            'refs/heads',
          ]).catch(() => '')
        )
          .split('\n')
          .filter(Boolean),
      })),
    );
  }
}

async function git(cwd: string, args: readonly string[]): Promise<string> {
  const { stdout } = await run('git', ['-C', cwd, ...args], { timeout: 15_000 });
  return stdout;
}
