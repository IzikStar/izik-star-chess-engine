import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AnalysisPanel, EvalBar, gameKey, useAnalysis, useLiveEval } from './Analysis';
import { Board } from './Board';
import { ClockFace } from './Clock';
import { PgnDialog } from './PgnDialog';
import { Games } from './Games';
import { Lab } from './Lab';
import { Variants } from './Variants';
import { MoveList, RepeatButton } from './MoveList';
import { CHAMPION_LEVELS, maxLevelFor, NewGameDialog, type NewGameChoice } from './NewGameDialog';
import { SettingsDialog } from './SettingsDialog';
import { loadSettings, saveSettings, type Settings } from './settings';
import { artUrls, codeOf, PieceArt, PieceSvg } from './pieces';
import { captured, checksGiven, colorName, goalRule, pieceSetBase, engineText, LEVELS, MAX_LEVEL, isOver, checkedSquares, materialOf, movesByFrom, other, resultText, timeControlOf, timeKey, variantOf, VARIANTS, withPremoves } from './chess';
import { useGame, type Champion, type Color, type GameEvent, type GameState, type Weights } from './protocol';
import { play } from './sounds';
import { inventedReach } from './reach';
import { Icon } from './icons';
import { Confetti } from './Confetti';
import { Tour } from './Tour';

const EMPTY = new Map<string, string[]>();

type Page = 'game' | 'games' | 'variants' | 'lab';

