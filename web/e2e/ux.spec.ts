import { expect, test } from '@playwright/test';

// Small things that make the screens readable and pleasant (the UX review of 2026-10-06).

test('a run card shows a readable Delete button', async ({ page }) => {
  await page.goto('/#lab');
  const del = page.getByTestId('run-card').filter({ hasText: 'Demo' }).getByRole('button', { name: 'Delete' });
  await expect(del).toBeVisible();
  // the label must not be painted in the button's own background colour
  const [color, background] = await del.evaluate((b) => [getComputedStyle(b).color, getComputedStyle(b).backgroundColor]);
  expect(color).not.toBe(background);
});

test('the selected piece stands out on a dark square as well as a light one', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'New game' }).click();
  const dialog = page.getByRole('dialog', { name: 'New game' });
  await dialog.getByRole('button', { name: 'A friend', exact: true }).click();
  await dialog.getByRole('button', { name: 'Start game' }).click();
  // g1 is a dark square: the selection used to be a green as dark as the square itself
  const g1 = page.locator('[data-square="g1"]').first();
  const tint = () => g1.evaluate((el) => (el.firstElementChild?.nextElementSibling as HTMLElement | null)?.style.backgroundColor ?? '');
  await g1.click();
  await expect.poll(tint).toContain('240, 165, 40');
});

test('the hall of fame keeps its Play and PGN links inside the table row', async ({ page }) => {
  await page.goto('/#lab/fame');
  const actions = page.locator('.fame-actions').first();
  await expect(actions).toBeVisible();
  expect(await actions.evaluate((el) => getComputedStyle(el.parentElement!).display)).toBe('table-cell');
});
