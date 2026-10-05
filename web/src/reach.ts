// Where a piece can go, worked out in the browser from its atoms (ai.piece.Atom): the designer's
// preview, and the squares shown when the mouse is over an invented piece on any board.

export type Kind = 'LEAP' | 'SLIDE';
export type Mode = 'MOVE' | 'CAPTURE' | 'BOTH';
export type Symmetry = 'ONE' | 'SIDEWAYS' | 'ALL';

export interface Atom {
  kind: Kind;
  forward: number;
  right: number;
  symmetry: Symmetry;
  mode: Mode;
  /** For a slide: at most this many steps; 0 = to the edge. */
  range: number;
  firstMoveOnly: boolean;
}

/** move: to an empty square; capture: onto a piece; both: either. */
export type Reach = 'move' | 'capture' | 'both';

const FILES = 'abcdefgh';

/** The {forward, right} offsets an atom covers, each once (ai.piece.Atom.offsets). */
export function offsets(a: Atom): [number, number][] {
  const seen = new Map<string, [number, number]>();
  const add = (f: number, r: number) => seen.set(`${f},${r}`, [f, r]);
  if (a.symmetry === 'ONE') add(a.forward, a.right);
  else if (a.symmetry === 'SIDEWAYS') {
    add(a.forward, a.right);
    add(a.forward, -a.right);
  } else {
    for (const [f, r] of [[a.forward, a.right], [a.right, a.forward]]) {
      for (const sf of [1, -1]) for (const sr of [1, -1]) add(f * sf, r * sr);
    }
  }
  return [...seen.values()];
}

/**
 * Where a piece on {@code square} can go, by square name. Black's forward is down the board and
 * its right is White's left. With {@code board} (square -> FEN letter) pieces block slides, a move
 * needs an empty square and a capture an enemy piece; without it the board is empty and each
 * square says which kind of move reaches it. Checks and pins are not looked at.
 */
export function reach(atoms: Atom[], square: string, white: boolean, moved: boolean,
                      board?: Record<string, string>): Map<string, Reach> {
  const out = new Map<string, Reach>();
  const file = FILES.indexOf(square[0]);
  const rank = Number(square.slice(1)) - 1;
  const sign = white ? 1 : -1;
  const mark = (sq: string, now: Reach) => {
    const was = out.get(sq);
    out.set(sq, !was || was === now ? now : 'both');
  };
  for (const a of atoms) {
    if (a.firstMoveOnly && moved) continue;
    for (const [f, r] of offsets(a)) {
      for (let k = 1; ; k++) {
        const fl = file + r * k * sign;
        const rk = rank + f * k * sign;
        if (fl < 0 || fl > 7 || rk < 0 || rk > 7) break;
        const sq = FILES[fl] + (rk + 1);
        const there = board?.[sq];
        if (!board) {
          mark(sq, a.mode === 'BOTH' ? 'both' : a.mode === 'MOVE' ? 'move' : 'capture');
        } else if (!there) {
          if (a.mode !== 'CAPTURE') mark(sq, 'move');
        } else {
          if (a.mode !== 'MOVE' && (there === there.toUpperCase()) !== white) mark(sq, 'capture');
          break;
        }
        if (a.kind === 'LEAP' || (a.range > 0 && k >= a.range)) break;
      }
    }
  }
  return out;
}

/** The FEN board field as square name -> letter. */
export function placementOf(fen: string): Record<string, string> {
  const board: Record<string, string> = {};
  fen.trim().split(/\s+/)[0].split('/').forEach((row, i) => {
    let file = 0;
    for (const c of row) {
      if (/\d/.test(c)) file += Number(c);
      else board[FILES[file++] + (8 - i)] = c;
    }
  });
  return board;
}

/**
 * Whether the piece on {@code square} has moved, as the engine counts it for "first move only":
 * it has, unless the same piece stood on that square in the start position.
 */
export function hasMoved(square: string, letter: string, startFen: string): boolean {
  return placementOf(startFen)[square] !== letter;
}

/**
 * For a board: where the invented piece on a square could go in {@code fen}, or null for an empty
 * square or a piece the variant does not mark as invented (the chess pieces need no help).
 */
export function inventedReach(pieces: { letter: string; atoms?: Atom[]; invented?: boolean }[] | undefined,
                              fen: string, startFen: string): ((square: string) => Map<string, Reach> | null) | undefined {
  const invented = pieces?.filter((p) => p.invented && p.atoms);
  if (!invented?.length) return undefined;
  const board = placementOf(fen);
  return (square) => {
    const c = board[square];
    const p = c && invented.find((q) => q.letter === c.toUpperCase());
    return p ? reach(p.atoms!, square, c === c.toUpperCase(), hasMoved(square, c, startFen), board) : null;
  };
}
