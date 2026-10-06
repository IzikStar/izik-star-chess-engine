import { orList, type GoalDef, type GoalKind } from '../chess';
import type { RulesCheck, Stalemate, VariantDef } from './model';

// The rules of a variant in plain words, as the engine plays them (ai.board.BoardRules,
// ai.board.GenericBoard, ai.variant.Variant, docs/variant-rules.md). Each sentence follows the code;
// change them together.

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

/** The centre squares of a board: one or two middle files by one or two middle ranks. */
export function hillSquares(width: number, height: number): string[] {
  const out: string[] = [];
  for (let row = Math.floor((height - 1) / 2); row <= Math.floor(height / 2); row++) {
    for (let col = Math.floor((width - 1) / 2); col <= Math.floor(width / 2); col++) {
      out.push(FILES[col] + (height - row));
    }
  }
  return out.sort();
}

/** The names of the piece types with these letters, "Queen or Rook". */
export function typeNames(v: VariantDef, letters: string | undefined): string {
  return orList([...(letters ?? '')].map((l) => v.pieces.find((p) => p.letter === l)?.name ?? l));
}

// ---- goals ----------------------------------------------------------------------------------------

/** The kinds of goal, with a name for the editor. */
export const GOAL_KINDS: { kind: GoalKind; name: string }[] = [
  { kind: 'CHECKMATE', name: 'Checkmate' },
  { kind: 'REACH_SQUARES', name: 'Reach squares' },
  { kind: 'CHECKS', name: 'Give N checks' },
  { kind: 'CAPTURE_ALL_OF', name: 'Capture all of a piece type' },
  { kind: 'BARE_ROYAL', name: 'Bare royal' },
  { kind: 'LOSE_EVERYTHING', name: 'Lose everything (antichess)' },
];

/** A new goal of {@code kind} with its usual parameters. */
export function newGoal(kind: GoalKind, v: VariantDef): GoalDef {
  switch (kind) {
    case 'REACH_SQUARES': return { kind, squares: hillSquares(v.width, v.height), pieces: '' };
    case 'CHECKS': return { kind, count: 3 };
    case 'CAPTURE_ALL_OF': return { kind, pieces: v.pieces.find((p) => !p.royal)?.letter ?? v.pieces[0].letter };
    default: return { kind };
  }
}

/** One goal in one sentence. */
export function goalText(g: GoalDef, v: VariantDef): string {
  switch (g.kind) {
    case 'CHECKMATE':
      return 'You win when the opponent is in check and has no legal move.';
    case 'REACH_SQUARES': {
      const who = g.pieces ? `pieces of type ${typeNames(v, g.pieces)}` : 'royal pieces';
      return `You win the moment one of your ${who} stands on ${orList([...(g.squares ?? [])].sort())}.`;
    }
    case 'CHECKS':
      return `You win by giving check ${g.count} times: a move counts as a check when it leaves one of the opponent's royal pieces attacked`
        + (v.royalMode === 'LAST_STANDING' ? ' while the opponent has a single royal piece left.' : '.');
    case 'CAPTURE_ALL_OF':
      return `You win when the opponent has no ${typeNames(v, g.pieces)} left.`;
    case 'BARE_ROYAL':
      return 'You win when the opponent has nothing left but royal pieces.';
    case 'LOSE_EVERYTHING':
      return 'You win when you have no pieces left, or when it is your turn and you have no legal move.'
        + (v.pieces.some((p) => p.royal) ? ' Royal pieces still may not be left attacked, which limits your moves.' : '');
  }
}

export const GOALS_TEXT = 'After every move the goals are checked in this order and the first one met decides the game. Checkmate, and having no legal move, are judged when the side to move has no move.';

