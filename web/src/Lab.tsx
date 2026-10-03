import { useEffect, useState } from 'react';
import { Board } from './Board';
import type { Champion } from './protocol';

// The lab page: evolution runs recorded by `lab.Cli` (web.LabApi serves them), read only.

interface Yardstick { wins: number; draws: number; losses: number; fraction: number; elo: number; eloLow: number; eloHigh: number }
interface Settings { generations: number; depth: number; openingsPerPairing: number; variety: number; maxPlies: number; threads: number; seed: number; yardstickEvery: number; yardstickOpenings: number; yardsticks?: string[]; deepDepth?: number; deepShareFirst?: number; deepShareLast?: number }
interface RunSummary { file: string; name: string; algorithm: string; startedAt: string; settings: Settings; generationsDone: number; lastYardstick?: Yardstick }
interface YardstickResult extends Yardstick { opponent: string; label: string; depth: number }
interface GenerationRow { number: number; champion: number; championScore: number; games: number; finishedAt: string; yardstick?: Yardstick; yardsticks?: YardstickResult[] }
interface Weight { name: string; group: string; description: string; default: number; min: number; max: number; values: number[] }
interface RunDetail extends Omit<RunSummary, 'generationsDone' | 'lastYardstick'> { generations: GenerationRow[]; weights: Weight[] }
interface GameRow { index: number; kind: 'population' | 'yardstick'; white: string; black: string; opening: string; result: string; reason: string; plies: number; depth?: number }
interface GenerationDetail { number: number; members: number; games: GameRow[] }
interface Replay { white: string; black: string; opening: string; result: string; reason: string; startFen: string; moves: { uci: string; san: string; fenAfter: string }[] }

const EMPTY = new Map<string, string[]>();
const REFRESH_MS = 5000;

async function get<T>(url: string): Promise<T> {
  const r = await fetch(url);
  if (!r.ok) throw new Error(`${r.status} ${await r.text()}`);
  return r.json() as Promise<T>;
}

/** Re-fetches every few seconds, so a run that is still going fills in on its own. */
function usePolled<T>(url: string | null): [T | null, string | null] {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    setData(null);
    if (!url) return;
    let live = true;
    const load = () => get<T>(url).then((d) => live && (setData(d), setError(null)), (e) => live && setError(String(e)));
    load();
    const timer = window.setInterval(load, REFRESH_MS);
    return () => {
      live = false;
      window.clearInterval(timer);
    };
  }, [url]);
  return [data, error];
}

const player = (name: string) =>
  name === 'default' ? 'Default weights'
    : name === 'classic' ? 'Classic weights'
      : name === 'champion' ? 'Champion'
        : /^sf\d/.test(name) ? `Stockfish ${name.slice(2)}`
          : /^\d+$/.test(name) ? `#${name}` : name;
const resultText = (r: string) => (r === 'WHITE_WINS' ? '1–0' : r === 'BLACK_WINS' ? '0–1' : '½–½');
const elo = (y: Yardstick) => `${Math.round(y.elo) >= 0 ? '+' : ''}${Math.round(y.elo)}`;

