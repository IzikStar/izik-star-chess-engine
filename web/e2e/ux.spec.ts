import { expect, test } from '@playwright/test';

// Small things that make the screens readable and pleasant (the UX review of 2026-10-06).

test('a run card shows a readable Delete button', async ({ page }) => {
  await page.goto('/#lab');
  const del = page.getByTestId('run-card').filter({ hasText: 'Demo' }).getByRole('button', { name: 'Delete' });
  await expect(del).toBeVisible();
  // the label must not be painted in the button's own background colour
  const [color, background] = await del.evaluate((b) => [getComputedStyle(b).color, getComputedStyle(b).backgroundColor]);
  expect(color).not.toBe(background);
});

test('the selected piece stands out on a dark square as well as a light one', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'New game' }).click();
  const dialog = page.getByRole('dialog', { name: 'New game' });
  await dialog.getByRole('button', { name: 'A friend', exact: true }).click();
  await dialog.getByRole('button', { name: 'Start game' }).click();
  // g1 is a dark square: the selection used to be a green as dark as the square itself
  const g1 = page.locator('[data-square="g1"]').first();
  const tint = () => g1.evaluate((el) => (el.firstElementChild?.nextElementSibling as HTMLElement | null)?.style.backgroundColor ?? '');
  await g1.click();
  await expect.poll(tint).toContain('240, 165, 40');
});

test('the hall of fame keeps its Play and PGN links inside the table row', async ({ page }) => {
  await page.goto('/#lab/fame');
  const actions = page.locator('.fame-actions').first();
  await expect(actions).toBeVisible();
  expect(await actions.evaluate((el) => getComputedStyle(el.parentElement!).display)).toBe('table-cell');
});

test('sound and dark mode are icon switches that say what they are', async ({ page }) => {
  await page.goto('/');
  const sound = page.getByRole('button', { name: 'Sound' });
  const before = await sound.getAttribute('aria-pressed');
  await sound.click();
  await expect(sound).not.toHaveAttribute('aria-pressed', before!);
  const dark = page.getByRole('button', { name: 'Dark mode' });
  await dark.click();
  const theme = await page.evaluate(() => document.documentElement.dataset.theme);
  await expect(dark).toHaveAttribute('aria-pressed', theme === 'dark' ? 'true' : 'false');
  await sound.click(); // as it was
});

test('on a phone the opponent sits above the board and you below it, the top bar on two rows', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  const board = await page.getByTestId('board').boundingBox();
  const top = await page.locator('.player.top').boundingBox();
  const bottom = await page.locator('.player.bottom').boundingBox();
  expect(top!.y + top!.height).toBeLessThanOrEqual(board!.y);
  expect(bottom!.y).toBeGreaterThanOrEqual(board!.y + board!.height);
  // nothing in the top bar sticks out of the screen
  const bar = await page.locator('.topbar').boundingBox();
  expect(bar!.width).toBeLessThanOrEqual(390);
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});

test('the variant designer puts the list beside the open variant on a wide screen', async ({ page }) => {
  await page.goto('/#variants');
  const list = await page.getByTestId('variant-list').boundingBox();
  const editor = await page.getByRole('button', { name: 'Make a copy' }).boundingBox();
  expect(editor!.x).toBeGreaterThan(list!.x + list!.width);
});

test('a win sets off confetti over the board; a new game clears it', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'New game' }).click();
  const dialog = page.getByRole('dialog', { name: 'New game' });
  await dialog.getByRole('button', { name: 'Chess', exact: true }).click(); // earlier specs leave other variants picked
  await dialog.getByRole('button', { name: 'A friend', exact: true }).click();
  await dialog.getByRole('button', { name: 'Untimed' }).click();
  await dialog.getByRole('button', { name: 'Start game' }).click();
  await expect(page.getByTestId('status')).toHaveText('White to move');
  const play = async (from: string, to: string, san: string) => {
    await page.locator(`[data-square="${from}"]`).first().click();
    await page.locator(`[data-square="${to}"]`).first().click();
    await expect(page.getByTestId('move-list')).toContainText(san);
  };
  await expect(page.getByTestId('confetti')).toHaveCount(0);
  await play('f2', 'f3', 'f3');
  await play('e7', 'e5', 'e5');
  await play('g2', 'g4', 'g4');
  await play('d8', 'h4', 'Qh4#');
  await expect(page.getByTestId('result')).toContainText('Checkmate');
  await expect(page.getByTestId('confetti')).toBeVisible();
  await expect(page.getByTestId('result')).toHaveClass(/won/);
  // it is a burst, not a screensaver
  await expect(page.getByTestId('confetti')).toHaveCount(0, { timeout: 5000 });
  await page.getByTestId('result').getByRole('button', { name: 'Rematch' }).click();
  await expect(page.getByTestId('result')).toBeHidden();
});

test('a first visit offers a short tour that walks the top bar and is not offered again', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('tour-offer').getByRole('button', { name: 'Show me around' }).click();
  const tour = page.getByTestId('tour');
  await expect(tour).toContainText('1 / 5');
  await expect(page.locator('[data-tour="new-game"]')).toHaveClass(/tour-target/);
  await tour.getByRole('button', { name: 'Next' }).click();
  await expect(tour).toContainText('My games');
  await page.keyboard.press('Escape');
  await expect(tour).toBeHidden();
  await page.reload();
  await expect(page.getByTestId('board')).toBeVisible();
  await expect(page.getByTestId('tour-offer')).toHaveCount(0);
  // Settings brings it back
  await page.getByRole('button', { name: 'Settings' }).click();
  await page.getByRole('button', { name: 'Show the tour' }).click();
  await expect(page.getByTestId('tour')).toContainText('1 / 5');
});
