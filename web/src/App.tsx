import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Board } from './Board';
import { ClockFace } from './Clock';
import { PgnDialog } from './PgnDialog';
import { Lab } from './Lab';
import { NewGameDialog, type NewGameChoice } from './NewGameDialog';
import { codeOf, PieceSvg } from './pieces';
import { captured, colorName, isOver, kingSquare, LEVELS, materialOf, movesByFrom, other, resultText, timeControlOf, timeKey, turnOf, withPremoves } from './chess';
import { useGame, type Champion, type Color, type GameEvent, type GameState } from './protocol';
import { play } from './sounds';

const EMPTY = new Map<string, string[]>();

function stored(key: string, fallback: string): string {
  try {
    return localStorage.getItem(key) ?? fallback;
  } catch {
    return fallback;
  }
}

function store(key: string, value: string) {
  try {
    localStorage.setItem(key, value);
  } catch {
    // private window: the setting just isn't remembered
  }
}

export function App() {
  const [soundOn, setSoundOn] = useState(() => stored('sound', 'on') === 'on');
  const [theme, setTheme] = useState(() => stored('theme', 'system'));
  const [flipped, setFlipped] = useState(false);
  const [autoFlip, setAutoFlip] = useState(() => stored('autoFlip', 'off') === 'on');
  /** The ply being reviewed (0 = start position), or null for the live position. */
  const [view, setView] = useState<number | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  /** The champion the New game dialog opens with (from the lab's "Play the champion"). */
  const [dialogChampion, setDialogChampion] = useState<Champion | null>(null);
  /** Which screen: the game, or the lab (#lab). */
  const [page, setPage] = useState(() => (location.hash === '#lab' ? 'lab' : 'game'));
  /** Moves queued while the engine thinks (from + to [+ piece]), played one per turn, oldest first. */
  const [premoves, setPremoves] = useState<string[]>([]);
  /** The state a premove was last sent from, so the next one waits for the engine's reply. */
  const premoveSentFrom = useRef<GameState | null>(null);
  const soundRef = useRef(soundOn);
  soundRef.current = soundOn;
  const [pgnOpen, setPgnOpen] = useState(false);
  /** Why the server refused the pasted PGN. */
  const [pgnError, setPgnError] = useState<string | null>(null);
  /** A pasted PGN was sent and its answer has not come yet. */
  const [pgnLoading, setPgnLoading] = useState(false);
  const pgnLoadingRef = useRef(false);
  pgnLoadingRef.current = pgnLoading;
  const [confirmResign, setConfirmResign] = useState(false);
  /** A short message under the status line (a declined draw), cleared by the next move. */
  const [notice, setNotice] = useState<string | null>(null);

  const onEvents = useCallback((events: GameEvent[], state: GameState) => {
    const sound = soundRef.current;
    for (const e of events) {
      switch (e.kind) {
        case 'move':
          setView(null);
          setNotice(null);
          play(e.move.status === 'CHECK' ? 'check' : e.move.capture ? 'capture' : e.move.castling ? 'castle' : 'move', sound);
          break;
        case 'gameOver': {
          const humanLost = state.config.mode === 'engine' && e.status === 'CHECKMATE' && state.turn === state.config.humanColor;
          play(e.status === 'CHECKMATE' ? (humanLost ? 'lose' : 'win') : 'draw', sound);
          break;
        }
        case 'ended': {
          const humanLost = state.config.mode === 'engine' && e.result !== '1/2-1/2' && e.side === state.config.humanColor;
          play(e.result === '1/2-1/2' ? 'draw' : humanLost ? 'lose' : 'win', sound);
          setConfirmResign(false);
          setNotice(null);
          break;
        }
        case 'drawDeclined':
          setNotice(state.config.mode === 'engine' ? 'The engine declines the draw.' : `${colorName(other(e.by))} declines the draw.`);
          break;
        case 'hint':
          play('hint', sound);
          break;
        case 'reset':
          if (pgnLoadingRef.current) {
            setPgnLoading(false);
            setPgnOpen(false);
          }
          setNotice(null);
          setConfirmResign(false);
          setView(null);
          setPremoves([]);
          break;
        case 'config':
          setView(null);
          setPremoves([]);
          break;
        case 'rejected':
          if (e.reason.startsWith('Could not read the PGN')) {
            setPgnError(e.reason.replace('Could not read the PGN: ', 'Could not load it: '));
            setPgnLoading(false);
          }
          play('invalid', sound);
          break;
      }
    }
  }, []);

  const { state, receivedAt, connection, send } = useGame(onEvents);

  useEffect(() => {
    const onHash = () => setPage(location.hash === '#lab' ? 'lab' : 'game');
    window.addEventListener('hashchange', onHash);
    return () => window.removeEventListener('hashchange', onHash);
  }, []);
  const showPage = (p: 'game' | 'lab') => {
    history.replaceState(null, '', p === 'lab' ? '#lab' : location.pathname);
    setPage(p);
  };

  useEffect(() => {
    const root = document.documentElement;
    if (theme === 'system') delete root.dataset.theme;
    else root.dataset.theme = theme;
  }, [theme]);

  // play the next premove the moment it is our turn, if it is legal then; an illegal one drops
  // the whole queue, since the moves after it were planned from a position that will not happen
  useEffect(() => {
    if (premoves.length === 0 || !state) return;
    if (!state.humanTurn) {
      if (isOver(state)) setPremoves([]);
      return;
    }
    if (premoveSentFrom.current === state) return;
    const [next, ...rest] = premoves;
    const options = state.legalMoves.filter((u) => u.startsWith(next));
    // a promotion premove already names its piece, so it matches exactly one move
    const uci = options.find((u) => u.length === 4) ?? options.find((u) => u === next) ?? options.find((u) => u.endsWith('q'));
    if (uci) {
      premoveSentFrom.current = state;
      setPremoves(rest);
      send({ type: 'move', uci });
    } else {
      setPremoves([]);
      play('invalid', soundRef.current);
    }
  }, [state, premoves, send]);

  const legal = useMemo(() => (state && state.humanTurn ? movesByFrom(state.legalMoves) : EMPTY), [state]);

  const moves = state?.moves ?? [];
  const live = view === null || view >= moves.length;
  const ply = live ? moves.length : view!;

  const goTo = useCallback((p: number) => setView(p >= moves.length ? null : Math.max(0, p)), [moves.length]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (dialogOpen || pgnOpen || page !== 'game' || (e.target as HTMLElement).closest('input, select, textarea')) return;
      if (e.key === 'ArrowLeft') goTo(ply - 1);
      else if (e.key === 'ArrowRight') goTo(ply + 1);
      else if (e.key === 'Home') goTo(0);
      else if (e.key === 'End') goTo(moves.length);
      else if (e.key === 'f') setFlipped((f) => !f);
      else return;
      e.preventDefault();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [goTo, ply, moves.length, dialogOpen, pgnOpen, page]);

  if (!state) {
    return (
      <div className="app loading">
        <p>{connection === 'lost' ? 'Waiting for the game server…' : 'Connecting…'}</p>
      </div>
    );
  }

  const { config } = state;
  const fen = live ? state.fen : ply === 0 ? state.startFen : moves[ply - 1].fenAfter;
  const shownStatus = live ? state.status : ply === 0 ? 'IN_PROGRESS' : moves[ply - 1].status;
  const lastMove = ply === 0 ? null : moves[ply - 1].uci;
  const checkSquare = shownStatus === 'CHECK' || shownStatus === 'CHECKMATE' ? kingSquare(fen, turnOf(fen)) : null;
  const over = isOver(state);

  const baseOrientation: Color =
    config.mode === 'engine' ? config.humanColor : config.mode === 'friend' && autoFlip ? state.turn : 'white';
  const orientation = flipped ? other(baseOrientation) : baseOrientation;
  const premoveColor = live && config.mode === 'engine' && !over && !state.humanTurn ? config.humanColor : null;

  const startNewGame = (choice: NewGameChoice) => {
    setDialogOpen(false);
    setFlipped(false);
    setView(null);
    setPremoves([]);
    setAutoFlip(choice.autoFlip);
    store('autoFlip', choice.autoFlip ? 'on' : 'off');
    play('start', soundOn);
    const champion = choice.mode === 'engine' && choice.champion ? { run: choice.champion.run, generation: choice.champion.generation } : null;
    send({ type: 'newGame', mode: choice.mode, color: choice.color, level: choice.level, blackLevel: choice.blackLevel, champion, time: timeControlOf(choice.time) });
  };

  const openDialog = (champion: Champion | null) => {
    setDialogChampion(champion);
    setDialogOpen(true);
  };

  // a resignation, a loss on time or an agreed draw cannot be taken back (a mate can)
  const canUndo = live && moves.length > 0 && config.mode !== 'computer' && !state.end
    && !(config.mode === 'engine' && moves.length === 1 && config.humanColor === 'black');

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand"><span aria-hidden="true">♞</span> IzikStar Chess</div>
        <div className="tabs" role="group" aria-label="Screen">
          <button type="button" className={'btn ghost' + (page === 'game' ? ' on' : '')} aria-pressed={page === 'game'} onClick={() => showPage('game')}>Game</button>
          <button type="button" className={'btn ghost' + (page === 'lab' ? ' on' : '')} aria-pressed={page === 'lab'} onClick={() => showPage('lab')}>Lab</button>
        </div>
        <div className="spacer" />
        <button type="button" className="btn ghost" aria-pressed={!soundOn} onClick={() => {
          setSoundOn(!soundOn);
          store('sound', soundOn ? 'off' : 'on');
        }}>{soundOn ? 'Sound on' : 'Sound off'}</button>
        <button type="button" className="btn ghost" onClick={() => {
          const dark = theme === 'dark' || (theme === 'system' && matchMedia('(prefers-color-scheme: dark)').matches);
          const next = dark ? 'light' : 'dark';
          setTheme(next);
          store('theme', next);
        }}>Dark / light</button>
        <button type="button" className="btn primary" onClick={() => openDialog(state.opponent)}>New game</button>
      </header>

      {page === 'lab' && <Lab onPlay={(champion) => openDialog(champion)} />}

      <main className="game" hidden={page !== 'game'}>
        <section className="board-area" aria-label="Board">
          <Board
            fen={premoveColor ? withPremoves(fen, premoves) : fen}
            orientation={orientation}
            legal={live ? legal : EMPTY}
            lastMove={lastMove}
            checkSquare={checkSquare}
            hint={live ? state.hint : null}
            onMove={(uci) => send({ type: 'move', uci })}
            premoveColor={premoveColor}
            premoves={premoveColor ? premoves : []}
            onPremove={(uci) => {
              setPremoves((queued) => (uci ? [...queued, uci] : []));
              if (uci) play('select', soundOn);
            }}
            onSelect={() => play('select', soundOn)}
            onIllegal={() => play('invalid', soundOn)}
          />
        </section>

        <aside className="side">
          <PlayerCard state={state} color={other(orientation)} fen={fen} live={live} receivedAt={receivedAt} />

          <StatusLine state={state} connection={connection} live={live} ply={ply} premoves={premoveColor ? premoves : []} onReturn={() => goTo(moves.length)} />

          {notice && <p className="notice" role="status" data-testid="notice">{notice}</p>}

          {state.drawOffer && !over && (
            <section className="ask" aria-label="Draw offer" data-testid="draw-offer">
              <span><strong>{colorName(state.drawOffer)} offers a draw.</strong> {colorName(other(state.drawOffer))}, do you accept?</span>
              <div className="row">
                <button type="button" className="btn primary" onClick={() => send({ type: 'answerDraw', accept: true })}>Accept draw</button>
                <button type="button" className="btn" onClick={() => send({ type: 'answerDraw', accept: false })}>Decline</button>
              </div>
            </section>
          )}

          {confirmResign && state.canResign && (
            <section className="ask warn" aria-label="Resign" data-testid="confirm-resign">
              <span><strong>{config.mode === 'friend' ? `Resign for ${colorName(state.turn)}?` : 'Resign this game?'}</strong></span>
              <div className="row">
                <button type="button" className="btn danger" onClick={() => {
                  setConfirmResign(false);
                  send({ type: 'resign' });
                }}>Resign</button>
                <button type="button" className="btn" onClick={() => setConfirmResign(false)}>Keep playing</button>
              </div>
            </section>
          )}

          {live && over && (
            <section className="result" aria-label="Result" data-testid="result">
              <div className="score">{state.result === '1/2-1/2' ? '½ – ½' : state.result!.replace('-', ' – ')}</div>
              <div className="reason">{resultText(state.status, state.turn, state.end)}</div>
              <div className="row">
                <button type="button" className="btn primary" onClick={() => startNewGame({ mode: config.mode, color: config.humanColor, level: config.level, blackLevel: config.blackLevel, autoFlip, champion: state.opponent, time: timeKey(state.clock) })}>Rematch</button>
                <button type="button" className="btn" onClick={() => goTo(0)}>Review game</button>
              </div>
            </section>
          )}

          <MoveList state={state} ply={ply} onPick={goTo} />

          <div className="nav" role="group" aria-label="Review moves">
            <button type="button" className="icon" aria-label="First position" disabled={ply === 0} onClick={() => goTo(0)}>⏮</button>
            <button type="button" className="icon" aria-label="Previous move" disabled={ply === 0} onClick={() => goTo(ply - 1)}>◀</button>
            <button type="button" className="icon" aria-label="Next move" disabled={live} onClick={() => goTo(ply + 1)}>▶</button>
            <button type="button" className="icon" aria-label="Latest move" disabled={live} onClick={() => goTo(moves.length)}>⏭</button>
          </div>

          <PlayerCard state={state} color={orientation} fen={fen} live={live} receivedAt={receivedAt} />

          <div className="controls" role="group" aria-label="Game controls">
            <button type="button" className="icon labelled" disabled={!canUndo} onClick={() => {
              setPremoves([]);
              play('back', soundOn);
              send({ type: 'undo' });
            }}><b aria-hidden="true">↶</b>Take back</button>
            <button type="button" className="icon labelled" disabled={!live || !state.humanTurn || state.hintPending} onClick={() => send({ type: 'hint' })}><b aria-hidden="true">✦</b>Hint</button>
            <button type="button" className="icon labelled" onClick={() => setFlipped(!flipped)}><b aria-hidden="true">⇅</b>Flip board</button>
            <button type="button" className="icon labelled" disabled={!live || !state.canOfferDraw} onClick={() => {
              setNotice(null);
              send({ type: 'offerDraw' });
            }}><b aria-hidden="true">½</b>Offer draw</button>
            <button type="button" className="icon labelled" disabled={!state.canResign} onClick={() => setConfirmResign(true)}><b aria-hidden="true">⚑</b>Resign</button>
            <button type="button" className="icon labelled" onClick={() => {
              setPgnError(null);
              setPgnOpen(true);
            }}><b aria-hidden="true">⎘</b>PGN</button>
          </div>
        </aside>
      </main>

      {dialogOpen && (
        <NewGameDialog
          initial={{ mode: dialogChampion ? 'engine' : config.mode, color: config.humanColor, level: config.level, blackLevel: config.blackLevel, autoFlip, champion: dialogChampion, time: timeKey(state.clock) }}
          onStart={(choice) => {
            showPage('game');
            startNewGame(choice);
          }}
          onCancel={() => setDialogOpen(false)}
        />
      )}

      {pgnOpen && (
        <PgnDialog
          pgn={state.pgn}
          error={pgnError}
          loading={pgnLoading}
          onLoad={(pgn) => {
            setPgnError(null);
            setPgnLoading(true);
            setFlipped(false);
            send({ type: 'loadPgn', pgn });
          }}
          onClose={() => setPgnOpen(false)}
        />
      )}
    </div>
  );
}

