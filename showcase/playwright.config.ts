import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  timeout: 45_000,
  expect: { timeout: 10_000 },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: "http://127.0.0.1:3100",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  webServer: {
    command: "corepack pnpm exec next dev --port 3100",
    url: "http://127.0.0.1:3100/login",
    reuseExistingServer: !process.env.CI,
    timeout: 90_000,
    env: { ...process.env, NEXT_PUBLIC_AGENT_API_BASE_URL: "http://127.0.0.1:3100" },
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});