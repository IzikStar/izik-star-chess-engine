import { expect, type Page } from '@playwright/test';

/** The board square called {@code name} ("e4"). */
export const square = (page: Page, name: string) => page.locator(`[data-square="${name}"]`).first();

/** Plays a move by clicking its two squares. */
export async function clickMove(page: Page, from: string, to: string) {
  await square(page, from).click();
  await square(page, to).click();
}

/** Sends one command over a second game socket, as another tab would. */
export async function sendCommand(page: Page, command: object) {
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

/** Opens one of a variant's screens and waits for it to be the current one. */
export async function tab(page: Page, name: 'Overview' | 'Board' | 'Pieces' | 'Health') {
  const link = page.getByRole('navigation', { name: 'Variant screens' }).getByRole('link', { name });
  await link.click();
  await expect(link).toHaveAttribute('aria-current', 'page');
}
