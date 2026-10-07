import { expect, test, type Page } from '@playwright/test';
import { clickMove, sendCommand, square } from './helpers';

// Variants (Phase 6 R4e): picking one in the New game dialog, and its rules on the board.

const SHOTS = '../target/e2e-screens';
const moveList = (page: Page) => page.getByTestId('move-list');

async function newGame(page: Page, variant: string, opponent: 'Computer' | 'A friend') {
  await page.getByRole('button', { name: 'New game' }).click();
  const dialog = page.getByRole('dialog', { name: 'New game' });
  await dialog.getByRole('button', { name: variant, exact: true }).click();
  await dialog.getByRole('button', { name: opponent, exact: true }).click();
  await dialog.getByRole('button', { name: 'Untimed', exact: true }).click();
  await dialog.getByRole('button', { name: 'Start game' }).click();
  await expect(dialog).toBeHidden();
}

/** Sends one command over a second socket (the server shares the game with every browser). */
test.afterEach(async ({ page }) => {
  await newGame(page, 'Chess', 'A friend'); // the next spec starts from chess
});

test('antichess: the rule is shown, a capture is forced, and a pawn may become a king', async ({ page }) => {
  await page.goto('/');
  await newGame(page, 'Antichess', 'A friend');
  await expect(page.getByTestId('variant-tag')).toContainText('Lose every piece to win');
  await clickMove(page, 'e2', 'e3');
  await clickMove(page, 'b7', 'b5');
  // Bxb5 is the only move: no other piece can be picked up
  await square(page, 'g1').click();
  await expect(square(page, 'g1')).not.toHaveAttribute('style', /rgba\(20, 85, 60/);
  await clickMove(page, 'f1', 'b5');
  await expect(moveList(page)).toContainText('Bxb5');
  await page.screenshot({ path: `${SHOTS}/variant-antichess.png` });

  await sendCommand(page, { type: 'loadPgn', pgn: '[Variant "Antichess"]\n[FEN "8/4P3/8/8/8/8/8/k7 w - - 0 1"]\n*' });
  await expect(page.getByTestId('variant-tag')).toBeVisible();
  await clickMove(page, 'e7', 'e8');
  const picker = page.getByRole('dialog', { name: 'Promote to' });
  await expect(picker.getByRole('button')).toHaveCount(5);
  await picker.getByRole('button', { name: 'King' }).click();
  await expect(moveList(page)).toContainText('e8=K');
});

test('three-check: each side\'s checks are counted, and Stockfish\'s analysis is chess only', async ({ page }) => {
  await page.goto('/');
  await newGame(page, 'Three-check', 'A friend');
  await clickMove(page, 'e2', 'e4');
  await clickMove(page, 'f7', 'f6');
  await clickMove(page, 'd1', 'h5');
  await expect(page.getByTestId('checks-white')).toContainText('1/3');
  await expect(page.getByTestId('checks-black')).toContainText('0/3');
  await expect(page.getByRole('button', { name: 'Analyse' })).toBeDisabled();
  await page.waitForTimeout(400); // let the queen land before the screenshot
  await page.screenshot({ path: `${SHOTS}/variant-three-check.png` });
});

test('a variant against the computer: the ladder stops below Stockfish, and the engine replies', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'New game' }).click();
  const dialog = page.getByRole('dialog', { name: 'New game' });
  await dialog.getByRole('button', { name: 'King of the Hill', exact: true }).click();
  await dialog.getByRole('button', { name: 'Computer', exact: true }).click();
  await expect(dialog.getByTestId('variant-rule')).toContainText('centre squares');
  await expect(dialog.getByLabel('Strength', { exact: true })).toHaveAttribute('max', '8');
  await dialog.getByLabel('Strength', { exact: true }).fill('2');
  await dialog.getByRole('button', { name: 'White', exact: true }).click();
  await dialog.getByRole('button', { name: 'Untimed', exact: true }).click();
  await page.screenshot({ path: `${SHOTS}/variant-dialog.png` });
  await dialog.getByRole('button', { name: 'Start game' }).click();
  await clickMove(page, 'e2', 'e4');
  await expect(moveList(page).getByRole('button')).toHaveCount(2);
  await expect(page.getByTestId('variant-tag')).toContainText('King of the Hill');
});
