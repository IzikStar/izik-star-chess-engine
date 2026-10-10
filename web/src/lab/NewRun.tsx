import { useEffect, useMemo, useState } from 'react';
import { get, goLab, post, usePolled, type Algorithm, type AlgorithmOption, type FameEntry, type Job, type VariantRow } from './api';

/** The run's settings as the form holds them (strings, so a half-typed number does not jump). */
interface Form {
  name: string;
  variant: string;
  algorithm: string;
  generations: string;
  depth: string;
  deepDepth: string;
  deepShareFirst: string;
  deepShareLast: string;
  openingsPerPairing: string;
  openingSource: 'book' | 'random';
  openingPlies: string;
  randomOpenings: string;
  maxPlies: string;
  variety: string;
  threads: string;
  seed: string;
  yardsticks: string[];
  yardstickEvery: string;
  yardstickOpenings: string;
  stockfishFrom: string;
  memberStockfishOpenings: string;
  options: Record<string, string>;
  /** "worker": the run waits for the owner's PC (lab.Worker) and plays there; its file stays on this server. */
  where: 'server' | 'worker';
}

const DEFAULTS: Form = {
  name: '', variant: 'antichess', algorithm: 'evolution.FromZero', generations: '30', depth: '3', deepDepth: '4',
  deepShareFirst: '10', deepShareLast: '40', openingsPerPairing: '2', openingSource: 'random', openingPlies: '4',
  randomOpenings: '50', maxPlies: '300', variety: '20', threads: String(Math.max(1, (navigator.hardwareConcurrency || 4) - 1)),
  seed: '1', yardsticks: ['zero'], yardstickEvery: '5', yardstickOpenings: '20', stockfishFrom: '10', memberStockfishOpenings: '0',
  options: {}, where: 'server',
};

const STORE_KEY = 'lab.newRun';

function restore(): Form {
  try {
    const saved = localStorage.getItem(STORE_KEY);
    return saved ? { ...DEFAULTS, ...(JSON.parse(saved) as Partial<Form>) } : DEFAULTS;
  } catch {
    return DEFAULTS;
  }
}

