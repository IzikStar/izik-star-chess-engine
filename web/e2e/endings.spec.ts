import { expect, test, type Page } from '@playwright/test';

// Clocks, resigning, draw offers and PGN, through the browser against the packaged jar.

const SHOTS = '../target/e2e-screens';
const square = (page: Page, name: string) => page.locator(`[data-square="${name}"]`).first();
const moveList = (page: Page) => page.getByTestId('move-list');

async function clickMove(page: Page, from: string, to: string) {
  await square(page, from).click();
  await square(page, to).click();
}

async function newGame(page: Page, opponent: 'Computer' | 'A friend', time = 'Untimed', level?: number) {
  await page.getByRole('button', { name: 'New game' }).click();
  const dialog = page.getByRole('dialog', { name: 'New game' });
  await dialog.getByRole('button', { name: opponent, exact: true }).click();
  if (level !== undefined) await dialog.getByLabel('Strength', { exact: true }).fill(String(level));
  await dialog.getByRole('button', { name: time, exact: true }).click();
  await dialog.getByRole('button', { name: 'Start game' }).click();
  await expect(dialog).toBeHidden();
}

/** Sends one command over a second socket (the server shares the game with every browser). */
async function sendCommand(page: Page, command: object) {
  await page.evaluate((cmd) => new Promise<void>((resolve) => {
    const ws = new WebSocket(`ws://${location.host}/ws`);
    ws.onopen = () => {
      ws.send(JSON.stringify(cmd));
      setTimeout(() => {
        ws.close();
        resolve();
      }, 200);
    };
  }), command);
}

test('a timed game: clocks start at the time control and run for the side to move', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'New game' }).click();
  await page.getByRole('dialog', { name: 'New game' }).getByRole('button', { name: '3+2', exact: true }).click();
  await page.screenshot({ path: `${SHOTS}/new-game-time.png` });
  await page.getByRole('dialog', { name: 'New game' }).getByRole('button', { name: 'Cancel' }).click();
  await newGame(page, 'A friend', '3+2');
  await expect(page.getByTestId('clock-white')).toHaveText('3:00');
  await expect(page.getByTestId('clock-black')).toHaveText('3:00');
  await clickMove(page, 'e2', 'e4');
  await expect(page.getByTestId('clock-black')).toHaveClass(/running/);
  await expect(page.getByTestId('clock-black')).not.toHaveText('3:00', { timeout: 3000 });
  await expect(page.getByTestId('clock-white')).toHaveText('3:00'); // the first move is not timed
  await clickMove(page, 'e7', 'e5');
  await expect(page.getByTestId('clock-white')).toHaveClass(/running/);
  await page.screenshot({ path: `${SHOTS}/clocks.png` });

  // an untimed game has no clocks
  await newGame(page, 'A friend', 'Untimed');
  await expect(page.getByTestId('clock-white')).toHaveCount(0);
});

test('running out of time loses the game', async ({ page }) => {
  await page.goto('/');
  await sendCommand(page, { type: 'newGame', mode: 'friend', color: 'white', level: 1, blackLevel: 1, time: { initialMs: 2500, incrementMs: 0 } });
  await expect(page.getByTestId('clock-white')).toHaveText('0:02.5');
  await clickMove(page, 'e2', 'e4');
  const result = page.getByTestId('result');
  await expect(result).toContainText('Black ran out of time · White wins', { timeout: 10_000 });
  await expect(result).toContainText('1 – 0');
  await expect(page.getByTestId('clock-black')).toHaveText('0:00.0');
  await expect(page.getByRole('button', { name: 'Take back' })).toBeDisabled();
  await page.screenshot({ path: `${SHOTS}/flag.png` });
});

