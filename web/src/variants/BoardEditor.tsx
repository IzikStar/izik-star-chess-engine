import { useEffect, useLayoutEffect, useMemo, useRef, useState, type KeyboardEvent as ReactKeyboardEvent, type PointerEvent as ReactPointerEvent } from 'react';
import { PieceSvg } from '../pieces';
import type { Reach } from '../reach';
import './editor.css';

// The start-position editor of the variant designer: a big board and a palette beside it. Click a
// square to choose its piece from a small palette there; or pick a piece in the palette and click
// squares to stamp it; drag pieces about (onto a piece swaps them), from the palette onto the
// board, and off the board to take them away. The keyboard does all of it too.

/** A piece the variant has: its FEN letter (upper case) and name. */
export interface EditorPiece {
  letter: string;
  name: string;
}

/** square ("e4") -> FEN letter ("N" white, "n" black), the shape {@link placementOf} gives. */
export type Placement = Record<string, string>;

export interface BoardEditorProps {
  board: Placement;
  /** The variant's pieces; their pictures come from the PieceArt provider around the editor. */
  pieces: EditorPiece[];
  /** A new placement; without it the board is only shown (a built-in variant). */
  onChange?: (board: Placement) => void;
  /** Squares to mark (where the piece under the mouse goes). */
  marks?: Map<string, Reach>;
  /** The square under the mouse, or null when it leaves the board. */
  onHover?: (square: string | null) => void;
  label?: string;
  testId?: string;
  /** Board size; 8x8 unless given. */
  files?: number;
  ranks?: number;
}

const FILE_NAMES = 'abcdefghijklmnop';
const STANDARD = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR';
/** How far the pointer moves before a press becomes a drag, in CSS pixels. */
const DRAG_START = 5;

const isWhite = (c: string) => c === c.toUpperCase();
const codeOf = (c: string) => (isWhite(c) ? 'w' : 'b') + c.toUpperCase();
const sideName = (c: string) => (isWhite(c) ? 'white' : 'black');

function sameBoard(a: Placement, b: Placement): boolean {
  const ka = Object.keys(a);
  return ka.length === Object.keys(b).length && ka.every((k) => a[k] === b[k]);
}

/** What is being dragged: a piece from a square, or a new piece from the palette. */
interface Drag {
  from: string | null;
  piece: string;
  x0: number;
  y0: number;
  x: number;
  y: number;
  active: boolean;
}

