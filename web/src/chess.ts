// Display helpers only. Nothing here decides what is legal; that comes from the server.

import type { Color, GameState, Status, StockfishInfo } from './protocol';

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

export function kingSquare(fen: string, color: Color): string | null {
  const king = color === 'white' ? 'K' : 'k';
  const entry = Object.entries(boardOf(fen)).find(([, p]) => p === king);
  return entry ? entry[0] : null;
}

/**
 * Pieces each side has taken, lowest value first, as FEN letters of the captured pieces.
 * Counted from what is missing on the board (a promoted pawn shows as a taken pawn, like lichess).
 */
export function captured(fen: string): Record<Color, string[]> {
  const left: Record<string, number> = {};
  for (const p of Object.values(boardOf(fen))) left[p] = (left[p] ?? 0) + 1;
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

export function materialOf(fen: string): number {
  let score = 0;
  for (const p of Object.values(boardOf(fen))) {
    const v = VALUES[p.toLowerCase()];
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

/** "Checkmate · Black wins", "Draw by threefold repetition", ... for a finished game. */
export function resultText(status: Status, turn: Color): string {
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
    default:
      return '';
  }
}

export function isOver(state: GameState): boolean {
  return state.result !== null;
}

export const LEVELS: { name: string; engine: string }[] = [
  { name: 'Random moves', engine: 'Plays any legal move' },
  { name: 'Beginner', engine: 'Built-in engine, looks 1 move ahead' },
  { name: 'Novice', engine: 'Built-in engine, 2 plies' },
  { name: 'Casual', engine: 'Built-in engine, 3 plies' },
  { name: 'Improving', engine: 'Built-in engine, 4 plies' },
  { name: 'Club player', engine: 'Built-in engine, 5 plies' },
  { name: 'Strong club player', engine: 'Built-in engine, 6 plies' },
  { name: 'Expert', engine: 'Stockfish if installed' },
  { name: 'Master', engine: 'Stockfish if installed' },
  { name: 'Grandmaster', engine: 'Stockfish if installed' },
];

/** Levels 8-10 play Stockfish: its skill and thinking time (engine.StockfishEngine). */
const STOCKFISH_LEVELS: Record<number, string> = { 8: 'Stockfish, skill 13, 0.3 s a move', 9: 'Stockfish, skill 15, 0.6 s a move', 10: 'Stockfish, skill 17, 1 s a move' };

/** Who really plays a level: for 8-10, Stockfish if the server found it, else the built-in engine at Level 7. */
export function engineText(level: number, stockfish: StockfishInfo | undefined): string {
  if (level < 8 || !stockfish) return LEVELS[level - 1].engine;
  return stockfish.available ? STOCKFISH_LEVELS[level] : 'Stockfish not found: the built-in engine plays at Level 7';
}