test('resigning against the computer asks first, then ends the game', async ({ page }) => {
  await page.goto('/');
  await newGame(page, 'Computer', 'Untimed', 3);
  await clickMove(page, 'e2', 'e4');
  await expect(moveList(page).getByRole('button')).toHaveCount(2);
  await page.getByRole('button', { name: 'Resign' }).click();
  const confirm = page.getByTestId('confirm-resign');
  await expect(confirm).toContainText('Resign this game?');
  await page.screenshot({ path: `${SHOTS}/resign-confirm.png` });
  await confirm.getByRole('button', { name: 'Keep playing' }).click();
  await expect(confirm).toBeHidden();

  await page.getByRole('button', { name: 'Resign' }).click();
  await confirm.getByRole('button', { name: 'Resign' }).click();
  const result = page.getByTestId('result');
  await expect(result).toContainText('White resigns · Black wins');
  await expect(result).toContainText('0 – 1');
  await expect(page.getByRole('button', { name: 'Resign' })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Offer draw' })).toBeDisabled();
});

test('the engine accepts a draw in an even position', async ({ page }) => {
  await page.goto('/');
  await newGame(page, 'Computer', 'Untimed', 3);
  await page.getByRole('button', { name: 'Offer draw' }).click();
  await expect(page.getByTestId('result')).toContainText('Draw by agreement');
  await expect(page.getByTestId('result')).toContainText('½ – ½');
});

test('two players: one offers a draw, the other declines, then accepts', async ({ page }) => {
  await page.goto('/');
  await newGame(page, 'A friend');
  await page.getByRole('button', { name: 'Offer draw' }).click();
  const offer = page.getByTestId('draw-offer');
  await expect(offer).toContainText('White offers a draw. Black, do you accept?');
  await expect(page.getByRole('button', { name: 'Offer draw' })).toBeDisabled();
  await page.screenshot({ path: `${SHOTS}/draw-offer.png` });
  await offer.getByRole('button', { name: 'Decline' }).click();
  await expect(page.getByTestId('notice')).toHaveText('Black declines the draw.');

  await clickMove(page, 'e2', 'e4');
  await expect(page.getByTestId('notice')).toBeHidden();
  await page.getByRole('button', { name: 'Offer draw' }).click(); // Black offers now
  await expect(offer).toContainText('Black offers a draw.');
  await offer.getByRole('button', { name: 'Accept draw' }).click();
  await expect(page.getByTestId('result')).toContainText('Draw by agreement');
});

test('PGN: copy and download the game, load another one', async ({ page, context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await page.goto('/');
  await newGame(page, 'A friend', '5+0');
  await clickMove(page, 'e2', 'e4');
  await clickMove(page, 'e7', 'e5');
  await page.getByRole('button', { name: 'PGN' }).click();
  const dialog = page.getByRole('dialog', { name: 'PGN' });
  const out = dialog.getByLabel('This game');
  await expect(out).toHaveValue(/\[TimeControl "300"\]/);
  await expect(out).toHaveValue(/\n1\. e4 e5 \*\n$/);

  await dialog.getByRole('button', { name: 'Copy' }).click();
  await expect(dialog.getByText('Copied')).toBeVisible();
  expect(await page.evaluate(() => navigator.clipboard.readText())).toContain('1. e4 e5 *');

  const [download] = await Promise.all([page.waitForEvent('download'), dialog.getByRole('button', { name: 'Download .pgn' }).click()]);
  expect(download.suggestedFilename()).toMatch(/^izikstar-\d{4}-\d{2}-\d{2}\.pgn$/);

  const paste = dialog.getByLabel('Load a game');
  await paste.fill('1. e4 e5 2. Ke3');
  await dialog.getByRole('button', { name: 'Load game' }).click();
  await expect(dialog.getByRole('alert')).toHaveText('Could not load it: move 2. Ke3 is not a legal move');
  await page.screenshot({ path: `${SHOTS}/pgn-dialog.png` });

  await paste.fill('[Event "Opera game"]\n1. e4 e5 2. Nf3 d6 3. d4 Bg4 {a comment} 4. dxe5 Bxf3 5. Qxf3 dxe5 6. Bc4 Nf6 7. Qb3 Qe7\n8. Nc3 c6 9. Bg5 b5 10. Nxb5 cxb5 11. Bxb5+ Nbd7 12. O-O-O Rd8 13. Rxd7 Rxd7\n14. Rd1 Qe6 15. Bxd7+ Nxd7 16. Qb8+ Nxb8 17. Rd8# 1-0');
  await dialog.getByRole('button', { name: 'Load game' }).click();
  await expect(dialog).toBeHidden();
  await expect(moveList(page)).toContainText('Rd8#');
  await expect(page.getByTestId('result')).toContainText('Checkmate · White wins');
  await expect(page.getByTestId('clock-white')).toHaveCount(0);
  await page.keyboard.press('ArrowLeft');
  await expect(page.getByTestId('status')).toContainText('Reviewing 16… Nxb8');
});
