import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent, type RefObject } from 'react';
import type { VariantDef } from '../Variants';
import './health.css';

// The variant health check (web.HealthApi, lab.VariantHealth): the engine plays the variant against
// itself and the games say whether it is fun to play. Settings (the essentials, and more under
// Advanced), progress, and the report: what happened, in numbers, small charts and plain words.

// ---- what the server sends ----------------------------------------------------------------------

interface Interval { value: number; low: number; high: number }
interface Spread { count: number; mean: number; min: number; p10: number; median: number; p90: number; max: number }
interface Ending { reason: string; count: number; share: number; whiteWins: number; blackWins: number; draws: number }
interface LengthBucket { from: number; to: number; all: number; decisive: number; drawn: number }
interface PlyBucket { from: number; to: number; games: number; legalMoves: number; whiteMaterial: number; blackMaterial: number; pieces: number }
interface PieceStats {
  letter: string; name: string; value: number; royal: boolean; start: number; end: number; onBoard: number;
  moves: number; moveShare: number; captures: number; captured: number; promotions: number; survival: number;
}
interface SampleGame { label: string; index: number; result: string; ending: string; plies: number; uci: string[]; san: string[] }
interface Details {
  results: { whiteWins: Interval; blackWins: Interval; draws: Interval; whiteScore: Interval; firstMover: 'white' | 'black'; firstMoverWins: number; firstMoverShare: number };
  endings: Ending[];
  lengths: { all: Spread; decisive: Spread; drawn: Spread; histogram: LengthBucket[] };
  branching: { mean: number; white: number; black: number; positions: Spread; byPly: PlyBucket[] };
  captures: { perGame: Spread; firstCapturePly: Spread; gamesWithoutCapture: number };
  material: { whiteStart: number; blackStart: number; white: number; black: number; whiteEnd: number; blackEnd: number; piecesStart: number; pieces: number; piecesEnd: number };
  pieces: PieceStats[];
  checks: { white: number; black: number; whitePerGame: number; blackPerGame: number; whiteGames: number; blackGames: number };
  samples: SampleGame[];
}
interface Report {
  games: number; whiteWins: number; blackWins: number; draws: number; averagePlies: number; shortest: number; longest: number;
  movesPerTurn: number; whiteScore: number; decisiveShare: number; endings: Record<string, number>; notes: string[];
  details?: Details;
}
interface RunSettings {
  games: number; whiteDepth: number; blackDepth: number; randomPlies: number; maxPlies: number; seed: number;
  threads: number; variety: number; engine: 'built-in' | 'fairy-stockfish'; fairyNodes: number;
}
interface HealthState {
  running: boolean; cancelled?: boolean; variantId?: string; variantName?: string; games?: number; depth?: number;
  done?: number; report?: Report; error?: string; settings?: RunSettings; fairyInstalled?: boolean; cores?: number;
}

async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, { ...init, headers: { 'Content-Type': 'application/json' } });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(body.error ?? `${res.status} ${res.statusText}`);
  return body as T;
}

/** The width of an element, followed as it changes, so a chart draws at its real size. */
function useWidth<T extends HTMLElement>(): [RefObject<T | null>, number] {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState(560);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const measure = () => setWidth(Math.max(240, Math.round(el.getBoundingClientRect().width)));
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, []);
  return [ref, width];
}

// ---- words and numbers --------------------------------------------------------------------------

const ENDINGS: Record<string, string> = {
  CHECKMATE: 'Checkmate',
  STALEMATE: 'Stalemate',
  DRAW_FIFTY_MOVE: '50-move rule',
  DRAW_THREEFOLD: 'Repetition',
  DRAW_INSUFFICIENT_MATERIAL: 'Not enough material',
  HILL_REACHED: 'King reached the centre',
  CHECKS_GIVEN: 'Checks given',
  NO_PIECES_LEFT: 'Lost every piece',
  NO_MOVES_LEFT: 'No move left',
  PLY_CAP: 'Still going at the move limit',
};

const pc = (x: number) => `${Math.round(100 * x)}%`;
const one = (x: number) => (Math.round(x * 10) / 10).toString();
/** Plies as moves (a move is White's and Black's turn). */
const moves = (plies: number) => Math.round(plies / 2);
const plusMinus = (i: Interval) => Math.round(100 * Math.max(i.high - i.value, i.value - i.low));
const RESULT: Record<string, string> = { '1-0': 'White won', '0-1': 'Black won', '1/2-1/2': 'Draw' };

