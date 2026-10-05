import type { Mode } from './protocol';

/** The player's preferences, kept in the browser (localStorage 'settings'). */
export interface Settings {
  /** Whether the Hint button is offered at all. */
  hints: boolean;
  /** The level a hint plays as (LEVELS), or null for the strongest the app has. */
  hintLevel: number | null;
  /** Whether the evaluation bar beside the board shows, for each kind of game. */
  evalBar: Record<Mode, boolean>;
}

/** Hints on at full strength; the bar in games with the engine, not between two people. */
export const DEFAULT_SETTINGS: Settings = {
  hints: true,
  hintLevel: null,
  evalBar: { engine: true, computer: true, friend: false },
};

export function loadSettings(): Settings {
  try {
    const saved = JSON.parse(localStorage.getItem('settings') ?? '{}');
    return {
      hints: typeof saved.hints === 'boolean' ? saved.hints : DEFAULT_SETTINGS.hints,
      hintLevel: typeof saved.hintLevel === 'number' ? saved.hintLevel : DEFAULT_SETTINGS.hintLevel,
      evalBar: { ...DEFAULT_SETTINGS.evalBar, ...(saved.evalBar ?? {}) },
    };
  } catch {
    return DEFAULT_SETTINGS;
  }
}

export function saveSettings(settings: Settings) {
  try {
    localStorage.setItem('settings', JSON.stringify(settings));
  } catch {
    // private window: the settings just aren't remembered
  }
}