function PlayerCard({ state, color, fen, live, receivedAt }: { state: GameState; color: Color; fen: string; live: boolean; receivedAt: number }) {
  const { config } = state;
  const isEngine = config.mode === 'computer' || (config.mode === 'engine' && color !== config.humanColor);
  const level = config.mode === 'computer' && color === 'black' ? config.blackLevel : config.level;
  const name = isEngine ? (state.opponent && config.mode === 'engine' ? state.opponent.label : `Engine · Level ${level}`)
    : config.mode === 'engine' ? 'You' : colorName(color);
  const detail = isEngine ? LEVELS[level - 1].engine : `Plays ${colorName(color)}`;
  const taken = captured(fen)[color];
  const lead = materialOf(fen) * (color === 'white' ? 1 : -1);
  const toMove = live && !isOver(state) && state.turn === color;
  return (
    <div className={'player' + (toMove ? ' to-move' : '')} data-testid={`player-${color}`}>
      <div className="avatar" aria-hidden="true">{isEngine ? '⚙' : <PieceSvg code={color === 'white' ? 'wK' : 'bK'} />}</div>
      <div className="who">
        <div className="name">{name}</div>
        <div className="detail">{detail}</div>
      </div>
      <div className="taken" aria-label={`${colorName(color)} has taken`}>
        <span className="glyphs">{taken.map((p, i) => <PieceSvg key={i} code={codeOf(p)} />)}</span>
        {lead > 0 && <span className="lead">+{lead}</span>}
      </div>
      {state.clock && <ClockFace clock={state.clock} color={color} receivedAt={receivedAt} />}
    </div>
  );
}

