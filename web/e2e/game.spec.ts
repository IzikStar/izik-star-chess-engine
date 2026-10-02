import { expect, test, type Page } from '@playwright/test';

// Plays real games through the browser UI against the packaged jar (Phase 4c, docs/ui-research.md §7 step 5).

const square = (page: Page, name: string) => page.locator(`[data-square="${name}"]`).first();

async function clickMove(page: Page, from: string, to: string) {
  await square(page, from).click();
  await square(page, to).click();
}

/** A real drag: press, move in steps (the board starts a drag only after a few pixels), release. */
async function dragMove(page: Page, from: string, to: string) {
  const a = await square(page, from).boundingBox();
  const b = await square(page, to).boundingBox();
  await page.mouse.move(a!.x + a!.width / 2, a!.y + a!.height / 2);
  await page.mouse.down();
  await page.mouse.move(b!.x + b!.width / 2, b!.y + b!.height / 2, { steps: 12 });
  await page.mouse.up();
}

async function newGame(page: Page, opponent: 'Computer' | 'A friend' | 'Watch the engine', level?: number) {
  await page.getByRole('button', { name: 'New game' }).click();
  const dialog = page.getByRole('dialog', { name: 'New game' });
  await dialog.getByRole('button', { name: opponent, exact: true }).click();
  if (level !== undefined) await dialog.getByLabel('Strength').fill(String(level));
  await dialog.getByRole('button', { name: 'Start game' }).click();
  await expect(dialog).toBeHidden();
}

const moveList = (page: Page) => page.getByTestId('move-list');

test('two players play a game to checkmate, then review it', async ({ page }) => {
  await page.goto('/');
  await newGame(page, 'A friend');
  await expect(page.getByTestId('status')).toHaveText('White to move');

  await clickMove(page, 'f2', 'f3');
  await expect(page.getByTestId('status')).toHaveText('Black to move');
  await clickMove(page, 'e7', 'e5');
  // drag works as well as click
  await dragMove(page, 'g2', 'g4');
  await expect(moveList(page)).toContainText('g4');
  await clickMove(page, 'd8', 'h4');

  const result = page.getByTestId('result');
  await expect(result).toContainText('0 – 1');
  await expect(result).toContainText('Checkmate · Black wins');
  await expect(moveList(page)).toContainText('Qh4#');
  await expect(page.getByRole('button', { name: 'Hint' })).toBeDisabled();

  await result.getByRole('button', { name: 'Review game' }).click();
  await expect(page.getByTestId('status')).toContainText('Reviewing the start position');
  await page.keyboard.press('ArrowRight');
  await expect(page.getByTestId('status')).toContainText('Reviewing 1. f3');
  await page.getByRole('button', { name: 'Back to game' }).click();
  await expect(page.getByTestId('result')).toBeVisible();
});

test('an illegal move is not played', async ({ page }) => {
  await page.goto('/');
  await newGame(page, 'A friend');
  await clickMove(page, 'e2', 'e5');
  await clickMove(page, 'e2', 'e4');
  await expect(moveList(page)).toHaveText(/1\.\s*e4/);
  await expect(moveList(page).getByRole('button')).toHaveCount(1);
});

test('promotion asks which piece', async ({ page }) => {
  await page.goto('/');
  await newGame(page, 'A friend');
  const moves: [string, string][] = [
    ['a2', 'a4'], ['b7', 'b5'], ['a4', 'b5'], ['a7', 'a6'], ['b5', 'a6'], ['c8', 'b7'], ['a6', 'b7'], ['b8', 'c6'],
  ];
  for (const [from, to] of moves) await clickMove(page, from, to);
  await expect(moveList(page).getByRole('button')).toHaveCount(8);
  await clickMove(page, 'b7', 'a8');
  const picker = page.getByRole('dialog', { name: 'Promote to' });
  await expect(picker).toBeVisible();
  await picker.getByRole('button', { name: 'Knight' }).click();
  await expect(moveList(page)).toContainText('bxa8=N');
});

test('against the computer: it replies, and a take-back removes both moves', async ({ page }) => {
  await page.goto('/');
  await newGame(page, 'Computer', 3);
  await expect(page.getByTestId('player-black')).toContainText('Engine · Level 3');
  await clickMove(page, 'e2', 'e4');
  await expect(moveList(page).getByRole('button')).toHaveCount(2);
  await expect(page.getByTestId('status')).toHaveText('Your move');

  await page.getByRole('button', { name: 'Hint' }).click();
  await expect(page.getByTestId('board')).toHaveAttribute('data-hint', /^[a-h][1-8][a-h][1-8]/);

  await page.getByRole('button', { name: 'Take back' }).click();
  await expect(page.getByText('No moves yet.')).toBeVisible();
});

test('watching the engine play itself, then stopping it with a new game', async ({ page }) => {
  await page.goto('/');
  await newGame(page, 'Watch the engine', 1);
  await expect(page.getByTestId('player-white')).toContainText('Engine · Level 1');
  await expect(moveList(page).getByRole('button')).toHaveCount(3, { timeout: 15_000 });
  await expect(page.getByRole('button', { name: 'Take back' })).toBeDisabled();
  await newGame(page, 'A friend');
  await expect(page.getByText('No moves yet.')).toBeVisible();
  await page.waitForTimeout(1500);
  await expect(page.getByText('No moves yet.')).toBeVisible();
});
