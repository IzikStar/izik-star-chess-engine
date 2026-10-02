import { useEffect, useRef, useState } from 'react';
import { LEVELS } from './chess';
import type { Champion, Color, Mode } from './protocol';

export interface NewGameChoice {
  mode: Mode;
  color: Color | 'random';
  level: number;
  /** Black's level when watching the engine; level is then White's. */
  blackLevel: number;
  autoFlip: boolean;
  /** Play this evolved champion instead of the usual engine (levels 2-7, the built-in engine's). */
  champion: Champion | null;
}

/** The champion is the built-in engine: level 1 plays random moves and 8-10 hand over to Stockfish. */
export const CHAMPION_LEVELS = { min: 2, max: 7 };

function LevelSlider({ id, label, value, onChange, min = 1, max = 10 }: {
  id: string;
  label: string;
  value: number;
  onChange: (v: number) => void;
  min?: number;
  max?: number;
}) {
  const level = LEVELS[value - 1];
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input id={id} type="range" min={min} max={max} value={value} onChange={(e) => onChange(Number(e.target.value))} />
      <div className="level">
        <span><strong>Level {value}</strong> · {level.name}</span>
        <span className="muted">{level.engine}</span>
      </div>
    </div>
  );
}

function Segmented<T extends string>({ label, value, options, onChange }: {
  label: string;
  value: T;
  options: [T, string][];
  onChange: (v: T) => void;
}) {
  return (
    <fieldset className="field">
      <legend>{label}</legend>
      <div className="seg">
        {options.map(([v, text]) => (
          <button key={v} type="button" aria-pressed={v === value} className={v === value ? 'on' : ''} onClick={() => onChange(v)}>
            {text}
          </button>
        ))}
      </div>
    </fieldset>
  );
}

/** Who plays, which colour, how strong: the one place a game is set up (replaces the Settings tab). */
export function NewGameDialog({ initial, onStart, onCancel }: {
  initial: NewGameChoice;
  onStart: (choice: NewGameChoice) => void;
  onCancel: () => void;
}) {
  const [choice, setChoice] = useState<NewGameChoice>(() => initial.champion
    // level 1 (random moves) would hide the champion's weights: start it at Casual
    ? { ...initial, mode: 'engine', level: initial.level <= 1 ? 4 : Math.min(CHAMPION_LEVELS.max, initial.level) }
    : initial);
  const dialog = useRef<HTMLDialogElement>(null);
  const set = (patch: Partial<NewGameChoice>) => setChoice({ ...choice, ...patch });

  useEffect(() => {
    const d = dialog.current;
    if (d && !d.open) d.showModal();
  }, []);

  return (
    <dialog ref={dialog} className="dialog" aria-labelledby="new-game-title" onCancel={(e) => {
      e.preventDefault();
      onCancel();
    }}>
      <form method="dialog" onSubmit={(e) => {
        e.preventDefault();
        onStart(choice);
      }}>
        <h2 id="new-game-title">New game</h2>
        <Segmented<Mode>
          label="Opponent"
          value={choice.mode}
          options={[['engine', 'Computer'], ['friend', 'A friend'], ['computer', 'Watch the engine']]}
          onChange={(mode) => set({ mode })}
        />
        {choice.mode === 'engine' && (
          <Segmented<Color | 'random'>
            label="Play as"
            value={choice.color}
            options={[['white', 'White'], ['random', 'Random'], ['black', 'Black']]}
            onChange={(color) => set({ color })}
          />
        )}
        {choice.mode === 'engine' && choice.champion && (
          <div className="champion-pick" data-testid="champion-pick">
            <span><strong>{choice.champion.label}</strong><br /><span className="muted">An evolved set of weights from the lab</span></span>
            <button type="button" className="btn" onClick={() => set({ champion: null })}>Usual engine</button>
          </div>
        )}
        {choice.mode === 'engine' && (
          <LevelSlider id="level" label="Strength" value={choice.level}
            onChange={(level) => set({ level })}
            min={choice.champion ? CHAMPION_LEVELS.min : 1} max={choice.champion ? CHAMPION_LEVELS.max : 10} />
        )}
        {choice.mode === 'computer' && (
          <>
            <LevelSlider id="level" label="White's strength" value={choice.level} onChange={(level) => set({ level })} />
            <LevelSlider id="blackLevel" label="Black's strength" value={choice.blackLevel} onChange={(blackLevel) => set({ blackLevel })} />
          </>
        )}
        {choice.mode === 'friend' && (
          <label className="check">
            <input type="checkbox" checked={choice.autoFlip} onChange={(e) => set({ autoFlip: e.target.checked })} />
            Turn the board to the side to move
          </label>
        )}
        <div className="row end">
          <button type="button" className="btn" onClick={onCancel}>Cancel</button>
          <button type="submit" className="btn primary">Start game</button>
        </div>
      </form>
    </dialog>
  );
}
