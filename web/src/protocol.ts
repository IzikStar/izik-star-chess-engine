import { useCallback, useEffect, useRef, useState } from 'react';

// The JSON the server sends (web.GameHub / web.GameStateJson). The server is the only rules
// authority: legal moves, SAN, status and results all come from it.

export type Color = 'white' | 'black';
export type Mode = 'engine' | 'friend' | 'computer';
export type Status =
  | 'IN_PROGRESS'
  | 'CHECK'
  | 'CHECKMATE'
  | 'STALEMATE'
  | 'DRAW_FIFTY_MOVE'
  | 'DRAW_THREEFOLD'
  | 'DRAW_INSUFFICIENT_MATERIAL';

export interface MoveInfo {
  uci: string;
  san: string;
  color: Color;
  number: number;
  fenAfter: string;
  capture: boolean;
  castling: boolean;
  enPassant: boolean;
  promotion: boolean;
  status: Status;
}

export interface GameConfig {
  mode: Mode;
  humanColor: Color;
  /** The engine's level; when the engine plays both sides, White's. */
  level: number;
  /** Black's level when the engine plays both sides (otherwise equal to level). */
  blackLevel: number;
}

export interface GameState {
  startFen: string;
  fen: string;
  turn: Color;
  status: Status;
  result: '1-0' | '0-1' | '1/2-1/2' | null;
  humanTurn: boolean;
  engineThinking: boolean;
  hintPending: boolean;
  hint: string | null;
  material: number;
  legalMoves: string[];
  moves: MoveInfo[];
  config: GameConfig;
  /** The evolved champion the engine plays as, or null for the usual engine. */
  opponent: Champion | null;
  /** Whether Levels 8-10 really get Stockfish (absent when the server has no Stockfish slot). */
  stockfish?: StockfishInfo;
}

export interface StockfishInfo {
  available: boolean;
  /** The executable found, or null if none was. */
  path: string | null;
}

/** An evolved champion to play against: a run file in the lab and one of its generations. */
export interface Champion {
  run: string;
  generation: number;
  label: string;
}

export type GameEvent =
  | { kind: 'move'; move: MoveInfo; byEngine: boolean }
  | { kind: 'gameOver'; status: Status }
  | { kind: 'reset' }
  | { kind: 'config' }
  | { kind: 'hint'; uci: string }
  | { kind: 'rejected'; reason: string };

export interface ServerMessage {
  type: 'state';
  events: GameEvent[];
  state: GameState;
}

export type Command =
  | { type: 'move'; uci: string }
  | { type: 'undo' }
  | { type: 'hint' }
  | { type: 'newGame'; mode: Mode; color: Color | 'random'; level: number; blackLevel: number; champion?: { run: string; generation: number } | null };

export type Connection = 'connecting' | 'open' | 'lost';

/**
 * The game as the server sees it, kept current over one WebSocket that reconnects on its own
 * (the server keeps the game, so a reload or a dropped connection loses nothing).
 */
export function useGame(onEvents: (events: GameEvent[], state: GameState) => void) {
  const [state, setState] = useState<GameState | null>(null);
  const [connection, setConnection] = useState<Connection>('connecting');
  const socket = useRef<WebSocket | null>(null);
  const handler = useRef(onEvents);
  handler.current = onEvents;

  useEffect(() => {
    let closed = false;
    let retry: number | undefined;
    const connect = () => {
      const proto = location.protocol === 'https:' ? 'wss' : 'ws';
      const ws = new WebSocket(`${proto}://${location.host}/ws`);
      socket.current = ws;
      ws.onopen = () => setConnection('open');
      ws.onmessage = (e) => {
        const msg = JSON.parse(e.data) as ServerMessage;
        setState(msg.state);
        if (msg.events.length) handler.current(msg.events, msg.state);
      };
      ws.onclose = () => {
        if (closed) return;
        setConnection('lost');
        retry = window.setTimeout(connect, 1000);
      };
    };
    connect();
    return () => {
      closed = true;
      window.clearTimeout(retry);
      socket.current?.close();
    };
  }, []);

  const send = useCallback((command: Command) => {
    if (socket.current?.readyState === WebSocket.OPEN) {
      socket.current.send(JSON.stringify(command));
    }
  }, []);

  return { state, connection, send };
}
