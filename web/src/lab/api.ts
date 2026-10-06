import { useEffect, useState } from 'react';

// The Lab's view of web.LabApi and web.LabJobs: the types the server sends, and polling.

export interface Score { wins: number; draws: number; losses: number; fraction: number; elo: number; eloLow: number; eloHigh: number }
export interface Settings {
  generations: number; depth: number; openingsPerPairing: number; variety: number; maxPlies: number; threads: number; seed: number;
  yardstickEvery: number; yardstickOpenings: number; yardsticks?: string[]; stockfishFrom?: number;
  deepDepth?: number; deepShareFirst?: number; deepShareLast?: number; memberStockfishOpenings?: number;
  variant?: string; openingPlies?: number; randomOpenings?: number; algorithmOptions?: Record<string, string>;
}
export interface RunSummary {
  file: string; name: string; algorithm: string; startedAt: string; settings: Settings; variantName: string; variantId: string;
  generationsDone: number; lastYardstick?: Score;
}
export interface YardstickResult extends Score { opponent: string; label: string; depth: number }
export interface Stats { games: number; whiteWins: number; blackWins: number; draws: number; averagePlies: number; capped: number }
export interface GenerationRow {
  number: number; champion: number; championScore: number; games: number; finishedAt: string; stats: Stats;
  yardstick?: Score; yardsticks: YardstickResult[];
}
export interface Weight { name: string; group: string; description: string; default: number; min: number; max: number; values: number[] }
export interface RunDetail extends Omit<RunSummary, 'generationsDone' | 'lastYardstick'> {
  generations: GenerationRow[]; weights: Weight[]; parameters: number; nextGeneration: number; nextGenerationGames: number;
}
export interface GameRow { index: number; kind: 'population' | 'yardstick' | 'stockfish'; white: string; black: string; opening: string; result: string; reason: string; plies: number; depth?: number }
export interface Standing { member: number; score: number; games: number; stockfish?: number; stockfishLevel?: number }
export interface Member { index: number; values: Record<string, number> }
export interface GenerationDetail { number: number; members: Member[]; standings: Standing[]; games: GameRow[] }
export interface Spec { name: string; group: string; default: number; min: number; max: number; description: string }
export interface FameEntry { name: string; reason: string; savedAt: string; run: string | null; runName: string | null; generation: number; member: number; yardsticks: string[]; games: number }
export interface Replay { white: string; black: string; opening: string; result: string; reason: string; startFen: string; moves: { uci: string; san: string; fenAfter: string }[] }
export interface AlgorithmOption { key: string; label: string; help: string; default: string; min?: number; max?: number; choices?: string[]; multiple?: boolean }
export interface Algorithm { className: string; name: string; about: string; chessOnly: boolean; options: AlgorithmOption[] }
export interface Job {
  running: boolean; file?: string; name?: string; generation?: number; gamesDone?: number; gamesPlanned?: number;
  startedAt?: string; stopping?: boolean; finished?: boolean; error?: string;
}
export interface VariantRow { id: string; name: string; builtIn: boolean; goal: string; fairy?: boolean }

export const REFRESH_MS = 4000;

export async function get<T>(url: string): Promise<T> {
  const r = await fetch(url);
  if (!r.ok) throw new Error(`${r.status} ${await r.text()}`);
  return r.json() as Promise<T>;
}

export async function post<T>(url: string, body?: unknown, method = 'POST'): Promise<T> {
  const r = await fetch(url, { method, body: body === undefined ? undefined : JSON.stringify(body) });
  if (!r.ok) throw new Error(await r.text());
  return r.json() as Promise<T>;
}

/** Re-fetches every few seconds, so a run that is still going fills in on its own. `tick` forces a refresh. */
export function usePolled<T>(url: string | null, every = REFRESH_MS): [T | null, string | null, () => void] {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);
  useEffect(() => {
    setData(null);
    if (!url) return;
    let live = true;
    const load = () => get<T>(url).then((d) => live && (setData(d), setError(null)), (e) => live && setError(String(e)));
    load();
    const timer = window.setInterval(load, every);
    return () => {
      live = false;
      window.clearInterval(timer);
    };
  }, [url, every, tick]);
  return [data, error, () => setTick((t) => t + 1)];
}

export const runUrl = (file: string) => `/api/lab/runs/${encodeURIComponent(file)}`;

/** How the page names a player: a member by its number, a yardstick by what it is. */
export const player = (name: string): string =>
  name === 'default' ? 'Default weights'
    : name === 'classic' ? 'Classic weights'
      : name === 'zero' ? 'All-zero weights'
        : name === 'champion' ? 'Champion'
          : name === 'random' ? 'Random mover'
          : name === 'fsf' ? 'Fairy-Stockfish'
          : /^fsf:?\d/.test(name) ? `Fairy-Stockfish ${Number(name.replace(/^fsf:?/, '')).toLocaleString('en')} nodes`
          : /^sf\d/.test(name) ? `Stockfish ${name.slice(2)}`
            : /^\d+$/.test(name) ? `#${name}` : name.replace(/^hof:/, 'Hall of fame: ');
export const resultText = (r: string) => (r === 'WHITE_WINS' ? '1–0' : r === 'BLACK_WINS' ? '0–1' : '½–½');
export const signed = (n: number) => `${Math.round(n) > 0 ? '+' : ''}${Math.round(n)}`;
export const elo = (s: Score) => signed(s.elo);
export const pct = (f: number) => `${Math.round(f * 100)}%`;
export const algorithmName = (className: string) => className.replace(/^.*\./, '');
export const reasonText = (reason: string) => reason.toLowerCase().replace(/_/g, ' ').replace('ply cap', 'move limit');

/** "2 h 05 min", "7 min", "40 s" between two ISO times. */
export function duration(from: string, to: string): string {
  const s = Math.max(0, Math.round((Date.parse(to) - Date.parse(from)) / 1000));
  if (s < 90) return `${s} s`;
  const m = Math.round(s / 60);
  if (m < 90) return `${m} min`;
  return `${Math.floor(m / 60)} h ${String(m % 60).padStart(2, '0')} min`;
}

/** The hash below "#lab": [] for the overview, ["run", file], ["run", file, "gen", "3"], ["new"], ["fame"], ["guide"]. */
export function labRoute(): string[] {
  const hash = location.hash.slice(1);
  if (!hash.startsWith('lab/')) return [];
  return hash.slice(4).split('/').map(decodeURIComponent);
}

export function goLab(...parts: string[]) {
  location.hash = parts.length === 0 ? 'lab' : 'lab/' + parts.map(encodeURIComponent).join('/');
}
