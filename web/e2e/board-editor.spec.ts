import { expect, test, type Locator, type Page } from '@playwright/test';
import { tab } from './helpers';

// The designer's big start-position editor (variants/BoardEditor.tsx) and the piece drawer
// (variants/PieceDrawer.tsx): click a square for a piece, drag, swap, stamp, take away, the keys;
// draw a piece's picture and see it on the board.

const SHOTS = '../target/e2e-screens';

/** Presses on one element, moves the mouse over another in steps and lets go there. */
async function drag(page: Page, from: Locator, to: Locator | { x: number; y: number }) {
  const a = (await from.boundingBox())!;
  await page.mouse.move(a.x + a.width / 2, a.y + a.height / 2);
  await page.mouse.down();
  let x: number;
  let y: number;
  if ('x' in to) ({ x, y } = to);
  else {
    const b = (await to.boundingBox())!;
    x = b.x + b.width / 2;
    y = b.y + b.height / 2;
  }
  await page.mouse.move(x, y, { steps: 6 });
  await page.mouse.up();
}

async function copyChess(page: Page, name: string) {
  await page.goto('/#variants/chess');
  const editor = page.getByTestId('variant-editor');
  await expect(editor.getByRole('heading', { name: 'Chess' })).toBeVisible();
  await editor.getByRole('button', { name: 'Make a copy' }).click();
  await editor.getByLabel('Name', { exact: true }).first().fill(name);
  return editor;
}