/** Why a goal's parameters are wrong, or null. */
export function goalProblem(g: GoalDef, v: VariantDef): string | null {
  const letters = new Set(v.pieces.map((p) => p.letter));
  if (g.kind === 'REACH_SQUARES') {
    if (!g.squares?.length) return 'Name at least one square.';
    const bad = g.squares.find((s) => !/^[a-p](1[0-6]|[1-9])$/.test(s) || FILES.indexOf(s[0]) >= v.width || Number(s.slice(1)) > v.height);
    if (bad) return `${bad} is not a square of this board.`;
  }
  if ((g.kind === 'REACH_SQUARES' || g.kind === 'CAPTURE_ALL_OF') && [...(g.pieces ?? '')].some((l) => !letters.has(l))) {
    return 'A piece type it names is not in this variant.';
  }
  if (g.kind === 'CAPTURE_ALL_OF' && !g.pieces) return 'Pick at least one piece type.';
  const needsRoyal = g.kind === 'CHECKMATE' || g.kind === 'CHECKS' || g.kind === 'BARE_ROYAL' || (g.kind === 'REACH_SQUARES' && !g.pieces);
  if (needsRoyal && !v.pieces.some((p) => p.royal)) return 'This goal needs a royal piece, and no piece is royal.';
  return null;
}

// ---- royal pieces -------------------------------------------------------------------------------

export const ROYAL_MODES: { id: VariantDef['royalMode']; name: string; text: string }[] = [
  { id: 'ALL_SAFE', name: 'All must stay safe',
    text: 'Every royal piece must stay safe: a move may not leave any of them attacked, and an attack on any one of them is check.' },
  { id: 'LAST_STANDING', name: 'Last one standing',
    text: 'A side with two or more royal pieces may leave them attacked and lose them; check applies once it has one left, and a side with none left has lost.' },
];

/** What "check" and a royal piece mean here, given how many royal pieces a side starts with. */
export function royalText(v: VariantDef): string {
  const n = whiteRoyals(v);
  const names = v.pieces.filter((p) => p.royal).map((p) => p.name);
  if (!names.length) return 'No piece is royal, so there is no check: any piece may be captured.';
  if (n > 1 && v.royalMode === 'LAST_STANDING') {
    return `Each side starts with ${n} royal pieces (${names.join(', ')}). While a side has two or more, there is no check: they may be left attacked and may be captured. Once a side has one left, it is held to check as in chess, and checkmate needs it.`;
  }
  if (n > 1) {
    return `Each side starts with ${n} royal pieces (${names.join(', ')}). Every one of them must stay safe: a move may never leave any of them attacked, so a check on any one must be answered, and checkmate means one of them cannot be saved.`;
  }
  return `The ${names.join(' / ')} is royal: a move may never leave it attacked, and when it is attacked it is in check.`;
}

// ---- endings ------------------------------------------------------------------------------------

export const STALEMATES: { id: Stalemate; name: string }[] = [
  { id: 'DRAW', name: 'A draw' },
  { id: 'WIN', name: 'A win for the side with no move' },
  { id: 'LOSS', name: 'A loss for the side with no move' },
];

export function stalemateText(v: VariantDef): string {
  const result = v.stalemate === 'DRAW' ? 'the game is a draw' : v.stalemate === 'WIN' ? 'that side wins' : 'that side loses';
  let text = `When the side to move has no legal move and is not checkmated, ${result}.`;
  if (!v.goals.some((g) => g.kind === 'CHECKMATE') && v.pieces.some((p) => p.royal)) {
    text += ' Checkmate is not a goal here, so being in check with no legal move counts the same way.';
  }
  if (v.goals.some((g) => g.kind === 'LOSE_EVERYTHING')) {
    text += ' With the lose-everything goal, having no legal move always wins.';
  }
  return text;
}

export function repetitionText(on: boolean): string {
  return on
    ? 'The same position three times (the same pieces on the same squares, the same side to move, the same castling and en passant rights) is a draw.'
    : 'Repeating a position never ends the game.';
}

export function moveLimitText(n: number): string {
  return n > 0
    ? `After ${n} moves by each side with no capture and no move of a piece that can promote, the game is a draw.`
    : 'There is no move limit: a game without captures goes on.';
}

export function forcedCaptureText(on: boolean): string {
  return on
    ? 'When any legal move captures, you must play one of the captures (any one you like). A capture that would leave a royal piece attacked is not legal and does not count.'
    : 'Capturing is optional, as in chess.';
}

// ---- castling -----------------------------------------------------------------------------------

