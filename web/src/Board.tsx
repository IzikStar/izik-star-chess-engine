import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { Chessboard, type Arrow } from 'react-chessboard';
import type { Color } from './protocol';
import { QUALITY, type Quality } from './Analysis';
import { boardOf } from './chess';
import { codeOf, PieceSvg, pieceSet } from './pieces';
import type { Reach } from './reach';

interface Props {
  fen: string;
  orientation: Color;
  /** Legal moves by start square; empty when the board should not accept moves. */
  legal: Map<string, string[]>;
  lastMove: string | null;
  /** Royal pieces in check, marked red. */
  checkSquares: string[];
  hint: string | null;
  onMove: (uci: string) => void;
  onSelect: () => void;
  onIllegal: () => void;
  /**
   * While the engine thinks: the human's colour, so its pieces can be picked up to queue a
   * premove (any target square; it is checked when it is played), several in a row. Null otherwise.
   */
  premoveColor: Color | null;
  /** The queued premoves (from + to [+ piece]), oldest first, shown on the board. */
  premoves: string[];
  /** Queues one more premove, or with null drops them all. */
  onPremove: (uci: string | null) => void;
  /** The analysis mark of the move shown, drawn on its destination square (as chess.com does). */
  badge?: { square: string; quality: Quality } | null;
  /** Where the piece on a square could go, shown while the mouse is over it (invented pieces); null for none. */
  reachOf?: (square: string) => Map<string, Reach> | null;
  /**
   * False while the board is hidden (another tab is open): react-chessboard measures a square to
   * slide a piece, and a hidden board's squares have no size, which it throws on.
   */
  animate?: boolean;
}

/** Each board's own id: react-chessboard finds its squares by id, so two boards must not share one. */
let boards = 0;

const LAST = 'rgba(240, 214, 96, 0.52)';
// warm, so it shows on the green squares as well as the light ones
const SELECTED = 'rgba(240, 165, 40, 0.72)';
const PREMOVE = 'rgba(40, 70, 140, 0.5)';
const DOT = 'radial-gradient(circle, rgba(16, 32, 24, 0.3) 19%, rgba(16, 32, 24, 0.18) 21%, transparent 22.5%)';
const RING = 'radial-gradient(circle, transparent 78%, rgba(16, 32, 24, 0.32) 79.5%)';
/** The square the mouse is over while a piece is chosen and could go there. */
const TARGET = 'inset 0 0 0 3px rgba(255, 255, 255, 0.55)';
/** Motion on the board, kept short: a piece's slide, and the marks that grow in when a piece is chosen. */
const SLIDE_MS = 240;
const GROW_IN = { backgroundRepeat: 'no-repeat', backgroundPosition: 'center', backgroundSize: '100% 100%', animation: 'sq-grow 160ms var(--ease-out)' } as const;
/** A square marked with a right click, drawn over whatever else the square shows. */
const MARK = 'linear-gradient(rgba(235, 97, 80, 0.75), rgba(235, 97, 80, 0.75))';
/** Where the piece under the mouse could go: a violet dot, or a violet ring around a piece it could take. */
const HOVER_DOT = 'radial-gradient(circle, rgba(130, 70, 200, 0.55) 20%, transparent 21%)';
const HOVER_RING = 'radial-gradient(circle, transparent 74%, rgba(130, 70, 200, 0.7) 75%)';
const CHECK = 'radial-gradient(circle, rgba(255, 0, 0, 0.85) 0%, rgba(231, 0, 0, 0.5) 30%, rgba(169, 0, 0, 0) 75%)';

/** A short effect over one square: a piece landing, a piece being taken, a king put in check. */
type Fx = { id: number; kind: 'land' | 'capture' | 'check'; square: string; code?: string };
let fxIds = 0;

/** True while the player asks for less motion (the system setting). */
function useReducedMotion() {
  const query = '(prefers-reduced-motion: reduce)';
  const [reduced, setReduced] = useState(() => typeof matchMedia === 'function' && matchMedia(query).matches);
  useEffect(() => {
    if (typeof matchMedia !== 'function') return;
    const m = matchMedia(query);
    const on = () => setReduced(m.matches);
    m.addEventListener('change', on);
    return () => m.removeEventListener('change', on);
  }, []);
  return reduced;
}

/** Where a square sits on the board as it is turned, in eighths from the top left. */
function cell(square: string, orientation: Color) {
  const file = square.charCodeAt(0) - 97;
  const rank = Number(square.slice(1)) - 1;
  return { col: orientation === 'white' ? file : 7 - file, row: orientation === 'white' ? 7 - rank : rank };
}

/**
 * What the last move did, if it was just played (not stepped back to): the square it landed on
 * and the piece it took, with the square that piece stood on (en passant takes beside the target).
 */
