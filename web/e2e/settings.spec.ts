import { expect, test, type Page } from '@playwright/test';

// The Settings dialog (hints, hint strength, the evaluation bar per kind of game) and "Play the
// next level". The live bar needs Stockfish where the server can find it (apt install stockfish).

const square = (page: Page, name: string) => page.locator(`[data-square="${name}"]`).first();

async function clickMove(page: Page, from: string, to: string) {
  await square(page, from).click();
  await square(page, to).click();
}

async function newGame(page: Page, opponent: 'Computer' | 'A friend', level?: number) {
  await page.getByRole('button', { name: 'New game' }).click();
  const dialog = page.getByRole('dialog', { name: 'New game' });
  // the dialog keeps the last game's variant, and designer.spec plays a made one; the bar is chess only
  await dialog.getByRole('button', { name: 'Chess', exact: true }).click();
  await dialog.getByRole('button', { name: opponent, exact: true }).click();
  if (opponent === 'Computer') await dialog.getByRole('button', { name: 'White', exact: true }).click();
  if (level !== undefined) await dialog.getByLabel('Strength', { exact: true }).fill(String(level));
  await dialog.getByRole('button', { name: 'Start game' }).click();
  await expect(dialog).toBeHidden();
}

async function openSettings(page: Page) {
  await page.getByRole('button', { name: 'Settings' }).click();
  return page.getByRole('dialog', { name: 'Settings' });
}

test.beforeEach(async ({ page }) => {
  await page.goto('/');
  await page.evaluate(() => localStorage.removeItem('settings'));
  await page.reload();
});

test('the evaluation bar follows every move against the computer, and is off between two people', async ({ page }) => {
  const asked: string[] = [];
  page.on('request', (r) => {
    if (r.url().endsWith('/api/eval')) asked.push(r.postData() ?? '');
  });
  await newGame(page, 'Computer', 2);
  const bar = page.getByTestId('eval-bar');
  await expect(bar).toBeVisible();
  await expect(bar).not.toHaveAttribute('data-score', '…', { timeout: 15_000 });

  // each new position is scored: the start, after 1.e4, after the engine's reply
  await clickMove(page, 'e2', 'e4');
  await expect(page.getByTestId('status')).toHaveText('Your move', { timeout: 15_000 });
  await expect.poll(() => asked.length, { timeout: 15_000 }).toBeGreaterThanOrEqual(3);
  expect(asked.some((body) => JSON.parse(body).moves.length === 2)).toBe(true);
  await expect(bar).not.toHaveAttribute('data-score', '…');

  // the same game kind can switch it off
  let settings = await openSettings(page);
  await settings.getByLabel('Against the computer').uncheck();
  await settings.getByRole('button', { name: 'Done' }).click();
  await expect(bar).toHaveCount(0);

  // a game between two people has no bar by default
  settings = await openSettings(page);
  await expect(settings.getByLabel('Against a friend')).not.toBeChecked();
  await expect(settings.getByLabel('Watching the engine')).toBeChecked();
  await settings.getByRole('button', { name: 'Defaults' }).click();
  await settings.getByRole('button', { name: 'Done' }).click();
  await newGame(page, 'A friend');
  await expect(page.getByTestId('eval-bar')).toHaveCount(0);
});

test('hints can be turned off, and played at a chosen level', async ({ page }) => {
  await newGame(page, 'A friend');
  let settings = await openSettings(page);
  await settings.getByLabel('Offer hints').uncheck();
  await expect(settings.getByLabel('Hint strength')).toBeDisabled();
  await settings.getByRole('button', { name: 'Done' }).click();
  await expect(page.getByRole('button', { name: 'Hint' })).toHaveCount(0);

  settings = await openSettings(page);
  await settings.getByLabel('Offer hints').check();
  await settings.getByLabel('Hint strength').selectOption('2');
  await settings.getByRole('button', { name: 'Done' }).click();
  const hint = page.getByRole('button', { name: 'Hint' });
  await expect(hint).toHaveAttribute('title', 'What Level 2 would play');
  await hint.click();
  await expect(page.getByTestId('board')).toHaveAttribute('data-hint', /^[a-h][1-8][a-h][1-8]/, { timeout: 15_000 });

  // the choice is remembered
  await page.reload();
  await expect(page.getByRole('button', { name: 'Hint' })).toHaveAttribute('title', 'What Level 2 would play');
});

test('after a game against the computer, one click plays the next level', async ({ page }) => {
  await newGame(page, 'Computer', 3);
  await page.getByRole('button', { name: 'Resign' }).click();
  await page.getByTestId('confirm-resign').getByRole('button', { name: 'Resign' }).click();
  const result = page.getByTestId('result');
  await expect(result).toBeVisible();
  await result.getByRole('button', { name: 'Play Level 4' }).click();
  await expect(result).toHaveCount(0);
  await expect(page.getByTestId('player-black')).toContainText('Level 4');
  await expect(page.getByTestId('player-white')).toContainText('You');
});
