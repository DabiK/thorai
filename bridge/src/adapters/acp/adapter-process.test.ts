import { describe, expect, it } from 'vitest';
import { adapterSpec } from './adapter-process.ts';

const binaries = { claude: '/test/claude', codex: '/test/codex' };

describe('Codex runtime configuration', () => {
  it('keeps the proot workaround on the Thor', () => {
    const spec = adapterSpec('codex', binaries, 'linux');
    expect(JSON.parse(spec.env.CODEX_CONFIG ?? '{}').developer_instructions).toContain(
      'require_escalated',
    );
  });
  it('does not tell Mac agents to bypass their working native sandbox', () => {
    const spec = adapterSpec('codex', binaries, 'darwin');
    expect(JSON.parse(spec.env.CODEX_CONFIG ?? '{}')).not.toHaveProperty('developer_instructions');
    expect(spec.env.CODEX_PATH).toBe('/test/codex');
    expect(spec.env.INITIAL_AGENT_MODE).toBe('read-only');
  });
});

it('runs the official OpenCode executable directly over ACP', () => {
  const spec = adapterSpec('opencode', { ...binaries, opencode: '/test/opencode' });
  expect(spec.binary).toBe('/test/opencode');
  expect(spec.args).toEqual(['acp']);
  expect(spec.packageName).toBeNull();
});
