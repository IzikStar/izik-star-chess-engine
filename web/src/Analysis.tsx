import { useCallback, useEffect, useRef, useState } from 'react';
import type { Color } from './protocol';
import { StockfishInstall } from './StockfishInstall';

// Game analysis with Stockfish (web.AnalysisApi, analysis.GameAnalyzer): the server judges every
// position; this file only shows what it said. Formulas: docs/game-analysis.md.

export interface Score {
  cp?: number;
  mate?: number;
}

export type Quality = 'best' | 'excellent' | 'good' | 'inaccuracy' | 'mistake' | 'blunder';

export interface MoveReport {
  uci: string;
  san: string;
  color: Color;
  bestUci: string | null;
  bestSan: string | null;
  cpLoss: number;
  winChanceLoss: number;
  accuracy: number;
  quality: Quality;
}

export interface SideReport {
  moves: number;
  acpl: number;
  accuracy: number;
  elo: number;
  inaccuracies: number;
  mistakes: number;
  blunders: number;
}

export interface Report {
  depth: number;
  /** evals[i]: the position after i plies, from White's side. */
  evals: Score[];
  moves: MoveReport[];
  white: SideReport;
  black: SideReport;
}

export type AnalysisState =
  | { status: 'idle' }
  | { status: 'running'; key: string; progress: number; total: number }
  | { status: 'done'; key: string; report: Report }
  | { status: 'error'; key: string; error: string; noStockfish?: boolean };

/** Which game an analysis belongs to: its start and moves. */
export function gameKey(startFen: string, moves: string[]): string {
  return startFen + '|' + moves.join(' ');
}

/** Starts an analysis on the server and polls it until the report is in. */
export function useAnalysis() {
  const [state, setState] = useState<AnalysisState>({ status: 'idle' });
  const run = useRef(0);

  useEffect(() => () => {
    run.current++; // stop polling on unmount
  }, []);

  const start = useCallback(async (startFen: string, moves: string[]) => {
    const mine = ++run.current;
    const key = gameKey(startFen, moves);
    setState({ status: 'running', key, progress: 0, total: moves.length + 1 });
    const fail = (error: string, noStockfish = false) => {
      if (run.current === mine) setState({ status: 'error', key, error, noStockfish });
    };
    try {
      const res = await fetch('/api/analysis', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ startFen, moves }) });
      const body = await res.json();
      if (!res.ok) return fail(body.error ?? `The server answered ${res.status}`, body.noStockfish === true);
      const id: number = body.id;
      while (run.current === mine) {
        await new Promise((r) => setTimeout(r, 250));
        const poll = await (await fetch(`/api/analysis/${id}`)).json();
        if (run.current !== mine) return;
        if (poll.error) return fail(poll.error);
        if (poll.done) {
          setState({ status: 'done', key, report: poll.report });
          return;
        }
        setState({ status: 'running', key, progress: poll.progress, total: poll.total });
      }
    } catch {
      fail('Could not reach the game server.');
    }
  }, []);

  const clear = useCallback(() => {
    run.current++;
    setState({ status: 'idle' });
  }, []);

  return { analysis: state, start, clear };
}

/** Lichess's winning chances for White, 0-100 (the same curve the server uses). */
export function whiteWinChance(s: Score): number {
  if (s.mate !== undefined) return s.mate > 0 ? 100 : s.mate < 0 ? 0 : 50;
  const cp = s.cp!;
  if (Math.abs(cp) >= 10000) return cp > 0 ? 100 : 0;
  return 50 + 50 * (2 / (1 + Math.exp(-0.00368208 * cp)) - 1);
}

/** "+1.3", "−0.4", "M3", "−M2", "1-0". */
export function scoreText(s: Score): string {
  if (s.mate !== undefined) return (s.mate < 0 ? '−' : '') + 'M' + Math.abs(s.mate);
  const cp = s.cp!;
  if (Math.abs(cp) >= 10000) return cp > 0 ? '1-0' : '0-1';
  const pawns = (Math.abs(cp) / 100).toFixed(1);
  return cp > 0 ? '+' + pawns : cp < 0 ? '−' + pawns : '0.0';
}

export const QUALITY: Record<Quality, { label: string; glyph: string }> = {
  best: { label: 'Best move', glyph: '★' },
  excellent: { label: 'Excellent', glyph: '' },
  good: { label: 'Good', glyph: '' },
  inaccuracy: { label: 'Inaccuracy', glyph: '?!' },
  mistake: { label: 'Mistake', glyph: '?' },
  blunder: { label: 'Blunder', glyph: '??' },
};

