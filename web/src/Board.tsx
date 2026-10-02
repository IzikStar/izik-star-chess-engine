import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { Chessboard, defaultPieces, type Arrow } from 'react-chessboard';
import type { Color } from './protocol';
import { boardOf } from './chess';

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
   * premove (any target square; it is checked when it is played). Null otherwise.
   */
  premoveColor: Color | null;
  /** The queued premove (from + to), shown on the board. */
  premove: string | null;
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
export function Board({ fen, orientation, legal, lastMove, checkSquare, hint, onMove, onSelect, onIllegal, premoveColor, premove, onPremove }: Props) {
  const [selected, setSelected] = useState<string | null>(null);
  const [promotion, setPromotion] = useState<{ from: string; to: string } | null>(null);
  const pieces = useMemo(() => boardOf(fen), [fen]);

  // a new position (or the end of our turn) drops any selection
  useEffect(() => {
    setSelected(null);
    setPromotion(null);
  }, [fen, legal.size === 0]);

  const premoving = legal.size === 0 && premoveColor !== null;
  const isOwn = (square: string | null) => {
    const p = square ? pieces[square] : undefined;
    return !!p && (p === p.toUpperCase()) === (premoveColor === 'white');
  };
  const canPick = (square: string | null) => !!square && (premoving ? isOwn(square) : legal.has(square));

  const candidates = (from: string, to: string) => (legal.get(from) ?? []).filter((u) => u.slice(2, 4) === to);

  /** Plays from -> to if legal; asks for the piece first on a promotion. Returns true if played. */
  const tryMove = (from: string, to: string): boolean => {
    const moves = candidates(from, to);
    if (moves.length === 0) return false;
    setSelected(null);
    if (moves.some((u) => u.length === 5)) {
      setPromotion({ from, to });
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
  if (premove) {
    add(premove.slice(0, 2), { backgroundColor: PREMOVE });
    add(premove.slice(2, 4), { backgroundColor: PREMOVE });
  }
  if (selected) {
    add(selected, { backgroundColor: SELECTED });
    for (const uci of legal.get(selected) ?? []) {
      const to = uci.slice(2, 4);
      add(to, { backgroundImage: pieces[to] ? RING : DOT, cursor: 'pointer' });
    }
  }

  const arrows: Arrow[] = hint ? [{ startSquare: hint.slice(0, 2), endSquare: hint.slice(2, 4), color: 'rgba(31, 122, 100, 0.85)' }] : [];

  const promoting = promotion ? pieces[promotion.from] : null;
  const promoColor = promoting && promoting === promoting.toUpperCase() ? 'w' : 'b';

  return (
    <div className="board" data-testid="board" data-hint={hint ?? ''}>
      <Chessboard
        options={{
          id: 'main',
          position: fen,
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
              setSelected(null);
              onPremove(sourceSquare + targetSquare);
              return false;
            }
            if (candidates(sourceSquare, targetSquare).length === 0) {
              onIllegal();
              return false;
            }
            return tryMove(sourceSquare, targetSquare);
          },
          onSquareClick: ({ square }) => {
            if (premoving) {
              if (selected && selected !== square && !isOwn(square)) {
                onPremove(selected + square);
                setSelected(null);
              } else if (isOwn(square) && square !== selected) {
                setSelected(square);
                onPremove(null);
              } else {
                setSelected(null);
                onPremove(null);
              }
            } else if (promotion) {
              setPromotion(null);
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
          color={promoColor}
          onPick={(piece) => {
            onMove(promotion.from + promotion.to + piece);
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
          const Piece = defaultPieces[color + p.toUpperCase()];
          return (
            <button key={p} type="button" className="promo-piece" aria-label={{ q: 'Queen', r: 'Rook', b: 'Bishop', n: 'Knight' }[p]} onClick={() => onPick(p)}>
              <Piece />
            </button>
          );
        })}
      </div>
    </div>
  );
}
