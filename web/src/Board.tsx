import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { Chessboard, type Arrow } from 'react-chessboard';
import type { Color } from './protocol';
import { boardOf } from './chess';
import { PIECES, PieceSvg } from './pieces';

interface Props {
  fen: string;
  orientation: Color;
  /** Legal moves by start square; empty when the board should not accept moves. */
  legal: Map<string, string[]>;
  lastMove: string | null;
  checkSquare: string | null;
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
}

const LAST = 'rgba(235, 220, 90, 0.5)';
const SELECTED = 'rgba(20, 85, 60, 0.5)';
const PREMOVE = 'rgba(40, 70, 140, 0.5)';
const DOT = 'radial-gradient(circle, rgba(20, 30, 20, 0.28) 22%, transparent 23%)';
const RING = 'radial-gradient(circle, transparent 79%, rgba(20, 30, 20, 0.3) 80%)';
const CHECK = 'radial-gradient(circle, rgba(255, 0, 0, 0.85) 0%, rgba(231, 0, 0, 0.5) 30%, rgba(169, 0, 0, 0) 75%)';

/**
 * The board: react-chessboard with click-to-move and drag-to-move, conventional highlights,
 * the hint as an arrow and a promotion picker over the promotion square. It only offers the
 * moves it is given.
 */
export function Board({ fen, orientation, legal, lastMove, checkSquare, hint, onMove, onSelect, onIllegal, premoveColor, premoves, onPremove }: Props) {
  const [selected, setSelected] = useState<string | null>(null);
  const [promotion, setPromotion] = useState<{ from: string; to: string; color: 'w' | 'b'; premove: boolean } | null>(null);
  const pieces = useMemo(() => boardOf(fen), [fen]);

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
    if (premovePromotes(from, to)) setPromotion({ from, to, color: pieces[from] === 'P' ? 'w' : 'b', premove: true });
    else onPremove(from + to);
  };

  const candidates = (from: string, to: string) => (legal.get(from) ?? []).filter((u) => u.slice(2, 4) === to);

  /** Plays from -> to if legal; asks for the piece first on a promotion. Returns true if played. */
  const tryMove = (from: string, to: string): boolean => {
    const moves = candidates(from, to);
    if (moves.length === 0) return false;
    setSelected(null);
    if (moves.some((u) => u.length === 5)) {
      setPromotion({ from, to, color: pieces[from] === pieces[from]?.toUpperCase() ? 'w' : 'b', premove: false });
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
  if (checkSquare) add(checkSquare, { backgroundImage: CHECK });
  for (const uci of premoves) {
    add(uci.slice(0, 2), { backgroundColor: PREMOVE });
    add(uci.slice(2, 4), { backgroundColor: PREMOVE });
  }
  if (selected) {
    add(selected, { backgroundColor: SELECTED });
    for (const uci of legal.get(selected) ?? []) {
      const to = uci.slice(2, 4);
      add(to, { backgroundImage: pieces[to] ? RING : DOT, cursor: 'pointer' });
    }
  }

  const arrows: Arrow[] = hint ? [{ startSquare: hint.slice(0, 2), endSquare: hint.slice(2, 4), color: 'rgba(31, 122, 100, 0.85)' }] : [];

  return (
    <div className="board" data-testid="board" data-hint={hint ?? ''}>
      <Chessboard
        options={{
          id: 'main',
          position: fen,
          pieces: PIECES,
          boardOrientation: orientation,
          squareStyles,
          arrows,
          allowDrawingArrows: true,
          animationDurationInMs: 200,
          lightSquareStyle: { backgroundColor: 'var(--sq-light)' },
          darkSquareStyle: { backgroundColor: 'var(--sq-dark)' },
          lightSquareNotationStyle: { color: 'var(--sq-dark)' },
          darkSquareNotationStyle: { color: 'var(--sq-light)' },
          dropSquareStyle: { boxShadow: 'inset 0 0 0 4px rgba(255,255,255,0.6)' },
          canDragPiece: ({ square }) => canPick(square),
          onPieceDrag: ({ square }) => {
            if (canPick(square)) setSelected(square);
          },
          onPieceDrop: ({ sourceSquare, targetSquare }) => {
            if (!targetSquare || targetSquare === sourceSquare) return false;
            if (premoving) {
              queuePremove(sourceSquare, targetSquare);
              return false;
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
      {promotion && (
        <PromotionPicker
          square={promotion.to}
          orientation={orientation}
          color={promotion.color}
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

function PromotionPicker({ square, orientation, color, onPick, onCancel }: {
  square: string;
  orientation: Color;
  color: 'w' | 'b';
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
        {(['q', 'r', 'b', 'n'] as const).map((p) => {
          return (
            <button key={p} type="button" className="promo-piece" aria-label={{ q: 'Queen', r: 'Rook', b: 'Bishop', n: 'Knight' }[p]} onClick={() => onPick(p)}>
              <PieceSvg piece={color + p.toUpperCase()} />
            </button>
          );
        })}
      </div>
    </div>
  );
}
