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

  const [sync, setSync] = useState<SyncStatus | null>(null);
  useEffect(() => {
    fetch('/api/sync').then((r) => r.ok ? r.json() : null).then(setSync).catch(() => setSync(null));
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

        {sync?.enabled && (
          <fieldset className="field">
            <legend>Shared database</legend>
            <p className="field-note" data-testid="sync-status">{syncText(sync)}</p>
            {sync.warnings.map((w) => <p key={w} className="muted field-note">{w}</p>)}
          </fieldset>
        )}

        <div className="row end">
          <button type="button" className="btn ghost" onClick={onTour}>Show the tour</button>
          <button type="button" className="btn" onClick={() => set(DEFAULT_SETTINGS)}>Defaults</button>
          <button type="submit" className="btn primary">Done</button>
        </div>
      </form>
    </dialog>
  );
}

/** What /api/sync answers (cloud.CloudSync.status). */
export type SyncStatus = {
  enabled: boolean;
  copy: string;
  lastSync: string | null;
  error: string | null;
  retryAt: string | null;
  waitingFiles: number;
  waitingGenerations: number;
  warnings: string[];
};

const clock = (iso: string) => new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

/** One line: this copy's name, when it last synced, what still waits and why. */
export function syncText(s: SyncStatus): string {
  const parts = [`This copy: ${s.copy}.`];
  parts.push(s.lastSync ? `Last synced at ${clock(s.lastSync)}.` : 'Not synced yet.');
  const waiting = [
    s.waitingFiles ? `${s.waitingFiles} file${s.waitingFiles === 1 ? '' : 's'}` : '',
    s.waitingGenerations ? `${s.waitingGenerations} generation${s.waitingGenerations === 1 ? '' : 's'}` : '',
  ].filter(Boolean).join(' and ');
  if (waiting) parts.push(`Waiting to be sent: ${waiting}.`);
  if (s.error) parts.push(`${s.error}${s.retryAt ? `; trying again at ${clock(s.retryAt)}` : ''}.`);
  return parts.join(' ');
}
