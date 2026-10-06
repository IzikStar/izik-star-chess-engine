import { useEffect, useMemo, useState } from 'react';
import { Board } from '../Board';
import type { Champion } from '../protocol';
import {
  algorithmName, duration, elo, goLab, pct, player, post, reasonText, resultText, runUrl, signed, usePolled,
  type GenerationDetail, type GenerationRow, type Job, type Replay, type RunDetail, type Score, type Spec, type Weight,
} from './api';

const EMPTY = new Map<string, string[]>();
const TABS: [string, string][] = [['overview', 'Overview'], ['generations', 'Generations'], ['weights', 'Weights'], ['settings', 'Settings']];

/** One run: its progress, every generation, how the weights moved, its settings; and one generation in detail. */
export function Run({ file, generation, tab, job, onPlay }: {
  file: string;
  generation: number | null;
  tab: string;
  job: Job | null;
  onPlay: (champion: Champion) => void;
}) {
  const isRunning = job?.running && job.file === file;
  const [run, error, refresh] = usePolled<RunDetail>(runUrl(file), isRunning ? 3000 : 15_000);
  // a run that just stopped or finished: show its last generations now, not at the next slow poll
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const justEnded = !!job && job.file === file && !job.running && !!job.finished;
  useEffect(() => { if (!isRunning) refresh(); }, [isRunning, justEnded]);
  const [message, setMessage] = useState<string | null>(null);
  if (error) return <section className="panel"><p className="muted">Could not load the run: {error}</p></section>;
  if (!run) return <section className="panel"><p className="muted">Loading the run…</p></section>;

  const last = run.generations.at(-1) ?? null;
  const finished = run.generations.length >= run.settings.generations;
  const state = isRunning ? (job?.stopping ? 'stopping' : 'playing') : finished ? 'finished' : 'stopped';
  const act = async (what: 'resume' | 'stop' | 'stopNow') => {
    setMessage(null);
    try {
      if (what === 'resume') await post(runUrl(file) + '/resume');
      else await post(runUrl(file) + '/stop', { now: what === 'stopNow' });
      refresh();
    } catch (e) {
      setMessage(String((e as Error).message ?? e));
    }
  };
  const champion = (g: GenerationRow): Champion => ({ run: file, generation: g.number, variant: run.variantId, label: `Champion of ${run.name}, generation ${g.number}` });

  return (
    <div className="lab-screen run" data-testid="run">
      <section className="panel run-top">
        <div className="run-head">
          <div>
            <h2>{run.name} <span className={'state ' + state}>{state}</span></h2>
            <p className="muted">
              {run.variantName} · {algorithmName(run.algorithm)} · {run.parameters} weights · depth {run.settings.depth}
              {run.settings.deepDepth ? ` (${run.settings.deepShareFirst}–${run.settings.deepShareLast}% of the games at ${run.settings.deepDepth})` : ''}
              {' '}· seed {run.settings.seed} · started {new Date(run.startedAt).toLocaleString()}
            </p>
          </div>
          <div className="actions">
            {isRunning && !job?.stopping && <button type="button" className="btn" onClick={() => act('stop')}>Stop after this generation</button>}
            {isRunning && <button type="button" className="btn" onClick={() => act('stopNow')}>Stop now</button>}
            {!isRunning && !finished && <button type="button" className="btn" disabled={!!job?.running} onClick={() => act('resume')}>Resume</button>}
            {last && <button type="button" className="btn primary" onClick={() => onPlay(champion(last))}>Play generation {last.number}'s champion</button>}
            <a className="btn" href={`${runUrl(file)}/pgn`} download>Download the games (PGN)</a>
          </div>
        </div>
        {message && <p className="error">{message}</p>}
        <div className="progress" role="progressbar" aria-valuenow={run.generations.length} aria-valuemax={run.settings.generations} aria-label="Generations done">
          <div className="bar" style={{ width: `${Math.min(100, (100 * run.generations.length) / run.settings.generations)}%` }} />
        </div>
        <p>
          <strong>{run.generations.length}</strong> of {run.settings.generations} generations
          {isRunning && job?.generation !== undefined && job.generation >= 0 && <> · now playing generation {job.generation}: {job.gamesDone} of about {job.gamesPlanned} games</>}
          {!isRunning && !finished && run.nextGenerationGames > 0 && <> · generation {run.nextGeneration} was under way ({run.nextGenerationGames} games) and is replayed on resume</>}
          {run.generations.length > 1 && <> · about {duration(run.startedAt, last!.finishedAt)} so far, {duration(run.generations.at(-2)!.finishedAt, last!.finishedAt)} for the last generation</>}
        </p>
        <nav className="sub-tabs" aria-label="Run screens">
          {TABS.map(([key, label]) => (
            <button key={key} type="button" className={'btn ghost' + (tab === key ? ' on' : '')} aria-current={tab === key ? 'page' : undefined}
              onClick={() => goLab('run', file, ...(key === 'overview' ? [] : [key]))}>{label}</button>
          ))}
          {last && (
            <label className="pick">
              <span className="muted">Generation</span>
              <select value={tab === 'generation' && generation !== null ? generation : ''} aria-label="Generation" onChange={(e) => e.target.value !== '' && goLab('run', file, 'gen', e.target.value)}>
                <option value="">Open one…</option>
                {run.generations.map((g) => <option key={g.number} value={g.number}>{g.number}</option>)}
              </select>
            </label>
          )}
        </nav>
      </section>

      {tab === 'overview' && <Overview run={run} />}
      {tab === 'generations' && <Generations run={run} onPlay={onPlay} />}
      {tab === 'weights' && <WeightsScreen run={run} />}
      {tab === 'settings' && <SettingsScreen run={run} />}
      {tab === 'generation' && generation !== null && <GenerationScreen run={run} number={generation} onPlay={onPlay} />}
    </div>
  );
}

// ---- overview -------------------------------------------------------------------------------------

function Overview({ run }: { run: RunDetail }) {
  const yardsticks = useMemo(() => {
    const seen: string[] = [];
    run.generations.forEach((g) => g.yardsticks.forEach((y) => !seen.includes(y.opponent) && seen.push(y.opponent)));
    return seen;
  }, [run]);
  const [yardstick, setYardstick] = useState<string | null>(null);
  const shown = yardstick && yardsticks.includes(yardstick) ? yardstick : yardsticks[0] ?? null;
  const points = shown ? run.generations.map((g) => ({ number: g.number, score: combined(g, shown) })).filter((p) => p.score) as { number: number; score: Score }[] : [];
  if (run.generations.length === 0) return <section className="panel"><p className="muted">No generation finished yet.</p></section>;
  return (
    <>
      <section className="panel">
        <div className="run-head">
          <h3>Champion against {shown ? player(shown).toLowerCase().replace(/^d/, 'd') : 'the yardsticks'}</h3>
          {yardsticks.length > 1 && (
            <label className="pick"><span className="muted">Yardstick</span>
              <select value={shown ?? ''} onChange={(e) => setYardstick(e.target.value)}>{yardsticks.map((y) => <option key={y} value={y}>{player(y)}</option>)}</select>
            </label>
          )}
        </div>
        <p className="muted">Elo of each measured champion against a fixed opponent, with its 95% interval. The run is getting somewhere when the whole bar climbs above zero.</p>
        <EloChart points={points} last={run.generations.at(-1)!.number} />
      </section>
      <section className="panel">
        <h3>What the games looked like</h3>
        <p className="muted">Per generation: the champion's score among its peers, how many games were decided, and how long they lasted.</p>
        <div className="chart-row">
          <SeriesChart title="Champion's score" unit="%" values={run.generations.map((g) => Math.round(100 * g.championScore))} min={0} max={100} />
          <SeriesChart title="Decisive games" unit="%" values={run.generations.map((g) => (g.stats.games ? Math.round((100 * (g.stats.whiteWins + g.stats.blackWins)) / g.stats.games) : 0))} min={0} max={100} />
          <SeriesChart title="Average length" unit=" moves" values={run.generations.map((g) => Math.round(g.stats.averagePlies / 2))} min={0} />
        </div>
      </section>
      <section className="panel">
        <h3>Biggest changes in the champions' weights</h3>
        <WeightsTable weights={run.weights} limit={10} />
        <p><button type="button" className="link" onClick={() => goLab('run', run.file, 'weights')}>All the weights that moved</button></p>
      </section>
    </>
  );
}

function combined(g: GenerationRow, opponent: string): Score | null {
  const mine = g.yardsticks.filter((y) => y.opponent === opponent);
  if (mine.length === 0) return null;
  if (mine.length === 1) return mine[0];
  const wins = mine.reduce((s, y) => s + y.wins, 0);
  const draws = mine.reduce((s, y) => s + y.draws, 0);
  const losses = mine.reduce((s, y) => s + y.losses, 0);
  const n = wins + draws + losses;
  const fraction = n ? (wins + draws / 2) / n : 0.5;
  const eloOf = (f: number) => (f <= 0 ? -800 : f >= 1 ? 800 : -400 * Math.log10(1 / f - 1));
  // the same normal approximation the server uses, give or take
  const sd = n ? Math.sqrt(Math.max(0.0001, (wins * (1 - fraction) ** 2 + draws * (0.5 - fraction) ** 2 + losses * fraction ** 2) / n) / Math.sqrt(n)) : 0.5;
  return { wins, draws, losses, fraction, elo: eloOf(fraction), eloLow: eloOf(Math.max(0, fraction - 1.96 * sd)), eloHigh: eloOf(Math.min(1, fraction + 1.96 * sd)) };
}

/** Elo of each measured champion against a yardstick, with its 95% interval. */
function EloChart({ points, last }: { points: { number: number; score: Score }[]; last: number }) {
  if (points.length === 0) return <p className="muted">No yardstick match played yet.</p>;
  const W = 720, H = 260, L = 48, R = 12, T = 12, B = 36;
  const top = Math.max(100, ...points.map((p) => p.score.eloHigh));
  const bottom = Math.min(-100, ...points.map((p) => p.score.eloLow));
  const x = (n: number) => L + ((W - L - R) * n) / Math.max(1, last);
  const y = (e: number) => T + ((H - T - B) * (top - e)) / (top - bottom);
  const ticks = [bottom, bottom / 2, 0, top / 2, top].map(Math.round);
  const every = Math.ceil((last + 1) / 12);
  return (
    <svg className="chart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Champion Elo against the yardstick by generation">
      {ticks.map((t) => (
        <g key={t}>
          <line x1={L} x2={W - R} y1={y(t)} y2={y(t)} className={t === 0 ? 'zero' : 'grid'} />
          <text x={L - 6} y={y(t) + 4} textAnchor="end">{t > 0 ? '+' + t : t}</text>
        </g>
      ))}
      {Array.from({ length: last + 1 }, (_, i) => i).filter((i) => i % every === 0).map((i) => (
        <text key={i} x={x(i)} y={H - B + 14} textAnchor="middle">{i}</text>
      ))}
      <text x={W - R} y={H - 2} textAnchor="end">generation</text>
      <polyline className="line" points={points.map((p) => `${x(p.number)},${y(p.score.elo)}`).join(' ')} />
      {points.map((p) => (
        <g key={p.number}>
          <line className="err" x1={x(p.number)} x2={x(p.number)} y1={y(p.score.eloHigh)} y2={y(p.score.eloLow)} />
          <circle className="dot" cx={x(p.number)} cy={y(p.score.elo)} r={4}>
            <title>{`Generation ${p.number}: ${elo(p.score)} Elo (${Math.round(p.score.eloLow)} to ${Math.round(p.score.eloHigh)}), +${p.score.wins} =${p.score.draws} -${p.score.losses}`}</title>
          </circle>
        </g>
      ))}
    </svg>
  );
}

/** A small line chart of one number per generation. */
function SeriesChart({ title, unit, values, min, max }: { title: string; unit: string; values: number[]; min?: number; max?: number }) {
  const W = 240, H = 120, L = 34, R = 8, T = 8, B = 22;
  const lo = min ?? Math.min(...values);
  const hi = max ?? Math.max(...values, lo + 1);
  const x = (i: number) => L + ((W - L - R) * i) / Math.max(1, values.length - 1);
  const y = (v: number) => T + ((H - T - B) * (hi - v)) / (hi - lo || 1);
  const latest = values.at(-1);
  return (
    <figure className="series">
      <figcaption>{title}{latest !== undefined && <strong> {latest}{unit}</strong>}</figcaption>
      <svg className="chart small" viewBox={`0 0 ${W} ${H}`} role="img" aria-label={`${title} by generation`}>
        {[lo, (lo + hi) / 2, hi].map((t) => (
          <g key={t}><line x1={L} x2={W - R} y1={y(t)} y2={y(t)} className="grid" /><text x={L - 4} y={y(t) + 3} textAnchor="end">{Math.round(t)}</text></g>
        ))}
        <text x={L} y={H - 4}>0</text>
        <text x={W - R} y={H - 4} textAnchor="end">{values.length - 1}</text>
        <polyline className="line" points={values.map((v, i) => `${x(i)},${y(v)}`).join(' ')} />
      </svg>
    </figure>
  );
}

// ---- generations ----------------------------------------------------------------------------------

function Generations({ run, onPlay }: { run: RunDetail; onPlay: (c: Champion) => void }) {
  const yardsticks = useMemo(() => {
    const seen: string[] = [];
    run.generations.forEach((g) => g.yardsticks.forEach((y) => !seen.includes(y.opponent) && seen.push(y.opponent)));
    return seen;
  }, [run]);
  return (
    <section className="panel">
      <h3>Every generation</h3>
      <p className="muted">Click a generation for its members, games and standings.</p>
      <div className="table-wrap">
        <table data-testid="generations">
          <thead>
            <tr><th>Generation</th><th>Champion</th><th>Score</th><th>Games</th><th>Decisive</th><th>Length</th>
              {yardsticks.map((y) => <th key={y}>vs {player(y)}</th>)}<th>Took</th><th></th></tr>
          </thead>
          <tbody>
            {[...run.generations].reverse().map((g, i, all) => {
              const previous = all[i + 1];
              return (
                <tr key={g.number}>
                  <td><button type="button" className="link" onClick={() => goLab('run', run.file, 'gen', String(g.number))}>{g.number}</button></td>
                  <td>#{g.champion}</td>
                  <td>{pct(g.championScore)}</td>
                  <td>{g.games}</td>
                  <td>{g.stats.games ? pct((g.stats.whiteWins + g.stats.blackWins) / g.stats.games) : ''}</td>
                  <td>{g.stats.games ? `${Math.round(g.stats.averagePlies / 2)} moves` : ''}</td>
                  {yardsticks.map((y) => {
                    const s = combined(g, y);
                    return <td key={y} className={s ? (s.eloLow > 0 ? 'up' : s.eloHigh < 0 ? 'down' : '') : ''}>{s ? `${elo(s)} (${pct(s.fraction)})` : ''}</td>;
                  })}
                  <td>{duration(previous ? previous.finishedAt : run.startedAt, g.finishedAt)}</td>
                  <td><button type="button" className="link" onClick={() => onPlay({ run: run.file, generation: g.number, variant: run.variantId, label: `Champion of ${run.name}, generation ${g.number}` })}>Play</button></td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}

// ---- weights --------------------------------------------------------------------------------------

function Spark({ values, min, max }: { values: number[]; min: number; max: number }) {
  const W = 120, H = 24;
  const span = max - min || 1;
  const pts = values.map((v, i) => `${values.length === 1 ? W / 2 : (W * i) / (values.length - 1)},${H - 2 - ((H - 4) * (v - min)) / span}`);
  return <svg className="spark" viewBox={`0 0 ${W} ${H}`} aria-hidden="true"><polyline points={pts.join(' ')} /></svg>;
}

function WeightsTable({ weights, limit, group }: { weights: Weight[]; limit?: number; group?: string }) {
  const sorted = useMemo(() => [...weights]
    .filter((w) => !group || w.group === group)
    .sort((a, b) => Math.abs(b.values.at(-1)! - b.default) / (b.max - b.min) - Math.abs(a.values.at(-1)! - a.default) / (a.max - a.min)), [weights, group]);
  if (sorted.length === 0) return <p className="muted">Every champion so far plays with the default weights{group ? ` in ${group}` : ''}.</p>;
  const shown = limit ? sorted.slice(0, limit) : sorted;
  return (
    <div className="table-wrap">
      <table className="weights" data-testid="weights">
        <thead><tr><th>Weight</th><th>Group</th><th>Default</th><th>Latest champion</th><th>Change</th><th>Over the generations</th></tr></thead>
        <tbody>
          {shown.map((w) => {
            const latest = w.values.at(-1)!;
            const change = latest - w.default;
            return (
              <tr key={w.name} title={w.description}>
                <td className="mono">{w.name}</td>
                <td>{w.group}</td>
                <td>{w.default}</td>
                <td>{latest}</td>
                <td className={change > 0 ? 'up' : change < 0 ? 'down' : ''}>{signed(change)}</td>
                <td><Spark values={w.values} min={Math.min(w.default, ...w.values)} max={Math.max(w.default, ...w.values)} /></td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function WeightsScreen({ run }: { run: RunDetail }) {
  const groups = useMemo(() => Array.from(new Set(run.weights.map((w) => w.group))), [run.weights]);
  const [group, setGroup] = useState('');
  return (
    <section className="panel">
      <div className="run-head">
        <h3>How the champions' weights moved</h3>
        {groups.length > 1 && (
          <label className="pick"><span className="muted">Group</span>
            <select value={group} onChange={(e) => setGroup(e.target.value)}><option value="">All</option>{groups.map((g) => <option key={g} value={g}>{g}</option>)}</select>
          </label>
        )}
      </div>
      <p className="muted">Each generation's champion, compared with the default. The {run.weights.length} weights that moved most are listed (of {run.parameters}),
        hover one for what it means. Material weights are in centipawns: 100 is a pawn in chess.</p>
      <WeightsTable weights={run.weights} group={group || undefined} />
    </section>
  );
}

// ---- settings -------------------------------------------------------------------------------------

function SettingsScreen({ run }: { run: RunDetail }) {
  const s = run.settings;
  const row = (label: string, value: string | number | undefined, help: string) => (
    <tr><th>{label}</th><td>{value === undefined ? '' : String(value)}</td><td className="muted">{help}</td></tr>
  );
  return (
    <section className="panel">
      <h3>Settings of this run</h3>
      <div className="table-wrap">
        <table className="settings">
          <tbody>
            {row('Game', run.variantName, 'What the members play.')}
            {row('Algorithm', run.algorithm, 'Who decides the next generation.')}
            {Object.entries(s.algorithmOptions ?? {}).map(([k, v]) => row(`  ${k}`, v, 'A setting of the algorithm.'))}
            {row('Generations', s.generations, 'How many generations the run plays.')}
            {row('Depth', s.depth, 'Search depth of the games.')}
            {row('Deep depth', s.deepDepth || 'none', s.deepDepth ? `${s.deepShareFirst}% of the games in generation 0 to ${s.deepShareLast}% in the last are played at this depth.` : 'No deep games.')}
            {row('Openings per pairing', s.openingsPerPairing, 'Each pairing plays this many openings, each with both colours.')}
            {row('Openings', s.openingPlies ? `${s.randomOpenings} random openings of ${s.openingPlies} half-moves` : 'the chess opening book', 'Where the games start.')}
            {row('Move limit', s.maxPlies, 'Plies after which a game is a draw.')}
            {row('Variety', s.variety, 'Centipawns below the best move that may still be played.')}
            {row('Threads', s.threads, 'Games played at once.')}
            {row('Seed', s.seed, 'The run\'s randomness.')}
            {row('Yardsticks', (s.yardsticks ?? ['default']).map(player).join(', '), 'Who the champion is measured against.')}
            {row('Measured every', s.yardstickEvery ? `${s.yardstickEvery} generations, ${s.yardstickOpenings} openings` : 'never', 'How often, and over how many openings (both colours).')}
            {s.stockfishFrom ? row('Stockfish from generation', s.stockfishFrom, 'Stockfish yardsticks start here.') : null}
            {s.memberStockfishOpenings ? row('Members against Stockfish', `${s.memberStockfishOpenings} openings`, 'Every member plays Stockfish every generation.') : null}
            {row('File', run.file, 'The SQLite file in the runs folder.')}
          </tbody>
        </table>
      </div>
      <p className="muted">The same run from the command line:</p>
      <pre className="cli">{cli(run)}</pre>
    </section>
  );
}

function cli(run: RunDetail): string {
  const s = run.settings;
  const parts = [`java -cp target/izikstar-chess-3.1.0.jar lab.Cli run runs/${run.file} --name "${run.name}" --algorithm ${run.algorithm}`,
    `--generations ${s.generations} --depth ${s.depth} --openings-per-pairing ${s.openingsPerPairing} --variety ${s.variety} --max-plies ${s.maxPlies}`,
    `--threads ${s.threads} --seed ${s.seed} --yardstick-every ${s.yardstickEvery} --yardstick-openings ${s.yardstickOpenings} --yardsticks ${(s.yardsticks ?? ['default']).join(',')}`,
    `--deep-depth ${s.deepDepth ?? 0} --deep-share ${s.deepShareFirst ?? 0}-${s.deepShareLast ?? 0}`];
  if (s.variant && s.variant !== 'chess') parts.push(`--variant ${s.variant} --opening-plies ${s.openingPlies ?? 4} --random-openings ${s.randomOpenings ?? 50}`);
  if (s.memberStockfishOpenings) parts.push(`--member-stockfish-openings ${s.memberStockfishOpenings}`);
  const options = Object.entries(s.algorithmOptions ?? {}).map(([k, v]) => `${k}=${v}`).join(',');
  if (options) parts.push(`--options ${options}`);
  return parts.join(' \\\n    ');
}

// ---- one generation -------------------------------------------------------------------------------

function GenerationScreen({ run, number, onPlay }: { run: RunDetail; number: number; onPlay: (c: Champion) => void }) {
  const base = `${runUrl(run.file)}/generations/${number}`;
  const [detail, error] = usePolled<GenerationDetail>(base, 15_000);
  const [schema] = usePolled<{ parameters: Spec[] }>(`${runUrl(run.file)}/schema`, 600_000);
  const [game, setGame] = useState<number | null>(null);
  const [kind, setKind] = useState<'all' | 'population' | 'yardstick' | 'stockfish'>('all');
  const [member, setMember] = useState<number | null>(null);
  useEffect(() => {
    setGame(null);
    setMember(null);
  }, [base]);
  const row = run.generations.find((g) => g.number === number);
  if (error) return <section className="panel"><p className="muted">Could not load the generation: {error}</p></section>;
  if (!row) return <section className="panel"><p className="muted">Generation {number} is not finished.</p></section>;
  const games = detail?.games.filter((g) => (kind === 'all' || g.kind === kind) && (member === null || g.white === String(member) || g.black === String(member))) ?? [];
  // the material weights, the ones a reader wants to compare between members
  const material = (schema?.parameters ?? []).filter((p) => p.group === 'material');
  const valueOf = (values: Record<string, number>, spec: Spec) => values[spec.name] ?? spec.default;
  return (
    <>
      <section className="panel">
        <div className="run-head">
          <h3>Generation {number}</h3>
          <div className="actions">
            {number > 0 && <button type="button" className="btn ghost" onClick={() => goLab('run', run.file, 'gen', String(number - 1))}>← {number - 1}</button>}
            {number < run.generations.length - 1 && <button type="button" className="btn ghost" onClick={() => goLab('run', run.file, 'gen', String(number + 1))}>{number + 1} →</button>}
            <button type="button" className="btn primary" onClick={() => onPlay({ run: run.file, generation: number, variant: run.variantId, label: `Champion of ${run.name}, generation ${number}` })}>Play champion #{row.champion}</button>
          </div>
        </div>
        <p className="muted">
          {detail ? `${detail.members.length} members, ` : ''}{row.games} games. Champion #{row.champion} scored {pct(row.championScore)} among its peers.
          {row.stats.games > 0 && <> {pct((row.stats.whiteWins + row.stats.blackWins) / row.stats.games)} of the games were decided
            (White won {row.stats.whiteWins}, Black {row.stats.blackWins}, {row.stats.draws} draws, {row.stats.capped} hit the move limit), {Math.round(row.stats.averagePlies / 2)} moves on average.</>}
        </p>
        <KeepButton key={`${base}-${row.champion}`} url={`${base}/keep`} member={row.champion} />
        {row.yardsticks.length > 0 && (
          <>
            <h4>The champion against the yardsticks</h4>
            <div className="table-wrap">
              <table data-testid="yardsticks">
                <thead><tr><th>Against</th><th>Depth</th><th>Games</th><th>Score</th><th>Elo (95%)</th></tr></thead>
                <tbody>
                  {row.yardsticks.map((y) => (
                    <tr key={y.opponent + y.depth}>
                      <td>{player(y.label)}</td><td>{y.depth}</td><td>+{y.wins} ={y.draws} -{y.losses}</td><td>{pct(y.fraction)}</td>
                      <td className={y.eloLow > 0 ? 'up' : y.eloHigh < 0 ? 'down' : ''}>{elo(y)} ({Math.round(y.eloLow)} to {Math.round(y.eloHigh)})</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </section>

      <section className="panel">
        <h4>Standings</h4>
        <p className="muted">Every member's score this generation{material.length > 0 ? ', and what it thinks each piece is worth' : ''}. Click a member to see only its games; its weights can be downloaded as a parameter file.</p>
        <div className="table-wrap">
          <table data-testid="standings">
            <thead>
              <tr><th>Member</th><th>Games</th><th>Score</th>
                {detail?.standings.some((s) => s.stockfish !== undefined) && <th>vs Stockfish {detail.standings.find((s) => s.stockfishLevel)?.stockfishLevel}</th>}
                {material.map((m) => <th key={m.name} title={m.description}>{m.name.replace(/^material\./, '')}</th>)}<th></th></tr>
            </thead>
            <tbody>
              {detail?.standings.map((s) => {
                const values = detail.members[s.member]?.values ?? {};
                return (
                  <tr key={s.member} className={(s.member === member ? 'on ' : '') + (s.member === row.champion ? 'champion' : '')} onClick={() => setMember(member === s.member ? null : s.member)}>
                    <td>#{s.member}{s.member === row.champion ? ' ★' : ''}</td>
                    <td>{s.games}</td>
                    <td>{pct(s.score)}</td>
                    {detail.standings.some((x) => x.stockfish !== undefined) && <td>{s.stockfish === undefined ? '' : pct(s.stockfish)}</td>}
                    {material.map((m) => <td key={m.name}>{valueOf(values, m)}</td>)}
                    <td><a className="link" href={`${base}/members/${s.member}`} download onClick={(e) => e.stopPropagation()}>weights</a></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>

      <section className="panel">
        <div className="run-head">
          <h4>Games{member !== null ? ` of #${member}` : ''}</h4>
          <div className="seg small" role="group" aria-label="Which games">
            {(['all', 'population', 'yardstick', 'stockfish'] as const).filter((k) => k === 'all' || detail?.games.some((g) => g.kind === k)).map((k) => (
              <button key={k} type="button" className={kind === k ? 'on' : ''} onClick={() => setKind(k)}>{k === 'all' ? 'All' : k === 'population' ? 'Members' : k === 'yardstick' ? 'Yardsticks' : 'Stockfish'}</button>
            ))}
          </div>
        </div>
        <div className="games-replay">
          <div className="table-wrap games">
            <table data-testid="games">
              <thead><tr><th>White</th><th>Black</th><th>Opening</th><th>Depth</th><th>Result</th><th>How</th><th>Moves</th></tr></thead>
              <tbody>
                {games.map((g) => (
                  <tr key={g.index} className={(g.index === game ? 'on ' : '') + g.kind} onClick={() => setGame(g.index)}>
                    <td>{player(g.white)}</td><td>{player(g.black)}</td><td>{g.opening}</td><td>{g.depth ?? ''}</td>
                    <td>{resultText(g.result)}</td><td className="muted">{reasonText(g.reason)}</td><td>{Math.ceil(g.plies / 2)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {game !== null && <GameReplay url={`${base}/games/${game}`} />}
        </div>
      </section>
    </>
  );
}

function GameReplay({ url }: { url: string }) {
  const [replay, setReplay] = useState<Replay | null>(null);
  const [ply, setPly] = useState(0);
  useEffect(() => {
    let live = true;
    setReplay(null);
    fetch(url).then((r) => r.json()).then((r: Replay) => {
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
      <p><strong>{player(replay.white)}</strong> – <strong>{player(replay.black)}</strong> · {replay.opening} · {resultText(replay.result)} ({reasonText(replay.reason)})</p>
      <div className="replay-board">
        <Board fen={fen} orientation="white" legal={EMPTY} lastMove={ply === 0 ? null : replay.moves[ply - 1].uci}
          checkSquares={[]} hint={null} onMove={() => {}} onSelect={() => {}} onIllegal={() => {}}
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

/** Keeps the generation's champion in the hall of fame, with an optional note. */
function KeepButton({ url, member }: { url: string; member: number }) {
  const [note, setNote] = useState('');
  const [kept, setKept] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const keep = async () => {
    setError(null);
    const r = await fetch(url, { method: 'POST', body: JSON.stringify({ member, note }) });
    if (r.ok) setKept(((await r.json()) as { name: string }).name);
    else setError(await r.text());
  };
  if (kept) return <p className="muted" data-testid="kept">Kept in the hall of fame as <code>hof:{kept}</code>.</p>;
  return (
    <div className="keep">
      <input id="keep-note" type="text" placeholder="Note (optional)" value={note} onChange={(e) => setNote(e.target.value)} aria-label="Note" />
      <button type="button" className="btn" onClick={keep}>Keep champion #{member} in the hall of fame</button>
      {error && <span className="muted">{error}</span>}
    </div>
  );
}
