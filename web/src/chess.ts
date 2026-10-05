// Display helpers only. Nothing here decides what is legal; that comes from the server.

import type { Color, GameEnd, GameState, Status, StockfishInfo, VariantId, Weights } from './protocol';

/** The variants the New game dialog offers, with the rule that makes each one different. */
export const VARIANTS: { id: VariantId; name: string; rule: string }[] = [
  { id: 'chess', name: 'Chess', rule: 'The usual game: checkmate the king.' },
  { id: 'antichess', name: 'Antichess', rule: 'Lose every piece to win. Captures are forced and the king is an ordinary piece.' },
  { id: 'king-of-the-hill', name: 'King of the Hill', rule: 'Checkmate, or bring your king to one of the four centre squares.' },
  { id: 'three-check', name: 'Three-check', rule: 'Checkmate, or give check three times.' },
];

/** How a variant is won, in words, for one the player made (the built-ins have their own rule above). */
export function goalRule(goal: string, checksToWin = 3): string {
  switch (goal) {
    case 'LOSE_EVERYTHING': return 'Lose every piece, or have no move left, to win.';
    case 'KING_OF_THE_HILL': return 'Checkmate, or bring your king to one of the four centre squares.';
    case 'CHECKS': return `Checkmate, or give check ${checksToWin} times.`;
    default: return 'Checkmate the king.';
  }
}

/** The game's variant id; a state without one is chess. */
export function variantOf(state: GameState): VariantId {
  return state.variant?.id ?? 'chess';
}

/**
 * Three-check: the checks each side has given, from the FEN's "3+3" field (the checks each side
 * still has to give); null in any other game.
 */
export function checksGiven(fen: string, toWin = 3): Record<Color, number> | null {
  const field = fen.split(' ').find((f) => /^\d+\+\d+$/.test(f));
  if (!field) return null;
  const [white, black] = field.split('+').map(Number);
  return { white: toWin - white, black: toWin - black };
}

export const VALUES: Record<string, number> = { p: 1, n: 3, b: 3, r: 5, q: 9, k: 0 };
const START_COUNT: Record<string, number> = { p: 8, n: 2, b: 2, r: 2, q: 1 };

/** Square name -> FEN piece letter, from a FEN's board field. */
export function boardOf(fen: string): Record<string, string> {
  const board: Record<string, string> = {};
  fen.split(' ')[0].split('/').forEach((row, i) => {
    let file = 0;
    for (const ch of row) {
      if (/\d/.test(ch)) file += Number(ch);
      else board['abcdefgh'[file++] + (8 - i)] = ch;
    }
  });
  return board;
}

/** A FEN's board field from square name -> piece letter. */
function placement(board: Record<string, string>): string {
  const rows: string[] = [];
  for (let rank = 8; rank >= 1; rank--) {
    let row = '';
    let empty = 0;
    for (const file of 'abcdefgh') {
      const p = board[file + rank];
      if (!p) {
        empty++;
        continue;
      }
      if (empty) row += empty;
      row += p;
      empty = 0;
    }
    rows.push(empty ? row + empty : row);
  }
  return rows.join('/');
}

/**
 * The position as it will look once the queued premoves are played, so the next premove can start
 * where the last one left a piece. Display only: each premove is checked when it is played.
 */
export function withPremoves(fen: string, premoves: string[]): string {
  if (premoves.length === 0) return fen;
  const board = boardOf(fen);
  for (const uci of premoves) {
    const from = uci.slice(0, 2);
    const to = uci.slice(2, 4);
    const piece = board[from];
    if (!piece) continue;
    delete board[from];
    const white = piece === piece.toUpperCase();
    board[to] = uci.length === 5 ? (white ? uci[4].toUpperCase() : uci[4]) : piece;
    // castling: a king moving two files takes its rook over with it
    if (piece.toLowerCase() === 'k' && from[0] === 'e' && (to[0] === 'g' || to[0] === 'c')) {
      const rookFrom = (to[0] === 'g' ? 'h' : 'a') + from[1];
      const rookTo = (to[0] === 'g' ? 'f' : 'd') + from[1];
      if (board[rookFrom]) {
        board[rookTo] = board[rookFrom];
        delete board[rookFrom];
      }
    }
  }
  return [placement(board), ...fen.split(' ').slice(1)].join(' ');
}

export function turnOf(fen: string): Color {
  return fen.split(' ')[1] === 'b' ? 'black' : 'white';
}

/**
 * The squares to mark as in check: the server's list of attacked royal pieces, or (from a server
 * older than that) the king of the side to move when the status says check.
 */
export function checkedSquares(checked: string[] | undefined, status: string, fen: string): string[] {
  if (checked) return checked;
  if (status !== 'CHECK' && status !== 'CHECKMATE') return [];
  const king = kingSquare(fen, turnOf(fen));
  return king ? [king] : [];
}

export function kingSquare(fen: string, color: Color): string | null {
  const king = color === 'white' ? 'K' : 'k';
  const entry = Object.entries(boardOf(fen)).find(([, p]) => p === king);
  return entry ? entry[0] : null;
}

