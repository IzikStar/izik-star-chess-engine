import type { VariantDef } from './model';

// The rules of a variant in plain words, as the engine plays them (ai.board.BoardRules,
// ai.board.GenericBoard, ai.variant.Variant). Each sentence follows the code; change them together.

const FILES = 'abcdefghijklmnop';

/** The rows of the FEN placement, top row first, each as its cells (a letter or '' for empty). */
function rows(v: VariantDef): string[][] {
  return v.start.trim().split(/\s+/)[0].split('/').map((row) => {
    const out: string[] = [];
    for (const c of row.match(/\d+|./g) ?? []) {
      if (/^\d+$/.test(c)) for (let i = 0; i < Number(c); i++) out.push('');
      else out.push(c);
    }
    return out;
  });
}

/** How many royal pieces White has in the start position. */
export function whiteRoyals(v: VariantDef): number {
  const royal = new Set(v.pieces.filter((p) => p.royal).map((p) => p.letter));
  return rows(v).flat().filter((c) => c && royal.has(c)).length;
}

/** The centre squares of the King of the Hill goal: one or two middle files by one or two middle ranks. */
export function hillSquares(width: number, height: number): string[] {
  const out: string[] = [];
  for (let row = Math.floor((height - 1) / 2); row <= Math.floor(height / 2); row++) {
    for (let col = Math.floor((width - 1) / 2); col <= Math.floor(width / 2); col++) {
      out.push(FILES[col] + (height - row));
    }
  }
  return out.sort();
}

function list(words: string[]): string {
  return words.length <= 1 ? words.join('') : words.slice(0, -1).join(', ') + ' or ' + words.at(-1);
}

/** What "check" and a royal piece mean here, given how many royal pieces a side starts with. */
export function royalText(v: VariantDef): string {
  const n = whiteRoyals(v);
  const names = v.pieces.filter((p) => p.royal).map((p) => p.name);
  if (!names.length) return 'No piece is royal, so there is no check: any piece may be captured.';
  if (n > 1) {
    return `Each side starts with ${n} royal pieces (${names.join(', ')}). Every one of them must stay safe: a move may never leave any of them attacked, so a check on any one must be answered, and checkmate means one of them cannot be saved.`;
  }
  return `The ${names.join(' / ')} is royal: a move may never leave it attacked, and when it is attacked it is in check.`;
}

/** The goal in one or two sentences. */
export function goalText(v: VariantDef): string {
  const mate = 'Checkmate wins: the side to move is in check and has no legal move. No legal move without check is stalemate, a draw.';
  switch (v.goal) {
    case 'LOSE_EVERYTHING':
      return 'You win when you have no pieces left, or when it is your turn and you have no legal move.'
        + (v.pieces.some((p) => p.royal) ? ' Royal pieces still may not be left attacked, which limits your moves.' : '');
    case 'KING_OF_THE_HILL':
      return `${mate} You also win the moment one of your royal pieces stands on a centre square: ${list(hillSquares(v.width, v.height))}.`;
    case 'CHECKS':
      return `${mate} You also win by giving check ${v.checksToWin} times: a move counts as a check when it leaves any of the opponent's royal pieces attacked.`;
    default:
      return mate;
  }
}

export const DRAW_TEXT = 'Every goal also draws after 50 moves by each side with no capture and no move of a piece that can promote, and on the same position three times.';

export function forcedCaptureText(on: boolean): string {
  return on
    ? 'When any legal move captures, you must play one of the captures (any one you like). A capture that would leave a royal piece attacked is not legal and does not count.'
    : 'Capturing is optional, as in chess.';
}

/** Castling as the engine reads it from the start position. */
export function castlingText(v: VariantDef): string[] {
  if (!v.castling) return ['No castling, whatever castling roles the pieces have.'];
  const king = v.pieces.filter((p) => p.castlingRole === 'KING');
  const rook = v.pieces.filter((p) => p.castlingRole === 'ROOK');
  const w = v.width;
  const out = [
    `Castling is read from the start position: for each side, a piece with the King castling role castles with the outermost piece with the Rook role on its row, on either side of it. The king lands on the ${FILES[w - 2]}-file (toward ${FILES[w - 1]}) or the c-file (toward a), and the rook on the square next to it on the inside (${FILES[w - 3]} or d).`,
    'As in chess: neither piece may have moved, the squares between and the two landing squares must be empty, and the king may not start on, cross, or land on an attacked square. The start FEN\'s castling field (K, Q, k, q) says which castlings are allowed at all.',
  ];
  if (!king.length || !rook.length) {
    out.push(`No piece has the ${king.length ? 'Rook' : 'King'} castling role, so nobody castles.`);
  } else if (rook.length > 1) {
    out.push(`Note: only one piece type can be the castling rook in play, the last one with the Rook role (${rook.at(-1)!.name}); ${list(rook.slice(0, -1).map((p) => p.name))} never castle${rook.length > 2 ? '' : 's'}.`);
  }
  const field = v.start.trim().split(/\s+/)[2] ?? '-';
  if (king.length && rook.length && !/[KQkq]/.test(field)) out.push('The start FEN\'s castling field is "-", so neither side may castle.');
  return out;
}

/** Short hints for the piece settings. */
export const PIECE_HINTS = {
  royal: 'Royal: it may never be left attacked. Being attacked is check; with no way out, checkmate.',
  enPassant: 'En passant: a two-square straight move lets the opponent capture it on the square it passed, and its own capture moves may capture that way.',
  promotesTo: 'Promotes to: on reaching the far row it becomes one of these pieces (letters of this variant); empty means it never promotes.',
  castling: 'Castling role: the King castles with the outermost Rook on its row (when castling is on for the variant).',
  value: 'Value: what the engine thinks it is worth; a pawn is 100.',
};
