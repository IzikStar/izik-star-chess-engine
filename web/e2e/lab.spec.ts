import { expect, test } from '@playwright/test';

// The Lab over the tiny chess run playwright.config.ts records before starting the server, and a
// run started from the page.

test('the lab shows a run over several screens, replays a game and starts a game against its champion', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Lab' }).click();
  await expect(page).toHaveURL(/#lab$/);

  // the runs screen: one finished run
  const card = page.getByTestId('run-card').filter({ hasText: 'Demo' });
  await expect(card).toContainText('finished');
  await expect(card).toContainText('2 of 2 generations');
  await card.getByRole('button', { name: 'Open' }).click();
  await expect(page).toHaveURL(/#lab\/run\/demo\.db$/);

  const run = page.getByTestId('run');
  await expect(run.getByRole('heading', { name: /Demo/ })).toBeVisible();
  await expect(run.getByRole('img', { name: /Champion Elo/ })).toBeVisible();
  await expect(run.getByRole('img', { name: /Decisive games/ })).toBeVisible();

  // every generation in a table, with the yardstick columns
  await run.getByRole('button', { name: 'Generations', exact: true }).click();
  const generations = page.getByTestId('generations');
  await expect(generations).toContainText('vs Default weights');
  await expect(generations).toContainText('vs Classic weights');
  await expect(generations.locator('tbody tr')).toHaveCount(2);
  await page.screenshot({ path: '../target/e2e-screens/lab-generations.png', fullPage: true });

  // the weights and the settings screens
  await run.getByRole('button', { name: 'Weights', exact: true }).click();
  await expect(page.getByTestId('weights')).toBeVisible();
  await run.getByRole('button', { name: 'Settings', exact: true }).click();
  await expect(page.locator('pre.cli')).toContainText('lab.Cli run runs/demo.db');

  // generation 1: 28 pairings × 2 colours, then the champion against two yardsticks (2 openings × 2 colours each)
  await page.getByLabel('Generation', { exact: true }).selectOption('1');
  await expect(page).toHaveURL(/#lab\/run\/demo\.db\/gen\/1$/);
  const games = page.getByTestId('games').locator('tbody tr');
  await expect(games).toHaveCount(56 + 8);
  const yardsticks = page.getByTestId('yardsticks');
  await expect(yardsticks).toContainText('Default weights');
  await expect(yardsticks).toContainText('Classic weights');
  await expect(page.getByTestId('standings').locator('tbody tr')).toHaveCount(8);
  await page.getByRole('group', { name: 'Which games' }).getByRole('button', { name: 'Yardsticks' }).click();
  await expect(games).toHaveCount(8);
  await page.getByRole('group', { name: 'Which games' }).getByRole('button', { name: 'All' }).click();
  await page.getByLabel('Note').fill('e2e pick');
  await page.getByRole('button', { name: /Keep champion #\d+ in the hall of fame/ }).click();
  await expect(page.getByTestId('kept')).toContainText('hof:Demo-g1-m');
  await expect(page.getByRole('link', { name: 'Download the games (PGN)' })).toBeVisible();
  await page.screenshot({ path: '../target/e2e-screens/lab-generation.png', fullPage: true });
  await games.first().click();
  const replay = page.getByTestId('replay');
  await expect(replay.locator('[data-testid="board"]')).toBeVisible();
  await replay.getByRole('button', { name: 'First position' }).click();
  await expect(replay.getByRole('button', { name: 'Previous move' })).toBeDisabled();
  await expect(replay.getByRole('button', { name: 'Next move' })).toBeEnabled();

  // the hall of fame holds the run's last champion and the one just kept
  await page.getByRole('button', { name: 'Hall of fame' }).click();
  const fame = page.getByTestId('fame');
  await expect(fame).toContainText('last champion');
  await expect(fame).toContainText('e2e pick');

  await page.getByRole('button', { name: 'Runs' }).click();
  await card.getByRole('button', { name: 'Open' }).click();
  await page.getByRole('button', { name: "Play generation 1's champion" }).click();
  const dialog = page.getByRole('dialog', { name: 'New game' });
  await expect(dialog.getByTestId('champion-pick')).toContainText('Champion of Demo, generation 1');
  await dialog.getByRole('button', { name: 'Start game' }).click();

  await expect(page).not.toHaveURL(/#lab/);
  await expect(page.getByTestId('player-black')).toContainText('Champion of Demo, generation 1');
  await page.locator('[data-square="e2"]').first().click();
  await page.locator('[data-square="e4"]').first().click();
  await expect(page.getByTestId('move-list')).toContainText('e4');
  await expect(page.getByTestId('status')).toContainText('Your move', { timeout: 20_000 });

  // a new game from the dialog without the champion is against the usual engine again
  await page.getByRole('button', { name: 'New game' }).click();
  await dialog.getByRole('button', { name: 'Usual engine' }).click();
  await dialog.getByRole('button', { name: 'Start game' }).click();
  await expect(page.getByTestId('player-black')).toContainText('Black · Level');
});

test('an antichess run from zero is started on the page, finishes, and its champion plays antichess', async ({ page }) => {
  await page.goto('/#lab/new');
  const form = page.getByTestId('new-run');
  await expect(form.getByRole('heading', { name: 'New run' })).toBeVisible();
  await page.screenshot({ path: '../target/e2e-screens/lab-new-run.png', fullPage: true });
  await form.getByLabel('Name', { exact: true }).fill('E2E anti');
  await form.getByLabel('Game', { exact: true }).selectOption('antichess');
  await form.getByLabel('Algorithm', { exact: true }).selectOption('evolution.FromZero');
  await form.getByLabel('Population', { exact: true }).fill('4');
  await form.getByLabel('Generations', { exact: true }).fill('2');
  await form.getByLabel('Search depth', { exact: true }).fill('1');
  // the rest of the settings wait under Advanced settings
  await form.getByText('Advanced settings').click();
  await form.getByLabel('Deep depth', { exact: true }).fill('0');
  await form.getByLabel('Openings per pairing', { exact: true }).fill('1');
  await form.getByLabel('Measure every N generations', { exact: true }).fill('1');
  await form.getByLabel('Yardstick openings', { exact: true }).fill('1');
  await form.getByLabel('Threads', { exact: true }).fill('2');
  // outside chess the book and Stockfish are off
  await expect(form.getByLabel('Openings from', { exact: true })).toBeDisabled();
  await expect(form.getByLabel('Stockfish (auto level)', { exact: true })).toBeDisabled();
  // ...but Fairy-Stockfish knows antichess, and the random mover plays any game
  await expect(form.getByLabel('Fairy-Stockfish (20,000 nodes)', { exact: true })).toBeEnabled();
  await form.getByLabel('Random mover', { exact: true }).check();
  await expect(form).toContainText('4 members → 6 pairings → 12 games a generation');
  await form.getByRole('button', { name: 'Start the run' }).click();

  await expect(page).toHaveURL(/#lab\/run\/e2e-anti\.db$/);
  const run = page.getByTestId('run');
  await expect(run.getByRole('heading', { name: /E2E anti/ })).toBeVisible();
  // ("finished" alone would also match "No generation finished yet")
  await expect(run).toContainText('2 of 2 generations', { timeout: 60_000 });
  await expect(run).toContainText('finished');
  await expect(run).toContainText('Antichess · FromZero');
  await run.getByRole('button', { name: 'Generations', exact: true }).click();
  await expect(page.getByTestId('generations')).toContainText('vs All-zero weights');
  await expect(page.getByTestId('generations')).toContainText('vs Random mover');
  await page.getByLabel('Generation', { exact: true }).selectOption('0');
  // the standings show what each member thinks the pieces are worth; member 0 started from nothing
  const standings = page.getByTestId('standings');
  await expect(standings.locator('thead')).toContainText('queen');
  await expect(standings.locator('tbody tr')).toHaveCount(4);

  await page.getByRole('button', { name: /Play champion #\d+/ }).click();
  const dialog = page.getByRole('dialog', { name: 'New game' });
  await expect(dialog.getByTestId('champion-pick')).toContainText('Champion of E2E anti, generation 0');
  await expect(dialog.getByTestId('variant-rule')).toContainText('Captures are forced');
  await dialog.getByRole('button', { name: 'Start game' }).click();
  await expect(page.getByTestId('player-black')).toContainText('Champion of E2E anti');

  // the runs screen lists both, and the finished antichess run can be deleted
  await page.getByRole('button', { name: 'Lab' }).click();
  const card = page.getByTestId('run-card').filter({ hasText: 'E2E anti' });
  await expect(card).toContainText('finished');
  page.once('dialog', (d) => d.accept());
  await card.getByRole('button', { name: 'Delete' }).click();
  await expect(card).toHaveCount(0);
});