function pageOf(): Page {
  const hash = location.hash.slice(1);
  if (hash === 'lab' || hash.startsWith('lab/')) return 'lab';
  return hash === 'games' || hash === 'variants' ? hash : 'game';
}

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
  /** The built-in engine's weights for the next game, remembered like the other settings. */
  const [weights, setWeights] = useState<Weights>(() => stored('weights', 'tuned') === 'classic' ? 'classic' : 'tuned');
  /** The ply being reviewed (0 = start position), or null for the live position. */
  const [view, setView] = useState<number | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  /** Hints and the evaluation bar (the Settings dialog). */
  const [settings, setSettings] = useState<Settings>(loadSettings);
  /** The champion the New game dialog opens with (from the lab's "Play the champion"). */
  const [dialogChampion, setDialogChampion] = useState<Champion | null>(null);
  /** The variant the New game dialog opens with (from the designer's "Play it"). */
  const [dialogVariant, setDialogVariant] = useState<string | null>(null);
  /** Which screen: the game, my games (#games), the variant designer (#variants), or the lab (#lab). */
  const [page, setPage] = useState<Page>(pageOf);
  /** Moves queued while the engine thinks (from + to [+ piece]), played one per turn, oldest first. */
  const [premoves, setPremoves] = useState<string[]>([]);
  /** The state a premove was last sent from, so the next one waits for the engine's reply. */
  const premoveSentFrom = useRef<GameState | null>(null);
  /** The premove just sent, shown on the board until the server's answer arrives (no flicker back). */
  const premoveSent = useRef<string | null>(null);
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
  /** Counts the wins worth celebrating; each new one replays the confetti. */
  const [party, setParty] = useState(0);
  const [touring, setTouring] = useState(false);

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
          // the result says who won: in antichess the side left without pieces is the winner
          const winner = state.result === '1-0' ? 'white' : state.result === '0-1' ? 'black' : null;
          const humanLost = state.config.mode === 'engine' && winner !== null && winner !== state.config.humanColor;
          play(winner ? (humanLost ? 'lose' : 'win') : 'draw', sound);
          // you beat the engine, or one of two friends won; engine against engine is no one's win
          if (winner && !humanLost && state.config.mode !== 'computer') setParty((n) => n + 1);
          break;
        }
        case 'ended': {
          const humanLost = state.config.mode === 'engine' && e.result !== '1/2-1/2' && e.side === state.config.humanColor;
          play(e.result === '1/2-1/2' ? 'draw' : humanLost ? 'lose' : 'win', sound);
          if (e.result !== '1/2-1/2' && !humanLost && state.config.mode !== 'computer') setParty((n) => n + 1);
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
          setParty(0);
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

  // a made variant's piece pictures (the designer saves them; the game state only names the variant)
  const customId = state?.variant?.custom ? state.variant.id : null;
  const [art, setArt] = useState<Record<string, string>>({});
  useEffect(() => {
    setArt({});
    if (!customId) return;
    let live = true;
    fetch(`/api/variants/${encodeURIComponent(customId)}`).then((r) => (r.ok ? r.json() : null))
      .then((v: { art?: Record<string, Record<string, number>> } | null) => { if (live && v) setArt(artUrls(customId, v.art)); })
      .catch(() => {});
    return () => { live = false; };
  }, [customId, page]);
  const { analysis, start: startAnalysis, clear: clearAnalysis } = useAnalysis();
  const currentKey = state ? gameKey(state.startFen, state.moves.map((m) => m.uci)) : '';
  const allUci = useMemo(() => state?.moves.map((m) => m.uci) ?? [], [state?.moves]);

  // an analysis stays while the game only grows past it; a take-back or a new game drops it
  useEffect(() => {
    if (analysis.status !== 'idle' && currentKey !== analysis.key && !currentKey.startsWith(analysis.key + ' ')) clearAnalysis();
  }, [currentKey, analysis, clearAnalysis]);

  useEffect(() => {
    const onHash = () => setPage(pageOf());
    window.addEventListener('hashchange', onHash);
    return () => window.removeEventListener('hashchange', onHash);
  }, []);
  const showPage = (p: Page) => {
    history.replaceState(null, '', p === 'game' ? location.pathname : '#' + p);
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
      premoveSent.current = uci;
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
  // one ply back or forward from wherever the view is now; a held button calls it many times, so
  // it reads the current view instead of a ply captured when the button was pressed
  const step = useCallback((delta: number) => setView((v) => {
    const p = (v ?? moves.length) + delta;
    return p >= moves.length ? null : Math.max(0, p);
  }), [moves.length]);
  const stepRef = useRef(step);
  stepRef.current = step;

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (dialogOpen || pgnOpen || settingsOpen || page !== 'game' || (e.target as HTMLElement).closest('input, select, textarea')) return;
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
  }, [goTo, ply, moves.length, dialogOpen, pgnOpen, settingsOpen, page]);

  const report = analysis.status === 'done' ? analysis.report : null;
  const reportEval = report && ply < report.evals.length ? report.evals[ply] : null;
  // Stockfish judges chess only, so the bar and the analysis are for chess games
  const isChess = !!state && variantOf(state) === 'chess';
  const barWanted = !!state && isChess && settings.evalBar[state.config.mode] && state.stockfish?.available !== false;
  // the analysis already knows the score of every position it covered; ask only for the rest
  const liveEval = useLiveEval(barWanted && !reportEval && page === 'game', state?.startFen ?? '', useMemo(() => allUci.slice(0, ply), [allUci, ply]));

  if (!state) {
    return (
      <div className="app loading">
        <p>{connection === 'lost' ? 'Waiting for the game server…' : 'Connecting…'}</p>
      </div>
    );
  }

  const { config } = state;
  const isDark = theme === 'dark' || (theme === 'system' && matchMedia('(prefers-color-scheme: dark)').matches);
  const fen = live ? state.fen : ply === 0 ? state.startFen : moves[ply - 1].fenAfter;
  const shownStatus = live ? state.status : ply === 0 ? 'IN_PROGRESS' : moves[ply - 1].status;
  const lastMove = ply === 0 ? null : moves[ply - 1].uci;
  const checkSquares = checkedSquares(live ? state.checked : ply === 0 ? [] : moves[ply - 1].checked, shownStatus, fen);
  const over = isOver(state);
  const shownEval = reportEval ?? liveEval.score;
  // the bar always shows with an analysis; otherwise as the settings say for this kind of game
  const showBar = !!report || (barWanted && !liveEval.unavailable);
  // what the engine would have played instead of the move just shown (as the analysis line says);
  // never in a live game still being played
  const shownMove = report && (!live || over) && ply > 0 && ply <= report.moves.length ? report.moves[ply - 1] : null;
  const bestArrow = shownMove && shownMove.quality !== 'best' ? shownMove.bestUci : null;
  const analyse = () => startAnalysis(state.startFen, moves.map((m) => m.uci));

  const baseOrientation: Color =
    config.mode === 'engine' ? config.humanColor : config.mode === 'friend' && autoFlip ? state.turn : 'white';
  const orientation = flipped ? other(baseOrientation) : baseOrientation;
  const premoveColor = live && config.mode === 'engine' && !over && !state.humanTurn ? config.humanColor : null;
  // the premoves as the board shows them: the queue, and the one on its way to the server. When
  // the engine replies they stay on the board until the server has played them, so a premoved
  // piece never jumps back to its square and forward again.
  const inFlight = premoveSentFrom.current === state && premoveSent.current ? [premoveSent.current] : [];
  const shownPremoves = live && config.mode === 'engine' && !over ? [...inFlight, ...premoves] : [];

  const startNewGame = (choice: NewGameChoice) => {
    setDialogOpen(false);
    setFlipped(false);
    setView(null);
    setPremoves([]);
    setAutoFlip(choice.autoFlip);
    store('autoFlip', choice.autoFlip ? 'on' : 'off');
    setWeights(choice.weights);
    store('weights', choice.weights);
    play('start', soundOn);
    const champion = choice.mode === 'engine' && choice.champion ? { run: choice.champion.run, generation: choice.champion.generation } : null;
    send({ type: 'newGame', mode: choice.mode, color: choice.color, level: choice.level, blackLevel: choice.blackLevel, champion, weights: choice.weights, time: timeControlOf(choice.time), variant: choice.variant });
  };

  const openDialog = (champion: Champion | null, variantId: string | null = null) => {
    setDialogChampion(champion);
    setDialogVariant(variantId);
    setDialogOpen(true);
  };

  // after a game against the engine: the next level up (a champion only plays the built-in engine's levels)
  const variant = variantOf(state);
  const topLevel = state.opponent ? CHAMPION_LEVELS.max : Math.min(MAX_LEVEL, maxLevelFor(variant));
  const nextLevel = config.mode === 'engine' && config.level < topLevel ? Math.max(config.level + 1, state.opponent ? CHAMPION_LEVELS.min : 0) : null;
  // a hint level the ladder no longer has falls back to the strongest
  const hintLevel = settings.hintLevel !== null && settings.hintLevel <= MAX_LEVEL ? settings.hintLevel : null;

  // a resignation, a loss on time or an agreed draw cannot be taken back (a mate can)
  const canUndo = live && moves.length > 0 && config.mode !== 'computer' && !state.end
    && !(config.mode === 'engine' && moves.length === 1 && config.humanColor === 'black');

  return (
    <PieceArt.Provider value={art}>
    <div className="app">
      <header className="topbar">
        <div className="brand"><span aria-hidden="true">♞</span> IzikStar Chess</div>
        <div className="tabs" role="group" aria-label="Screen">
          <button type="button" className={'btn ghost' + (page === 'game' ? ' on' : '')} aria-pressed={page === 'game'} onClick={() => showPage('game')}>Game</button>
          <button type="button" className={'btn ghost' + (page === 'games' ? ' on' : '')} aria-pressed={page === 'games'} data-tour="games" onClick={() => showPage('games')}>My games</button>
          <button type="button" className={'btn ghost' + (page === 'variants' ? ' on' : '')} aria-pressed={page === 'variants'} data-tour="variants" onClick={() => showPage('variants')}>Variants</button>
          <button type="button" className={'btn ghost' + (page === 'lab' ? ' on' : '')} aria-pressed={page === 'lab'} data-tour="lab" onClick={() => showPage('lab')}>Lab</button>
        </div>
        <div className="spacer" />
        <button type="button" className="btn ghost icon-btn" aria-label="Sound" aria-pressed={soundOn}
          title={soundOn ? 'Sound is on (click to mute)' : 'Sound is off (click to turn on)'} onClick={() => {
          setSoundOn(!soundOn);
          store('sound', soundOn ? 'off' : 'on');
        }}><Icon name={soundOn ? 'sound' : 'mute'} /></button>
        <button type="button" className="btn ghost icon-btn" aria-label="Dark mode" aria-pressed={isDark}
          title={isDark ? 'Switch to light' : 'Switch to dark'} onClick={() => {
          const next = isDark ? 'light' : 'dark';
          setTheme(next);
          store('theme', next);
        }}><Icon name={isDark ? 'sun' : 'moon'} /></button>
        <button type="button" className="btn ghost icon-btn" aria-label="Settings" title="Settings" data-tour="settings" onClick={() => setSettingsOpen(true)}><Icon name="gear" /><span className="wide-only">Settings</span></button>
        <button type="button" className="btn primary" data-tour="new-game" onClick={() => openDialog(state.opponent)}>New game</button>
      </header>

      <Tour open={touring} onOpen={() => setTouring(true)} onClose={() => setTouring(false)} />

      {page === 'variants' && <Variants onPlay={(id) => openDialog(null, id)} />}

      {page === 'lab' && <Lab onPlay={(champion) => openDialog(champion)} />}

      {page === 'games' && (
        <Games liveId={state.savedId ?? null} onShowLive={() => showPage('game')} onResume={(id) => {
          setFlipped(false);
          setView(null);
          setPremoves([]);
          play('start', soundOn);
          send({ type: 'resumeGame', id });
          showPage('game');
        }} />
      )}

      <main className="game" hidden={page !== 'game'}>
        <section className="board-area" aria-label="Board">
          <div className="board-wrap">
          {showBar && <EvalBar score={shownEval} orientation={orientation} />}
          {party > 0 && <Confetti key={party} />}
          <Board
            fen={shownPremoves.length ? withPremoves(fen, shownPremoves) : fen}
            orientation={orientation}
            legal={live ? legal : EMPTY}
            lastMove={lastMove}
            checkSquares={checkSquares}
            hint={bestArrow ?? (live && settings.hints ? state.hint : null)}
            onMove={(uci) => send({ type: 'move', uci })}
            reachOf={inventedReach(state.variant?.pieces, fen, state.startFen)}
            premoveColor={premoveColor}
            premoves={premoveColor ? premoves : []}
            onPremove={(uci) => {
              setPremoves((queued) => (uci ? [...queued, uci] : []));
              if (uci) play('select', soundOn);
            }}
            onSelect={() => play('select', soundOn)}
            onIllegal={() => play('invalid', soundOn)}
            animate={page === 'game'}
            badge={shownMove ? { square: shownMove.uci.slice(2, 4), quality: shownMove.quality } : null}
          />
          </div>
        </section>

        <aside className="side">
          <PlayerCard state={state} color={other(orientation)} fen={fen} live={live} receivedAt={receivedAt} place="top" />

          <StatusLine state={state} connection={connection} live={live} ply={ply} premoves={premoveColor ? premoves : []} onReturn={() => goTo(moves.length)} />

          {variant !== 'chess' && (
            <p className="variant-tag" data-testid="variant-tag">
              <strong>{state.variant?.name ?? variant}</strong> · {VARIANTS.find((v) => v.id === variant)?.rule ?? goalRule(state.variant?.goal ?? 'CHECKMATE', state.variant?.checksToWin)}
            </p>
          )}

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

          {over && (
            <section className={'result' + (party > 0 ? ' won' : '')} aria-label="Result" data-testid="result">
              <div className="score">{state.result === '1/2-1/2' ? '½ – ½' : state.result!.replace('-', ' – ')}</div>
              <div className="reason">{resultText(state.status, state.turn, state.end)}</div>
              <div className="row">
                <button type="button" className="btn primary" onClick={() => startNewGame({ mode: config.mode, color: config.humanColor, level: config.level, blackLevel: config.blackLevel, autoFlip, champion: state.opponent, weights, time: timeKey(state.clock), variant })}>Rematch</button>
                {nextLevel !== null && (
                  <button type="button" className="btn" data-testid="next-level" title={LEVELS[nextLevel].name}
                    onClick={() => startNewGame({ mode: 'engine', color: config.humanColor, level: nextLevel, blackLevel: nextLevel, autoFlip, champion: state.opponent, weights, time: timeKey(state.clock), variant })}>
                    Play Level {nextLevel}
                  </button>
                )}
                <button type="button" className="btn" onClick={() => goTo(0)}>Review game</button>
                {isChess && (
                  <button type="button" className="btn" disabled={analysis.status === 'running'} onClick={() => {
                    analyse();
                    goTo(0);
                  }}>Analyse game</button>
                )}
              </div>
            </section>
          )}

          <AnalysisPanel analysis={analysis} ply={ply} onPick={goTo} onRetry={analyse} />

          <MoveList moves={moves} result={state.result} ply={ply} onPick={goTo} qualities={report?.moves.map((m) => m.quality)}
            empty={`No moves yet. ${state.humanTurn ? 'Click or drag a piece to start.' : ''}`} />

          <div className="nav" role="group" aria-label="Review moves">
            <button type="button" className="icon" aria-label="First position" disabled={ply === 0} onClick={() => goTo(0)}><Icon name="first" /></button>
            <RepeatButton label="Previous move" disabled={ply === 0} onStep={() => stepRef.current(-1)}><Icon name="prev" /></RepeatButton>
            <RepeatButton label="Next move" disabled={live} onStep={() => stepRef.current(1)}><Icon name="next" /></RepeatButton>
            <button type="button" className="icon" aria-label="Latest move" disabled={live} onClick={() => goTo(moves.length)}><Icon name="last" /></button>
          </div>

          <PlayerCard state={state} color={orientation} fen={fen} live={live} receivedAt={receivedAt} place="bottom" />

          <div className="controls" role="group" aria-label="Game controls">
            <button type="button" className="icon labelled" disabled={!canUndo} onClick={() => {
              setPremoves([]);
              play('back', soundOn);
              send({ type: 'undo' });
            }}><b aria-hidden="true">↶</b>Take back</button>
            {settings.hints && (
              <button type="button" className="icon labelled" disabled={!live || !state.humanTurn || state.hintPending}
                title={hintLevel === null ? 'The strongest move' : `What Level ${hintLevel} would play`}
                onClick={() => send({ type: 'hint', level: hintLevel })}><b aria-hidden="true">✦</b>Hint</button>
            )}
            <button type="button" className="icon labelled" onClick={() => setFlipped(!flipped)}><b aria-hidden="true">⇅</b>Flip board</button>
            <button type="button" className="icon labelled" disabled={!isChess || moves.length === 0 || analysis.status === 'running'}
              title={isChess ? undefined : 'Stockfish analyses chess games only'} onClick={analyse}><b aria-hidden="true">≋</b>Analyse</button>
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
          stockfish={state.stockfish}
          initial={{ mode: dialogChampion ? 'engine' : config.mode, color: config.humanColor, level: config.level, blackLevel: config.blackLevel, autoFlip, champion: dialogChampion, weights, time: timeKey(state.clock),
            variant: dialogChampion ? dialogChampion.variant ?? 'chess' : dialogVariant ?? variant }}
          onStart={(choice) => {
            showPage('game');
            startNewGame(choice);
          }}
          onCancel={() => setDialogOpen(false)}
        />
      )}

      {settingsOpen && (
        <SettingsDialog
          settings={settings}
          weights={weights}
          stockfish={state.stockfish}
          onChange={(next) => {
            setSettings(next);
            saveSettings(next);
          }}
          onClose={() => setSettingsOpen(false)}
          onTour={() => {
            setSettingsOpen(false);
            setTouring(true);
          }}
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
    </PieceArt.Provider>
  );
}