export function Lab({ onPlay }: { onPlay: (champion: Champion) => void }) {
  const [runs, runsError] = usePolled<{ folder: string; runs: RunSummary[] }>('/api/lab/runs');
  const [file, setFile] = useState<string | null>(null);
  const shown = file ?? runs?.runs[0]?.file ?? null;
  const [run] = usePolled<RunDetail>(shown ? `/api/lab/runs/${encodeURIComponent(shown)}` : null);
  const [picked, setPicked] = useState<number | null>(null);
  const lastGeneration = run?.generations.at(-1)?.number ?? null;
  const generation = picked ?? lastGeneration;

  useEffect(() => setPicked(null), [shown]);

  if (runsError) return <main className="lab"><p className="muted">Could not load the runs: {runsError}</p></main>;
  if (!runs) return <main className="lab"><p className="muted">Loading the runs…</p></main>;
  if (runs.runs.length === 0) {
    return (
      <main className="lab">
        <section className="panel empty-lab" data-testid="lab-empty">
          <h2>No evolution runs yet</h2>
          <p>Runs are started from the command line and recorded in <code>{runs.folder}</code>:</p>
          <pre>java -cp target/izikstar-chess-3.1.0.jar lab.Cli run runs/first.db --algorithm evolution.RandomMutationExample</pre>
          <p className="muted">This page shows them as they go: progress, how the weights move, every game.</p>
        </section>
      </main>
    );
  }

  const row = run?.generations.find((g) => g.number === generation) ?? null;
  return (
    <main className="lab">
      <nav className="runs panel" aria-label="Runs">
        <h2>Runs</h2>
        <ul>
          {runs.runs.map((r) => (
            <li key={r.file}>
              <button type="button" className={r.file === shown ? 'on' : ''} onClick={() => setFile(r.file)}>
                <strong>{r.name}</strong>
                <span className="muted">
                  {r.generationsDone}/{r.settings.generations} generations
                  {r.lastYardstick && <> · {elo(r.lastYardstick)} Elo</>}
                </span>
              </button>
            </li>
          ))}
        </ul>
      </nav>

      {run && (
        <div className="run" data-testid="run">
          <section className="panel">
            <div className="run-head">
              <div>
                <h2>{run.name}</h2>
                <p className="muted">
                  {run.algorithm.replace(/^.*\./, '')} · depth {run.settings.depth}{run.settings.deepDepth ? ` (${run.settings.deepShareFirst}–${run.settings.deepShareLast}% at ${run.settings.deepDepth})` : ''} · {run.generations.length}/{run.settings.generations} generations · seed {run.settings.seed}
                </p>
              </div>
              {row && (
                <button type="button" className="btn primary" onClick={() => onPlay({
                  run: run.file, generation: row.number, label: `Champion of ${run.name}, generation ${row.number}`,
                })}>Play generation {row.number}'s champion</button>
              )}
            </div>
            <h3>Champion against {player(run.settings.yardsticks?.[0] ?? 'default').replace(/^D/, 'd').replace(/^C(?=lassic)/, 'c')}</h3>
            <ProgressChart generations={run.generations} />
          </section>

          <section className="panel">
            <h3>How the champions' weights moved</h3>
            <Weights weights={run.weights} />
          </section>

          {generation !== null && (
            <GenerationGames file={run.file} generations={run.generations} generation={generation} onPick={setPicked} />
          )}
        </div>
      )}
    </main>
  );
}

/** Elo of each measured champion against the default weights, with its 95% interval. */
function ProgressChart({ generations }: { generations: GenerationRow[] }) {
  const points = generations.filter((g) => g.yardstick);
  if (points.length === 0) return <p className="muted">No yardstick match played yet.</p>;
  const W = 640, H = 230, L = 46, R = 12, T = 12, B = 36;
  const last = Math.max(1, generations.at(-1)!.number);
  const lows = points.map((g) => g.yardstick!.eloLow);
  const highs = points.map((g) => g.yardstick!.eloHigh);
  const top = Math.max(100, ...highs);
  const bottom = Math.min(-100, ...lows);
  const x = (n: number) => L + ((W - L - R) * n) / last;
  const y = (e: number) => T + ((H - T - B) * (top - e)) / (top - bottom);
  const ticks = [bottom, bottom / 2, 0, top / 2, top].map(Math.round);
  return (
    <svg className="chart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Champion Elo against the default weights by generation">
      {ticks.map((t) => (
        <g key={t}>
          <line x1={L} x2={W - R} y1={y(t)} y2={y(t)} className={t === 0 ? 'zero' : 'grid'} />
          <text x={L - 6} y={y(t) + 4} textAnchor="end">{t > 0 ? '+' + t : t}</text>
        </g>
      ))}
      {generations.filter((g) => g.number % Math.ceil((last + 1) / 12) === 0).map((g) => (
        <text key={g.number} x={x(g.number)} y={H - B + 14} textAnchor="middle">{g.number}</text>
      ))}
      <text x={W - R} y={H - 2} textAnchor="end">generation</text>
      <polyline className="line" points={points.map((g) => `${x(g.number)},${y(g.yardstick!.elo)}`).join(' ')} />
      {points.map((g) => (
        <g key={g.number}>
          <line className="err" x1={x(g.number)} x2={x(g.number)} y1={y(g.yardstick!.eloHigh)} y2={y(g.yardstick!.eloLow)} />
          <circle className="dot" cx={x(g.number)} cy={y(g.yardstick!.elo)} r={4}>
            <title>{`Generation ${g.number}: ${elo(g.yardstick!)} Elo (${Math.round(g.yardstick!.eloLow)} to ${Math.round(g.yardstick!.eloHigh)}), +${g.yardstick!.wins} =${g.yardstick!.draws} -${g.yardstick!.losses}`}</title>
          </circle>
        </g>
      ))}
    </svg>
  );
}

