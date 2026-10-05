import { defineConfig } from '@playwright/test';

// Browser end-to-end tests against the packaged jar (build it first: `mvn package` in the repo
// root). The jar runs on its own port so a game you have open on :7070 is not touched.
// One-time setup on a new machine: `npx playwright install chromium`
// (or set CHROMIUM_PATH to an existing Chromium).
export default defineConfig({
  testDir: 'e2e',
  timeout: 60_000,
  workers: 1, // one server, one shared game
  use: {
    baseURL: 'http://127.0.0.1:7071',
    viewport: { width: 1280, height: 860 },
    launchOptions: process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {},
  },
  webServer: {
    // first a tiny evolution run for the lab page to show (lab.spec.ts), then the server
    command: 'node -e "for (const d of [\'../target/e2e-runs\', \'../target/e2e-games\', \'../target/e2e-variants\']) require(\'fs\').rmSync(d, { recursive: true, force: true })"'
      + ' && java -cp ../target/izikstar-chess-3.1.0.jar lab.Cli run ../target/e2e-runs/demo.db --name Demo'
      + ' --generations 2 --depth 1 --openings-per-pairing 1 --yardstick-every 1 --yardstick-openings 2 --threads 2'
      + ' && java -jar ../target/izikstar-chess-3.1.0.jar --port 7071 --no-browser --runs ../target/e2e-runs --games ../target/e2e-games --variants ../target/e2e-variants',
    url: 'http://127.0.0.1:7071/',
    reuseExistingServer: false,
    timeout: 90_000,
  },
});