/** One-line readings of the numbers, the most important first. */
function readings(r: Report, d: Details): string[] {
  const out: string[] = [];
  const s = d.results.whiteScore;
  const side = s.value >= 0.5 ? 'White' : 'Black';
  const score = s.value >= 0.5 ? s : { value: 1 - s.value, low: 1 - s.high, high: 1 - s.low };
  const edge = s.low > 0.5 || s.high < 0.5
    ? (score.value >= 0.6 ? `${side} has a clear edge` : `${side} has a slight edge`)
    : 'neither side has a clear edge';
  out.push(`${side} scores ${pc(score.value)} ± ${plusMinus(s)}%: ${edge}.`);
  const draws = d.results.draws.value;
  out.push(draws >= 0.7 ? `${pc(draws)} of the games are drawn: they rarely get decided.`
    : draws <= 0.1 ? `Only ${pc(draws)} draws: nearly every game has a winner.`
      : `${pc(draws)} draws, ${pc(1 - draws)} decisive.`);
  const top = d.endings[0];
  if (top) out.push(`Most games end by ${(ENDINGS[top.reason] ?? top.reason).toLowerCase()} (${pc(top.share)}).`);
  const len = d.lengths.all;
  out.push(`A typical game lasts ${moves(len.median)} moves; most last ${moves(len.p10)} to ${moves(len.p90)}.`
    + (len.median < 20 ? ' That is short.' : len.median > 200 ? ' That is long.' : ''));
  const b = d.branching.mean;
  out.push(`${one(b)} moves to choose from per turn`
    + (b < 8 ? ': few choices.' : b > 60 ? ': a lot to consider.' : Math.abs(b - 30) < 10 ? ', about as many as in chess.' : '.'));
  const fc = d.captures.firstCapturePly;
  if (fc.count) out.push(`The first capture comes at move ${moves(fc.median) || 1} in a typical game, ${one(d.captures.perGame.mean)} captures a game.`);
  const idle = d.pieces.filter((p) => !p.royal && p.start > 0 && p.moves < 0.5);
  if (idle.length) out.push(`${idle.map((p) => p.name).join(', ')} hardly ${idle.length > 1 ? 'move' : 'moves'} (under one move in two games).`);
  if (r.games < 50) out.push(`With ${r.games} games the margins are wide; more games narrow them.`);
  return out;
}

// ---- the page -----------------------------------------------------------------------------------

