import { useEffect, useRef, useState } from 'react';
import { LEVELS } from './chess';
import type { Color, Mode } from './protocol';

export interface NewGameChoice {
  mode: Mode;
  color: Color | 'random';
  level: number;
  autoFlip: boolean;
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
  const [choice, setChoice] = useState<NewGameChoice>(initial);
  const dialog = useRef<HTMLDialogElement>(null);
  const set = (patch: Partial<NewGameChoice>) => setChoice({ ...choice, ...patch });

  useEffect(() => {
    const d = dialog.current;
    if (d && !d.open) d.showModal();
  }, []);

  const level = LEVELS[choice.level - 1];
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
        {choice.mode !== 'friend' ? (
          <div className="field">
            <label htmlFor="level">Strength</label>
            <input id="level" type="range" min={1} max={10} value={choice.level} onChange={(e) => set({ level: Number(e.target.value) })} />
            <div className="level">
              <span><strong>Level {choice.level}</strong> · {level.name}</span>
              <span className="muted">{level.engine}</span>
            </div>
          </div>
        ) : (
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
