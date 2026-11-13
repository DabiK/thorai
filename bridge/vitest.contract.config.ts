import { defineConfig } from 'vitest/config';

// Contract tests run the real ACP adapters, on the Thor. They are never part of `npm run check`.
export default defineConfig({
  test: {
    name: 'contract',
    environment: 'node',
    include: ['src/**/*.contract.test.ts'],
    testTimeout: 6 * 60 * 1000,
    hookTimeout: 60 * 1000,
    fileParallelism: false,
  },
});
