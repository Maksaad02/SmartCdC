import { defineConfig, devices } from "@playwright/test";

/**
 * Tests de bout en bout : un vrai navigateur contre la pile Docker complete (docker compose up).
 *
 *   E2E_BASE_URL=http://localhost:8080 E2E_ADMIN_EMAIL=... E2E_ADMIN_PASSWORD=... npm run e2e
 *
 * Le chatbot est simule (page.route) : les tests ne doivent ni couter de jetons OpenAI ni dependre
 * d'un fournisseur externe.
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false, // les parcours partagent la meme base
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:8080",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