/** Opens one of the variant's screens by its tab. */
test('the start-position editor: click, drag, swap, stamp, take away, keys', async ({ page }) => {
  const editor = await copyChess(page, 'Board editor test');
  await tab(page, 'Board');
  const board = page.getByTestId('start-board');
  const sq = (name: string) => board.locator(`[data-square="${name}"]`);
  const piece = (name: string, code: string) => sq(name).locator(`[data-piece="${code}"]`);

  // click an empty square: a little palette there; choose the white knight
  await sq('e4').click();
  const pop = page.getByRole('dialog', { name: 'Piece for e4' });
  await expect(pop).toBeVisible();
  await pop.getByRole('button', { name: 'white Knight' }).click();
  await expect(pop).toBeHidden();
  await expect(piece('e4', 'wN')).toBeVisible();

  // drag it to d5; then onto the e2 pawn, which swaps them
  await drag(page, sq('e4'), sq('d5'));
  await expect(piece('d5', 'wN')).toBeVisible();
  await expect(sq('e4').locator('[data-piece]')).toHaveCount(0);
  await drag(page, sq('d5'), sq('e2'));
  await expect(piece('e2', 'wN')).toBeVisible();
  await expect(piece('d5', 'wP')).toBeVisible();

  // right-click takes it away
  await sq('d5').click({ button: 'right' });
  await expect(sq('d5').locator('[data-piece]')).toHaveCount(0);

  // pick the black queen in the palette and stamp it; Esc stops, and a click opens the palette again
  await page.getByRole('button', { name: 'black Q', exact: true }).click();
  await sq('a3').click();
  await sq('b3').click();
  await expect(piece('a3', 'bQ')).toBeVisible();
  await expect(piece('b3', 'bQ')).toBeVisible();
  await page.keyboard.press('Escape');
  await sq('c3').click();
  await expect(page.getByRole('dialog', { name: 'Piece for c3' })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog', { name: 'Piece for c3' })).toBeHidden();
  await expect(sq('c3').locator('[data-piece]')).toHaveCount(0);

  // drag from the palette onto the board, and a piece off the board
  await drag(page, page.getByRole('button', { name: 'white B', exact: true }), sq('h4'));
  await expect(piece('h4', 'wB')).toBeVisible();
  const head = (await editor.getByRole('heading', { name: 'Start position' }).boundingBox())!;
  await drag(page, sq('a3'), { x: head.x + 5, y: head.y + 5 });
  await expect(sq('a3').locator('[data-piece]')).toHaveCount(0);

  // keys: from the last square (h4) up one, a letter puts a piece there, Delete clears it
  await board.focus();
  await page.keyboard.press('ArrowUp');
  await page.keyboard.press('Shift+R');
  await expect(piece('h5', 'wR')).toBeVisible();
  await page.keyboard.press('ArrowLeft');
  await page.keyboard.press('n');
  await expect(piece('g5', 'bN')).toBeVisible();
  await page.keyboard.press('Delete');
  await expect(sq('g5').locator('[data-piece]')).toHaveCount(0);

  // undo brings the knight back; the FEN follows every change
  await page.getByRole('button', { name: 'Undo' }).click();
  await expect(piece('g5', 'bN')).toBeVisible();
  await expect(editor.getByLabel('Start FEN')).toHaveValue('rnbqkbnr/pppppppp/8/6nR/7B/1q6/PPPPNPPP/RNBQKBNR w KQkq - 0 1');
  await page.screenshot({ path: `${SHOTS}/board-editor.png`, fullPage: true });

  // the toolbar: clear, then the standard start, then mirror White's pieces onto Black's side
  await page.getByRole('button', { name: 'Clear board' }).click();
  await expect(board.locator('[data-piece]')).toHaveCount(0);
  await page.getByRole('button', { name: 'Standard start' }).click();
  await expect(editor.getByLabel('Start FEN')).toHaveValue('rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1');
  await sq('a8').click({ button: 'right' });
  await sq('d8').click({ button: 'right' });
  await page.getByRole('button', { name: 'Mirror White to Black' }).click();
  await expect(editor.getByLabel('Start FEN')).toHaveValue('rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1');
});

test('draw a piece picture and see it on the board', async ({ page }) => {
  const editor = await copyChess(page, 'Drawn piece');
  await tab(page, 'Pieces');
  await editor.getByRole('button', { name: 'Add a piece' }).click();
  const pieceEditor = page.getByTestId('piece-editor');
  await pieceEditor.getByLabel('Name', { exact: true }).fill('Wizard');
  await pieceEditor.getByLabel('Betza').fill('WF');
  await pieceEditor.getByLabel('Betza').press('Enter');
  await expect(page.getByTestId('piece-preview').locator('.r-both')).toHaveCount(8);
  // put it on d4 and e5 with the little palette
  await tab(page, 'Board');
  const board = page.getByTestId('start-board');
  for (const [square, name] of [['d4', 'white Wizard'], ['e5', 'black Wizard']]) {
    await board.locator(`[data-square="${square}"]`).click();
    await page.getByRole('dialog', { name: `Piece for ${square}` }).getByRole('button', { name }).click();
  }
  await editor.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(editor.getByRole('button', { name: 'Saved', exact: true })).toBeVisible();

  await tab(page, 'Pieces');
  await page.getByTestId('piece-list').getByRole('button', { name: /Wizard/ }).click();
  const pictures = page.getByTestId('pictures');
  await pictures.getByRole('button', { name: 'Draw it' }).first().click();
  const dialog = page.getByRole('dialog', { name: 'Draw the A' });
  await expect(dialog).toBeVisible();
  const canvas = dialog.getByTestId('piece-canvas');
  const box = (await canvas.boundingBox())!;
  const at = (fx: number, fy: number) => ({ x: box.x + box.width * fx, y: box.y + box.height * fy });

  // an ellipse for the body, a line for the base, a pen stroke, and a fill
  await dialog.getByRole('button', { name: 'Ellipse' }).click();
  await page.mouse.move(at(0.3, 0.2).x, at(0.3, 0.2).y);
  await page.mouse.down();
  await page.mouse.move(at(0.7, 0.75).x, at(0.7, 0.75).y, { steps: 5 });
  await page.mouse.up();
  await dialog.getByRole('button', { name: 'Line' }).click();
  await page.mouse.move(at(0.2, 0.85).x, at(0.2, 0.85).y);
  await page.mouse.down();
  await page.mouse.move(at(0.8, 0.85).x, at(0.8, 0.85).y, { steps: 5 });
  await page.mouse.up();
  await dialog.getByRole('button', { name: 'Pen' }).click();
  await dialog.getByRole('button', { name: 'Gold' }).click();
  await page.mouse.move(at(0.5, 0.1).x, at(0.5, 0.1).y);
  await page.mouse.down();
  for (const [fx, fy] of [[0.45, 0.2], [0.55, 0.3], [0.45, 0.4]]) await page.mouse.move(at(fx, fy).x, at(fx, fy).y, { steps: 3 });
  await page.mouse.up();
  await dialog.getByRole('button', { name: 'Fill' }).click();
  await dialog.getByRole('button', { name: 'Ivory' }).click();
  await canvas.click({ position: { x: box.width * 0.5, y: box.height * 0.6 } });
  // the drawing is not empty
  const filled = await canvas.evaluate((c: HTMLCanvasElement) => {
    const d = c.getContext('2d')!.getImageData(0, 0, c.width, c.height).data;
    let n = 0;
    for (let i = 3; i < d.length; i += 4) if (d[i]) n++;
    return n;
  });
  expect(filled).toBeGreaterThan(2000);
  await dialog.getByRole('button', { name: 'Undo' }).click();
  await dialog.getByRole('button', { name: 'Redo' }).click();
  await dialog.getByRole('button', { name: 'Make the black one from the white one' }).click();
  await expect(dialog.getByRole('button', { name: 'Black piece' })).toHaveAttribute('aria-pressed', 'true');
  await page.screenshot({ path: `${SHOTS}/piece-drawer.png` });
  await dialog.getByRole('button', { name: 'Save pictures' }).click();
  await expect(dialog).toBeHidden();

  // both sides have their own picture now, in the piece editor and on the board
  await expect(pictures.getByRole('img', { name: 'White picture' })).toBeVisible();
  await expect(pictures.getByRole('img', { name: 'Black picture' })).toBeVisible();
  await tab(page, 'Board');
  await expect(board.locator('[data-square="d4"] img[data-piece="wA"]')).toBeVisible();
  await expect(board.locator('[data-square="e5"] img[data-piece="bA"]')).toBeVisible();
  await expect(board.locator('[data-square="e5"] img[data-piece="bA"]')).not.toHaveCSS('filter', /brightness/);

  page.once('dialog', (d) => d.accept());
  await editor.getByRole('button', { name: 'Delete' }).click();
  await expect(page.getByTestId('variant-list').getByRole('button', { name: 'Drawn piece' })).toHaveCount(0);
});
