import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts', 'tests/**/*.test.ts'],
    // Contract tests drive the real agents: on demand only, see vitest.contract.config.ts.
    exclude: ['**/*.contract.test.ts', 'node_modules/**'],
  },
});
