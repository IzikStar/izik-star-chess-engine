import { expect, test, type Page } from '@playwright/test';

// Every chart opens large (ChartFrame.tsx), over the tiny chess run playwright.config.ts records;
// and the New run form folds its less used settings.

async function openRun(page: Page) {
  await page.goto('/#lab/run/demo.db');
  const run = page.getByTestId('run');
  await expect(run.getByRole('img', { name: /Champion Elo/ })).toBeVisible();
  return run;
}

test('a run chart opens large with axes and a tooltip, and Esc closes it', async ({ page }) => {
  const run = await openRun(page);
  const elo = run.locator('.chart-frame').filter({ has: page.getByRole('img', { name: /Champion Elo/ }) });
  await elo.hover();
  await elo.getByRole('button', { name: 'Open chart large' }).click();

  const dialog = page.getByRole('dialog', { name: /Champion against/ });
  await expect(dialog).toBeVisible();
  const chart = dialog.getByRole('img', { name: /large/ });
  await expect(chart).toBeVisible();
  await expect(chart).toContainText('Generation');
  await expect(chart).toContainText('Elo against the yardstick');
  // it fills most of the window
  const box = (await chart.boundingBox())!;
  expect(box.width).toBeGreaterThan(900);
  expect(box.height).toBeGreaterThan(500);
  // the value under the pointer
  await chart.hover({ position: { x: box.width - 30, y: box.height / 2 } });
  await expect(dialog.getByTestId('chart-tip')).toContainText(/Generation 1: [+-]?\d+ Elo/);
  await page.screenshot({ path: '../target/e2e-screens/chart-large.png' });

  await page.keyboard.press('Escape');
  await expect(dialog).toBeHidden();

  // a small series chart opens on a click anywhere on it, and its close button closes it
  await run.getByRole('img', { name: 'Decisive games by generation' }).click();
  const series = page.getByRole('dialog', { name: 'Decisive games by generation' });
  await expect(series.getByRole('img', { name: /large/ })).toContainText('Decisive games (%)');
  await series.getByRole('button', { name: 'Close' }).click();
  await expect(series).toBeHidden();
});

test("a weight's sparkline opens as a chart of the weight over the generations", async ({ page }) => {
  const run = await openRun(page);
  await run.getByRole('button', { name: 'Weights', exact: true }).click();
  const weights = page.getByTestId('weights');
  await expect(weights).toBeVisible();
  const first = weights.locator('tbody tr').first();
  const name = (await first.locator('td').first().textContent())!;
  await first.locator('.spark').click();

  const dialog = page.getByRole('dialog', { name: `${name} over the generations` });
  await expect(dialog).toBeVisible();
  const chart = dialog.getByRole('img', { name: /large/ });
  await expect(chart).toContainText('Generation');
  await expect(chart).toContainText('default');
  const box = (await chart.boundingBox())!;
  await chart.hover({ position: { x: 80, y: box.height / 2 } });
  await expect(dialog.getByTestId('chart-tip')).toContainText(/Generation 0: -?\d+/);
  // a click on the backdrop closes it
  await page.mouse.click(4, 4);
  await expect(dialog).toBeHidden();
});

test('the New run form folds the advanced settings and opens them on request', async ({ page }) => {
  await page.goto('/#lab/new');
  const form = page.getByTestId('new-run');
  // the essentials are in sight
  for (const label of ['Name', 'Game', 'Algorithm', 'Population', 'Generations', 'Search depth', 'Threads', 'All-zero weights']) {
    await expect(form.getByLabel(label, { exact: true })).toBeVisible();
  }
  await expect(form).toContainText('What it adds up to');
  const advanced = form.getByTestId('advanced-settings');
  await expect(advanced).not.toHaveAttribute('open');
  await expect(form.getByLabel('Seed', { exact: true })).toBeHidden();
  await expect(form.getByLabel('Deep depth', { exact: true })).toBeHidden();

  await advanced.getByText('Advanced settings').click();
  await expect(form.getByLabel('Seed', { exact: true })).toBeVisible();
  await expect(form.getByLabel('Move limit (plies)', { exact: true })).toBeVisible();
  await expect(form.getByLabel('Yardstick openings', { exact: true })).toBeVisible();
  // a folded setting still counts: 12 members, 66 pairings, 3 openings with both colours
  await form.getByLabel('Openings per pairing', { exact: true }).fill('3');
  await expect(form).toContainText('66 pairings → 396 games a generation');
});

test('the analysis graph opens large and a click in it goes to that position', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'New game' }).click();
  const newGame = page.getByRole('dialog', { name: 'New game' });
  await newGame.getByRole('button', { name: 'A friend', exact: true }).click();
  await newGame.getByRole('button', { name: 'Start game' }).click();
  await expect(newGame).toBeHidden();
  const moves = [['e2', 'e4'], ['e7', 'e5'], ['f1', 'c4'], ['b8', 'c6'], ['d1', 'h5'], ['g8', 'f6'], ['h5', 'f7']];
  for (const [i, [from, to]] of moves.entries()) {
    await page.locator(`[data-square="${from}"]`).first().click();
    await page.locator(`[data-square="${to}"]`).first().click();
    await expect(page.getByTestId('move-list').getByRole('button')).toHaveCount(i + 1);
  }
  await page.getByTestId('result').getByRole('button', { name: 'Analyse game' }).click();
  const analysis = page.getByTestId('analysis');
  await expect(analysis.getByTestId('analysis-white')).toBeVisible({ timeout: 30_000 });

  await analysis.getByTestId('eval-graph').hover();
  await analysis.getByRole('button', { name: 'Open chart large' }).click();
  const dialog = page.getByRole('dialog', { name: "White's winning chances" });
  const chart = dialog.getByRole('img', { name: /large/ });
  await expect(chart).toContainText('Ply (half-move)');
  const box = (await chart.boundingBox())!;
  await chart.hover({ position: { x: box.width - 25, y: box.height / 2 } });
  await expect(dialog.getByTestId('chart-tip')).toContainText('4. Qxf7#');
  await chart.click({ position: { x: 75, y: box.height / 2 } });
  await expect(page.getByTestId('analysis-move')).toContainText('Start position');
  await page.keyboard.press('Escape');
  await expect(dialog).toBeHidden();
});