/** Self-play of the engine: is the variant balanced, decisive, long enough, rich in choices? */
export function HealthPage({ variant }: { variant: VariantDef }) {
  const [games, setGames] = useState(100);
  const [depth, setDepth] = useState(2);
  const [whiteDepth, setWhiteDepth] = useState(0); // 0: the depth above
  const [blackDepth, setBlackDepth] = useState(0);
  const [randomPlies, setRandomPlies] = useState(4);
  const [maxPlies, setMaxPlies] = useState(300);
  const [variety, setVariety] = useState(20);
  const [seed, setSeed] = useState(2026);
  const [threads, setThreads] = useState(0);
  const [engine, setEngine] = useState<'built-in' | 'fairy-stockfish'>('built-in');
  const [fairyNodes, setFairyNodes] = useState(20000);
  const [state, setState] = useState<HealthState | null>(null);
  const [error, setError] = useState<string | null>(null);

  const poll = useCallback(() => api<HealthState>('/api/health').then(setState).catch(() => {}), []);
  useEffect(() => { poll(); }, [poll]);
  useEffect(() => {
    if (!state?.running) return;
    const t = setTimeout(poll, 500);
    return () => clearTimeout(t);
  }, [state, poll]);

  const start = () => {
    setError(null);
    const body = {
      variant, games, depth, whiteDepth: whiteDepth || depth, blackDepth: blackDepth || depth, randomPlies, maxPlies,
      variety, seed, threads, engine, fairyNodes,
    };
    api<HealthState>('/api/health', { method: 'POST', body: JSON.stringify(body) })
      .then(setState)
      .catch((e: Error) => setError(e.message));
  };

  const mine = state && state.variantId === variant.id ? state : null;
  const r = mine?.report;
  const builtIn = engine === 'built-in';
  const num = (set: (n: number) => void) => (e: ChangeEvent<HTMLInputElement | HTMLSelectElement>) => set(Number(e.target.value));
  return (
    <section className="panel health hc" aria-label="Health check" data-testid="health">
      <div className="run-head">
        <h3>Health check</h3>
        <div className="filters">
          <label className="inline">Games
            <select value={games} onChange={num(setGames)} aria-label="Games">
              {[20, 50, 100, 200, 500, 1000].map((n) => <option key={n} value={n}>{n}</option>)}
            </select>
          </label>
          <label className="inline">Depth
            <select value={depth} onChange={num(setDepth)} aria-label="Depth" disabled={!builtIn}>
              {[1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{n}</option>)}
            </select>
          </label>
          {mine?.running
            ? <button type="button" className="btn" onClick={() => api<HealthState>('/api/health', { method: 'DELETE' }).then(setState)}>Stop</button>
            : <button type="button" className="btn primary" onClick={start}>Run</button>}
        </div>
      </div>
      <p className="muted small">The engine plays this variant against itself (a few random moves first, so the games differ) and the games show whether it is fun to play: balanced, decisive, not over at once, with moves to choose from. Unsaved changes are checked too. Depth 4 and 5 are slow.</p>
      <details className="hc-advanced" data-testid="health-advanced">
        <summary>Advanced</summary>
        <div className="hc-grid">
          <label>White's depth
            <select value={whiteDepth} onChange={num(setWhiteDepth)} disabled={!builtIn}>
              <option value={0}>Same ({depth})</option>
              {[1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{n}</option>)}
            </select>
          </label>
          <label>Black's depth
            <select value={blackDepth} onChange={num(setBlackDepth)} disabled={!builtIn}>
              <option value={0}>Same ({depth})</option>
              {[1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{n}</option>)}
            </select>
          </label>
          <label>Random opening plies
            <input type="number" min={0} max={40} value={randomPlies} onChange={num(setRandomPlies)} />
          </label>
          <label>Move limit (plies)
            <input type="number" min={10} max={2000} step={10} value={maxPlies} onChange={num(setMaxPlies)} />
          </label>
          <label>Variety (centipawns)
            <input type="number" min={0} max={500} step={5} value={variety} onChange={num(setVariety)} disabled={!builtIn} />
          </label>
          <label>Seed
            <input type="number" value={seed} onChange={num(setSeed)} />
          </label>
          <label>Threads
            <select value={threads} onChange={num(setThreads)}>
              <option value={0}>Automatic</option>
              {Array.from({ length: Math.max(1, state?.cores ?? 4) }, (_, i) => i + 1).map((n) => <option key={n} value={n}>{n}</option>)}
            </select>
          </label>
          <label>Engine
            <select value={engine} onChange={(e) => setEngine(e.target.value as typeof engine)}>
              <option value="built-in">Built-in</option>
              <option value="fairy-stockfish" disabled={!state?.fairyInstalled}>Fairy-Stockfish{state?.fairyInstalled ? '' : ' (not installed)'}</option>
            </select>
          </label>
          {!builtIn && (
            <label>Fairy-Stockfish nodes a move
              <input type="number" min={100} max={5000000} step={1000} value={fairyNodes} onChange={num(setFairyNodes)} />
            </label>
          )}
        </div>
        <p className="muted small">The first plies of each game are random so the games differ. A game still going at the move limit counts as a draw. Variety lets the engine pick at random among moves this close to its best (100 = a pawn); 0 always plays the best. The same seed and settings give the same games.</p>
      </details>
      {error && <p className="error" role="alert">{error}</p>}
      {state?.running && !mine && <p className="muted small">A check of {state.variantName} is running; Run stops it.</p>}
      {mine?.running && (
        <div className="progress hc-progress" role="progressbar" aria-valuemin={0} aria-valuemax={mine.games} aria-valuenow={mine.done}>
          <div style={{ width: pc((mine.done ?? 0) / (mine.games || 1)) }} />
          <span>{mine.done} of {mine.games} games</span>
        </div>
      )}
      {mine?.error && <p className="error">{mine.error}</p>}
      {r && <HealthReport r={r} settings={mine?.settings} depth={mine?.depth} />}
    </section>
  );
}

