import { execFileSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, realpathSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { GitWorkspace } from './git-workspace.ts';

let base: string;
let root: string;
let workspace: GitWorkspace;

function git(cwd: string, ...args: string[]) {
  execFileSync('git', ['-C', cwd, ...args], { stdio: 'pipe' });
}

beforeEach(() => {
  base = realpathSync(mkdtempSync(join(tmpdir(), 'regie-ws-')));
  root = join(base, 'projects');
  const demo = join(root, 'demo');
  mkdirSync(demo, { recursive: true });
  mkdirSync(join(root, 'plain'));
  mkdirSync(join(base, 'outside'));
  symlinkSync(join(base, 'outside'), join(root, 'escape'));
  git(demo, 'init', '-q', '-b', 'main');
  git(demo, 'config', 'user.email', 'test@example.invalid');
  git(demo, 'config', 'user.name', 'test');
  writeFileSync(join(demo, 'a.txt'), 'a\n');
  git(demo, 'add', '.');
  git(demo, 'commit', '-q', '-m', 'init');
  workspace = new GitWorkspace(root);
});
afterEach(() => {
  rmSync(base, { recursive: true, force: true });
});

describe('GitWorkspace', () => {
  it('resolves a project of the projects root', async () => {
    await expect(workspace.resolveProject('demo')).resolves.toEqual({
      name: 'demo',
      cwd: join(root, 'demo'),
    });
  });

  it.each([
    ['a parent path', '..'],
    ['a path that leaves the root', '../outside'],
    ['an absolute path', '/etc'],
    ['a link that points outside', 'escape'],
    ['the root itself', '.'],
    ['a missing folder', 'nope'],
    ['an empty name', ''],
  ])('refuses %s', async (_case, project) => {
    await expect(workspace.resolveProject(project)).rejects.toMatchObject({ code: 'bad_project' });
  });

  it('switches to an existing branch, or creates it', async () => {
    const cwd = join(root, 'demo');
    git(cwd, 'branch', 'existing');

    await workspace.switchBranch(cwd, 'existing');
    expect(await workspace.currentBranch(cwd)).toBe('existing');
    await workspace.switchBranch(cwd, 'feature/new');
    expect(await workspace.currentBranch(cwd)).toBe('feature/new');
  });

  it('fails clearly on an invalid branch name or outside a repository', async () => {
    await expect(workspace.switchBranch(join(root, 'demo'), 'bad..name')).rejects.toMatchObject({
      code: 'branch_failed',
    });
    await expect(workspace.switchBranch(join(root, 'plain'), 'x')).rejects.toMatchObject({
      code: 'branch_failed',
    });
  });

  it('lists the projects with their branch', async () => {
    await expect(workspace.listProjects()).resolves.toEqual([
      { name: 'demo', branch: 'main' },
      { name: 'plain', branch: null },
    ]);
  });
});