export const CASTLING_SIDES: { id: VariantDef['castlingRule']['sides']; name: string }[] = [
  { id: 'BOTH', name: 'Both sides' },
  { id: 'KING_SIDE', name: 'King side only (toward the h-file)' },
  { id: 'QUEEN_SIDE', name: 'Queen side only (toward the a-file)' },
];

/** Castling as the engine reads it from the start position and the castling settings. */
export function castlingText(v: VariantDef): string[] {
  if (!v.castling) return ['No castling, whatever castling roles the pieces have.'];
  const r = v.castlingRule;
  const king = v.pieces.filter((p) => p.castlingRole === 'KING');
  const rook = v.pieces.filter((p) => p.castlingRole === 'ROOK');
  const w = v.width;
  const sides = r.sides === 'BOTH' ? 'either side of it' : r.sides === 'KING_SIDE' ? `its ${FILES[w - 1]}-file side only` : 'its a-file side only';
  const out = [
    `Castling is read from the start position: a piece with the King castling role${king.length ? ` (${orList(king.map((p) => p.name))})` : ''} castles with the outermost piece with the Rook role${rook.length ? ` (${orList(rook.map((p) => p.name))})` : ''} on its row, on ${sides}.`,
    (r.steps === 0
      ? `It lands on the ${FILES[w - 2]}-file (toward ${FILES[w - 1]}) or the c-file (toward a), as in chess`
      : `It moves ${r.steps} squares toward its partner`)
    + (r.partner === 'INSIDE'
      ? ', and the partner lands on the square next to it on the inside, the last one it crossed.'
      : ', and the partner lands on the square next to it on the outside, toward the edge.'),
    'Neither piece may have moved, and the squares between them and both landing squares must be empty. '
    + (r.safePassage
      ? 'It may not castle out of check, across an attacked square or onto one.'
      : 'It may castle out of or across attacked squares; only the landing square must be safe, as for any move.')
    + (v.royalMode === 'LAST_STANDING' && r.safePassage ? ' With two or more royal pieces a side there is no check, so attacks do not stop castling then.' : ''),
    'If the piece can reach its landing square by an ordinary move, that move is the one played: there is no castling to that square. The start FEN\'s castling field (K, Q, k, q) says which castlings are allowed at all.',
  ];
  if (!king.length || !rook.length) {
    out.push(`No piece has the ${king.length ? 'Rook' : 'King'} castling role, so nobody castles.`);
  }
  const field = v.start.trim().split(/\s+/)[2] ?? '-';
  if (king.length && rook.length && !/[KQkq]/.test(field)) out.push('The start FEN\'s castling field is "-", so neither side may castle.');
  return out;
}

/** The castlings the engine found, in words: "White: e1 to g1, rook h1 to f1". */
export function castlingMoves(check: RulesCheck | null): string[] {
  return (check?.castlings ?? []).map((c) =>
    `${c.side === 'white' ? 'White' : 'Black'} ${c.kingSide ? 'king side' : 'queen side'}: ${c.king} to ${c.kingTo}, partner ${c.rook} to ${c.rookTo}`);
}

// ---- Fairy-Stockfish ----------------------------------------------------------------------------

export function fairyText(check: RulesCheck | null): string {
  if (!check) return '';
  return check.fairy
    ? 'Fairy-Stockfish can play it, so it can be the opponent in the health check and a yardstick in the Lab.'
    : `Our engine only: Fairy-Stockfish cannot play it (${check.fairyReason ?? 'not supported'}). The health check and Lab runs still work, without Fairy-Stockfish.`;
}

/** Short hints for the piece settings. */
export const PIECE_HINTS = {
  royal: 'Royal: it may never be left attacked (see the royal mode on the Overview). Being attacked is check; with no way out, checkmate.',
  enPassant: 'En passant: a two-square straight move lets the opponent capture it on the square it passed, and its own capture moves may capture that way.',
  promotesTo: 'Promotes to: on reaching the far row it becomes one of these pieces (letters of this variant); empty means it never promotes.',
  castling: 'Castling role: a King castles with the outermost Rook on its row (when castling is on for the variant; see the Overview).',
  value: 'Value: what the engine thinks it is worth; a pawn is 100.',
};
