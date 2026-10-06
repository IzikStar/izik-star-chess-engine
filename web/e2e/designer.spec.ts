import { expect, test } from '@playwright/test';

// The variant designer (Phase 6 R5c): copy a built-in variant, invent a piece by clicking squares
// and by Betza text, put it in the start position, save, and delete.

const SHOTS = '../target/e2e-screens';
/** A 1x1 PNG, enough for a piece's picture. */
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==', 'base64');

test('make a variant with an invented piece', async ({ page }) => {
  await page.goto('/#variants');
  const editor = page.getByTestId('variant-editor');
  await expect(editor.getByRole('heading', { name: 'Chess' })).toBeVisible();
  await expect(editor.getByText('A built-in variant: make a copy to change it.')).toBeVisible();
  await expect(editor.getByRole('button', { name: 'Save', exact: true })).toHaveCount(0);

  await editor.getByRole('button', { name: 'Make a copy' }).click();
  await expect(editor.getByRole('heading', { name: 'My Chess' })).toBeVisible();
  await editor.getByLabel('Name', { exact: true }).first().fill('Designer test');

  // a new piece: one jump makes a knight in all eight ways
  await editor.getByRole('button', { name: 'Add a piece' }).click();
  const piece = page.getByTestId('piece-editor');
  await page.getByTestId('move-grid').locator('[data-offset="2,1"]').click();
  await expect(piece.getByLabel('Betza')).toHaveValue('N');
  await expect(page.getByTestId('piece-preview').locator('.r-both')).toHaveCount(8);

  // and the Amazon by text: queen plus knight from d4 reaches 27 + 8 squares
  await piece.getByLabel('Betza').fill('QN');
  await piece.getByLabel('Betza').press('Enter');
  await expect(page.getByTestId('piece-preview').locator('.r-both')).toHaveCount(35);
  await piece.getByLabel('Name', { exact: true }).fill('Amazon');

  // it replaces the queens
  await page.getByRole('button', { name: 'white A', exact: true }).click();
  await page.getByTestId('start-board').locator('[data-square="d1"]').click();
  await page.getByRole('button', { name: 'black A', exact: true }).click();
  await page.getByTestId('start-board').locator('[data-square="d8"]').click();
  await expect(editor.getByLabel('Start FEN')).toHaveValue('rnbakbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBAKBNR w KQkq - 0 1');
  await page.screenshot({ path: `${SHOTS}/designer.png`, fullPage: true });

  await editor.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(editor.getByRole('button', { name: 'Saved', exact: true })).toBeVisible();
  await page.reload();
  const list = page.getByTestId('variant-list');
  await list.getByRole('button', { name: 'Designer test' }).click();
  await expect(page.getByRole('button', { name: /Amazon/ })).toBeVisible();

  page.once('dialog', (d) => d.accept());
  await editor.getByRole('button', { name: 'Delete' }).click();
  await expect(list.getByRole('button', { name: 'Designer test' })).toHaveCount(0);
});

test('a variant the server cannot play is refused with the reason', async ({ page }) => {
  await page.goto('/#variants');
  const editor = page.getByTestId('variant-editor');
  await editor.getByRole('button', { name: 'Make a copy' }).click();
  await editor.getByLabel('Start FEN').fill('8/8/8/8/8/8/8/8 w - - 0 1');
  await editor.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(editor.getByRole('alert')).toBeVisible();
});

test('the health check plays a variant against itself and reports', async ({ page }) => {
  await page.goto('/#variants');
  await page.getByTestId('variant-list').getByRole('button', { name: /Antichess/ }).click();
  await expect(page.getByTestId('variant-editor').getByRole('heading', { name: 'Antichess' })).toBeVisible();
  const health = page.getByTestId('health');
  await health.getByLabel('Games').selectOption('20');
  await health.getByLabel('Depth', { exact: true }).selectOption('1');
  await health.getByRole('button', { name: 'Run' }).click();
  const report = page.getByTestId('health-report');
  await expect(report).toBeVisible({ timeout: 60_000 });
  await expect(report).toContainText('White wins');
  await expect(report).toContainText('Lost every piece');
  await health.scrollIntoViewIfNeeded();
  await page.screenshot({ path: `${SHOTS}/health.png`, fullPage: true });
});

