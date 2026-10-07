import { expect, test, type Page } from '@playwright/test';

// My games: every game is saved as it is played; a saved game opens for review, and an
// unfinished one can be carried on.

const SHOTS = '../target/e2e-screens';
const square = (page: Page, name: string) => page.locator(`[data-square="${name}"]`).first();

/** Plays a move and waits for the server to take it, so the next click is not lost to the redraw. */
async function clickMove(page: Page, from: string, to: string) {
  const moves = page.getByTestId('move-list').getByRole('button');
  const before = await moves.count();
  await square(page, from).click();
  await square(page, to).click();
  await expect(moves).toHaveCount(before + 1);
}

async function friendGame(page: Page, time = 'Untimed') {
  await page.getByRole('button', { name: 'New game' }).click();
  const dialog = page.getByRole('dialog', { name: 'New game' });
  await dialog.getByRole('button', { name: 'A friend', exact: true }).click();
  await dialog.getByRole('button', { name: time, exact: true }).click();
  await dialog.getByRole('button', { name: 'Start game' }).click();
  await expect(dialog).toBeHidden();
  // the last game's moves stay on screen until the new game arrives: wait for it, or the next
  // clickMove counts the old moves
  await expect(page.getByText('No moves yet.')).toBeVisible();
}

test('games are saved as they are played, reviewed, and carried on', async ({ page }) => {
  // the other specs share this server: start from an empty list
  await page.goto('/');
  await friendGame(page);
  const { games } = await (await page.request.get('/api/games')).json();
  for (const g of games) await page.request.delete(`/api/games/${g.id}`);

  // a finished game: fool's mate
  await friendGame(page);
  for (const [from, to] of [['f2', 'f3'], ['e7', 'e5'], ['g2', 'g4'], ['d8', 'h4']]) await clickMove(page, from, to);
  await expect(page.getByTestId('result')).toBeVisible();

  // an unfinished timed game, left by starting another
  await friendGame(page, '5+0');
  await clickMove(page, 'e2', 'e4');
  await clickMove(page, 'c7', 'c5');
  await expect(page.getByTestId('move-list')).toContainText('c5');
  await friendGame(page);

  await page.getByRole('button', { name: 'My games' }).click();
  const table = page.getByTestId('saved-games');
  await expect(table.locator('tbody tr')).toHaveCount(2);
  await expect(table.locator('tbody tr').first()).toContainText('Unfinished');
  await expect(table.locator('tbody tr').nth(1)).toContainText('0–1');
  await page.screenshot({ path: `${SHOTS}/my-games.png` });

  await page.getByLabel('Result').selectOption('unfinished');
  await expect(table.locator('tbody tr')).toHaveCount(1);
  await page.getByLabel('Result').selectOption('all');

  // review the finished game
  await table.locator('tbody tr').nth(1).click();
  const head = page.getByTestId('review-head');
  await expect(head).toContainText('Black won by checkmate');
  await expect(page.getByTestId('move-list')).toContainText('Qh4#');
  await page.getByRole('button', { name: 'First position' }).click();
  await page.getByRole('button', { name: 'Next move' }).click();
  await expect(page.locator('.mv.current')).toHaveText('f3');
  await page.screenshot({ path: `${SHOTS}/my-games-review.png` });
  await page.getByRole('button', { name: '← My games' }).click();

  // carry the unfinished one on: its moves and clock come back, and play goes on
  await table.locator('tbody tr').first().click();
  await expect(head).toContainText('time left');
  await page.getByRole('button', { name: 'Continue this game' }).click();
  await expect(page.getByTestId('move-list')).toContainText('c5');
  await expect(page.getByTestId('clock-white')).toHaveClass(/running/);
  await clickMove(page, 'g1', 'f3');
  await expect(page.getByTestId('move-list')).toContainText('Nf3');

  await page.getByRole('button', { name: 'My games' }).click();
  await expect(table.locator('tbody tr')).toHaveCount(2);
  await expect(table.locator('tbody tr').first()).toContainText('Playing');
  await expect(table.locator('tbody tr').first().locator('td').nth(4)).toHaveText('2');
});

test('a game the engine plays against itself is saved too, with both levels', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'New game' }).click();
  const dialog = page.getByRole('dialog', { name: 'New game' });
  await dialog.getByRole('button', { name: 'Watch the engine', exact: true }).click();
  await dialog.getByLabel("White's strength", { exact: true }).fill('0');
  await dialog.getByLabel("Black's strength").fill('1');
  await dialog.getByRole('button', { name: 'Start game' }).click();
  await expect(dialog).toBeHidden();
  await expect.poll(() => page.getByTestId('move-list').getByRole('button').count(), { timeout: 15_000 }).toBeGreaterThanOrEqual(4);

  await page.getByRole('button', { name: 'My games' }).click();
  const table = page.getByTestId('saved-games');
  const row = table.locator('tbody tr', { hasText: 'Level 0 Random moves vs Level 1 Beginner' });
  await expect(row).toHaveCount(1);
  await expect(row).toContainText('Playing');

  // the opponent filter has its own entry for these games
  await page.getByLabel('Opponent').selectOption('computer');
  await expect(table.locator('tbody tr')).toHaveCount(1);
  await expect(table.locator('tbody tr').first()).toContainText('vs Level 1 Beginner');

  // it opens for review like any other game
  await table.locator('tbody tr').first().click();
  await expect(page.getByTestId('review-head')).toContainText('Level 0 Random moves vs Level 1 Beginner');
  await expect(page.getByRole('button', { name: 'Analyse game' })).toBeEnabled();
  await page.getByRole('button', { name: 'Back to this game' }).click();

  // stop it, so the specs after this one find a quiet board
  await friendGame(page);
  await expect(page.getByText('No moves yet.')).toBeVisible();
});