/** The bar beside the board: White's share grows from White's side of the board. */
export function EvalBar({ score, orientation }: { score: Score | null; orientation: Color }) {
  const white = score ? whiteWinChance(score) : 50;
  const text = score ? scoreText(score) : '…';
  const whiteLeads = white >= 50;
  return (
    <div className={'eval-bar' + (orientation === 'black' ? ' flipped' : '')} data-testid="eval-bar" data-score={text}
      role="meter" aria-label="Evaluation" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(white)} aria-valuetext={text}>
      <div className="eval-white" style={{ height: `${white}%` }} />
      <span className={'eval-text ' + (whiteLeads ? 'on-white' : 'on-black')}>{text}</span>
    </div>
  );
}

/** White's winning chances over the game; click to go to a position. */
export function EvalGraph({ evals, ply, onPick }: { evals: Score[]; ply: number; onPick: (ply: number) => void }) {
  const W = 300;
  const H = 70;
  const n = Math.max(1, evals.length - 1);
  const x = (i: number) => (i / n) * W;
  const y = (s: Score) => H - (whiteWinChance(s) / 100) * H;
  const line = evals.map((s, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(s).toFixed(1)}`).join(' ');
  return (
    <svg className="eval-graph" viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" data-testid="eval-graph" role="img"
      aria-label="White's winning chances, move by move"
      onClick={(e) => {
        const r = e.currentTarget.getBoundingClientRect();
        onPick(Math.round(((e.clientX - r.left) / r.width) * n));
      }}>
      <rect className="graph-black" x={0} y={0} width={W} height={H} />
      <path className="graph-white" d={`${line} L${W},${H} L0,${H} Z`} />
      <line className="graph-mid" x1={0} x2={W} y1={H / 2} y2={H / 2} />
      <line className="graph-ply" x1={x(ply)} x2={x(ply)} y1={0} y2={H} />
    </svg>
  );
}

/** Running, failed, or the report: accuracy, average loss and Elo estimate for each side. */
export function AnalysisPanel({ analysis, ply, onPick, onRetry }: {
  analysis: AnalysisState;
  ply: number;
  onPick: (ply: number) => void;
  onRetry: () => void;
}) {
  if (analysis.status === 'idle') return null;
  if (analysis.status === 'running') {
    return (
      <section className="analysis" aria-label="Analysis" data-testid="analysis">
        <div className="status"><span className="spinner" aria-hidden="true" /> Stockfish is analysing… {analysis.progress} / {analysis.total} positions</div>
      </section>
    );
  }
  if (analysis.status === 'error') {
    return (
      <section className="analysis" aria-label="Analysis" data-testid="analysis">
        {analysis.noStockfish ? (
          <StockfishInstall why="Analysis needs Stockfish, and it is not installed yet." onInstalled={onRetry} />
        ) : (
          <>
            <p className="warn-note">{analysis.error}</p>
            <button type="button" className="btn" onClick={onRetry}>Try again</button>
          </>
        )}
      </section>
    );
  }
  const { report } = analysis;
  const move = ply > 0 ? report.moves[ply - 1] : null;
  const sides: [string, SideReport][] = [['White', report.white], ['Black', report.black]];
  return (
    <section className="analysis" aria-label="Analysis" data-testid="analysis">
      <table className="analysis-table">
        <thead>
          <tr><th /><th>Accuracy</th><th>Avg. loss</th><th title="Estimated from the average centipawn loss: 3100 × e^(−0.01 × loss)">Elo est.</th><th title="Inaccuracies / mistakes / blunders">?! / ? / ??</th></tr>
        </thead>
        <tbody>
          {sides.map(([name, s]) => (
            <tr key={name} data-testid={`analysis-${name.toLowerCase()}`}>
              <th>{name}</th>
              <td>{s.moves ? `${s.accuracy.toFixed(1)}%` : '–'}</td>
              <td>{s.moves ? s.acpl.toFixed(0) : '–'}</td>
              <td className="elo">{s.moves ? s.elo : '–'}</td>
              <td>{s.inaccuracies} / {s.mistakes} / {s.blunders}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <EvalGraph evals={report.evals} ply={ply} onPick={onPick} />
      <p className="analysis-move" data-testid="analysis-move">
        {move ? (
          <>
            <span className={'q-' + move.quality}><strong>{move.san}</strong> · {QUALITY[move.quality].label}</span>
            {move.quality !== 'best' && move.bestSan && <> · best was <strong>{move.bestSan}</strong></>}
            {' '}· {scoreText(report.evals[ply])}
          </>
        ) : (
          <>Start position · {scoreText(report.evals[0])}</>
        )}
      </p>
      <p className="muted small">Stockfish, depth {report.depth}. The Elo estimate is rough, more so in short games.</p>
    </section>
  );
}