test('the whole path: invent a piece, save the variant, play it from New game', async ({ page }) => {
  await page.goto('/#variants');
  const editor = page.getByTestId('variant-editor');
  await expect(editor.getByRole('heading', { name: 'Chess' })).toBeVisible();
  await editor.getByRole('button', { name: 'Make a copy' }).click();
  await editor.getByLabel('Name', { exact: true }).first().fill('Amazon path');
  await editor.getByRole('button', { name: 'Add a piece' }).click();
  const piece = page.getByTestId('piece-editor');
  await piece.getByLabel('Name', { exact: true }).fill('Amazon');
  await piece.getByLabel('Value').fill('1200');
  await piece.getByLabel('Betza').fill('QN');
  await piece.getByLabel('Betza').press('Enter');
  await expect(page.getByTestId('piece-preview').locator('.r-both')).toHaveCount(35);
  await page.getByRole('button', { name: 'white A', exact: true }).click();
  await page.getByTestId('start-board').locator('[data-square="d1"]').click();
  await page.getByRole('button', { name: 'black A', exact: true }).click();
  await page.getByTestId('start-board').locator('[data-square="d8"]').click();
  await editor.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(editor.getByRole('button', { name: 'Saved', exact: true })).toBeVisible();

  // a picture for the white Amazon; the start position shows it, and black's stands in darkened
  await page.getByTestId('pictures').getByLabel('White picture').setInputFiles({ name: 'amazon.png', mimeType: 'image/png', buffer: PNG });
  await expect(page.getByTestId('start-board').locator('[data-square="d1"] img[data-piece="wA"]')).toBeVisible();
  await expect(page.getByTestId('start-board').locator('[data-square="d8"] img[data-piece="bA"]')).toBeVisible();

  // the mouse over a piece in the start position shows where it goes
  await page.getByTestId('start-board').locator('[data-square="b1"]').hover();
  await expect(page.getByTestId('start-board').locator('.r-move')).toHaveCount(2);

  await editor.getByRole('button', { name: 'Play it' }).click();
  const dialog = page.getByRole('dialog', { name: 'New game' });
  await expect(dialog.getByLabel('My variants')).toHaveValue('amazon-path');
  await expect(dialog.getByTestId('variant-rule')).toHaveText(/Checkmate the king/);
  await dialog.getByRole('button', { name: 'A friend', exact: true }).click();
  await dialog.getByRole('button', { name: 'Untimed', exact: true }).click();
  await dialog.getByRole('button', { name: 'Start game' }).click();
  await expect(dialog).toBeHidden();

  await expect(page.getByText('Amazon path', { exact: true })).toBeVisible();
  const square = (name: string) => page.locator(`[data-square="${name}"]`).first();
  await expect(square('d1').locator('[data-piece="wA"]').first()).toBeVisible();
  await expect(square('d1').locator('img[data-piece="wA"]').first()).toBeVisible();
  // the mouse over the invented piece shows where it can go; over a chess piece it shows nothing
  await square('d1').hover();
  await expect(page.getByTestId('board')).toHaveAttribute('data-reach', 'c3 e3');
  await square('a2').hover();
  await expect(page.getByTestId('board')).toHaveAttribute('data-reach', '');
  // each move waits for the last one to be played: a click before the new position is dropped
  for (const [from, to, san] of [['e2', 'e4', 'e4'], ['e7', 'e5', 'e5'], ['d1', 'f3', 'Af3']]) {
    await square(from).click();
    await square(to).click();
    await expect(page.getByTestId('move-list')).toContainText(san);
  }
  await expect(page.getByTestId('move-list')).toContainText('Af3');
  await expect(square('f3').locator('[data-piece="wA"]').first()).toBeVisible();
  await page.screenshot({ path: `${SHOTS}/made-variant-game.png` });

  // back to chess for the next spec
  await page.getByRole('button', { name: 'New game' }).click();
  await dialog.getByRole('button', { name: 'Chess', exact: true }).click();
  await dialog.getByRole('button', { name: 'Start game' }).click();
  await expect(dialog).toBeHidden();
});
