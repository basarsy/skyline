import { defineConfig, devices } from '@playwright/test'

/**
 * Two modes:
 * - default: the app with its API mocked by MSW in the browser (vite --mode msw). Self-contained.
 * - E2E_LIVE=1: the real stack (docker compose up) through the dev server's /api proxy. Needs
 *   SKYLINE_ADMIN_EMAIL / SKYLINE_ADMIN_PASSWORD (the values the auth service was started with).
 */
const live = !!process.env.E2E_LIVE
const port = live ? 5173 : 5175
const baseURL = process.env.E2E_BASE_URL ?? `http://127.0.0.1:${port}`

export default defineConfig({
  testDir: './e2e',
  fullyParallel: !live,
  workers: live ? 1 : undefined,
  retries: process.env.CI ? 1 : 0,
  timeout: live ? 90_000 : 30_000,
  expect: { timeout: live ? 15_000 : 5_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: live
          ? `pnpm exec vite --host 127.0.0.1 --port ${port} --strictPort`
          : `pnpm exec vite --mode msw --host 127.0.0.1 --port ${port} --strictPort`,
        url: baseURL,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
})
