import { useEffect, useRef, useState } from 'react';
import { eloText, engineText, LEVELS, MAX_LEVEL, MIN_LEVEL, STOCKFISH_FROM_LEVEL, TIME_CONTROLS, TOP_BUILT_IN_LEVEL, VARIANTS, goalsRule } from './chess';
import type { Champion, Color, Mode, StockfishInfo, VariantId, Weights } from './protocol';
import { StockfishInstall } from './StockfishInstall';
import type { VariantRow } from './variants/model';

export interface NewGameChoice {
  mode: Mode;
  color: Color | 'random';
  level: number;
  /** Black's level when watching the engine; level is then White's. */
  blackLevel: number;
  autoFlip: boolean;
  /** Play this evolved champion instead of the usual engine (the built-in engine's levels). */
  champion: Champion | null;
  /** The built-in engine's weights (Levels 1-8; Stockfish's levels are not affected). */
  weights: Weights;
  /** A key of TIME_CONTROLS ("3+2"), or "none" for an untimed game. */
  time: string;
  /** Which game: chess or a variant. */
  variant: VariantId;
}


/** Stockfish plays chess only: in a variant the ladder stops at the built-in engine's top level. */
export function maxLevelFor(variant: VariantId): number {
  return variant === 'chess' ? MAX_LEVEL : TOP_BUILT_IN_LEVEL;
}

/**
 * The chess weights (and a champion's) play chess, King of the Hill and three-check. Antichess and
 * the player's own variants play with an evaluation built from their pieces.
 */
export function usesChessWeights(variant: VariantId): boolean {
  return variant === 'chess' || variant === 'king-of-the-hill' || variant === 'three-check';
}

const WEIGHTS_NOTE: Record<Weights, string> = {
  tuned: 'Fitted to thousands of Stockfish games; 100-250 Elo stronger than classic at the same level.',
  classic: 'The original hand-written weights.',
};

/** The champion is the built-in engine: Levels 0-2 are (partly) random moves and 9 up hand over to Stockfish. */
export const CHAMPION_LEVELS = { min: 3, max: TOP_BUILT_IN_LEVEL };

