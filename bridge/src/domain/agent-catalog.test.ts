import { expect, it } from 'vitest';
import { modeIdFor } from './agents.ts';
import { editableOptions } from './session-options.ts';

it('uses native OpenCode build and plan without equating build with allow-all', () => {
  expect(modeIdFor('opencode', 'ask')).toBe('build');
  expect(modeIdFor('opencode', 'auto-edit')).toBe('build');
  expect(modeIdFor('opencode', 'allow-all')).toBe('build');
  expect(modeIdFor('opencode', 'plan')).toBe('plan');
});

it('keeps OpenCode native primary-agent choices separate from product approval modes', () => {
  const choice = {
    id: 'mode',
    name: 'Session Mode',
    category: 'mode',
    currentValue: 'build',
    options: [{ value: 'build', name: 'Build' }],
  };
  expect(editableOptions([choice], 'opencode')).toEqual([choice]);
  expect(editableOptions([choice], 'codex')).toEqual([]);
});

it('classifies the OpenCode mode selector as its primary agent, not a permission level', async () => {
  const { sessionOptions } = await import('./session-options.ts');
  const raw = [
    {
      id: 'mode',
      name: 'Session Mode',
      type: 'select',
      category: 'mode',
      currentValue: 'build',
      options: [{ value: 'build', name: 'Build' }],
    },
  ];
  expect(sessionOptions(raw, 'opencode')[0]).toMatchObject({
    id: 'mode',
    category: 'agent',
    name: 'Agent opencode',
  });
});