function moveEffects(before: Record<string, string>, after: Record<string, string>, move: string): Omit<Fx, 'id'>[] {
  const from = move.slice(0, 2);
  const to = move.slice(2, 4);
  const mover = before[from];
  if (!mover || !after[to] || after[from] === mover && before[to] === after[to]) return [];
  const white = (p: string) => p === p.toUpperCase();
  const fx: Omit<Fx, 'id'>[] = [{ kind: 'land', square: to }];
  let taken = before[to] && white(before[to]) !== white(mover) ? to : null;
  if (!taken && mover.toLowerCase() === 'p' && from[0] !== to[0]) {
    const beside = to[0] + from.slice(1);
    if (before[beside] && !after[beside]) taken = beside;
  }
  if (taken) fx.push({ kind: 'capture', square: taken, code: codeOf(before[taken]) });
  return fx;
}

/**
 * The board: react-chessboard with click-to-move and drag-to-move, conventional highlights,
 * the hint as an arrow and a promotion picker over the promotion square. It only offers the
 * moves it is given. For planning: right-click a square to mark it, right-drag to draw an arrow;
 * a left click or the next move clears them.
 */
export function Board({ fen, orientation, legal, lastMove, checkSquares, hint, onMove, onSelect, onIllegal, premoveColor, premoves, onPremove, badge, reachOf, animate = true }: Props) {
  const [id] = useState(() => `board${++boards}`);
  const [hovered, setHovered] = useState<string | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  /** choices: the pieces the pawn may become, as the legal moves name them (a premove offers the usual four). */
  const [promotion, setPromotion] = useState<{ from: string; to: string; color: 'w' | 'b'; premove: boolean; choices: string[] } | null>(null);
  const pieces = useMemo(() => boardOf(fen), [fen]);
  const [marks, setMarks] = useState<string[]>([]);
  const rightPress = useRef<string | null>(null);
  const reduced = useReducedMotion();
  const moving = animate && !reduced;

  // the effects of a move just played, and of a check just given; each clears itself
  const [fx, setFx] = useState<Fx[]>([]);
  const before = useRef(pieces);
  const checked = useRef(checkSquares.join());
  useEffect(() => {
    const prev = before.current;
    before.current = pieces;
    const fresh: Omit<Fx, 'id'>[] = lastMove && prev !== pieces && animate ? moveEffects(prev, pieces, lastMove) : [];
    const nowChecked = checkSquares.join();
    if (nowChecked && nowChecked !== checked.current && animate) for (const square of checkSquares) fresh.push({ kind: 'check', square });
    checked.current = nowChecked;
    if (!fresh.length) return;
    const added = fresh.map((f) => ({ ...f, id: ++fxIds }));
    setFx((all) => [...all, ...added]);
    const ids = new Set(added.map((f) => f.id));
    const timer = window.setTimeout(() => {
      timers.current.delete(timer);
      setFx((all) => all.filter((f) => !ids.has(f.id)));
    }, 900);
    timers.current.add(timer);
  }, [pieces]);
  const timers = useRef(new Set<number>());
  useEffect(() => () => timers.current.forEach((t) => window.clearTimeout(t)), []);

  useEffect(() => setMarks([]), [fen]);

  // a new position (or the end of our turn) drops any selection; a premove's promotion picker
  // stays open, since the engine replying is exactly what a premove waits for
  useEffect(() => {
    setSelected(null);
    setPromotion((p) => (p?.premove ? p : null));
  }, [fen, legal.size === 0]);

  const premoving = legal.size === 0 && premoveColor !== null;
  const isOwn = (square: string | null) => {
    const p = square ? pieces[square] : undefined;
    return !!p && (p === p.toUpperCase()) === (premoveColor === 'white');
  };
  const canPick = (square: string | null) => !!square && (premoving ? isOwn(square) : legal.has(square));

  /** Premoves are not checked, so a pawn reaching the last rank is taken to be a promotion. */
  const premovePromotes = (from: string, to: string) => {
    const p = pieces[from];
    return (p === 'P' && to[1] === '8') || (p === 'p' && to[1] === '1');
  };
  const queuePremove = (from: string, to: string) => {
    setSelected(null);
    if (premovePromotes(from, to)) setPromotion({ from, to, color: pieces[from] === 'P' ? 'w' : 'b', premove: true, choices: USUAL_PROMOTIONS });
    else onPremove(from + to);
  };

  const candidates = (from: string, to: string) => (legal.get(from) ?? []).filter((u) => u.slice(2, 4) === to);

  /** Plays from -> to if legal; asks for the piece first on a promotion. Returns true if played. */
  const tryMove = (from: string, to: string): boolean => {
    const moves = candidates(from, to);
    if (moves.length === 0) return false;
    setSelected(null);
    if (moves.some((u) => u.length === 5)) {
      setPromotion({ from, to, color: pieces[from] === pieces[from]?.toUpperCase() ? 'w' : 'b', premove: false,
        choices: moves.filter((u) => u.length === 5).map((u) => u[4]) });
      return false;
    }
    onMove(moves[0]);
    return true;
  };

  const squareStyles: Record<string, CSSProperties> = {};
  const add = (square: string, style: CSSProperties) => {
    const prev = squareStyles[square] ?? {};
    const images = [style.backgroundImage, prev.backgroundImage].filter(Boolean).join(', ');
    squareStyles[square] = { ...prev, ...style, ...(images ? { backgroundImage: images } : {}) };
  };
  if (lastMove) {
    add(lastMove.slice(0, 2), { backgroundColor: LAST });
    add(lastMove.slice(2, 4), { backgroundColor: LAST });
  }
  for (const sq of checkSquares) add(sq, { backgroundImage: CHECK, animation: 'check-pulse 1.6s var(--ease-in-out) infinite' });
  for (const uci of premoves) {
    add(uci.slice(0, 2), { backgroundColor: PREMOVE });
    add(uci.slice(2, 4), { backgroundColor: PREMOVE });
  }
  for (const square of marks) add(square, { backgroundImage: MARK });
  if (selected) {
    add(selected, { backgroundColor: SELECTED });
    for (const uci of legal.get(selected) ?? []) {
      const to = uci.slice(2, 4);
      add(to, { backgroundImage: pieces[to] ? RING : DOT, cursor: 'pointer', ...GROW_IN, ...(to === hovered ? { boxShadow: TARGET } : {}) });
    }
  }

  const hoverReach = !selected && hovered && reachOf ? reachOf(hovered) : null;
  if (hoverReach) {
    for (const [to, kind] of hoverReach) add(to, { backgroundImage: kind === 'capture' ? HOVER_RING : HOVER_DOT });
  }

  const arrows: Arrow[] = hint ? [{ startSquare: hint.slice(0, 2), endSquare: hint.slice(2, 4), color: 'rgba(31, 132, 104, 0.88)' }] : [];
  const notation: CSSProperties = { fontFamily: 'var(--font)', fontWeight: 600, fontSize: 'clamp(8px, 1.15vmin, 12px)', opacity: 0.9 };

  return (
    <div className="board" data-testid="board" data-hint={hint ?? ''} data-marks={marks.join(' ')}
      data-reach={hoverReach ? [...hoverReach.keys()].sort().join(' ') : ''}>
      <Chessboard
        options={{
          id,
          position: fen,
          pieces: pieceSet,
          boardOrientation: orientation,
          squareStyles,
          arrows,
          allowDrawingArrows: true,
          animationDurationInMs: SLIDE_MS,
          showAnimations: moving,
          lightSquareStyle: { backgroundColor: 'var(--sq-light)' },
          darkSquareStyle: { backgroundColor: 'var(--sq-dark)' },
          lightSquareNotationStyle: { color: 'var(--sq-dark)' },
          darkSquareNotationStyle: { color: 'var(--sq-light)' },
          alphaNotationStyle: { ...notation, position: 'absolute', bottom: '2%', right: '5%', userSelect: 'none' },
          numericNotationStyle: { ...notation, position: 'absolute', top: '3%', left: '5%', userSelect: 'none' },
          dropSquareStyle: { boxShadow: 'inset 0 0 0 4px rgba(255,255,255,0.65)' },
          // a picked-up piece lifts off the board, its shadow under it; the one left behind fades
          draggingPieceStyle: reduced ? {} : { transform: 'scale(1.16)', filter: 'drop-shadow(0 14px 10px rgba(0, 0, 0, 0.32))' },
          draggingPieceGhostStyle: { opacity: 0.28 },
          canDragPiece: ({ square }) => canPick(square),
          onMouseOverSquare: ({ square }) => setHovered(square),
          onMouseOutSquare: ({ square }) => setHovered((h) => (h === square ? null : h)),
          // a right press and release on the same square marks it (a right drag is an arrow). Not
          // onSquareRightClick: on Linux the context menu fires on press, before a drag can start.
          onSquareMouseDown: ({ square }, e) => {
            if (e.button === 0) setMarks([]);
            if (e.button === 2) rightPress.current = square;
          },
          onSquareMouseUp: ({ square }, e) => {
            if (e.button !== 2) return;
            if (rightPress.current === square) {
              setMarks((m) => (m.includes(square) ? m.filter((s) => s !== square) : [...m, square]));
            }
            rightPress.current = null;
          },
          onPieceDrag: ({ square }) => {
            if (canPick(square)) setSelected(square);
          },
          onPieceDrop: ({ sourceSquare, targetSquare }) => {
            if (!targetSquare || targetSquare === sourceSquare) return false;
            if (premoving) {
              queuePremove(sourceSquare, targetSquare);
              // leave the piece where it was dropped (the queued premove shows it there), so it
              // doesn't fly back to its square and jump forward again; a promotion waits for its piece
              return !premovePromotes(sourceSquare, targetSquare);
            }
            if (candidates(sourceSquare, targetSquare).length === 0) {
              onIllegal();
              return false;
            }
            return tryMove(sourceSquare, targetSquare);
          },
          onSquareClick: ({ square }) => {
            if (promotion) {
              setPromotion(null);
            } else if (premoving) {
              if (selected && selected !== square && !isOwn(square)) {
                queuePremove(selected, square);
              } else if (isOwn(square) && square !== selected) {
                setSelected(square);
              } else {
                setSelected(null);
                onPremove(null);
              }
            } else if (selected && selected !== square && candidates(selected, square).length) {
              tryMove(selected, square);
            } else if (legal.has(square) && square !== selected) {
              setSelected(square);
              onSelect();
            } else {
              setSelected(null);
            }
          },
        }}
      />
      <div className="board-fx" aria-hidden="true">
        {fx.map((f) => <Effect key={f.id} fx={f} orientation={orientation} />)}
      </div>
      {badge && <QualityBadge square={badge.square} quality={badge.quality} orientation={orientation} />}
      {promotion && (
        <PromotionPicker
          square={promotion.to}
          orientation={orientation}
          color={promotion.color}
          choices={promotion.choices}
          onPick={(piece) => {
            const uci = promotion.from + promotion.to + piece;
            if (promotion.premove) onPremove(uci);
            else onMove(uci);
            setPromotion(null);
          }}
          onCancel={() => setPromotion(null)}
        />
      )}
    </div>
  );
}