/**
 * Pieces each side has taken, lowest value first, as FEN letters of the captured pieces.
 * Counted from what is missing on the board (a promoted pawn shows as a taken pawn, like lichess).
 */
/**
 * A made variant's pieces: what the captured pieces and the material lead are counted against
 * (its start position and the pieces' values in pawns, by lower-case letter).
 */
export interface PieceSetBase {
  startFen: string;
  values: Record<string, number>;
}

/** {@link PieceSetBase} for a game's variant, or undefined for the built-ins (they count as chess). */
export function pieceSetBase(state: GameState): PieceSetBase | undefined {
  const v = state.variant;
  if (!v?.custom || !v.pieces) return undefined;
  return { startFen: state.startFen, values: Object.fromEntries(v.pieces.map((p) => [p.letter.toLowerCase(), (p.value ?? 0) / 100])) };
}

export function captured(fen: string, base?: PieceSetBase): Record<Color, string[]> {
  const left: Record<string, number> = {};
  for (const p of Object.values(boardOf(fen))) left[p] = (left[p] ?? 0) + 1;
  if (base) {
    const start: Record<string, number> = {};
    for (const p of Object.values(boardOf(base.startFen))) start[p] = (start[p] ?? 0) + 1;
    const takenFrom = (color: Color) => Object.keys(start)
      .filter((p) => (p === p.toUpperCase()) === (color === 'white') && base.values[p.toLowerCase()] > 0)
      .sort((a, b) => base.values[a.toLowerCase()] - base.values[b.toLowerCase()])
      .flatMap((p) => Array<string>(Math.max(0, start[p] - (left[p] ?? 0))).fill(p));
    return { white: takenFrom('black'), black: takenFrom('white') };
  }
  const takenFrom = (color: Color) => {
    const out: string[] = [];
    for (const type of ['p', 'n', 'b', 'r', 'q']) {
      const letter = color === 'white' ? type.toUpperCase() : type;
      const missing = Math.max(0, START_COUNT[type] - (left[letter] ?? 0));
      for (let i = 0; i < missing; i++) out.push(letter);
    }
    return out;
  };
  // White has taken Black's missing pieces, and the other way round
  return { white: takenFrom('black'), black: takenFrom('white') };
}

export function materialOf(fen: string, values: Record<string, number> = VALUES): number {
  let score = 0;
  for (const p of Object.values(boardOf(fen))) {
    const v = values[p.toLowerCase()] ?? 0;
    score += p === p.toUpperCase() ? v : -v;
  }
  return score;
}

/** Legal moves grouped by the square they start from. */
export function movesByFrom(legal: string[]): Map<string, string[]> {
  const map = new Map<string, string[]>();
  for (const uci of legal) {
    const from = uci.slice(0, 2);
    const list = map.get(from) ?? [];
    list.push(uci);
    map.set(from, list);
  }
  return map;
}

export function other(color: Color): Color {
  return color === 'white' ? 'black' : 'white';
}

export function colorName(color: Color): string {
  return color === 'white' ? 'White' : 'Black';
}

/** "Checkmate · Black wins", "White resigns · Black wins", "Draw by agreement", ... for a finished game. */
export function resultText(status: Status, turn: Color, end: GameEnd | null = null): string {
  if (end) {
    const side = colorName(end.side);
    const winner = colorName(other(end.side));
    switch (end.reason) {
      case 'RESIGNATION':
        return `${side} resigns · ${winner} wins`;
      case 'TIMEOUT':
        return `${side} ran out of time · ${winner} wins`;
      case 'TIMEOUT_VS_INSUFFICIENT_MATERIAL':
        return `${side} ran out of time · Draw, ${winner} cannot mate`;
      case 'AGREEMENT':
        return 'Draw by agreement';
    }
  }
  switch (status) {
    case 'CHECKMATE':
      return `Checkmate · ${colorName(other(turn))} wins`;
    case 'STALEMATE':
      return `Stalemate · ${colorName(turn)} has no legal move`;
    case 'DRAW_FIFTY_MOVE':
      return 'Draw by the 50-move rule';
    case 'DRAW_THREEFOLD':
      return 'Draw by threefold repetition';
    case 'DRAW_INSUFFICIENT_MATERIAL':
      return 'Draw · not enough material to mate';
    case 'HILL_REACHED':
      return `King on the hill · ${colorName(other(turn))} wins`;
    case 'CHECKS_GIVEN':
      return `Third check · ${colorName(other(turn))} wins`;
    case 'NO_PIECES_LEFT':
      return `${colorName(turn)} has lost every piece · ${colorName(turn)} wins`;
    case 'NO_MOVES_LEFT':
      return `${colorName(turn)} has no move left · ${colorName(turn)} wins`;
    default:
      return '';
  }
}

export function isOver(state: GameState): boolean {
  return state.result !== null;
}

/** The time controls the New game dialog offers, in minutes + seconds of increment. */
export const TIME_CONTROLS: { key: string; minutes: number; increment: number }[] = [
  { key: '1+0', minutes: 1, increment: 0 },
  { key: '3+2', minutes: 3, increment: 2 },
  { key: '5+0', minutes: 5, increment: 0 },
  { key: '10+0', minutes: 10, increment: 0 },
  { key: '15+10', minutes: 15, increment: 10 },
];

