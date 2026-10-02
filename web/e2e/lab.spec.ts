import { expect, test } from '@playwright/test';

// The lab page over the tiny run playwright.config.ts records before starting the server.

test('the lab shows a run, replays a game and starts a game against its champion', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Lab' }).click();
  await expect(page).toHaveURL(/#lab$/);

  const run = page.getByTestId('run');
  await expect(run.getByRole('heading', { name: 'Demo' })).toBeVisible();
  await expect(run.getByText('2/2 generations')).toBeVisible();
  await expect(run.getByRole('img', { name: /Champion Elo/ })).toBeVisible();

  // generation 1: 28 pairings × 2 colours, then the champion against the default weights
  const games = page.getByTestId('games').locator('tbody tr');
  await expect(games).toHaveCount(56 + 4);
  await games.first().click();
  const replay = page.getByTestId('replay');
  await expect(replay.locator('[data-testid="board"]')).toBeVisible();
  await replay.getByRole('button', { name: 'First position' }).click();
  await expect(replay.getByRole('button', { name: 'Previous move' })).toBeDisabled();
  await expect(replay.getByRole('button', { name: 'Next move' })).toBeEnabled();

  await page.getByRole('button', { name: "Play generation 1's champion" }).click();
  const dialog = page.getByRole('dialog', { name: 'New game' });
  await expect(dialog.getByTestId('champion-pick')).toContainText('Champion of Demo, generation 1');
  await dialog.getByRole('button', { name: 'Start game' }).click();

  await expect(page).not.toHaveURL(/#lab$/);
  await expect(page.getByTestId('player-black')).toContainText('Champion of Demo, generation 1');
  await page.locator('[data-square="e2"]').first().click();
  await page.locator('[data-square="e4"]').first().click();
  await expect(page.getByTestId('move-list')).toContainText('e4');
  await expect(page.getByTestId('status')).toContainText('Your move', { timeout: 20_000 });

  // a new game from the dialog without the champion is against the usual engine again
  await page.getByRole('button', { name: 'New game' }).click();
  await dialog.getByRole('button', { name: 'Usual engine' }).click();
  await dialog.getByRole('button', { name: 'Start game' }).click();
  await expect(page.getByTestId('player-black')).toContainText('Engine · Level');
});