function PlayerCard({ state, color, fen, live, receivedAt, place }: { state: GameState; color: Color; fen: string; live: boolean; receivedAt: number; place: 'top' | 'bottom' }) {
  const { config } = state;
  const isEngine = config.mode === 'computer' || (config.mode === 'engine' && color !== config.humanColor);
  const level = config.mode === 'computer' && color === 'black' ? config.blackLevel : config.level;
  // every card says which colour it is and who plays it: you, a friend, or which engine
  const who = isEngine ? (state.opponent && config.mode === 'engine' ? state.opponent.label : `Level ${level}`)
    : config.mode === 'engine' ? 'You' : 'Player';
  const name = `${colorName(color)} · ${who}`;
  const checks = checksGiven(fen, state.variant?.checksToWin);
  const detail = isEngine ? engineText(level, state.stockfish) : config.mode === 'engine' ? 'Human player' : 'Human player, same board';
  const base = pieceSetBase(state);
  const taken = captured(fen, base)[color];
  // in antichess (or any lose-everything game) being ahead in material is no lead at all
  const lead = state.variant?.goal === 'LOSE_EVERYTHING' ? 0 : Math.round(materialOf(fen, base?.values) * (color === 'white' ? 1 : -1));
  const toMove = live && !isOver(state) && state.turn === color;
  return (
    <div className={'player ' + place + (toMove ? ' to-move' : '')} data-testid={`player-${color}`}>
      <div className="avatar" aria-hidden="true">{isEngine ? '⚙' : <PieceSvg code={color === 'white' ? 'wK' : 'bK'} />}</div>
      <div className="who">
        <div className="name">{name}</div>
        <div className="detail">{detail}</div>
      </div>
      {checks && (
        <div className="checks" data-testid={`checks-${color}`} title="Checks given">
          ✚ {checks[color]}/{state.variant?.checksToWin ?? 3}
        </div>
      )}
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