function HealthReport({ r, settings, depth }: { r: Report; settings?: RunSettings; depth?: number }) {
  const d = r.details;
  const said = useMemo(() => (d ? readings(r, d) : []), [r, d]);
  const who = settings
    ? (settings.engine === 'fairy-stockfish' ? `Fairy-Stockfish, ${settings.fairyNodes.toLocaleString()} nodes a move`
      : settings.whiteDepth === settings.blackDepth ? `depth ${settings.whiteDepth}` : `White depth ${settings.whiteDepth}, Black depth ${settings.blackDepth}`)
    : `depth ${depth}`;
  return (
    <div className="health-report hc-report" data-testid="health-report">
      <p className="muted small">{r.games} games, {who}{settings ? `, ${settings.randomPlies} random plies, limit ${settings.maxPlies} plies, seed ${settings.seed}` : ''}.</p>
      {d && (
        <section className="hc-section" data-testid="health-summary">
          <h4>In short</h4>
          <ul className="hc-readings">{said.map((s) => <li key={s}>{s}</li>)}</ul>
        </section>
      )}
      {r.notes.length
        ? <ul className="notes">{r.notes.map((n) => <li key={n}>{n}</li>)}</ul>
        : <p className="ok">Nothing stands out: balanced, decisive enough, and with choices.</p>}
      {d ? <DetailSections r={r} d={d} /> : <OldSummary r={r} />}
    </div>
  );
}

/** A report without details (an older server): the summary table. */
function OldSummary({ r }: { r: Report }) {
  return (
    <table>
      <tbody>
        <tr><th>White wins</th><td>{r.whiteWins}</td><th>Black wins</th><td>{r.blackWins}</td><th>Draws</th><td>{r.draws}</td></tr>
      </tbody>
    </table>
  );
}

