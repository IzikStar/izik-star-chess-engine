import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

// The variant designer's screens: the list (search, families, the built-ins below), one variant's
// tabs reached by their address, a copy that stays in its family, and an unsaved change that
// survives switching tabs and is asked about before it is dropped.

const SHOTS = '../target/e2e-screens';

const MADE = [
  { id: 'pages-alpha-one', name: 'Pages alpha one', family: 'Pages alpha' },
  { id: 'pages-alpha-two', name: 'Pages alpha two', family: 'Pages alpha', goals: [{ kind: 'CHECKMATE' }, { kind: 'CHECKS', count: 3 }] },
  { id: 'pages-solo', name: 'Pages solo', family: '' },
];

/** Saves made variants straight through the API: chess with another name, family and goal. */
async function makeVariants(request: APIRequestContext) {
  const chess = await (await request.get('/api/variants/chess')).json();
  delete chess.builtIn;
  delete chess.art;
  for (const m of MADE) {
    const res = await request.put(`/api/variants/${m.id}`, { data: { ...chess, ...m, notes: `Notes of ${m.name}` } });
    expect(res.ok(), await res.text()).toBeTruthy();
  }
}

async function removeVariants(request: APIRequestContext) {
  const list = (await (await request.get('/api/variants')).json()).variants as { id: string; builtIn: boolean }[];
  for (const v of list) if (!v.builtIn && v.id.startsWith('pages-')) await request.delete(`/api/variants/${v.id}`);
}

const tabs = (page: Page) => page.getByRole('navigation', { name: 'Variant screens' });

test.beforeAll(async ({ request }) => {
  await removeVariants(request);
  await makeVariants(request);
});

test.afterAll(async ({ request }) => {
  await removeVariants(request);
});

