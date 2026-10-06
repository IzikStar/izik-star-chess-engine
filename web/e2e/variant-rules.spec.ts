import { expect, test, type Page } from '@playwright/test';

// The rule building blocks in the designer (docs/variant-rules.md): ways to win as a list, the royal
// mode, the endings and castling, each explained; then two made variants played to the win.

const SHOTS = '../target/e2e-screens';
const square = (page: Page, name: string) => page.locator(`[data-square="${name}"]`).first();

async function tab(page: Page, name: 'Overview' | 'Board' | 'Pieces' | 'Health') {
  const link = page.getByRole('navigation', { name: 'Variant screens' }).getByRole('link', { name });
  await link.click();
  await expect(link).toHaveAttribute('aria-current', 'page');
}

/** Plays moves by clicking, each waiting for the move list to show it. */
async function play(page: Page, moves: [string, string, string][]) {
  for (const [from, to, san] of moves) {
    await square(page, from).click();
    await square(page, to).click();
    await expect(page.getByTestId('move-list')).toContainText(san);
  }
}

/** From the variant's page: Play it, against a friend, untimed. */
async function playIt(page: Page, id: string) {
  await page.getByTestId('variant-editor').getByRole('button', { name: 'Play it' }).click();
  const dialog = page.getByRole('dialog', { name: 'New game' });
  await expect(dialog.getByLabel('My variants')).toHaveValue(id);
  await dialog.getByRole('button', { name: 'A friend', exact: true }).click();
  await dialog.getByRole('button', { name: 'Untimed', exact: true }).click();
  await dialog.getByRole('button', { name: 'Start game' }).click();
  await expect(dialog).toBeHidden();
}

/** The New game dialog remembers the last game: the next spec gets chess back. */
async function backToChess(page: Page) {
  await page.getByRole('button', { name: 'New game' }).click();
  const dialog = page.getByRole('dialog', { name: 'New game' });
  await dialog.getByRole('button', { name: 'Chess', exact: true }).click();
  await dialog.getByRole('button', { name: 'Start game' }).click();
  await expect(dialog).toBeHidden();
}

async function remove(page: Page, id: string) {
  await page.request.delete(`/api/variants/${id}`);
}

test('the built-ins show their rules as building blocks, read-only', async ({ page }) => {
  await page.goto('/#variants/three-check');
  const goals = page.getByTestId('goal');
  await expect(goals).toHaveCount(2);
  await expect(goals.nth(0)).toHaveAttribute('data-kind', 'CHECKMATE');
  await expect(goals.nth(1)).toHaveAttribute('data-kind', 'CHECKS');
  await expect(page.getByTestId('goal-text')).toContainText('giving check 3 times');
  await expect(page.getByLabel('Checks to win')).toBeDisabled();
  await expect(page.getByTestId('stalemate-text')).toContainText('the game is a draw');
  await expect(page.getByTestId('repetition-text')).toContainText('three times');
  await expect(page.getByTestId('move-limit-text')).toContainText('After 50 moves');
  await expect(page.getByTestId('castling-moves')).toContainText('White king side: e1 to g1, partner h1 to f1');
  await expect(page.getByTestId('fairy-text')).toContainText('Fairy-Stockfish can play it');
});

test('two kings, last one standing: a king may be left attacked and captured; taking it can be mate', async ({ page }) => {
  const id = 'rules-two-kings';
  await remove(page, id);
  await page.goto('/#variants/chess');
  const editor = page.getByTestId('variant-editor');
  await editor.getByRole('button', { name: 'Make a copy' }).click();
  await editor.getByLabel('Name', { exact: true }).fill('Rules two kings');

  await editor.getByLabel('Royal mode').selectOption('LAST_STANDING');
  await expect(page.getByTestId('royal-mode-text')).toContainText('may leave them attacked and lose them');
  await tab(page, 'Board');
  // White: kings a1 and h1 behind pawns; Black: kings a8 and h8 and a queen next to the king on a1
  await editor.getByLabel('Start FEN').fill('k6k/8/8/8/8/8/1qP3PP/K6K w - - 0 1');
  await tab(page, 'Overview');
  await expect(page.getByTestId('royal-text')).toContainText('Each side starts with 2 royal pieces');
  await expect(page.getByTestId('royal-text')).toContainText('there is no check');
  await expect(page.getByTestId('fairy-text')).toContainText('Our engine only');
  await editor.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(editor.getByRole('button', { name: 'Saved', exact: true })).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`#variants/${id}$`));
  await page.screenshot({ path: `${SHOTS}/rules-two-kings.png`, fullPage: true });

  // the list says the Fairy-Stockfish part too
  const row = (await (await page.request.get('/api/variants')).json()).variants.find((v: { id: string }) => v.id === id);
  expect(row.fairy).toBe(false);
  expect(row.fairyReason).toBe('a side has more than one king');

  await playIt(page, id);
  // the king on a1 stands attacked by the queen and is not in check: White may play a pawn move
  await play(page, [['c2', 'c3', 'c3']]);
  // Black takes it; the queen now attacks the last king along the first rank, and it cannot get out
  await play(page, [['b2', 'a1', 'Qxa1']]);
  const result = page.getByTestId('result');
  await expect(result).toBeVisible();
  await expect(result).toContainText('0 – 1');
  await expect(result).toContainText('Checkmate · Black wins');
  await page.screenshot({ path: `${SHOTS}/rules-two-kings-won.png` });

  await backToChess(page);
  await remove(page, id);
});