/** "3+2" for a clock of 3 minutes with 2 seconds a move, or "none" for an untimed game. */
export function timeKey(clock: { initialMs: number; incrementMs: number } | null): string {
  if (!clock) return 'none';
  const key = `${clock.initialMs / 60_000}+${clock.incrementMs / 1000}`;
  return TIME_CONTROLS.some((t) => t.key === key) ? key : 'none';
}

export function timeControlOf(key: string): { initialMs: number; incrementMs: number } | null {
  const t = TIME_CONTROLS.find((c) => c.key === key);
  return t ? { initialMs: t.minutes * 60_000, incrementMs: t.increment * 1000 } : null;
}

/** 5:00, 0:42, and tenths under ten seconds: 0:09.4. */
export function formatClock(ms: number): string {
  const tenths = Math.floor(ms / 100);
  const seconds = Math.floor(ms / 1000);
  const m = Math.floor(seconds / 60);
  const s = String(seconds % 60).padStart(2, '0');
  if (ms < 10_000) return `${m}:${s}.${tenths % 10}`;
  if (m >= 60) return `${Math.floor(m / 60)}:${String(m % 60).padStart(2, '0')}:${s}`;
  return `${m}:${s}`;
}

/**
 * The difficulty ladder, Levels 0-13 (engine.Levels). elo is the measured strength on Stockfish's
 * UCI_Elo scale, a computer rating list (docs/difficulty-ladder.md); Level 0 sits below the ladder.
 */
export const LEVELS: { name: string; engine: string; elo: number | null }[] = [
  { name: 'Random moves', engine: 'Plays any legal move', elo: null },
  { name: 'Beginner', engine: 'Built-in engine, 1 ply, a quarter of its moves random', elo: 100 },
  { name: 'Novice', engine: 'Built-in engine, looks 1 move ahead', elo: 460 },
  { name: 'Casual', engine: 'Built-in engine, 2 plies', elo: 750 },
  { name: 'Improving', engine: 'Built-in engine, 3 plies', elo: 1070 },
  { name: 'Club player', engine: 'Built-in engine, 4 plies', elo: 1260 },
  { name: 'Strong club player', engine: 'Built-in engine, 5 plies', elo: 1530 },
  { name: 'Expert', engine: 'Built-in engine, 6 plies', elo: 1690 },
  { name: 'Strong expert', engine: 'Built-in engine, 7 plies (its deepest)', elo: 1910 },
  { name: 'Master', engine: 'Stockfish at 2150, 0.5 s a move', elo: 2150 },
  { name: 'International master', engine: 'Stockfish at 2400, 0.5 s a move', elo: 2400 },
  { name: 'Grandmaster', engine: 'Stockfish at 2650, 0.5 s a move', elo: 2650 },
  { name: 'Super grandmaster', engine: 'Stockfish at 2900, 0.5 s a move', elo: 2900 },
  { name: 'Full strength', engine: 'Stockfish at full strength, 1 s a move', elo: 3190 },
];

export const MIN_LEVEL = 0;
export const MAX_LEVEL = LEVELS.length - 1;
/** Levels from here up are played by Stockfish. */
export const STOCKFISH_FROM_LEVEL = 9;
/** The built-in engine's strongest level: what Stockfish's levels play when it is missing. */
export const TOP_BUILT_IN_LEVEL = STOCKFISH_FROM_LEVEL - 1;

/**
 * How much stronger the tuned weights are than the classic ones at each built-in level (1-8):
 * tuned against classic head to head at the level's depth, all openings with both colours
 * (docs/difficulty-ladder.md). Level 1 plays a quarter of its moves at random, so it gets three
 * quarters of Level 2's gain (inferred, not measured). LEVELS' Elo is the classic weights'.
 */
export const TUNED_ELO_GAIN: Record<number, number> = { 1: 65, 2: 90, 3: 130, 4: 170, 5: 120, 6: 210, 7: 250, 8: 200 };

/** A level's Elo with {@code weights}; Stockfish's levels are the same for both. */
export function levelElo(level: number, weights: Weights = 'tuned'): number | null {
  const elo = LEVELS[level].elo;
  if (elo === null || weights === 'classic') return elo;
  return elo + (TUNED_ELO_GAIN[level] ?? 0);
}

/** "≈ 1530 Elo", "3190+ Elo" for the top level, or "" for Level 0. */
export function eloText(level: number, weights: Weights = 'tuned'): string {
  const elo = levelElo(level, weights);
  if (elo === null) return '';
  return level === MAX_LEVEL ? `${elo}+ Elo` : `≈ ${elo} Elo`;
}

/** Who really plays a level: for Stockfish's levels, Stockfish if the server found it, else the built-in engine at its top level. */
export function engineText(level: number, stockfish: StockfishInfo | undefined): string {
  if (level < STOCKFISH_FROM_LEVEL || !stockfish || stockfish.available) return LEVELS[level].engine;
  return `Stockfish not found: the built-in engine plays at Level ${TOP_BUILT_IN_LEVEL}`;
}
