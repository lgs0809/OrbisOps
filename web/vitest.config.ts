import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    maxWorkers: 2,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    clearMocks: true,
    restoreMocks: true,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      include: [
        'src/features/chat/chat-run-summary.ts',
        'src/features/execution/execution-center-model.ts',
        'src/utils/project-context.ts',
        'src/services/auth-session.ts',
        'src/services/ops-http-client.ts',
        'src/services/ops-change-package-service.ts',
      ],
      thresholds: {
        statements: 75,
        branches: 55,
        functions: 80,
        lines: 75,
      },
    },
  },
});
