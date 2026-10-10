import { expect, test } from '@playwright/test';
import { tab } from './helpers';

// The piece bank (variants/PieceBank.tsx): save a variant's piece with its pictures, put it into
// another variant, delete it from the bank.

const SHOTS = '../target/e2e-screens';
/** A 1x1 PNG, enough for a piece's picture. */
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==', 'base64');

test('a piece goes into the bank with its picture and comes out in another variant', async ({ page }) => {
  page.on('dialog', (d) => d.accept());
  // a piece with a picture in one variant, saved to the bank
  await page.goto('/#variants/chess');
  const editor = page.getByTestId('variant-editor');
  await expect(editor.getByRole('heading', { name: 'Chess' })).toBeVisible();
  await editor.getByRole('button', { name: 'Make a copy' }).click();
  await editor.getByLabel('Name', { exact: true }).first().fill('Bank source');
  await tab(page, 'Pieces');
  await editor.getByRole('button', { name: 'Add a piece' }).click();
  const piece = page.getByTestId('piece-editor');
  await piece.getByLabel('Name', { exact: true }).fill('Wizard');
  await piece.getByLabel('Value').fill('450');
  await piece.getByLabel('Betza').fill('WF');
  await piece.getByLabel('Betza').press('Enter');
  await expect(page.getByTestId('piece-preview').locator('.r-both')).toHaveCount(8);
  await page.getByTestId('pictures').getByLabel('White picture').setInputFiles({ name: 'w.png', mimeType: 'image/png', buffer: PNG });
  await piece.getByRole('button', { name: 'Save to bank' }).click();
  await expect(piece.getByRole('status')).toHaveText('Wizard is in the piece bank now.');

  // a built-in's piece goes in too (no pictures)
  await page.goto('/#variants/chess/pieces/N');
  await page.getByTestId('piece-editor').getByRole('button', { name: 'Save to bank' }).click();
  await expect(page.getByTestId('piece-editor').getByRole('status')).toHaveText('Knight is in the piece bank now.');

  // another new variant takes the Wizard from the bank, picture and all
  await page.goto('/#variants/chess');
  await editor.getByRole('button', { name: 'Make a copy' }).click();
  await editor.getByLabel('Name', { exact: true }).first().fill('Bank target');
  await tab(page, 'Pieces');
  await editor.getByRole('button', { name: 'From the bank' }).click();
  const dialog = page.getByRole('dialog', { name: 'Piece bank' });
  await expect(dialog.getByTestId('bank-list').locator('li')).toHaveCount(2);
  await page.screenshot({ path: `${SHOTS}/piece-bank.png` });
  await dialog.getByRole('button', { name: 'Add Wizard' }).click();
  await expect(dialog).toBeHidden();
  await expect(piece.getByLabel('Name', { exact: true })).toHaveValue('Wizard');
  await expect(piece.getByLabel('Value')).toHaveValue('450');
  await expect(piece.getByLabel('Letter')).toHaveValue('A');
  await expect(page.getByTestId('piece-preview').locator('.r-both')).toHaveCount(8);
  const pictures = page.getByTestId('pictures');
  await expect(pictures.getByRole('img', { name: 'White picture' })).toBeVisible();
  await editor.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(editor.getByRole('button', { name: 'Saved', exact: true })).toBeVisible();
  const art = await page.evaluate(() => fetch('/api/variants/bank-target').then((r) => r.json()).then((v) => v.art));
  expect(Object.keys(art.A ?? {})).toEqual(['w']);

  // the Knight's letter is taken in chess, so it comes in under a free one
  await editor.getByRole('button', { name: 'From the bank' }).click();
  await dialog.getByRole('button', { name: 'Add Knight' }).click();
  await expect(piece.getByLabel('Name', { exact: true })).toHaveValue('Knight');
  await expect(piece.getByLabel('Letter')).toHaveValue('C');

  // and a piece leaves the bank
  await editor.getByRole('button', { name: 'From the bank' }).click();
  await dialog.getByRole('button', { name: 'Delete Knight' }).click();
  await expect(dialog.getByTestId('bank-list').locator('li')).toHaveCount(1);
});
