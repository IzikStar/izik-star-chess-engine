import { useCallback, useEffect, useMemo, useState } from 'react';
import { AnalysisPanel, EvalBar, useAnalysis } from './Analysis';
import { Board } from './Board';
import { colorName, formatClock, checkedSquares, LEVELS, levelElo, other, STOCKFISH_FROM_LEVEL, VARIANTS } from './chess';
import { MoveList, RepeatButton } from './MoveList';
import { Icon } from './icons';
import type { Color, MoveInfo, VariantId, Weights } from './protocol';

// "My games": every game played in the app, the engine's games against itself included, saved by
// the server after each move (web.GamesApi, game.GameArchive). Look one through with Stockfish's analysis, carry an unfinished one on, or
// take it away as PGN.

type Result = '1-0' | '0-1' | '1/2-1/2';

export interface GameSummary {
  id: string;
  started: string;
  updated: string;
  /** computer: the engine played itself ("Watch the engine"). */
  mode: 'engine' | 'friend' | 'computer';
  humanColor: Color;
  /** The engine's level; White's when it played itself. */
  level: number;
  /** Black's level when the engine played itself, else the same as level (absent from an older server). */
  blackLevel?: number;
  /** An evolved champion's name, when the engine played as one. */
  opponent: string | null;
  /** The built-in engine's weights ("classic" for games from before the choice). */
  weights: Weights;
  /** The game's variant ("chess" for games from before variants; absent from an older server). */
  variant?: VariantId;
  time: { initialMs: number; incrementMs: number } | null;
  plies: number;
  result: Result | null;
  termination: string | null;
}

interface GameDetail extends GameSummary {
  startFen: string;
  moves: MoveInfo[];
  /** Time left when the game was last saved. */
  clock: { white: number; black: number } | null;
  pgn: string;
}

type Outcome = 'won' | 'lost' | 'drawn' | 'unfinished' | 'played';

const EMPTY = new Map<string, string[]>();

/** The game from the player's side: won or lost against the engine; between two players or engine against engine only drawn, finished or not. */
export function outcomeOf(g: GameSummary): Outcome {
  if (g.result === null) return 'unfinished';
  if (g.result === '1/2-1/2') return 'drawn';
  if (g.mode !== 'engine') return 'played';
  return (g.result === '1-0') === (g.humanColor === 'white') ? 'won' : 'lost';
}

const OUTCOME_TEXT: Record<Outcome, string> = { won: 'Won', lost: 'Lost', drawn: 'Draw', unfinished: 'Unfinished', played: '' };

function opponentText(g: GameSummary): string {
  const variant = g.variant && g.variant !== 'chess' ? `${VARIANTS.find((v) => v.id === g.variant)?.name ?? g.variant} · ` : '';
  if (g.mode === 'friend') return variant + 'Two players';
  if (g.mode === 'computer') return variant + (g.opponent ? `${g.opponent}, both sides` : enginesText(g));
  return variant + (g.opponent ?? `Level ${g.level} · ${LEVELS[g.level]?.name ?? ''}${weightsSuffix(g.level, g.weights)}`);
}

/** The engine against itself: "Level 1 Beginner vs Level 3 Casual", White's level first. */
function enginesText(g: GameSummary): string {
  const black = g.blackLevel ?? g.level;
  const side = (l: number) => `Level ${l} ${LEVELS[l]?.name ?? ''}`.trim();
  const classic = weightsSuffix(g.level, g.weights) || weightsSuffix(black, g.weights);
  return `${side(g.level)} vs ${side(black)}${classic}`;
}

/** " (classic weights)" for a built-in level played with the classic weights; the tuned ones are the default. */
function weightsSuffix(level: number, weights: Weights): string {
  return weights === 'classic' && level > 0 && level < STOCKFISH_FROM_LEVEL ? ' (classic weights)' : '';
}

function timeText(t: GameSummary['time']): string {
  return t ? `${t.initialMs / 60_000}+${t.incrementMs / 1000}` : 'Untimed';
}

