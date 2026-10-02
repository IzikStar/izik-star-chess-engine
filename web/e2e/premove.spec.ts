import { expect, test, type Page, type WebSocketRoute } from '@playwright/test';

// Premoves in set-up positions. The real server cannot start from a chosen position, so these
// tests stand in for it on the WebSocket: they send the states a game would and check which moves
// the browser sends back.

const square = (page: Page, name: string) => page.locator(`[data-square="${name}"]`).first();

async function clickMove(page: Page, from: string, to: string) {
  await square(page, from).click();
  await square(page, to).click();
}

function state(fen: string, legalMoves: string[] = []) {
  const humanTurn = fen.split(' ')[1] === 'w';
  return JSON.stringify({
    type: 'state',
    events: [],
    state: {
      startFen: fen, fen, turn: humanTurn ? 'white' : 'black', status: 'IN_PROGRESS', result: null,
      humanTurn, engineThinking: !humanTurn, hintPending: false, hint: null, material: 0,
      legalMoves, moves: [], config: { mode: 'engine', humanColor: 'white', level: 1, blackLevel: 1 },
    },
  });
}

const move = (uci: string) => JSON.stringify({ type: 'move', uci });

/** Opens the app against a stand-in server that starts in `first` (the engine, Black, to move). */
async function fakeServer(page: Page, first: string) {
  const sent: string[] = [];
  let socket: WebSocketRoute | undefined;
  await page.routeWebSocket('**/ws', (ws) => {
    socket = ws;
    ws.onMessage((m) => sent.push(String(m)));
    ws.send(first);
  });
  await page.goto('/');
  await expect(page.getByTestId('status')).toContainText('Engine is thinking');
  return { sent, send: (msg: string) => socket!.send(msg) };
}

/** White to promote on a7 while the engine thinks, then after Black's reply Ke8-f7. */
const promoThinking = state('4k3/P7/8/8/8/8/8/4K3 b - - 0 1');
const promoTurn = state('8/P4k2/8/8/8/8/8/4K3 w - - 1 2', ['a7a8q', 'a7a8r', 'a7a8b', 'a7a8n', 'e1d1', 'e1d2', 'e1e2', 'e1f1', 'e1f2']);

async function premoveA8(page: Page) {
  await clickMove(page, 'a7', 'a8');
  const picker = page.getByRole('dialog', { name: 'Promote to' });
  await expect(picker).toBeVisible();
  return picker;
}

test('a promotion premove asks for the piece and plays it on the next turn', async ({ page }) => {
  const server = await fakeServer(page, promoThinking);
  const picker = await premoveA8(page);
  await picker.getByRole('button', { name: 'Knight' }).click();
  await expect(page.getByTestId('status')).toContainText('Premove a7–a8=N');

  server.send(promoTurn);
  await expect.poll(() => server.sent).toContain(move('a7a8n'));
});

test('the piece picker stays open if the engine replies while choosing', async ({ page }) => {
  const server = await fakeServer(page, promoThinking);
  const picker = await premoveA8(page);
  server.send(promoTurn);
  await expect(page.getByTestId('status')).not.toContainText('Engine is thinking');
  await expect(picker).toBeVisible();
  await picker.getByRole('button', { name: 'Rook' }).click();
  await expect.poll(() => server.sent).toContain(move('a7a8r'));
});

test('several premoves are played one per turn, and an illegal one drops the rest', async ({ page }) => {
  // after 1.e4, Black thinking: queue d2-d4, then the same pawn d4-d5, then Ng1-f3
  const server = await fakeServer(page, state('rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1'));
  await clickMove(page, 'd2', 'd4');
  await clickMove(page, 'd4', 'd5'); // the pawn is shown on d4 already, so it can be moved on
  await clickMove(page, 'g1', 'f3');
  await expect(page.getByTestId('status')).toContainText('Premoves d2–d4, d4–d5, g1–f3');

  // 1...a6: d2-d4 is played at once, and only that
  server.send(state('rnbqkbnr/1ppppppp/p7/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2', ['d2d4', 'd2d3', 'g1f3']));
  await expect.poll(() => server.sent).toEqual([move('d2d4')]);
  server.send(state('rnbqkbnr/1ppppppp/p7/8/3PP3/8/PPP2PPP/RNBQKBNR b KQkq - 0 2'));
  await expect(page.getByTestId('status')).toContainText('Premoves d4–d5, g1–f3');

  // 2...e5 attacks d4 but d4-d5 is still legal: played
  server.send(state('rnbqkbnr/1ppp1ppp/p7/4p3/3PP3/8/PPP2PPP/RNBQKBNR w KQkq - 0 3', ['d4d5', 'd4e5', 'g1f3']));
  await expect.poll(() => server.sent).toEqual([move('d2d4'), move('d4d5')]);
  server.send(state('rnbqkbnr/1ppp1ppp/p7/3Pp3/4P3/8/PPP2PPP/RNBQKBNR b KQkq - 0 3'));
  await expect(page.getByTestId('status')).toContainText('Premove g1–f3');

  // 3...Bb4+: Ng1-f3 no longer answers the check, so it is dropped, not played
  server.send(state('rnbqk1nr/1ppp1ppp/p7/3Pp3/1b2P3/8/PPP2PPP/RNBQKBNR w KQkq - 1 4', ['c2c3', 'b1c3', 'b1d2', 'c1d2', 'd1d2', 'e1e2']));
  await expect(page.getByTestId('status')).toContainText('Your move');
  await page.waitForTimeout(300);
  expect(server.sent).toEqual([move('d2d4'), move('d4d5')]);
});
