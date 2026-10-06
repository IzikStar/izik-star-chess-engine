import { useEffect, useRef, useState } from 'react';
import { eloText, LEVELS, MAX_LEVEL, MIN_LEVEL } from './chess';
import type { Mode, StockfishInfo, Weights } from './protocol';
import { DEFAULT_SETTINGS, type Settings } from './settings';

const GAME_KINDS: [Mode, string][] = [
  ['engine', 'Against the computer'],
  ['computer', 'Watching the engine'],
  ['friend', 'Against a friend'],
];

/** Hints and the evaluation bar: how much help the board gives. Changes apply at once. */
export function SettingsDialog({ settings, weights, stockfish, onChange, onClose, onTour }: {
  settings: Settings;
  /** Whose Elo the hint levels show. */
  weights: Weights;
  stockfish: StockfishInfo | undefined;
  onChange: (settings: Settings) => void;
  onClose: () => void;
  /** Walks the screens again (Tour.tsx). */
  onTour: () => void;
}) {
  const [s, setS] = useState(settings);
  const dialog = useRef<HTMLDialogElement>(null);
  const set = (patch: Partial<Settings>) => {
    const next = { ...s, ...patch };
    setS(next);
    onChange(next);
  };

  useEffect(() => {
    const d = dialog.current;
    if (d && !d.open) d.showModal();
  }, []);

  // a level the ladder no longer has falls back to the strongest
  const hintLevel = s.hintLevel !== null && s.hintLevel <= MAX_LEVEL ? s.hintLevel : null;

  return (
    <dialog ref={dialog} className="dialog" aria-labelledby="settings-title" onCancel={(e) => {
      e.preventDefault();
      onClose();
    }}>
      <form method="dialog" onSubmit={(e) => {
        e.preventDefault();
        onClose();
      }}>
        <h2 id="settings-title">Settings</h2>

        <fieldset className="field">
          <legend>Hints</legend>
          <label className="check">
            <input type="checkbox" checked={s.hints} onChange={(e) => set({ hints: e.target.checked })} />
            Offer hints (the Hint button)
          </label>
          <label htmlFor="hint-level" className="sub-label">Hint strength</label>
          <select id="hint-level" disabled={!s.hints} value={hintLevel ?? 'best'}
            onChange={(e) => set({ hintLevel: e.target.value === 'best' ? null : Number(e.target.value) })}>
            <option value="best">Strongest{stockfish?.available === false ? ' (built-in engine, no Stockfish)' : ' (Stockfish, 4 s)'}</option>
            {LEVELS.map((level, i) => i === MIN_LEVEL ? null : (
              <option key={i} value={i}>Level {i} · {level.name}{eloText(i, weights) ? ` · ${eloText(i, weights)}` : ''}</option>
            ))}
          </select>
          <p className="muted field-note">A hint shows the move this level would play.</p>
        </fieldset>

        <fieldset className="field">
          <legend>Evaluation bar</legend>
          {GAME_KINDS.map(([mode, text]) => (
            <label key={mode} className="check">
              <input type="checkbox" checked={s.evalBar[mode]} onChange={(e) => set({ evalBar: { ...s.evalBar, [mode]: e.target.checked } })} />
              {text}
            </label>
          ))}
          <p className="muted field-note">Stockfish scores the position after every move.{stockfish?.available === false ? ' It is not installed yet, so the bar stays hidden.' : ''}</p>
        </fieldset>

        <div className="row end">
          <button type="button" className="btn ghost" onClick={onTour}>Show the tour</button>
          <button type="button" className="btn" onClick={() => set(DEFAULT_SETTINGS)}>Defaults</button>
          <button type="submit" className="btn primary">Done</button>
        </div>
      </form>
    </dialog>
  );
}
