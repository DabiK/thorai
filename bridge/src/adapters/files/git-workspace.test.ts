import { execFileSync } from 'node:child_process';
import {
  mkdirSync,
  mkdtempSync,
  readdirSync,
  readFileSync,
  realpathSync,
  rmSync,
  symlinkSync,
  writeFileSync,
} from 'node:fs';
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

  it('accepts a valid branch name, and refuses an invalid one', async () => {
    await expect(workspace.isValidBranchName('feature/new')).resolves.toBe(true);
    await expect(workspace.isValidBranchName('ticket/13-ajustements')).resolves.toBe(true);
    for (const name of ['bad..name', '-x', 'a b', 'x~1', 'x:y', 'fin/', '@{-1}', 'a.lock']) {
      await expect(workspace.isValidBranchName(name)).resolves.toBe(false);
    }
  });

  it('reads the branch of a repository, and none outside one', async () => {
    const cwd = join(root, 'demo');
    git(cwd, 'switch', '-q', '-c', 'feature/x');

    await expect(workspace.currentBranch(cwd)).resolves.toBe('feature/x');
    await expect(workspace.currentBranch(join(root, 'plain'))).resolves.toBeNull();
  });

  it('lists the projects with their branch', async () => {
    await expect(workspace.listProjects()).resolves.toEqual([
      { name: 'demo', branch: 'main', branches: ['main'] },
      { name: 'plain', branch: null, branches: [] },
    ]);
  });
});

it('reads files functions and branches without following links or reading ignored secrets', async () => {
  const cwd = join(root, 'demo');
  writeFileSync(join(cwd, 'PassageCheck.ts'), 'export function minPassage() { return 70; }');
  writeFileSync(join(cwd, '.gitignore'), '.env\n');
  writeFileSync(join(cwd, '.env'), 'function secretName() {}');
  writeFileSync(join(base, 'outside', 'external.ts'), 'function externalSecret() {}');
  symlinkSync(join(base, 'outside', 'external.ts'), join(cwd, 'link.ts'));
  git(cwd, 'branch', 'feature/passage');
  const terms = await workspace.vocabulary(cwd);
  expect(terms).toEqual(
    expect.arrayContaining(['PassageCheck.ts', 'minPassage', 'main', 'feature/passage']),
  );
  expect(terms).not.toContain('secretName');
  expect(terms).not.toContain('externalSecret');
});

it('lists all local branch choices without changing HEAD or the worktree', async () => {
  const cwd = join(root, 'demo');
  git(cwd, 'branch', 'feature/existing');
  writeFileSync(join(cwd, 'a.txt'), 'uncommitted work');
  await expect(workspace.listProjects()).resolves.toContainEqual({
    name: 'demo',
    branch: 'main',
    branches: ['feature/existing', 'main'],
  });
  expect(
    execFileSync('git', ['-C', cwd, 'symbolic-ref', '--short', 'HEAD'], {
      encoding: 'utf8',
    }).trim(),
  ).toBe('main');
  expect(execFileSync('git', ['-C', cwd, 'diff'], { encoding: 'utf8' })).toContain(
    'uncommitted work',
  );
});

it('reserves a new empty repository on main and never overwrites a collision', async () => {
  const project = await workspace.createProject('meteo-du-jour', 'main');
  expect(project).toEqual({ name: 'meteo-du-jour', cwd: join(root, 'meteo-du-jour') });
  expect(readdirSync(project.cwd)).toEqual(['.git']);
  expect(await workspace.currentBranch(project.cwd)).toBe('main');
  const attempts = await Promise.allSettled([
    workspace.createProject('race', 'main'),
    workspace.createProject('race', 'main'),
  ]);
  expect(attempts.filter((it) => it.status === 'fulfilled')).toHaveLength(1);
  expect(attempts.find((it) => it.status === 'rejected')).toMatchObject({
    reason: { code: 'project_exists' },
  });
  for (const name of ['demo', 'plain', 'escape']) {
    await expect(workspace.createProject(name, 'main')).rejects.toMatchObject({
      code: 'project_exists',
    });
  }
  expect(readFileSync(join(root, 'demo', 'a.txt'), 'utf8')).toBe('a\n');
  expect(readdirSync(join(base, 'outside'))).toEqual([]);
});