function DetailSections({ r, d }: { r: Report; d: Details }) {
  const first = d.results.firstMover === 'white' ? 'White' : 'Black';
  const decisive = r.whiteWins + r.blackWins;
  return (
    <>
      <section className="hc-section" data-testid="health-results">
        <h4>Results by colour</h4>
        <ResultsChart d={d} r={r} />
        <p className="small muted">{first} moves first and won {d.results.firstMoverWins} of the {decisive} decisive games{decisive ? ` (${pc(d.results.firstMoverShare)})` : ''}. White's score (a draw is half a point): {pc(d.results.whiteScore.value)}, 95% interval {pc(d.results.whiteScore.low)} to {pc(d.results.whiteScore.high)}.</p>
      </section>

      <section className="hc-section" data-testid="health-endings">
        <h4>How games ended</h4>
        <div className="table-wrap">
          <table className="hc-table">
            <thead><tr><th>Ending</th><th className="n">Games</th><th className="n">Share</th><th className="bar-cell" aria-hidden="true" /><th className="n">White won</th><th className="n">Black won</th><th className="n">Drawn</th></tr></thead>
            <tbody>
              {d.endings.map((e) => (
                <tr key={e.reason}>
                  <td>{ENDINGS[e.reason] ?? e.reason}</td>
                  <td className="n">{e.count}</td>
                  <td className="n">{pc(e.share)}</td>
                  <td className="bar-cell"><span className="hc-bar" style={{ width: pc(e.share) }} /></td>
                  <td className="n">{e.whiteWins}</td>
                  <td className="n">{e.blackWins}</td>
                  <td className="n">{e.draws}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="hc-section" data-testid="health-lengths">
        <h4>Game length</h4>
        <LengthChart buckets={d.lengths.histogram} />
        <div className="table-wrap">
          <table className="hc-table">
            <thead><tr><th>Plies</th><th className="n">Games</th><th className="n">Median</th><th className="n">10% to 90%</th><th className="n">Mean</th><th className="n">Shortest to longest</th></tr></thead>
            <tbody>
              {([['All games', d.lengths.all], ['Decisive', d.lengths.decisive], ['Drawn', d.lengths.drawn]] as const).map(([name, s]) => (
                <tr key={name}>
                  <td>{name}</td><td className="n">{s.count}</td>
                  <td className="n">{s.count ? s.median : '–'}</td>
                  <td className="n">{s.count ? `${s.p10} to ${s.p90}` : '–'}</td>
                  <td className="n">{s.count ? one(s.mean) : '–'}</td>
                  <td className="n">{s.count ? `${s.min} to ${s.max}` : '–'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="small muted">A ply is one side's turn; two plies make a move.</p>
      </section>

      <section className="hc-section" data-testid="health-branching">
        <h4>Choices per turn</h4>
        <PlyChart buckets={d.branching.byPly} value={(b) => b.legalMoves} label="legal moves" />
        <p className="small muted">{one(d.branching.mean)} legal moves per turn on average (White {one(d.branching.white)}, Black {one(d.branching.black)}); half the positions had {d.branching.positions.median} or fewer, from {d.branching.positions.min} to {d.branching.positions.max}.</p>
      </section>

      <section className="hc-section hc-two" data-testid="health-play">
        <div>
          <h4>Captures and checks</h4>
          <table className="hc-table">
            <tbody>
              <tr><th>Captures a game</th><td className="n">{one(d.captures.perGame.mean)} (median {d.captures.perGame.median}, {d.captures.perGame.min} to {d.captures.perGame.max})</td></tr>
              <tr><th>First capture</th><td className="n">{d.captures.firstCapturePly.count ? `ply ${d.captures.firstCapturePly.median} (median), ${d.captures.firstCapturePly.p10} to ${d.captures.firstCapturePly.p90}` : 'never'}</td></tr>
              <tr><th>Games without a capture</th><td className="n">{d.captures.gamesWithoutCapture}</td></tr>
              <tr><th>White in check</th><td className="n">{one(d.checks.whitePerGame)} a game, in {pc(d.checks.whiteGames / (r.games || 1))} of games</td></tr>
              <tr><th>Black in check</th><td className="n">{one(d.checks.blackPerGame)} a game, in {pc(d.checks.blackGames / (r.games || 1))} of games</td></tr>
            </tbody>
          </table>
        </div>
        <div>
          <h4>Material</h4>
          <table className="hc-table">
            <thead><tr><th /><th className="n">Start</th><th className="n">Average</th><th className="n">End</th></tr></thead>
            <tbody>
              <tr><th>White</th><td className="n">{Math.round(d.material.whiteStart)}</td><td className="n">{Math.round(d.material.white)}</td><td className="n">{Math.round(d.material.whiteEnd)}</td></tr>
              <tr><th>Black</th><td className="n">{Math.round(d.material.blackStart)}</td><td className="n">{Math.round(d.material.black)}</td><td className="n">{Math.round(d.material.blackEnd)}</td></tr>
              <tr><th>Pieces on the board</th><td className="n">{one(d.material.piecesStart)}</td><td className="n">{one(d.material.pieces)}</td><td className="n">{one(d.material.piecesEnd)}</td></tr>
            </tbody>
          </table>
          <p className="small muted">Material in pawns × 100 by the pieces' values, kings left out.</p>
        </div>
      </section>

      <section className="hc-section" data-testid="health-pieces">
        <h4>What each piece did</h4>
        <div className="table-wrap">
          <table className="hc-table">
            <thead>
              <tr><th>Piece</th><th className="n">Moves a game</th><th className="bar-cell">Share of moves</th><th className="n">Captures</th><th className="n">Captured</th><th className="n">At start</th><th className="n">At end</th><th className="n">Survived</th><th className="n">On board (avg)</th></tr>
            </thead>
            <tbody>
              {d.pieces.map((p) => (
                <tr key={p.letter}>
                  <td>{p.name} <span className="muted">{p.letter}</span></td>
                  <td className="n">{one(p.moves)}</td>
                  <td className="bar-cell"><span className="hc-bar" style={{ width: pc(p.moveShare) }} /><span className="hc-bar-label">{pc(p.moveShare)}</span></td>
                  <td className="n">{one(p.captures)}</td>
                  <td className="n">{one(p.captured)}</td>
                  <td className="n">{one(p.start)}</td>
                  <td className="n">{one(p.end)}</td>
                  <td className="n">{p.start + p.promotions > 0 ? pc(p.survival) : '–'}</td>
                  <td className="n">{one(p.onBoard)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="small muted">Both sides together, per game. Captures and captured count the pieces a piece took and the times one was taken; survived is the share still on the board at the end (promotions count as arrivals).</p>
      </section>

      <section className="hc-section" data-testid="health-samples">
        <h4>Sample games</h4>
        {d.samples.map((g) => (
          <details key={g.index} className="hc-sample">
            <summary>{g.label}: {RESULT[g.result] ?? g.result}, {ENDINGS[g.ending] ?? g.ending}, {moves(g.plies)} moves</summary>
            <p className="hc-moves">{g.san.map((m, i) => (i % 2 === 0 ? `${i / 2 + 1}. ${m}` : m)).join(' ')}</p>
            <button type="button" className="btn ghost small-btn" onClick={() => navigator.clipboard?.writeText(g.uci.join(' '))}>Copy moves (UCI)</button>
          </details>
        ))}
      </section>
    </>
  );
}

// ---- charts ---------------------------------------------------------------------------------------

/** White wins, draws and Black wins with their 95% intervals, one bar each. */
function ResultsChart({ d, r }: { d: Details; r: Report }) {
  const rows = [
    { key: 'w', name: 'White wins', n: r.whiteWins, i: d.results.whiteWins },
    { key: 'd', name: 'Draws', n: r.draws, i: d.results.draws },
    { key: 'b', name: 'Black wins', n: r.blackWins, i: d.results.blackWins },
  ];
  const [ref, W] = useWidth<HTMLDivElement>();
  const L = 84, R = W < 480 ? 96 : 120, row = 28, H = rows.length * row + 24;
  const x = (v: number) => L + v * (W - L - R);
  return (
    <>
      <div className="score-bar" aria-label={`White ${r.whiteWins}, draws ${r.draws}, black ${r.blackWins}`}>
        <div className="w" style={{ flex: r.whiteWins }} />
        <div className="d" style={{ flex: r.draws }} />
        <div className="b" style={{ flex: r.blackWins }} />
      </div>
      <div ref={ref}>
      <svg className="hc-chart" viewBox={`0 0 ${W} ${H}`} width={W} height={H} role="img" aria-label="Results by colour with 95% intervals">
        {[0, 0.25, 0.5, 0.75, 1].map((t) => (
          <g key={t}>
            <line className="grid" x1={x(t)} x2={x(t)} y1={4} y2={H - 18} />
            <text x={x(t)} y={H - 4} textAnchor="middle">{pc(t)}</text>
          </g>
        ))}
        {rows.map((o, k) => {
          const y = 6 + k * row;
          return (
            <g key={o.key}>
              <text className="label" x={L - 8} y={y + 13} textAnchor="end">{o.name}</text>
              <rect className={`res-${o.key}`} x={L} y={y + 2} width={Math.max(0, x(o.i.value) - L)} height={14} rx={3}>
                <title>{`${o.name}: ${o.n} of ${r.games} (${pc(o.i.value)}), 95% interval ${pc(o.i.low)} to ${pc(o.i.high)}`}</title>
              </rect>
              <line className="whisker" x1={x(o.i.low)} x2={x(o.i.high)} y1={y + 9} y2={y + 9} />
              <line className="whisker" x1={x(o.i.low)} x2={x(o.i.low)} y1={y + 4} y2={y + 14} />
              <line className="whisker" x1={x(o.i.high)} x2={x(o.i.high)} y1={y + 4} y2={y + 14} />
              <text className="label" x={W - R + 8} y={y + 13}>{o.n} ({pc(o.i.value)} ± {plusMinus(o.i)})</text>
            </g>
          );
        })}
      </svg>
      </div>
    </>
  );
}

/** Games by length, decisive and drawn stacked. */
function LengthChart({ buckets }: { buckets: LengthBucket[] }) {
  const [ref, W] = useWidth<HTMLElement>();
  if (!buckets.length) return null;
  const H = 180, L = 34, R = 8, T = 18, B = 22;
  const top = Math.max(1, ...buckets.map((b) => b.all));
  const bw = (W - L - R) / buckets.length;
  const y = (v: number) => T + (H - T - B) * (1 - v / top);
  const every = Math.ceil(buckets.length / Math.max(2, Math.floor(W / 70)));
  return (
    <figure className="hc-figure" ref={ref}>
      <svg className="hc-chart" viewBox={`0 0 ${W} ${H}`} width={W} height={H} role="img" aria-label="Games by length in plies, decisive and drawn">
        {[0, top / 2, top].map((t) => (
          <g key={t}><line className="grid" x1={L} x2={W - R} y1={y(t)} y2={y(t)} /><text x={L - 4} y={y(t) + 4} textAnchor="end">{Math.round(t)}</text></g>
        ))}
        {buckets.map((b, i) => {
          const x0 = L + i * bw + 1;
          const w = Math.max(1, bw - 2);
          return (
            <g key={b.from}>
              <rect className="len-decisive" x={x0} y={y(b.decisive)} width={w} height={y(0) - y(b.decisive)} />
              <rect className="len-drawn" x={x0} y={y(b.all)} width={w} height={Math.max(0, y(b.decisive) - y(b.all) - (b.decisive && b.drawn ? 1 : 0))} />
              <rect className="hit" x={x0} y={T} width={w} height={H - T - B}>
                <title>{`${b.from} to ${b.to - 1} plies: ${b.all} games (${b.decisive} decisive, ${b.drawn} drawn)`}</title>
              </rect>
              {i % every === 0 && x0 < W - R - 60 && <text x={x0} y={H - 6}>{b.from}</text>}
            </g>
          );
        })}
        <text x={W - R} y={H - 6} textAnchor="end">plies</text>
      </svg>
      <figcaption className="hc-legend"><span className="key len-decisive" /> decisive <span className="key len-drawn" /> drawn</figcaption>
    </figure>
  );
}

/** One number per ply bucket as a line, with the share of games still going as faint bars. */
function PlyChart({ buckets, value, label }: { buckets: PlyBucket[]; value: (b: PlyBucket) => number; label: string }) {
  const [ref, W] = useWidth<HTMLElement>();
  const pts = buckets.filter((b) => b.games > 0);
  if (!pts.length) return null;
  const H = 180, L = 34, R = 8, T = 14, B = 22;
  const most = Math.max(1, ...pts.map((b) => b.games));
  const top = Math.max(1, ...pts.map(value)) * 1.1;
  const last = pts[pts.length - 1].to;
  const x = (ply: number) => L + (W - L - R) * (ply / last);
  const y = (v: number) => T + (H - T - B) * (1 - v / top);
  const mid = (b: PlyBucket) => (b.from + b.to) / 2;
  const ticks = [0, Math.round(top / 2), Math.round(top / 1.1)];
  return (
    <figure className="hc-figure" ref={ref}>
      <svg className="hc-chart" viewBox={`0 0 ${W} ${H}`} width={W} height={H} role="img" aria-label={`Average ${label} by ply`}>
        {pts.map((b) => (
          <rect key={`g${b.from}`} className="still" x={x(b.from) + 0.5} width={Math.max(1, x(b.to) - x(b.from) - 1)}
            y={y(0) - (H - T - B) * 0.25 * (b.games / most)} height={(H - T - B) * 0.25 * (b.games / most)} />
        ))}
        {ticks.map((t) => (
          <g key={t}><line className="grid" x1={L} x2={W - R} y1={y(t)} y2={y(t)} /><text x={L - 4} y={y(t) + 4} textAnchor="end">{t}</text></g>
        ))}
        <polyline className="line" points={pts.map((b) => `${x(mid(b))},${y(value(b))}`).join(' ')} />
        {pts.map((b) => (
          <g key={b.from}>
            <circle className="dot" cx={x(mid(b))} cy={y(value(b))} r={3} />
            <rect className="hit" x={x(b.from)} y={T} width={Math.max(1, x(b.to) - x(b.from))} height={H - T - B}>
              <title>{`Plies ${b.from} to ${b.to - 1}: ${one(value(b))} ${label} on average; ${b.games} games still going; ${one(b.pieces)} pieces on the board`}</title>
            </rect>
          </g>
        ))}
        <text x={L} y={H - 6}>0</text>
        <text x={W - R} y={H - 6} textAnchor="end">{last} plies</text>
      </svg>
      <figcaption className="hc-legend"><span className="key line-key" /> {label} <span className="key still" /> games still going</figcaption>
    </figure>
  );
}