function dateText(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function resultCell(g: GameSummary): string {
  const outcome = outcomeOf(g);
  if (outcome === 'unfinished') return 'Unfinished';
  const score = g.result === '1/2-1/2' ? '½–½' : g.result!.replace('-', '–');
  return outcome === 'played' ? score : `${OUTCOME_TEXT[outcome]} · ${score}`;
}

async function getJson<T>(url: string): Promise<T> {
  const r = await fetch(url);
  if (!r.ok) throw new Error(`${r.status} ${await r.text()}`);
  return r.json() as Promise<T>;
}

/**
 * @param liveId the saved game now on the game screen, if any
 * @param onResume carry this unfinished game on (on the game screen)
 * @param onShowLive go back to the game screen (the game picked is the one being played)
 */
export function Games({ liveId, onResume, onShowLive }: { liveId: string | null; onResume: (id: string) => void; onShowLive: () => void }) {
  const [data, setData] = useState<{ folder: string; games: GameSummary[] } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState<string | null>(null);
  const [level, setLevel] = useState('all');
  const [outcome, setOutcome] = useState('all');

  const load = useCallback(() => {
    getJson<{ folder: string; games: GameSummary[] }>('/api/games').then((d) => {
      setData(d);
      setError(null);
    }, (e) => setError(String(e)));
  }, []);
  useEffect(load, [load]);

  const games = data?.games ?? [];
  const levels = useMemo(() => [...new Set(games.filter((g) => g.mode === 'engine').map((g) => g.level))].sort((a, b) => a - b), [games]);
  const shown = games.filter((g) =>
    (level === 'all' || (level === 'friend' || level === 'computer' ? g.mode === level : g.mode === 'engine' && g.level === Number(level)))
    && (outcome === 'all' || outcomeOf(g) === outcome));

  if (open) {
    return <GameReview id={open} live={open === liveId} onBack={() => {
      setOpen(null);
      load();
    }} onResume={onResume} onShowLive={onShowLive} />;
  }

  return (
    <main className="my-games">
      {error && <p className="muted">Could not load your games: {error}</p>}
      {!data && !error && <p className="muted loading-line"><span className="spinner" aria-hidden="true" />Loading your games…</p>}
      {data && games.length === 0 && (
        <section className="panel" data-testid="games-empty">
          <h2>No games yet</h2>
          <p>Every game you play, or watch the engine play, is saved here on its own, after each move, finished or not.</p>
          <p className="muted small">They are kept in <code>{data.folder}</code>.</p>
          <button type="button" className="btn primary empty-cta" onClick={onShowLive}>Go to the board and play</button>
        </section>
      )}
      {games.length > 0 && (
        <>
          <section className="panel">
            <div className="run-head">
              <h2>My games</h2>
              <div className="filters">
                <label className="pick">
                  <span className="muted">Opponent</span>
                  <select aria-label="Opponent" value={level} onChange={(e) => setLevel(e.target.value)}>
                    <option value="all">All</option>
                    {levels.map((l) => <option key={l} value={l}>Level {l}</option>)}
                    {games.some((g) => g.mode === 'friend') && <option value="friend">Two players</option>}
                    {games.some((g) => g.mode === 'computer') && <option value="computer">Engine vs engine</option>}
                  </select>
                </label>
                <label className="pick">
                  <span className="muted">Result</span>
                  <select aria-label="Result" value={outcome} onChange={(e) => setOutcome(e.target.value)}>
                    <option value="all">All</option>
                    <option value="won">Won</option>
                    <option value="lost">Lost</option>
                    <option value="drawn">Draw</option>
                    <option value="unfinished">Unfinished</option>
                  </select>
                </label>
              </div>
            </div>
            <div className="table-wrap">
              <table className="game-table" data-testid="saved-games">
                <thead>
                  <tr><th>Date</th><th>Opponent</th><th>You</th><th>Time</th><th>Moves</th><th>Result</th></tr>
                </thead>
                <tbody>
                  {shown.map((g) => (
                    <tr key={g.id} className={'o-' + outcomeOf(g)} onClick={() => setOpen(g.id)} tabIndex={0}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') setOpen(g.id);
                      }}>
                      <td>{dateText(g.started)}{g.id === liveId && <span className="tag">Playing</span>}</td>
                      <td>{opponentText(g)}</td>
                      <td>{g.mode === 'engine' ? colorName(g.humanColor) : '–'}</td>
                      <td>{timeText(g.time)}</td>
                      <td>{Math.ceil(g.plies / 2)}</td>
                      <td className="res" title={g.termination ?? ''}>{resultCell(g)}</td>
                    </tr>
                  ))}
                  {shown.length === 0 && <tr><td colSpan={6} className="muted">No game matches.</td></tr>}
                </tbody>
              </table>
            </div>
          </section>
          <ByLevel games={games} />
        </>
      )}
    </main>
  );
}

/**
 * Wins, draws and losses against each level, and a performance rating from them: the level's Elo
 * plus 400 × log10(score / (1 − score)), the usual estimate from a score against one opponent.
 */