function Spark({ values, min, max }: { values: number[]; min: number; max: number }) {
  const W = 120, H = 24;
  const span = max - min || 1;
  const pts = values.map((v, i) => `${values.length === 1 ? W / 2 : (W * i) / (values.length - 1)},${H - 2 - ((H - 4) * (v - min)) / span}`);
  return (
    <svg className="spark" viewBox={`0 0 ${W} ${H}`} aria-hidden="true">
      <polyline points={pts.join(' ')} />
    </svg>
  );
}

const SHOWN_WEIGHTS = 12;

function Weights({ weights }: { weights: Weight[] }) {
  const [all, setAll] = useState(false);
  if (weights.length === 0) return <p className="muted">Every champion so far plays with the default weights.</p>;
  // the latest champion's biggest changes first, as a share of each parameter's range
  const sorted = [...weights].sort((a, b) =>
    Math.abs(b.values.at(-1)! - b.default) / (b.max - b.min) - Math.abs(a.values.at(-1)! - a.default) / (a.max - a.min));
  const shown = all ? sorted : sorted.slice(0, SHOWN_WEIGHTS);
  return (
    <div className="table-wrap">
      <table className="weights" data-testid="weights">
        <thead>
          <tr><th>Parameter</th><th>Default</th><th>Latest</th><th>Change</th><th>Over the generations</th></tr>
        </thead>
        <tbody>
          {shown.map((w) => {
            const latest = w.values.at(-1)!;
            const change = latest - w.default;
            return (
              <tr key={w.name} title={w.description}>
                <td className="mono">{w.name}</td>
                <td>{w.default}</td>
                <td>{latest}</td>
                <td className={change > 0 ? 'up' : change < 0 ? 'down' : ''}>{change > 0 ? '+' : ''}{change}</td>
                <td><Spark values={w.values} min={Math.min(w.default, ...w.values)} max={Math.max(w.default, ...w.values)} /></td>
              </tr>
            );
          })}
        </tbody>
      </table>
      {sorted.length > SHOWN_WEIGHTS && (
        <button type="button" className="btn ghost" onClick={() => setAll(!all)}>
          {all ? 'Show the biggest changes only' : `Show all ${sorted.length} parameters that moved`}
        </button>
      )}
    </div>
  );
}