/** The new-run form: every setting of a run with a line saying what it does, and what it adds up to. */
export function NewRun({ job }: { job: Job | null }) {
  const [form, setForm] = useState<Form>(restore);
  const [algorithms, setAlgorithms] = useState<Algorithm[]>([]);
  const [variants, setVariants] = useState<VariantRow[]>([]);
  const [fame] = usePolled<{ entries: FameEntry[] }>('/api/lab/fame', 60_000);
  const [error, setError] = useState<string | null>(null);
  const [starting, setStarting] = useState(false);
  const set = (patch: Partial<Form>) => setForm((f) => ({ ...f, ...patch }));

  useEffect(() => {
    get<{ algorithms: Algorithm[] }>('/api/lab/algorithms').then((r) => setAlgorithms(r.algorithms)).catch((e) => setError(String(e)));
    get<{ variants: VariantRow[] }>('/api/variants').then((r) => setVariants(r.variants)).catch(() => {});
  }, []);
  useEffect(() => {
    try {
      localStorage.setItem(STORE_KEY, JSON.stringify(form));
    } catch {
      // nothing to do
    }
  }, [form]);

  const algorithm = algorithms.find((a) => a.className === form.algorithm) ?? algorithms[0];
  const chess = form.variant === 'chess';
  // Fairy-Stockfish knows the built-in variants and the made ones the server can write a config for
  const fairy = variants.length === 0 || variants.some((v) => v.id === form.variant && (v.fairy ?? v.builtIn));
  // outside chess there is no opening book and no Stockfish
  useEffect(() => {
    if (!chess) {
      setForm((f) => ({
        ...f,
        openingSource: 'random',
        yardsticks: f.yardsticks.filter((y) => !y.startsWith('sf:')),
        memberStockfishOpenings: '0',
        algorithm: algorithms.find((a) => a.className === f.algorithm)?.chessOnly ? 'evolution.FromZero' : f.algorithm,
      }));
    }
  }, [chess, algorithms]);

  useEffect(() => {
    if (!fairy) {
      setForm((f) => (f.yardsticks.includes('fsf') ? { ...f, yardsticks: f.yardsticks.filter((y) => y !== 'fsf') } : f));
    }
  }, [fairy]);

  const n = (s: string, fallback = 0) => (s.trim() === '' || Number.isNaN(Number(s)) ? fallback : Number(s));
  const population = n(form.options.population ?? algorithm?.options.find((o) => o.key === 'population')?.default ?? '0')
    || (form.algorithm === 'evolution.MaterialExperiment' ? 16 : 8);
  const pairings = (population * (population - 1)) / 2;
  const gamesPerGeneration = pairings * n(form.openingsPerPairing) * 2 + population * n(form.memberStockfishOpenings) * 2;
  const yardstickGenerations = n(form.yardstickEvery) > 0 ? Math.floor((n(form.generations) - 1) / n(form.yardstickEvery)) + 1 : 0;
  const yardstickGames = yardstickGenerations * form.yardsticks.length * n(form.yardstickOpenings) * 2;
  const totalGames = gamesPerGeneration * n(form.generations) + yardstickGames;
  const estimate = useMemo(() => {
    // a rough speed, from measured antichess and chess games on three threads: seconds per game by depth
    const perGame: Record<number, number> = { 1: 0.05, 2: 0.1, 3: 0.3, 4: 0.5, 5: 0.9, 6: 2.5, 7: 8 };
    const d = n(form.depth, 3);
    const dd = n(form.deepDepth);
    const share = dd > 0 ? (n(form.deepShareFirst) + n(form.deepShareLast)) / 200 : 0;
    const base = (perGame[d] ?? 8) * (1 - share) + (dd > 0 ? (perGame[dd] ?? 8) * share : 0);
    const threads = Math.max(1, n(form.threads, 3));
    return (totalGames * base * (chess ? 1.6 : 1)) / threads * 3 / 3;
  }, [form, totalGames, chess]);

  // the population is an essential; the algorithm's other settings wait under Advanced settings
  const essentialOptions = algorithm?.options.filter((o) => o.key === 'population') ?? [];
  const otherOptions = algorithm?.options.filter((o) => o.key !== 'population') ?? [];
  const option = (o: AlgorithmOption) => <OptionField key={o.key} option={o} value={form.options[o.key] ?? ''} onChange={(v) => set({ options: { ...form.options, [o.key]: v } })} />;

  const toggleYardstick = (y: string) => set({ yardsticks: form.yardsticks.includes(y) ? form.yardsticks.filter((x) => x !== y) : [...form.yardsticks, y] });

  const start = async () => {
    setStarting(true);
    setError(null);
    try {
      const body = {
        name: form.name,
        where: form.where,
        algorithm: form.algorithm,
        variant: form.variant,
        settings: {
          generations: n(form.generations), depth: n(form.depth), deepDepth: n(form.deepDepth),
          deepShareFirst: n(form.deepShareFirst), deepShareLast: n(form.deepShareLast),
          openingsPerPairing: n(form.openingsPerPairing), openingPlies: form.openingSource === 'book' ? 0 : n(form.openingPlies),
          randomOpenings: n(form.randomOpenings), maxPlies: n(form.maxPlies), variety: n(form.variety), threads: n(form.threads),
          seed: n(form.seed), yardsticks: form.yardsticks, yardstickEvery: n(form.yardstickEvery),
          yardstickOpenings: n(form.yardstickOpenings), stockfishFrom: n(form.stockfishFrom),
          memberStockfishOpenings: n(form.memberStockfishOpenings),
        },
        options: form.options,
      };
      const r = await post<{ file: string }>('/api/lab/runs', body);
      goLab('run', r.file);
    } catch (e) {
      setError(String((e as Error).message ?? e));
    } finally {
      setStarting(false);
    }
  };

  const num = (key: keyof Form, label: string, help: string, min = 0, max = 100000, step = 1) => (
    <label className="setting">
      <span className="setting-label">{label}</span>
      <input type="number" aria-label={label} value={form[key] as string} min={min} max={max} step={step} onChange={(e) => set({ [key]: e.target.value } as Partial<Form>)} />
      <span className="help">{help}</span>
    </label>
  );

  return (
    <div className="lab-screen new-run" data-testid="new-run">
      <section className="panel">
        <h2>New run</h2>
        <p className="muted">A run breeds sets of evaluation weights: every generation the members play each other, the best go on,
          and their children fill the next generation. Hover a setting for its explanation, or read How it works.</p>
        {job?.running && <p className="error">A run is playing ({job.name}). Stop it on the Runs screen before starting another.</p>}
        <div className="setting-grid">
          <label className="setting wide">
            <span className="setting-label">Name</span>
            <input type="text" aria-label="Name" value={form.name} placeholder="e.g. Antichess from zero" onChange={(e) => set({ name: e.target.value })} />
            <span className="help">How the run is listed. Its file is named after it.</span>
          </label>
          <label className="setting">
            <span className="setting-label">Game</span>
            <select aria-label="Game" value={form.variant} onChange={(e) => set({ variant: e.target.value })}>
              {variants.filter((v) => v.builtIn).map((v) => <option key={v.id} value={v.id}>{v.name}</option>)}
              {variants.some((v) => !v.builtIn) && <optgroup label="My variants">{variants.filter((v) => !v.builtIn).map((v) => <option key={v.id} value={v.id}>{v.name}</option>)}</optgroup>}
              {variants.length === 0 && <option value={form.variant}>{form.variant}</option>}
            </select>
            <span className="help">Chess keeps the tuned chess evaluation (499 weights). Any other game evolves an evaluation built from its pieces: what each piece is worth, its mobility, and a value per square.</span>
          </label>
          <label className="setting">
            <span className="setting-label">Where it plays</span>
            <select aria-label="Where it plays" value={form.where} onChange={(e) => set({ where: e.target.value as Form['where'] })}>
              <option value="server">Here, on this server</option>
              <option value="worker">On my PC</option>
            </select>
            <span className="help">On my PC: the run waits here until your PC (IzikStar Chess started with IZIKSTAR_SERVER set) picks it up, plays it with the PC's cores, and sends every generation back. The run lives here either way. Threads are the cores of whichever computer plays it.</span>
          </label>
        </div>
      </section>

      <section className="panel">
        <h3>Algorithm</h3>
        <div className="setting-grid">
          <label className="setting wide">
            <span className="setting-label">Algorithm</span>
            <select aria-label="Algorithm" value={form.algorithm} onChange={(e) => set({ algorithm: e.target.value, options: {} })}>
              {algorithms.map((a) => <option key={a.className} value={a.className} disabled={a.chessOnly && !chess}>{a.name}{a.chessOnly ? ' (chess only)' : ''}</option>)}
            </select>
            <span className="help">{algorithm?.about}</span>
          </label>
        </div>
        {essentialOptions.length > 0 && (
          <div className="setting-grid">
            {essentialOptions.map(option)}
          </div>
        )}
        {algorithm && algorithm.options.length === 0 && <p className="muted">This algorithm has no settings of its own.</p>}
        {essentialOptions.length === 0 && otherOptions.length > 0 && <p className="muted">Its settings are under Advanced settings below.</p>}
      </section>

      <section className="panel">
        <h3>Games</h3>
        <div className="setting-grid">
          {num('generations', 'Generations', 'How many generations to play. A stopped run can be resumed up to this number.', 1, 10000)}
          {num('depth', 'Search depth', 'How many plies each side thinks ahead in every game. Deeper is stronger and slower: each ply costs about three times the time.', 1, 8)}
          {num('threads', 'Threads', 'Games played at the same time. One below your core count leaves the game page usable.', 1, 64)}
        </div>
      </section>

      <section className="panel">
        <h3>Measuring the champion</h3>
        <p className="muted">Every few generations the champion plays fixed opponents (yardsticks), so progress is measured against something that does not move. That score is what the charts show.</p>
        <div className="setting-grid">
          <div className="setting wide">
            <span className="setting-label">Yardsticks</span>
            <div className="checks">
              {([['default', chess ? 'Default weights (tuned-v1)' : 'Default weights of this game', 'Where tuning starts: the chess weights the app plays with, or a variant\'s piece values.'],
                ['classic', 'Classic weights', 'The hand-written chess weights.'],
                ['zero', 'All-zero weights', 'An engine that knows only the rules (and mate). Beating it is the first sign of life for a run from zero.'],
                ['random', 'Random mover', 'Plays any legal move (it only takes a win it sees one move ahead). The floor: unlike all-zero weights it does not search, so a young run still has something to beat.'],
                ['sf:auto', 'Stockfish (auto level)', 'Stockfish held to a UCI_Elo level that moves with the champion: up after a score above 70%, down below 30%. Chess only.'],
                ['fsf', 'Fairy-Stockfish (20,000 nodes)', 'The strongest open engine for variants, at full strength with 20,000 positions a move. The ceiling to measure against in antichess, king of the hill, three-check and most made variants (an invented piece goes to it as Betza text). Needs Fairy-Stockfish in engine/.'],
              ] as [string, string, string][]).map(([key, label, help]) => (
                <label key={key} className="check" title={help}>
                  <input type="checkbox" aria-label={label} checked={form.yardsticks.includes(key)} disabled={((key === 'sf:auto' || key === 'classic') && !chess) || (key === 'fsf' && !fairy)} onChange={() => toggleYardstick(key)} />
                  {label}
                </label>
              ))}
              {fame?.entries.map((e) => (
                <label key={e.name} className="check" title={e.yardsticks.join('\n') || e.reason}>
                  <input type="checkbox" checked={form.yardsticks.includes('hof:' + e.name)} onChange={() => toggleYardstick('hof:' + e.name)} />
                  Hall of fame: {e.name}
                </label>
              ))}
            </div>
            <span className="help">The first checked one is the one the progress chart follows. A hall of fame entry only works for a run of the same game.</span>
          </div>
        </div>
      </section>

      <details className="panel advanced" data-testid="advanced-settings">
        <summary><h3>Advanced settings</h3><span className="muted">deep games, openings, move limit, variety, seed, how the champion is measured{otherOptions.length > 0 ? ', and the algorithm\'s other settings' : ''}</span></summary>
        {otherOptions.length > 0 && (
          <>
            <h4>Algorithm</h4>
            <div className="setting-grid">
              {otherOptions.map(option)}
            </div>
          </>
        )}
        <h4>Games</h4>
        <div className="setting-grid">
          {num('deepDepth', 'Deep depth', 'Some games are played deeper, so weights that only pay off with more lookahead get a chance. 0 plays none.', 0, 8)}
          {num('deepShareFirst', 'Deep share, first generation (%)', 'What share of the games the first generation plays at the deep depth.', 0, 100)}
          {num('deepShareLast', 'Deep share, last generation (%)', '... and the last generation; it grows evenly in between, so later generations are judged more carefully.', 0, 100)}
          {num('openingsPerPairing', 'Openings per pairing', 'Each pair of members plays this many openings, each once with each colour. More openings, less luck.', 1, 50)}
          <label className="setting">
            <span className="setting-label">Openings from</span>
            <select aria-label="Openings from" value={form.openingSource} disabled={!chess} onChange={(e) => set({ openingSource: e.target.value as Form['openingSource'] })}>
              <option value="random">Random moves</option>
              <option value="book" disabled={!chess}>The chess opening book</option>
            </select>
            <span className="help">Chess can start from a suite of 50 balanced book openings. Every other game starts from a few random moves, so the games differ.</span>
          </label>
          {form.openingSource === 'random' && num('openingPlies', 'Random opening moves', 'How many random half-moves each opening has. Four keeps the games sane but different.', 1, 20)}
          {form.openingSource === 'random' && num('randomOpenings', 'Number of openings', 'How many random openings the run draws (with the seed below) and rotates through.', 1, 500)}
          {num('maxPlies', 'Move limit (plies)', 'A game still going after this many half-moves is stopped and counted as a draw.', 10, 1000)}
          {num('variety', 'Variety (centipawns)', 'Among moves scoring within this much of the best, the engine picks one at random, so the same two members do not replay the same game. 0 always plays the best move.', 0, 200)}
          {num('seed', 'Seed', 'The run\'s randomness. The same seed, settings and algorithm replay the same run.', 0, 1000000000)}
        </div>
        <h4>Measuring the champion</h4>
        <div className="setting-grid">
          {num('yardstickEvery', 'Measure every N generations', 'How often the champion plays the yardsticks (and always after the last generation). 0 never.', 0, 1000)}
          {num('yardstickOpenings', 'Yardstick openings', 'Openings of each yardstick match, each with both colours. 20 openings = 40 games, an Elo interval of about ±120.', 0, 200)}
          {chess && num('stockfishFrom', 'Stockfish from generation', 'Stockfish yardsticks only start at this generation, so early generations do not waste time losing to it.', 0, 10000)}
          {chess && num('memberStockfishOpenings', 'Every member against Stockfish (openings)', 'Every member of every generation plays Stockfish over this many openings, both colours; the algorithm can use the scores. 0 skips it. Chess only.', 0, 50)}
        </div>
      </details>

      <section className="panel summary">
        <h3>What it adds up to</h3>
        <p>
          <strong>{population}</strong> members → <strong>{pairings}</strong> pairings → <strong>{gamesPerGeneration.toLocaleString()}</strong> games a generation,
          plus <strong>{yardstickGames.toLocaleString()}</strong> yardstick games: <strong>{totalGames.toLocaleString()}</strong> games in all,
          roughly <strong>{estimate < 90 ? `${Math.round(estimate)} s` : estimate < 5400 ? `${Math.round(estimate / 60)} min` : `${(estimate / 3600).toFixed(1)} h`}</strong> on {n(form.threads, 1)} threads
          <span className="muted"> (a rough guess from measured game times; chess games take longer than antichess ones).</span>
        </p>
        {error && <p className="error" data-testid="new-run-error">{error}</p>}
        <div className="actions">
          <button type="button" className="btn primary" disabled={starting || !!job?.running} onClick={start}>Start the run</button>
          <button type="button" className="btn ghost" onClick={() => setForm(DEFAULTS)}>Reset to defaults</button>
        </div>
      </section>
    </div>
  );
}