function ByLevel({ games }: { games: GameSummary[] }) {
  // a built-in level is another opponent with each set of weights; Stockfish's levels are the same with both
  const rows = new Map<string, { level: number; weights: Weights; won: number; drawn: number; lost: number }>();
  for (const g of games) {
    const o = outcomeOf(g);
    // the levels' Elo is a chess Elo: a variant game says nothing about it
    if (g.mode !== 'engine' || g.opponent || (g.variant ?? 'chess') !== 'chess' || (o !== 'won' && o !== 'drawn' && o !== 'lost')) continue;
    const weights: Weights = g.level >= STOCKFISH_FROM_LEVEL ? 'tuned' : g.weights;
    const key = `${g.level}:${weights}`;
    const r = rows.get(key) ?? { level: g.level, weights, won: 0, drawn: 0, lost: 0 };
    r[o]++;
    rows.set(key, r);
  }
  if (rows.size === 0) return null;
  return (
    <section className="panel">
      <h3>Against each level</h3>
      <div className="table-wrap">
        <table className="game-table" data-testid="by-level">
          <thead>
            <tr><th>Level</th><th>Games</th><th>Won</th><th>Draw</th><th>Lost</th><th>Score</th><th title="The level's Elo + 400 × log10(score / (1 − score))">Your rating there</th></tr>
          </thead>
          <tbody>
            {[...rows.entries()].sort(([, a], [, b]) => a.level - b.level || a.weights.localeCompare(b.weights)).map(([key, r]) => {
              const level = r.level;
              const n = r.won + r.drawn + r.lost;
              const score = (r.won + r.drawn / 2) / n;
              const elo = LEVELS[level] ? levelElo(level, r.weights) : null;
              const perf = elo == null ? '–'
                : score === 0 ? `below ${elo - 400}`
                : score === 1 ? `above ${elo + 400}`
                : `≈ ${Math.round(elo + 400 * Math.log10(score / (1 - score)))}`;
              return (
                <tr key={key}>
                  <td>Level {level} · {LEVELS[level]?.name}{weightsSuffix(level, r.weights)}</td>
                  <td>{n}</td><td>{r.won}</td><td>{r.drawn}</td><td>{r.lost}</td>
                  <td>{Math.round(score * 100)}%</td>
                  <td>{perf}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <p className="muted small">A rough guide: a few games against a level say little.</p>
    </section>
  );
}

function GameReview({ id, live, onBack, onResume, onShowLive }: {
  id: string;
  live: boolean;
  onBack: () => void;
  onResume: (id: string) => void;
  onShowLive: () => void;
}) {
  const [game, setGame] = useState<GameDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [ply, setPly] = useState(0);
  const [flipped, setFlipped] = useState(false);
  const [copied, setCopied] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const { analysis, start } = useAnalysis();

  useEffect(() => {
    let current = true;
    getJson<GameDetail>(`/api/games/${encodeURIComponent(id)}`).then((g) => {
      if (!current) return;
      setGame(g);
      setPly(g.moves.length);
    }, (e) => current && setError(String(e)));
    return () => {
      current = false;
    };
  }, [id]);

  const count = game?.moves.length ?? 0;
  const goTo = useCallback((p: number) => setPly(Math.max(0, Math.min(count, p))), [count]);
  const step = useCallback((d: number) => setPly((p) => Math.max(0, Math.min(count, p + d))), [count]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.target as HTMLElement).closest('input, select, textarea')) return;
      if (e.key === 'ArrowLeft') step(-1);
      else if (e.key === 'ArrowRight') step(1);
      else if (e.key === 'Home') goTo(0);
      else if (e.key === 'End') goTo(count);
      else if (e.key === 'f') setFlipped((f) => !f);
      else return;
      e.preventDefault();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [step, goTo, count]);

  if (error) return <main className="my-games"><p className="muted">Could not load the game: {error}</p><button type="button" className="btn" onClick={onBack}>Back to my games</button></main>;
  if (!game) return <main className="my-games"><p className="muted">Loading the game…</p></main>;

  const { moves } = game;
  const fen = ply === 0 ? game.startFen : moves[ply - 1].fenAfter;
  const status = ply === 0 ? 'IN_PROGRESS' : moves[ply - 1].status;
  const checkSquares = checkedSquares(ply === 0 ? [] : moves[ply - 1].checked, status, fen);
  const base: Color = game.mode === 'engine' ? game.humanColor : 'white';
  const orientation = flipped ? other(base) : base;
  const report = analysis.status === 'done' ? analysis.report : null;
  const shownMove = report && ply > 0 && ply <= report.moves.length ? report.moves[ply - 1] : null;
  const analyse = () => start(game.startFen, moves.map((m) => m.uci));
  const outcome = outcomeOf(game);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(game.pgn);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };
  const download = () => {
    const url = URL.createObjectURL(new Blob([game.pgn], { type: 'application/x-chess-pgn' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = `izikstar-${game.id}.pgn`;
    a.click();
    URL.revokeObjectURL(url);
  };
  const remove = async () => {
    await fetch(`/api/games/${encodeURIComponent(game.id)}`, { method: 'DELETE' });
    onBack();
  };

  return (
    <main className="game" data-testid="game-review">
      <section className="board-area" aria-label="Board">
        <div className="board-wrap">
          {report && <EvalBar score={ply < report.evals.length ? report.evals[ply] : null} orientation={orientation} />}
          <Board fen={fen} orientation={orientation} legal={EMPTY} lastMove={ply === 0 ? null : moves[ply - 1].uci}
            checkSquares={checkSquares} hint={shownMove && shownMove.quality !== 'best' ? shownMove.bestUci : null}
            onMove={() => {}} onSelect={() => {}} onIllegal={() => {}} premoveColor={null} premoves={[]} onPremove={() => {}}
            badge={shownMove ? { square: shownMove.uci.slice(2, 4), quality: shownMove.quality } : null} />
        </div>
      </section>

      <aside className="side">
        <div className="row start">
          <button type="button" className="btn ghost" onClick={onBack}>← My games</button>
        </div>
        <section className={'result' + (outcome === 'unfinished' ? ' unfinished' : '')} aria-label="Saved game" data-testid="review-head">
          <div className="reason">{opponentText(game)}{game.mode === 'engine' && ` · you played ${colorName(game.humanColor)}`}</div>
          <div className="score">{game.result === null ? 'Unfinished' : game.result === '1/2-1/2' ? '½ – ½' : game.result.replace('-', ' – ')}</div>
          <div className="muted small">
            {dateText(game.started)} · {timeText(game.time)}
            {game.termination && <> · {game.termination}</>}
            {game.result === null && game.clock && <> · time left: White {formatClock(game.clock.white)}, Black {formatClock(game.clock.black)}</>}
          </div>
          <div className="row">
            {game.result === null && (live
              ? <button type="button" className="btn primary" onClick={onShowLive}>Back to this game</button>
              : <button type="button" className="btn primary" onClick={() => onResume(game.id)}>Continue this game</button>)}
            {(game.variant ?? 'chess') === 'chess' && (
              <button type="button" className="btn" disabled={moves.length === 0 || analysis.status === 'running'} onClick={analyse}>Analyse game</button>
            )}
          </div>
        </section>

        <AnalysisPanel analysis={analysis} ply={ply} onPick={goTo} onRetry={analyse} />

        <MoveList moves={moves} result={game.result} ply={ply} onPick={goTo} qualities={report?.moves.map((m) => m.quality)} empty="No moves." />

        <div className="nav" role="group" aria-label="Review moves">
          <button type="button" className="icon" aria-label="First position" disabled={ply === 0} onClick={() => goTo(0)}><Icon name="first" /></button>
          <RepeatButton label="Previous move" disabled={ply === 0} onStep={() => step(-1)}><Icon name="prev" /></RepeatButton>
          <RepeatButton label="Next move" disabled={ply === count} onStep={() => step(1)}><Icon name="next" /></RepeatButton>
          <button type="button" className="icon" aria-label="Last move" disabled={ply === count} onClick={() => goTo(count)}><Icon name="last" /></button>
        </div>

        <div className="controls" role="group" aria-label="Saved game">
          <button type="button" className="icon labelled" onClick={() => setFlipped(!flipped)}><b aria-hidden="true">⇅</b>Flip board</button>
          <button type="button" className="icon labelled" onClick={copy}><b aria-hidden="true">⎘</b>{copied ? 'Copied' : 'Copy PGN'}</button>
          <button type="button" className="icon labelled" onClick={download}><b aria-hidden="true">⤓</b>Download PGN</button>
        </div>

        {confirmDelete ? (
          <section className="ask warn" aria-label="Delete game">
            <span><strong>Delete this game?</strong> It cannot be brought back.</span>
            <div className="row">
              <button type="button" className="btn danger" onClick={remove}>Delete</button>
              <button type="button" className="btn" onClick={() => setConfirmDelete(false)}>Keep it</button>
            </div>
          </section>
        ) : (
          <div className="row end">
            <button type="button" className="btn ghost" disabled={live} title={live ? 'This game is being played' : ''} onClick={() => setConfirmDelete(true)}>Delete game</button>
          </div>
        )}
      </aside>
    </main>
  );
}