function GenerationGames({ file, generations, generation, onPick }: {
  file: string;
  generations: GenerationRow[];
  generation: number;
  onPick: (n: number) => void;
}) {
  const base = `/api/lab/runs/${encodeURIComponent(file)}/generations/${generation}`;
  const [detail] = usePolled<GenerationDetail>(base);
  const [game, setGame] = useState<number | null>(null);
  useEffect(() => setGame(null), [base]);
  const row = generations.find((g) => g.number === generation);
  return (
    <section className="panel">
      <div className="run-head">
        <h3>Generation {generation}</h3>
        <label className="pick">
          <span className="muted">Generation</span>
          <select value={generation} onChange={(e) => onPick(Number(e.target.value))} aria-label="Generation">
            {generations.map((g) => <option key={g.number} value={g.number}>{g.number}</option>)}
          </select>
        </label>
      </div>
      {row && (
        <p className="muted">
          {detail ? `${detail.members} members, ` : ''}{row.games} games. Champion #{row.champion} scored {Math.round(row.championScore * 100)}%
          {row.yardstick && !row.yardsticks?.length && <>; against the default weights +{row.yardstick.wins} ={row.yardstick.draws} -{row.yardstick.losses}, {elo(row.yardstick)} Elo
            ({Math.round(row.yardstick.eloLow)} to {Math.round(row.yardstick.eloHigh)})</>}.
        </p>
      )}
      {row?.yardsticks && row.yardsticks.length > 0 && (
        <div className="table-wrap">
          <table data-testid="yardsticks">
            <thead><tr><th>Champion against</th><th>Depth</th><th>Games</th><th>Score</th><th>Elo (95%)</th></tr></thead>
            <tbody>
              {row.yardsticks.map((y) => (
                <tr key={y.opponent + y.depth}>
                  <td>{player(y.label)}</td>
                  <td>{y.depth}</td>
                  <td>+{y.wins} ={y.draws} -{y.losses}</td>
                  <td>{Math.round(y.fraction * 100)}%</td>
                  <td>{elo(y)} ({Math.round(y.eloLow)} to {Math.round(y.eloHigh)})</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <div className="games-replay">
        <div className="table-wrap games">
          <table data-testid="games">
            <thead><tr><th>White</th><th>Black</th><th>Opening</th><th>Depth</th><th>Result</th><th>Plies</th></tr></thead>
            <tbody>
              {detail?.games.map((g) => (
                <tr key={g.index} className={(g.index === game ? 'on ' : '') + g.kind} onClick={() => setGame(g.index)}>
                  <td>{player(g.white)}</td>
                  <td>{player(g.black)}</td>
                  <td>{g.opening}</td>
                  <td>{g.depth ?? ''}</td>
                  <td title={g.reason}>{resultText(g.result)}</td>
                  <td>{g.plies}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {game !== null && <GameReplay url={`${base}/games/${game}`} />}
      </div>
    </section>
  );
}

function GameReplay({ url }: { url: string }) {
  const [replay, setReplay] = useState<Replay | null>(null);
  const [ply, setPly] = useState(0);
  useEffect(() => {
    let live = true;
    setReplay(null);
    get<Replay>(url).then((r) => {
      if (live) {
        setReplay(r);
        setPly(r.moves.length);
      }
    });
    return () => {
      live = false;
    };
  }, [url]);
  if (!replay) return <div className="replay muted">Loading the game…</div>;
  const fen = ply === 0 ? replay.startFen : replay.moves[ply - 1].fenAfter;
  return (
    <div className="replay" data-testid="replay">
      <p><strong>{player(replay.white)}</strong> – <strong>{player(replay.black)}</strong> · {replay.opening} · {resultText(replay.result)} ({replay.reason.toLowerCase().replace(/_/g, ' ')})</p>
      <div className="replay-board">
        <Board fen={fen} orientation="white" legal={EMPTY} lastMove={ply === 0 ? null : replay.moves[ply - 1].uci}
          checkSquare={null} hint={null} onMove={() => {}} onSelect={() => {}} onIllegal={() => {}}
          premoveColor={null} premoves={[]} onPremove={() => {}} />
      </div>
      <div className="nav" role="group" aria-label="Replay moves">
        <button type="button" className="icon" aria-label="First position" disabled={ply === 0} onClick={() => setPly(0)}>⏮</button>
        <button type="button" className="icon" aria-label="Previous move" disabled={ply === 0} onClick={() => setPly(ply - 1)}>◀</button>
        <button type="button" className="icon" aria-label="Next move" disabled={ply === replay.moves.length} onClick={() => setPly(ply + 1)}>▶</button>
        <button type="button" className="icon" aria-label="Last move" disabled={ply === replay.moves.length} onClick={() => setPly(replay.moves.length)}>⏭</button>
      </div>
      <p className="sans mono">
        {replay.moves.map((m, i) => (
          <button key={i} type="button" className={'mv' + (i + 1 === ply ? ' current' : '')} onClick={() => setPly(i + 1)}>
            {i % 2 === 0 ? `${i / 2 + 1}. ` : ''}{m.san}
          </button>
        ))}
      </p>
    </div>
  );
}