test('a squares goal on chosen squares: a knight on d5 wins', async ({ page }) => {
  const id = 'rules-knight-to-d5';
  await remove(page, id);
  await page.goto('/#variants/chess');
  const editor = page.getByTestId('variant-editor');
  await editor.getByRole('button', { name: 'Make a copy' }).click();
  await editor.getByLabel('Name', { exact: true }).fill('Rules knight to d5');

  await editor.getByLabel('Way to win to add').selectOption('REACH_SQUARES');
  await editor.getByRole('button', { name: 'Add a way to win' }).click();
  const goal = page.getByTestId('goal').nth(1);
  await expect(goal).toHaveAttribute('data-kind', 'REACH_SQUARES');
  await expect(goal.getByTestId('goal-sentence')).toContainText('royal pieces stands on d4, d5, e4 or e5');
  await goal.getByLabel('Goal squares').fill('d5');
  await goal.getByLabel('Reach squares: Knight').check();
  await expect(goal.getByTestId('goal-sentence')).toHaveText('You win the moment one of your pieces of type Knight stands on d5.');
  // it can go first: the list is checked in order
  await goal.getByRole('button', { name: 'Move up' }).click();
  await expect(page.getByTestId('goal').nth(0)).toHaveAttribute('data-kind', 'REACH_SQUARES');

  // the other settings, each explained
  await editor.getByLabel('Stalemate').selectOption('LOSS');
  await expect(page.getByTestId('stalemate-text')).toContainText('that side loses');
  await editor.getByLabel('Move limit').fill('30');
  await expect(page.getByTestId('move-limit-text')).toContainText('After 30 moves');
  await editor.getByLabel('Castling sides').selectOption('KING_SIDE');
  await expect(page.getByTestId('castling-moves')).toContainText('White king side: e1 to g1');
  await expect(page.getByTestId('castling-moves')).not.toContainText('queen side');
  await expect(page.getByTestId('fairy-text')).toContainText('Fairy-Stockfish can play it');
  await editor.getByLabel('No castling through check').uncheck();
  await expect(page.getByTestId('fairy-text')).toContainText('castling through attacked squares');
  await editor.getByLabel('No castling through check').check();

  // the board shows the castling: a ring where the king lands, a dashed one for the rook
  await tab(page, 'Board');
  await expect(page.getByTestId('board-castlings')).toContainText('e1 to g1');
  await page.getByTestId('start-board').locator('[data-square="e1"]').hover();
  await expect(page.getByTestId('start-board').locator('.r-castle')).toHaveCount(1);
  await expect(page.getByTestId('start-board').locator('[data-square="g1"]')).toHaveClass(/r-castle/);
  await expect(page.getByTestId('start-board').locator('[data-square="f1"]')).toHaveClass(/r-partner/);

  await editor.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(editor.getByRole('button', { name: 'Saved', exact: true })).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`#variants/${id}/board$`));
  await tab(page, 'Overview');
  await page.screenshot({ path: `${SHOTS}/rules-squares-goal.png`, fullPage: true });

  await playIt(page, id);
  const dialogRule = page.getByText('Bring a Knight to d5, or checkmate.');
  await expect(dialogRule).toBeVisible();
  await play(page, [['b1', 'c3', 'Nc3'], ['a7', 'a6', 'a6'], ['c3', 'd5', 'Nd5']]);
  const result = page.getByTestId('result');
  await expect(result).toContainText('1 – 0');
  await expect(result).toContainText('Goal square reached · White wins');
  await page.screenshot({ path: `${SHOTS}/rules-squares-won.png` });

  await backToChess(page);
  await remove(page, id);
});