function OptionField({ option, value, onChange }: { option: AlgorithmOption; value: string; onChange: (v: string) => void }) {
  if (option.choices) {
    if (option.multiple) {
      const chosen = (value || option.default).split(',').map((s) => s.trim()).filter(Boolean);
      return (
        <div className="setting">
          <span className="setting-label">{option.label}</span>
          <div className="checks">
            {option.choices.map((c) => (
              <label key={c} className="check">
                <input type="checkbox" checked={chosen.includes(c)} onChange={() => onChange((chosen.includes(c) ? chosen.filter((x) => x !== c) : [...chosen, c]).join(','))} />
                {c}
              </label>
            ))}
          </div>
          <span className="help">{option.help}</span>
        </div>
      );
    }
    return (
      <label className="setting">
        <span className="setting-label">{option.label}</span>
        <select aria-label={option.label} value={value || option.default} onChange={(e) => onChange(e.target.value)}>
          {option.choices.map((c) => <option key={c} value={c}>{c}</option>)}
        </select>
        <span className="help">{option.help}</span>
      </label>
    );
  }
  return (
    <label className="setting">
      <span className="setting-label">{option.label}</span>
      <input type="number" aria-label={option.label} value={value === '' ? option.default : value} min={option.min} max={option.max}
        step={Number(option.default) % 1 !== 0 || (option.max ?? 1) <= 1 ? 0.01 : 1} onChange={(e) => onChange(e.target.value)} />
      <span className="help">{option.help} ({option.min} to {option.max})</span>
    </label>
  );
}
