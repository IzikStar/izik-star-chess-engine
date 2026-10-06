import { expect, test } from '@playwright/test';

// The variant health check (variants/Health.tsx): a tiny self-play run of a built-in variant with
// a few advanced settings, then the report's sections: the readings, results by colour, endings,
// length, choices, captures and material, the pieces, and sample games.

const SHOTS = '../target/e2e-screens';

test('the health check runs with advanced settings and shows the statistics', async ({ page }) => {
  await page.goto('/#variants');
  await page.getByTestId('variant-list').getByRole('button', { name: /Antichess/ }).click();
  await expect(page.getByTestId('variant-editor').getByRole('heading', { name: 'Antichess' })).toBeVisible();
  const health = page.getByTestId('health');
  await health.getByLabel('Games', { exact: true }).selectOption('20');
  await health.getByLabel('Depth', { exact: true }).selectOption('1');

  await health.getByText('Advanced').click();
  await health.getByLabel("Black's depth").selectOption('2');
  await health.getByLabel('Move limit (plies)').fill('200');
  await health.getByLabel('Random opening plies').fill('6');
  await health.getByLabel('Seed').fill('7');
  await health.getByRole('button', { name: 'Run' }).click();

  const report = page.getByTestId('health-report');
  await expect(report).toBeVisible({ timeout: 60_000 });
  await expect(report).toContainText('20 games, White depth 1, Black depth 2, 6 random plies, limit 200 plies, seed 7');
  await expect(page.getByTestId('health-summary')).toContainText(/scores \d+% ± \d+%/);
  await expect(page.getByTestId('health-results')).toContainText('White wins');
  await expect(page.getByTestId('health-results').getByRole('img', { name: /Results by colour/ })).toBeVisible();
  await expect(page.getByTestId('health-endings')).toContainText('Lost every piece');
  await expect(page.getByTestId('health-lengths').getByRole('img', { name: /Games by length/ })).toBeVisible();
  await expect(page.getByTestId('health-lengths')).toContainText('All games');
  await expect(page.getByTestId('health-branching').getByRole('img', { name: /legal moves by ply/ })).toBeVisible();
  await expect(page.getByTestId('health-play')).toContainText('First capture');
  await expect(page.getByTestId('health-pieces').getByRole('row', { name: /Pawn/ })).toBeVisible();

  const samples = page.getByTestId('health-samples');
  await samples.locator('summary').first().click();
  await expect(samples.locator('.hc-moves').first()).toContainText('1. ');
  await page.screenshot({ path: `${SHOTS}/health-report.png`, fullPage: true });
});