export function BoardEditor({ board, pieces, onChange, marks, onHover, label = 'Board', testId, files = 8, ranks = 8 }: BoardEditorProps) {
  const editable = !!onChange;
  const boardEl = useRef<HTMLDivElement>(null);
  const popEl = useRef<HTMLDivElement>(null);
  /** The palette piece being stamped onto squares ('' = the eraser), or null. */
  const [stamp, setStamp] = useState<string | null>(null);
  /** The square whose little palette is open. */
  const [popover, setPopover] = useState<string | null>(null);
  const [focus, setFocus] = useState('e2');
  const [hover, setHover] = useState<string | null>(null);
  const [flipped, setFlipped] = useState(false);
  const [past, setPast] = useState<Placement[]>([]);
  const [future, setFuture] = useState<Placement[]>([]);
  const [drag, setDrag] = useState<Drag | null>(null);
  /** The square size while a drag runs, for the piece under the pointer. */
  const [ghostSize, setGhostSize] = useState(60);
  /** A drag just ended: the click that follows it is not a tap. */
  const dragged = useRef(false);
  const latest = useRef({ board, onChange });
  latest.current = { board, onChange };

  const letters = useMemo(() => pieces.map((p) => p.letter.toUpperCase()), [pieces]);
  const nameOf = (c: string) => pieces.find((p) => p.letter.toUpperCase() === c.toUpperCase())?.name || c.toUpperCase();
  const hasStandard = files === 8 && ranks === 8 && [...'PNBRQK'].every((l) => letters.includes(l));

  const commit = (next: Placement) => {
    const { board: now, onChange: change } = latest.current;
    if (!change || sameBoard(now, next)) return;
    setPast((p) => [...p.slice(-99), now]);
    setFuture([]);
    change(next);
  };
  const put = (sq: string, c: string) => {
    const next = { ...latest.current.board };
    if (c) next[sq] = c;
    else delete next[sq];
    commit(next);
  };
  const undo = () => {
    if (!past.length || !onChange) return;
    setFuture((f) => [board, ...f]);
    setPast((p) => p.slice(0, -1));
    onChange(past[past.length - 1]);
  };
  const redo = () => {
    if (!future.length || !onChange) return;
    setPast((p) => [...p, board]);
    setFuture((f) => f.slice(1));
    onChange(future[0]);
  };

  // the squares as drawn, top row first
  const rows: string[][] = [];
  for (let r = 0; r < ranks; r++) {
    const rank = flipped ? r + 1 : ranks - r;
    const row: string[] = [];
    for (let f = 0; f < files; f++) row.push(FILE_NAMES[flipped ? files - 1 - f : f] + rank);
    rows.push(row);
  }
  const at = (sq: string) => {
    const file = FILE_NAMES.indexOf(sq[0]);
    const rank = Number(sq.slice(1));
    return { col: flipped ? files - 1 - file : file, row: flipped ? rank - 1 : ranks - rank };
  };

  // Esc ends stamping, wherever the focus is; a press outside closes the little palette
  useEffect(() => {
    if (stamp === null && popover === null) return;
    const key = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return;
      if (popover !== null) {
        setPopover(null);
        boardEl.current?.focus();
      } else setStamp(null);
      e.preventDefault();
    };
    const down = (e: PointerEvent) => {
      const t = e.target as Node;
      if (popover !== null && !popEl.current?.contains(t) && !boardEl.current?.contains(t)) setPopover(null);
    };
    window.addEventListener('keydown', key);
    window.addEventListener('pointerdown', down);
    return () => {
      window.removeEventListener('keydown', key);
      window.removeEventListener('pointerdown', down);
    };
  }, [stamp, popover]);

  // the little palette sits under the square (over it near the bottom), inside the board's width
  useLayoutEffect(() => {
    const pop = popEl.current;
    const cell = popover ? boardEl.current?.querySelector<HTMLElement>(`[data-square="${popover}"]`) : null;
    if (!pop || !cell || !boardEl.current) return;
    const b = boardEl.current.getBoundingClientRect();
    const c = cell.getBoundingClientRect();
    const p = pop.getBoundingClientRect();
    const left = Math.min(Math.max(0, c.left - b.left + c.width / 2 - p.width / 2), Math.max(0, b.width - p.width));
    const below = c.bottom - b.top + p.height <= b.height || c.top - b.top < b.height / 2;
    pop.style.left = `${left}px`;
    pop.style.top = `${below ? c.bottom - b.top + 4 : c.top - b.top - p.height - 4}px`;
  }, [popover, flipped]);

  // the little palette takes the focus when it opens
  useEffect(() => {
    if (popover) popEl.current?.querySelector<HTMLButtonElement>('button')?.focus();
  }, [popover]);

  /** A click (or Enter) on a square: stamp the picked piece, or open the square's little palette. */
  const tap = (sq: string) => {
    if (!editable) return;
    setFocus(sq);
    if (stamp !== null) {
      put(sq, stamp);
      return;
    }
    setPopover((p) => (p === sq ? null : sq));
  };

  const squareAt = (x: number, y: number): string | null => {
    const el = document.elementFromPoint(x, y)?.closest<HTMLElement>('[data-square]');
    return el && boardEl.current?.contains(el) ? el.dataset.square ?? null : null;
  };

  const startDrag = (e: ReactPointerEvent, from: string | null, piece: string) => {
    if (!editable || e.button !== 0 || !piece) return;
    const d: Drag = { from, piece, x0: e.clientX, y0: e.clientY, x: e.clientX, y: e.clientY, active: false };
    const id = e.pointerId;
    const move = (ev: PointerEvent) => {
      if (ev.pointerId !== id) return;
      if (!d.active && Math.hypot(ev.clientX - d.x0, ev.clientY - d.y0) < DRAG_START) return;
      if (!d.active) {
        d.active = true;
        dragged.current = true;
        setPopover(null);
        setGhostSize((boardEl.current?.getBoundingClientRect().width ?? 480) / files);
      }
      d.x = ev.clientX;
      d.y = ev.clientY;
      setDrag({ ...d });
    };
    const end = (ev: PointerEvent) => {
      if (ev.pointerId !== id) return;
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', end);
      window.removeEventListener('pointercancel', end);
      setDrag(null);
      if (!d.active) return;
      setTimeout(() => { dragged.current = false; }, 0);
      const to = ev.type === 'pointercancel' ? from : squareAt(ev.clientX, ev.clientY);
      const next = { ...latest.current.board };
      if (from === null) {
        if (!to) return;
        next[to] = piece;
      } else if (to === null) {
        delete next[from];
      } else if (to !== from) {
        const there = next[to];
        next[to] = piece;
        if (there) next[from] = there;
        else delete next[from];
      }
      if (to) setFocus(to);
      commit(next);
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', end);
    window.addEventListener('pointercancel', end);
  };

  const onKey = (e: ReactKeyboardEvent) => {
    if (!editable || e.target !== boardEl.current) return;
    const { col, row } = at(focus);
    const move = (dc: number, dr: number) => {
      const c = Math.min(files - 1, Math.max(0, col + dc));
      const r = Math.min(ranks - 1, Math.max(0, row + dr));
      setFocus(rows[r][c]);
    };
    const mod = e.ctrlKey || e.metaKey;
    if (mod && e.key.toLowerCase() === 'z') e.shiftKey ? redo() : undo();
    else if (mod && e.key.toLowerCase() === 'y') redo();
    else if (mod || e.altKey) return;
    else if (e.key === 'ArrowUp') move(0, -1);
    else if (e.key === 'ArrowDown') move(0, 1);
    else if (e.key === 'ArrowLeft') move(-1, 0);
    else if (e.key === 'ArrowRight') move(1, 0);
    else if (e.key === 'Enter' || e.key === ' ') tap(focus);
    else if (e.key === 'Delete' || e.key === 'Backspace') put(focus, '');
    else if (/^[a-zA-Z]$/.test(e.key) && letters.includes(e.key.toUpperCase())) put(focus, e.key);
    else return;
    e.preventDefault();
  };

  const mirror = () => {
    const next: Placement = {};
    for (const [sq, c] of Object.entries(board)) if (isWhite(c)) next[sq] = c;
    for (const [sq, c] of Object.entries(board)) {
      if (!isWhite(c)) continue;
      const to = sq[0] + (ranks + 1 - Number(sq.slice(1)));
      if (!next[to]) next[to] = c.toLowerCase();
    }
    commit(next);
  };
  const standard = () => {
    const next: Placement = {};
    STANDARD.split('/').forEach((row, i) => {
      let f = 0;
      for (const c of row) {
        if (/\d/.test(c)) f += Number(c);
        else next[FILE_NAMES[f++] + (8 - i)] = c;
      }
    });
    commit(next);
  };

  const palette = pieces.flatMap((p) => [p.letter.toUpperCase(), p.letter.toLowerCase()]);
  const shown = hover ?? (editable ? focus : null);

  return (
    <div className={'be' + (editable ? ' editable' : '')}>
      <div className="be-main">
        {editable && (
          <div className="be-tools" role="toolbar" aria-label="Board tools">
            <button type="button" className="btn small-btn" onClick={() => commit({})}>Clear board</button>
            {hasStandard && <button type="button" className="btn small-btn" onClick={standard}>Standard start</button>}
            <button type="button" className="btn small-btn" onClick={mirror} title="Copy White's pieces to Black's side, mirrored">Mirror White to Black</button>
            <button type="button" className="btn small-btn" onClick={() => setFlipped((f) => !f)} aria-pressed={flipped}>Flip</button>
            <button type="button" className="btn small-btn" onClick={undo} disabled={!past.length} title="Undo (Ctrl+Z)">Undo</button>
            <button type="button" className="btn small-btn" onClick={redo} disabled={!future.length} title="Redo (Ctrl+Y)">Redo</button>
          </div>
        )}
        <div className="be-board-wrap">
          <div ref={boardEl} className={'be-board' + (stamp !== null ? ' stamping' : '')} role="grid" aria-label={label} data-testid={testId}
            tabIndex={editable ? 0 : -1} onKeyDown={onKey}
            aria-activedescendant={editable ? `${testId ?? 'be'}-${focus}` : undefined}
            style={{ gridTemplateColumns: `repeat(${files}, 1fr)`, gridTemplateRows: `repeat(${ranks}, 1fr)` }}
            onPointerLeave={() => { setHover(null); onHover?.(null); }}>
            {rows.map((row, r) => (
              <div role="row" key={r} className="be-row">
                {row.map((sq, c) => {
                  const p = board[sq];
                  const mark = marks?.get(sq);
                  const file = FILE_NAMES.indexOf(sq[0]);
                  const rank = Number(sq.slice(1));
                  return (
                    <div role="gridcell" key={sq} id={`${testId ?? 'be'}-${sq}`} data-square={sq}
                      aria-label={sq + (p ? ` ${sideName(p)} ${nameOf(p)}` : '') + (mark ? ' ' + mark : '')}
                      className={'be-sq ' + ((file + rank) % 2 === 1 ? 'dark' : 'light') + (mark ? ' r-' + mark : '')
                        + (editable && sq === focus ? ' focus' : '') + (popover === sq ? ' open' : '')
                        + (drag?.active && drag.from === sq ? ' lifted' : '')}
                      onPointerEnter={() => { setHover(sq); onHover?.(sq); }}
                      onPointerDown={p ? (e) => startDrag(e, sq, p) : undefined}
                      onClick={editable ? () => { if (!dragged.current) { tap(sq); boardEl.current?.focus({ preventScroll: true }); } } : undefined}
                      onContextMenu={editable ? (e) => { e.preventDefault(); setFocus(sq); put(sq, ''); } : undefined}>
                      {p && <PieceSvg code={codeOf(p)} />}
                      {c === 0 && <span className="be-coord rank">{rank}</span>}
                      {r === ranks - 1 && <span className="be-coord file">{sq[0]}</span>}
                    </div>
                  );
                })}
              </div>
            ))}
          </div>
          {popover && (
            <div ref={popEl} className="be-pop" role="dialog" aria-label={`Piece for ${popover}`}>
              <div className="be-pop-grid" style={{ gridTemplateColumns: `repeat(${Math.min(8, pieces.length)}, 40px)` }}>
                {[...palette.filter(isWhite), ...palette.filter((c) => !isWhite(c))].map((c) => (
                  <button type="button" key={c} className={'pal' + (board[popover] === c ? ' on' : '')} title={`${sideName(c)} ${nameOf(c)}`}
                    aria-label={`${sideName(c)} ${nameOf(c)}`} onClick={() => { put(popover, c); setPopover(null); boardEl.current?.focus(); }}>
                    <PieceSvg code={codeOf(c)} />
                  </button>
                ))}
              </div>
              <button type="button" className="btn small-btn be-pop-empty" onClick={() => { put(popover, ''); setPopover(null); boardEl.current?.focus(); }}>Empty square</button>
            </div>
          )}
        </div>
        <p className="be-status muted small" aria-live="polite">
          {shown ? <><b>{shown}</b>{board[shown] ? ` · ${sideName(board[shown])} ${nameOf(board[shown])}` : ''}</> : ' '}
          {stamp !== null && <span> · Placing {stamp ? `${sideName(stamp)} ${nameOf(stamp)}` : 'empty squares'} (Esc stops)</span>}
        </p>
      </div>
      {editable && (
        <div className="be-palette" role="group" aria-label="Piece to place">
          {(['white', 'black'] as const).map((side) => (
            <div key={side} className="be-pal-group">
              <span className="be-pal-head">{side === 'white' ? 'White' : 'Black'}</span>
              <div className="be-pal-grid">
                {palette.filter((c) => (side === 'white') === isWhite(c)).map((c) => (
                  <button type="button" key={c} className={'pal' + (stamp === c ? ' on' : '')} aria-pressed={stamp === c}
                    aria-label={`${side} ${c.toUpperCase()}`} title={`${side} ${nameOf(c)} (key ${c})`}
                    onPointerDown={(e) => startDrag(e, null, c)}
                    onClick={() => { if (!dragged.current) setStamp((s) => (s === c ? null : c)); }}>
                    <PieceSvg code={codeOf(c)} />
                  </button>
                ))}
              </div>
            </div>
          ))}
          <button type="button" className={'pal erase' + (stamp === '' ? ' on' : '')} aria-pressed={stamp === ''}
            onClick={() => setStamp((s) => (s === '' ? null : ''))}>Eraser</button>
          <p className="muted small be-help">
            Click a square to choose its piece, or pick one here and click squares to place it (Esc stops).
            Drag pieces to move them, onto a piece to swap; drag off the board or right-click to take one away.
            Keys: arrows, a piece's letter (Shift for White), Delete.
          </p>
        </div>
      )}
      {drag?.active && (
        <div className="be-ghost" aria-hidden style={{ width: ghostSize, height: ghostSize, left: drag.x - ghostSize / 2, top: drag.y - ghostSize / 2 }}>
          <PieceSvg code={codeOf(drag.piece)} />
        </div>
      )}
    </div>
  );
}
