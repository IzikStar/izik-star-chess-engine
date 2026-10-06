import { expect, test, type Page } from '@playwright/test';

// Game analysis with Stockfish (needs Stockfish where the server can find it, e.g. apt install stockfish).

const square = (page: Page, name: string) => page.locator(`[data-square="${name}"]`).first();

async function clickMove(page: Page, from: string, to: string) {
  await square(page, from).click();
  await square(page, to).click();
}

test("analysing Scholar's mate: eval bar, Elo per side, and ...Nf6 marked a blunder", async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'New game' }).click();
  const dialog = page.getByRole('dialog', { name: 'New game' });
  await dialog.getByRole('button', { name: 'A friend', exact: true }).click();
  await dialog.getByRole('button', { name: 'Start game' }).click();
  await expect(dialog).toBeHidden();

  // each move waits for the list to grow, so a busy machine cannot drop a click
  const moves = [['e2', 'e4'], ['e7', 'e5'], ['f1', 'c4'], ['b8', 'c6'], ['d1', 'h5'], ['g8', 'f6'], ['h5', 'f7']];
  for (const [i, [from, to]] of moves.entries()) {
    await clickMove(page, from, to);
    await expect(page.getByTestId('move-list').getByRole('button')).toHaveCount(i + 1);
  }
  const result = page.getByTestId('result');
  await expect(result).toContainText('1 – 0');
  await expect(page.getByTestId('eval-bar')).toHaveCount(0);

  await result.getByRole('button', { name: 'Analyse game' }).click();
  const analysis = page.getByTestId('analysis');
  await expect(analysis.getByTestId('analysis-white')).toBeVisible({ timeout: 30_000 });

  // the bar shows the start position (reviewing ply 0), roughly level
  const bar = page.getByTestId('eval-bar');
  await expect(bar).toBeVisible();
  const level = Number(await bar.getAttribute('aria-valuenow'));
  expect(level).toBeGreaterThan(40);
  expect(level).toBeLessThan(65);

  // each side gets an Elo estimate; the winner's is higher
  const whiteElo = Number(await analysis.getByTestId('analysis-white').locator('.elo').textContent());
  const blackElo = Number(await analysis.getByTestId('analysis-black').locator('.elo').textContent());
  expect(whiteElo).toBeGreaterThan(blackElo);

  // ...Nf6 is a blunder in the move list and in the move line, and the bar shows White mating
  await expect(page.getByTestId('move-list').getByTitle('Blunder')).toHaveCount(1);
  await page.getByTestId('move-list').getByRole('button', { name: /^Nf6/ }).click();
  await expect(page.getByTestId('analysis-move')).toContainText('Nf6 · Blunder');
  // the mark is on the board too, on Nf6's square
  await expect(page.getByTestId('quality-badge')).toHaveAttribute('data-quality', 'blunder');
  // the bar is exactly as tall as the board
  const barBox = (await bar.boundingBox())!;
  const boardBox = (await page.getByTestId('board').boundingBox())!;
  expect(Math.abs(barBox.height - boardBox.height)).toBeLessThan(2);
  expect(Math.abs(barBox.y - boardBox.y)).toBeLessThan(2);
  await expect(bar).toHaveAttribute('data-score', 'M1');
  // the arrow shows what Black should have played instead
  await expect(page.getByTestId('board')).not.toHaveAttribute('data-hint', '');
  await expect(page.getByTestId('board')).not.toHaveAttribute('data-hint', 'g8f6');
  // the mating move itself was the best move: no arrow
  await page.keyboard.press('ArrowRight');
  await expect(page.getByTestId('analysis-move')).toContainText('Qxf7# · Best move');
  await expect(page.getByTestId('board')).toHaveAttribute('data-hint', '');

  // the final position: the bar is all White
  await expect(bar).toHaveAttribute('aria-valuenow', '100');

  // holding the back button steps back again and again, to the start
  const back = page.getByRole('button', { name: 'Previous move' });
  await back.scrollIntoViewIfNeeded();
  const box = (await back.boundingBox())!;
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down();
  await expect(page.getByTestId('status')).toContainText('Reviewing the start position', { timeout: 3000 });
  await page.mouse.up();

  // a new game drops the analysis
  await page.getByRole('button', { name: 'New game' }).click();
  await page.getByRole('dialog', { name: 'New game' }).getByRole('button', { name: 'Start game' }).click();
  await expect(page.getByTestId('analysis')).toHaveCount(0);
  await expect(page.getByTestId('eval-bar')).toHaveCount(0);
});