function StatusLine({ state, connection, live, ply, premoves, onReturn }: {
  state: GameState;
  connection: string;
  live: boolean;
  ply: number;
  premoves: string[];
  onReturn: () => void;
}) {
  let text: string;
  let busy = false;
  let tone = '';
  if (connection !== 'open') {
    text = 'Lost the connection to the game. Reconnecting…';
    busy = true;
    tone = 'warn';
  } else if (!live) {
    const m = state.moves[ply - 1];
    text = ply === 0 ? 'Reviewing the start position' : `Reviewing ${m.number}${m.color === 'white' ? '.' : '…'} ${m.san}`;
  } else if (isOver(state)) {
    text = 'Game over';
  } else if (state.engineThinking) {
    const shown = premoves.map((u) => `${u.slice(0, 2)}–${u.slice(2, 4)}${u.length === 5 ? '=' + u[4].toUpperCase() : ''}`);
    text = shown.length ? `Engine is thinking… ${shown.length > 1 ? 'Premoves' : 'Premove'} ${shown.join(', ')}` : 'Engine is thinking…';
    busy = true;
  } else if (state.hintPending) {
    text = 'Looking for a hint…';
    busy = true;
  } else {
    const who = state.config.mode === 'engine' ? 'Your move' : `${colorName(state.turn)} to move`;
    text = state.status === 'CHECK' ? `${who} · Check!` : who;
    if (state.status === 'CHECK') tone = 'warn';
  }
  return (
    <div className={'status ' + tone} role="status" data-testid="status">
      {busy && <span className="spinner" aria-hidden="true" />}
      <span>{text}</span>
      {!live && <button type="button" className="link" onClick={onReturn}>Back to game</button>}
    </div>
  );
}

