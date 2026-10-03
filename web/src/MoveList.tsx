import { useEffect, useRef, type ReactNode } from 'react';
import { QUALITY, type Quality } from './Analysis';
import type { MoveInfo } from './protocol';

// The move list and the held review buttons: the game screen and a saved game share them.

/** A button that acts once on press and, held down, again and again (like a held arrow key). */
export function RepeatButton({ label, disabled, onStep, children }: { label: string; disabled: boolean; onStep: () => void; children: ReactNode }) {
  const timer = useRef<number | undefined>(undefined);
  const stop = () => {
    window.clearTimeout(timer.current);
    window.clearInterval(timer.current);
    timer.current = undefined;
  };
  useEffect(() => stop, []);
  useEffect(() => {
    if (disabled) stop();
  }, [disabled]);
  return (
    <button type="button" className="icon" aria-label={label} disabled={disabled}
      onPointerDown={(e) => {
        if (e.button !== 0) return;
        stop();
        onStep();
        timer.current = window.setTimeout(() => {
          timer.current = window.setInterval(onStep, 70);
        }, 350);
      }}
      onPointerUp={stop}
      onPointerLeave={stop}
      onPointerCancel={stop}
      onContextMenu={(e) => e.preventDefault()}
      // keyboard (Enter / Space) gives a click with detail 0; mouse and touch were handled on press
      onClick={(e) => {
        if (e.detail === 0) onStep();
      }}>
      {children}
    </button>
  );
}

export function MoveList({ moves, result, ply, onPick, qualities, empty }: {
  moves: MoveInfo[];
  result: string | null;
  ply: number;
  onPick: (ply: number) => void;
  qualities?: Quality[];
  /** What the list says before the first move. */
  empty: string;
}) {
  const list = useRef<HTMLOListElement>(null);
  useEffect(() => {
    list.current?.querySelector('.current')?.scrollIntoView({ block: 'nearest' });
  }, [ply, moves.length]);

  // rows of [white, black]; a game set up with Black to move would start with an empty White cell
  const rows: { number: number; cells: ({ san: string; ply: number } | null)[] }[] = [];
  moves.forEach((m, i) => {
    if (m.color === 'white' || rows.length === 0) rows.push({ number: m.number, cells: [null, null] });
    rows[rows.length - 1].cells[m.color === 'white' ? 0 : 1] = { san: m.san, ply: i + 1 };
  });

  return (
    <section className="moves" aria-label="Moves">
      {moves.length === 0 ? (
        <p className="empty">{empty}</p>
      ) : (
        <ol className="move-list" ref={list} data-testid="move-list">
          {rows.map((row) => (
            <li key={row.number}>
              <span className="num">{row.number}.</span>
              {row.cells.map((c, i) =>
                c ? (
                  <button key={i} type="button" className={'mv' + (c.ply === ply ? ' current' : '')} onClick={() => onPick(c.ply)}>
                    {c.san}
                    {qualities?.[c.ply - 1] && QUALITY[qualities[c.ply - 1]].glyph && (
                      <span className={'glyph q-' + qualities[c.ply - 1]} title={QUALITY[qualities[c.ply - 1]].label}>{QUALITY[qualities[c.ply - 1]].glyph}</span>
                    )}
                  </button>
                ) : (
                  <span key={i} className="mv" />
                ),
              )}
            </li>
          ))}
          {result && <li className="final">{result}</li>}
        </ol>
      )}
    </section>
  );
}