test('the list: your variants first, grouped by family, a search, and the built-ins small below', async ({ page }) => {
  await page.goto('/#variants');
  const list = page.getByTestId('variant-list');
  const alpha = list.locator('[data-testid="variant-family"][data-family="Pages alpha"]');
  await expect(alpha.getByRole('heading', { name: /Pages alpha/ })).toBeVisible();
  await expect(alpha.locator('.variant-card')).toHaveCount(2);
  await expect(alpha.getByRole('button', { name: /Pages alpha two/ })).toContainText('Give 3 checks');
  await expect(alpha.getByRole('button', { name: /Pages alpha one/ })).toContainText('6 pieces');
  await expect(alpha.getByRole('button', { name: /Pages alpha one/ })).toContainText(/Changed/);
  const solo = list.locator('[data-testid="variant-family"][data-family=""]');
  await expect(solo.getByRole('heading', { name: /No family/ })).toBeVisible();
  await expect(solo.getByRole('button', { name: /Pages solo/ })).toBeVisible();
  // the built-ins come after the player's variants
  const builtIn = list.locator('.builtins');
  await expect(builtIn.getByRole('button', { name: /^Antichess/ })).toBeVisible();
  const mineBox = (await alpha.boundingBox())!;
  const builtInBox = (await builtIn.boundingBox())!;
  expect(builtInBox.y).toBeGreaterThan(mineBox.y);

  // the search looks at names, families and notes
  const search = list.getByLabel('Search variants');
  await search.fill('two');
  await expect(list.locator('.variant-card')).toHaveCount(1);
  await expect(list.getByRole('button', { name: /Pages alpha two/ })).toBeVisible();
  await expect(builtIn.getByRole('button')).toHaveCount(0);
  await search.fill('notes of pages solo');
  await expect(list.locator('.variant-card')).toHaveCount(1);
  await expect(list.getByRole('button', { name: /Pages solo/ })).toBeVisible();
  await search.fill('pages alpha');
  await expect(list.locator('.variant-card')).toHaveCount(2);
  await search.fill('');

  // a card opens the variant
  await list.getByRole('button', { name: /Pages alpha one/ }).click();
  await expect(page).toHaveURL(/#variants\/pages-alpha-one$/);
  await expect(page.getByTestId('variant-editor').getByRole('heading', { name: 'Pages alpha one' })).toBeVisible();
  await expect(page.getByLabel('Family')).toHaveValue('Pages alpha');
  await expect(page.getByLabel('Notes')).toHaveValue('Notes of Pages alpha one');
  await page.getByRole('link', { name: '← All variants' }).click();
  await expect(list).toBeVisible();
  await page.screenshot({ path: `${SHOTS}/variants-list.png`, fullPage: true });
});

test('each tab of a variant has its own address', async ({ page }) => {
  await page.goto('/#variants/pages-alpha-two/pieces/N');
  const editor = page.getByTestId('variant-editor');
  await expect(editor.getByRole('heading', { name: 'Pages alpha two' })).toBeVisible();
  await expect(tabs(page).getByRole('link', { name: 'Pieces' })).toHaveAttribute('aria-current', 'page');
  await expect(page.getByTestId('piece-editor').getByRole('heading', { name: 'Knight' })).toBeVisible();
  await expect(page.getByTestId('piece-preview').locator('.r-both')).toHaveCount(8);
  await expect(page.getByTestId('grid-legend')).toContainText('first move only');

  await page.goto('/#variants/pages-alpha-two/pieces');
  await expect(page.getByTestId('piece-editor')).toHaveCount(0);
  await expect(page.getByTestId('piece-list').getByRole('button')).toHaveCount(6);
  await page.getByTestId('piece-list').getByRole('button', { name: /Pawn/ }).click();
  await expect(page).toHaveURL(/#variants\/pages-alpha-two\/pieces\/P$/);
  await expect(page.getByTestId('piece-editor').getByRole('heading', { name: 'Pawn' })).toBeVisible();

  await page.goto('/#variants/pages-alpha-two/board');
  await expect(page.getByTestId('start-board')).toBeVisible();
  await expect(editor.getByLabel('Start FEN')).toHaveValue(/^rnbqkbnr\//);

  await page.goto('/#variants/pages-alpha-two/health');
  await expect(page.getByTestId('health')).toBeVisible();

  await page.goto('/#variants/pages-alpha-two');
  await expect(tabs(page).getByRole('link', { name: 'Overview' })).toHaveAttribute('aria-current', 'page');
  await expect(page.getByTestId('goal-text')).toContainText('giving check 3 times');
  await expect(page.getByTestId('castling-text')).toContainText('g-file');
  await expect(page.getByTestId('royal-text')).toContainText('The King is royal');

  // a built-in opens read-only, with the way to a copy of your own
  await page.goto('/#variants/antichess');
  await expect(page.getByTestId('builtin-cta')).toBeVisible();
  await expect(editor.getByRole('button', { name: 'Save', exact: true })).toHaveCount(0);
  await expect(page.getByTestId('goal-text')).toContainText('no pieces left');
  await expect(page.getByTestId('capture-text')).toContainText('you must play one of the captures');
  await expect(page.getByTestId('royal-text')).toContainText('No piece is royal');

  // a variant that is not there says so
  await page.goto('/#variants/pages-nothing');
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.getByRole('link', { name: '← All variants' })).toBeVisible();
});

test('a copy lands in the family of what it was copied from', async ({ page }) => {
  await page.goto('/#variants/pages-alpha-one');
  const editor = page.getByTestId('variant-editor');
  await expect(editor.getByRole('heading', { name: 'Pages alpha one' })).toBeVisible();
  await editor.getByRole('button', { name: 'Make a copy' }).click();
  await expect(page).toHaveURL(/#variants\/new$/);
  await expect(editor.getByRole('heading', { name: 'Pages alpha one copy' })).toBeVisible();
  await expect(editor.getByLabel('Family')).toHaveValue('Pages alpha');
  await editor.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(editor.getByRole('button', { name: 'Saved', exact: true })).toBeVisible();
  await expect(page).toHaveURL(/#variants\/pages-alpha-one-copy$/);

  await page.getByRole('link', { name: '← All variants' }).click();
  const alpha = page.getByTestId('variant-list').locator('[data-testid="variant-family"][data-family="Pages alpha"]');
  await expect(alpha.locator('.variant-card')).toHaveCount(3);
  await expect(alpha.getByRole('button', { name: /Pages alpha one copy/ })).toBeVisible();

  // a copy of a variant with no family starts a family named after it
  await page.goto('/#variants/pages-solo');
  await editor.getByRole('button', { name: 'Make a copy' }).click();
  await expect(editor.getByLabel('Family')).toHaveValue('Pages solo');
});

test('an unsaved change survives switching tabs, and leaving asks first', async ({ page }) => {
  await page.goto('/#variants/pages-solo');
  const editor = page.getByTestId('variant-editor');
  await expect(editor.getByRole('heading', { name: 'Pages solo' })).toBeVisible();
  await editor.getByLabel('Name', { exact: true }).fill('Pages solo edited');
  await editor.getByLabel('Way to win to add').selectOption('REACH_SQUARES');
  await editor.getByRole('button', { name: 'Add a way to win' }).click();
  await expect(page.getByTestId('goal-text')).toContainText('d4, d5, e4 or e5');
  await expect(editor.getByText('Unsaved changes')).toBeVisible();

  // the board: take the a1 rook away
  await tabs(page).getByRole('link', { name: 'Board' }).click();
  await page.getByTestId('start-board').locator('[data-square="a1"]').click({ button: 'right' });
  await expect(editor.getByLabel('Start FEN')).toHaveValue(/^rnbqkbnr\/pppppppp\/8\/8\/8\/8\/PPPPPPPP\/1NBQKBNR /);
  // the pieces tab, and back: everything is still there
  await tabs(page).getByRole('link', { name: 'Pieces' }).click();
  await expect(page.getByTestId('piece-list')).toBeVisible();
  await tabs(page).getByRole('link', { name: 'Overview' }).click();
  await expect(editor.getByLabel('Name', { exact: true })).toHaveValue('Pages solo edited');
  await expect(page.getByTestId('goal').nth(1)).toHaveAttribute('data-kind', 'REACH_SQUARES');
  await tabs(page).getByRole('link', { name: 'Board' }).click();
  await expect(editor.getByLabel('Start FEN')).toHaveValue(/\/1NBQKBNR /);

  // leaving for the list asks; staying keeps the change
  let asked = '';
  page.once('dialog', (d) => { asked = d.message(); d.dismiss(); });
  await page.getByRole('link', { name: '← All variants' }).click();
  await expect.poll(() => asked).toContain('unsaved changes');
  await expect(page).toHaveURL(/#variants\/pages-solo\/board$/);
  await expect(editor.getByLabel('Start FEN')).toHaveValue(/\/1NBQKBNR /);
  // so does another page from the top bar
  asked = '';
  page.once('dialog', (d) => { asked = d.message(); d.dismiss(); });
  await page.getByRole('button', { name: 'My games' }).click();
  await expect.poll(() => asked).toContain('unsaved changes');
  await expect(editor).toBeVisible();

  // saved, nothing is asked
  await editor.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(editor.getByRole('button', { name: 'Saved', exact: true })).toBeVisible();
  await expect(editor.getByText('Unsaved changes')).toHaveCount(0);
  await page.getByRole('link', { name: '← All variants' }).click();
  await expect(page.getByTestId('variant-list').getByRole('button', { name: /Pages solo edited/ })).toContainText('King to the centre');

  // and leaving with a change, said yes to, drops it
  await page.getByTestId('variant-list').getByRole('button', { name: /Pages solo edited/ }).click();
  await editor.getByLabel('Name', { exact: true }).fill('Pages solo dropped');
  page.once('dialog', (d) => d.accept());
  await page.getByRole('link', { name: '← All variants' }).click();
  await expect(page.getByTestId('variant-list').getByRole('button', { name: /Pages solo edited/ })).toBeVisible();
  await expect(page.getByTestId('variant-list').getByRole('button', { name: /Pages solo dropped/ })).toHaveCount(0);
});