const USUAL_PROMOTIONS = ['q', 'r', 'b', 'n'];
const PIECE_NAMES: Record<string, string> = { q: 'Queen', r: 'Rook', b: 'Bishop', n: 'Knight', k: 'King' };

function PromotionPicker({ square, orientation, color, choices, onPick, onCancel }: {
  square: string;
  orientation: Color;
  color: 'w' | 'b';
  /** Piece letters, lower case, in the order the server lists them. */
  choices: string[];
  onPick: (piece: string) => void;
  onCancel: () => void;
}) {
  const file = square.charCodeAt(0) - 97;
  const col = orientation === 'white' ? file : 7 - file;
  // the picker hangs from the promotion square towards the middle of the board
  const fromTop = (orientation === 'white') === (color === 'w');
  return (
    <div className="promo-backdrop" onClick={onCancel}>
      <div
        className="promo"
        role="dialog"
        aria-label="Promote to"
        style={{ left: `${col * 12.5}%`, [fromTop ? 'top' : 'bottom']: 0, flexDirection: fromTop ? 'column' : 'column-reverse' }}
        onClick={(e) => e.stopPropagation()}
      >
        {choices.map((p) => (
          <button key={p} type="button" className="promo-piece" aria-label={PIECE_NAMES[p] ?? p.toUpperCase()} onClick={() => onPick(p)}>
            <PieceSvg code={color + p.toUpperCase()} />
          </button>
        ))}
      </div>
    </div>
  );
}

