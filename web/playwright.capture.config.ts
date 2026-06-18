import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './capture',
  timeout: 30_000,
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  outputDir: './test-results/readme-capture',
  use: {
    baseURL: 'http://127.0.0.1:4173',
    trace: 'off',
    screenshot: 'off',
    viewport: { width: 1440, height: 960 },
  },
  projects: [
    {
      name: 'chrome',
      use: { ...devices['Desktop Chrome'], channel: 'chrome', viewport: { width: 1440, height: 960 } },
    },
  ],
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port 4173',
    url: 'http://127.0.0.1:4173',
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