function LevelSlider({ id, label, value, onChange, stockfish, weights, min = MIN_LEVEL, max = MAX_LEVEL }: {
  id: string;
  label: string;
  value: number;
  onChange: (v: number) => void;
  stockfish: StockfishInfo | undefined;
  /** Whose Elo to show; a champion's, or any level's outside chess, is unknown. */
  weights: Weights | null;
  min?: number;
  max?: number;
}) {
  const level = LEVELS[value];
  const elo = weights ? eloText(value, weights) : '';
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input id={id} type="range" min={min} max={max} value={value} onChange={(e) => onChange(Number(e.target.value))} />
      <div className="level">
        <span><strong>Level {value}</strong> · {level.name}{elo && <span className="muted" data-testid="level-elo"> · {elo}</span>}</span>
        <span className="muted">{engineText(value, stockfish)}</span>
      </div>
      {value >= STOCKFISH_FROM_LEVEL && stockfish && !stockfish.available && (
        <div data-testid="stockfish-missing">
          <StockfishInstall why={`Stockfish is not installed, so this level plays the built-in engine at Level ${TOP_BUILT_IN_LEVEL}.`} />
        </div>
      )}
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
export function NewGameDialog({ initial, stockfish, onStart, onCancel }: {
  initial: NewGameChoice;
  stockfish: StockfishInfo | undefined;
  onStart: (choice: NewGameChoice) => void;
  onCancel: () => void;
}) {
  const [choice, setChoice] = useState<NewGameChoice>(() => initial.champion
    // Levels 0-2 (random moves) would hide the champion's weights: start it at Improving
    ? { ...initial, mode: 'engine', level: initial.level < CHAMPION_LEVELS.min ? 5 : Math.min(CHAMPION_LEVELS.max, initial.level) }
    : initial);
  const dialog = useRef<HTMLDialogElement>(null);
  const set = (patch: Partial<NewGameChoice>) => setChoice({ ...choice, ...patch });
  const maxLevel = maxLevelFor(choice.variant);
  const pickVariant = (variant: VariantId) => {
    const top = maxLevelFor(variant);
    set({
      variant,
      level: Math.min(choice.level, top),
      blackLevel: Math.min(choice.blackLevel, top),
      champion: choice.champion && (choice.champion.variant ?? 'chess') === variant ? choice.champion : null,
    });
  };

  /** The player's own variants, from the server (none until it answers). */
  const [made, setMade] = useState<VariantRow[]>([]);
  useEffect(() => {
    fetch('/api/variants').then((r) => r.json()).then((r: { variants: VariantRow[] }) => setMade(r.variants.filter((v) => !v.builtIn)))
      .catch(() => {});
  }, []);
  const madeVariant = made.find((v) => v.id === choice.variant);
  const builtIn = VARIANTS.find((v) => v.id === choice.variant);

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
        <Segmented<VariantId>
          label="Game"
          value={builtIn ? choice.variant : ''}
          options={VARIANTS.map((v): [VariantId, string] => [v.id, v.name])}
          onChange={pickVariant}
        />
        {(made.length > 0 || !builtIn) && (
          <div className="field">
            <label htmlFor="made-variant">My variants</label>
            <select id="made-variant" value={builtIn ? '' : choice.variant} onChange={(e) => pickVariant(e.target.value || 'chess')}>
              <option value="">None</option>
              {made.map((v) => <option key={v.id} value={v.id}>{v.name}</option>)}
              {!builtIn && !madeVariant && <option value={choice.variant}>{choice.variant}</option>}
            </select>
          </div>
        )}
        <p className="muted field-note" data-testid="variant-rule">
          {builtIn ? builtIn.rule : madeVariant ? goalsRule(madeVariant.goals) : ''}
          {choice.variant !== 'chess' && choice.mode !== 'friend' && ` Stockfish plays chess only, so the strongest opponent here is Level ${TOP_BUILT_IN_LEVEL}.`}
        </p>
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
            <span><strong>{choice.champion.label}</strong><br /><span className="muted">An evolved set of weights from the lab{choice.champion.variant && choice.champion.variant !== 'chess' ? `, for ${madeVariant?.name ?? VARIANTS.find((v) => v.id === choice.champion!.variant)?.name ?? choice.champion.variant}` : ''}</span></span>
            <button type="button" className="btn" onClick={() => set({ champion: null })}>Usual engine</button>
          </div>
        )}
        {choice.mode === 'engine' && (
          <LevelSlider id="level" label="Strength" value={choice.level} weights={choice.champion || choice.variant !== 'chess' ? null : choice.weights}
            onChange={(level) => set({ level })} stockfish={stockfish}
            min={choice.champion ? CHAMPION_LEVELS.min : MIN_LEVEL} max={choice.champion ? CHAMPION_LEVELS.max : maxLevel} />
        )}
        {choice.mode === 'computer' && (
          <>
            <LevelSlider id="level" label="White's strength" value={choice.level} weights={choice.variant === 'chess' ? choice.weights : null}
              onChange={(level) => set({ level })} stockfish={stockfish} max={maxLevel} />
            <LevelSlider id="blackLevel" label="Black's strength" value={choice.blackLevel} weights={choice.variant === 'chess' ? choice.weights : null}
              onChange={(blackLevel) => set({ blackLevel })} stockfish={stockfish} max={maxLevel} />
          </>
        )}
        {usesChessWeights(choice.variant) && (choice.mode === 'computer' || (choice.mode === 'engine' && !choice.champion)) && (
          <>
            <Segmented<Weights>
              label="Engine weights"
              value={choice.weights}
              options={[['tuned', 'Tuned'], ['classic', 'Classic']]}
              onChange={(weights) => set({ weights })}
            />
            <p className="muted field-note" data-testid="weights-note">{WEIGHTS_NOTE[choice.weights]}</p>
          </>
        )}
        {choice.mode === 'friend' && (
          <label className="check">
            <input type="checkbox" checked={choice.autoFlip} onChange={(e) => set({ autoFlip: e.target.checked })} />
            Turn the board to the side to move
          </label>
        )}
        <Segmented<string>
          label="Time control"
          value={choice.time}
          options={[['none', 'Untimed'], ...TIME_CONTROLS.map((t): [string, string] => [t.key, t.key])]}
          onChange={(time) => set({ time })}
        />
        <div className="row end">
          <button type="button" className="btn" onClick={onCancel}>Cancel</button>
          <button type="submit" className="btn primary">Start game</button>
        </div>
      </form>
    </dialog>
  );
}