/** One effect, over its square: a ring where a piece lands, a taken piece breaking up, a red wave for a check. */
function Effect({ fx, orientation }: { fx: Fx; orientation: Color }) {
  const { col, row } = cell(fx.square, orientation);
  return (
    <div className={'fx fx-' + fx.kind} data-fx={fx.kind} data-square-fx={fx.square} style={{ left: `${col * 12.5}%`, top: `${row * 12.5}%` }}>
      {fx.kind === 'capture' && fx.code && (
        <>
          <span className="fx-ghost"><PieceSvg code={fx.code} /></span>
          {SHARDS.map((angle, i) => <i key={i} className="fx-shard" style={{ '--a': `${angle}deg`, '--d': `${i % 2 ? 62 : 48}%` } as CSSProperties} />)}
        </>
      )}
    </div>
  );
}
const SHARDS = [12, 57, 102, 147, 192, 237, 282, 327];

/** A round mark in the top-right corner of a square: ★ best, ?? blunder and so on. */
function QualityBadge({ square, quality, orientation }: { square: string; quality: Quality; orientation: Color }) {
  const file = square.charCodeAt(0) - 97;
  const rank = Number(square[1]) - 1;
  const col = orientation === 'white' ? file : 7 - file;
  const row = orientation === 'white' ? 7 - rank : rank;
  const q = QUALITY[quality];
  return (
    <div className={'quality-badge q-bg-' + quality} data-testid="quality-badge" data-quality={quality} title={q.label}
      style={{ left: `${(col + 1) * 12.5}%`, top: `${row * 12.5}%` }}>
      {q.badge}
    </div>
  );
}