function MoveList({ state, ply, onPick }: { state: GameState; ply: number; onPick: (ply: number) => void }) {
  const list = useRef<HTMLOListElement>(null);
  const { moves } = state;
  useEffect(() => {
    list.current?.querySelector('.current')?.scrollIntoView({ block: 'nearest' });
  }, [ply, moves.length]);

  // rows of [white, black]; a game set up with Black to move would start with an empty White cell
  const rows: { number: number; cells: ({ san: string; ply: number } | null)[] }[] = [];
  moves.forEach((m, i) => {
    if (m.color === 'white' || rows.length === 0) rows.push({ number: m.number, cells: [null, null] });
    rows[rows.length - 1].cells[m.color === 'white' ? 0 : 1] = { san: m.san, ply: i + 1 };
  });

  return (
    <section className="moves" aria-label="Moves">
      {moves.length === 0 ? (
        <p className="empty">No moves yet. {state.humanTurn ? 'Click or drag a piece to start.' : ''}</p>
      ) : (
        <ol className="move-list" ref={list} data-testid="move-list">
          {rows.map((row) => (
            <li key={row.number}>
              <span className="num">{row.number}.</span>
              {row.cells.map((c, i) =>
                c ? (
                  <button key={i} type="button" className={'mv' + (c.ply === ply ? ' current' : '')} onClick={() => onPick(c.ply)}>
                    {c.san}
                  </button>
                ) : (
                  <span key={i} className="mv" />
                ),
              )}
            </li>
          ))}
          {state.result && <li className="final">{state.result}</li>}
        </ol>
      )}
    </section>
  );
}
