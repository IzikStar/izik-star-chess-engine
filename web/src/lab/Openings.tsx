import { useEffect, useState } from 'react';
import { Board } from '../Board';
import { Icon } from '../icons';
import { get, pct, runUrl, type OpeningBranch, type OpeningNode, type RunDetail, type Tally } from './api';

const EMPTY = new Map<string, string[]>();
const KINDS: [string, string][] = [['population', 'Members'], ['population,yardstick', 'Members + yardsticks'], ['population,yardstick,stockfish', 'All games']];
/** The generation chart has at most this many bars; generations are pooled into them. */
const BUCKETS = 20;

/**
 * A run's opening tree (web.LabApi tree): from a line of moves, which moves its players went on
 * with, how those games ended, and how the choice moved over the generations. Moves that were part
 * of the run's fixed openings are marked, since no one chose them.
 */
export function Openings({ run }: { run: RunDetail }) {
  const last = Math.max(0, run.nextGeneration < run.settings.generations ? run.nextGeneration : run.generations.length - 1);
  const [path, setPath] = useState<string[]>([]);
  const [from, setFrom] = useState(0);
  const [to, setTo] = useState(last);
  const [kinds, setKinds] = useState('population');
  const [node, setNode] = useState<OpeningNode | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [hover, setHover] = useState<string | null>(null);

  useEffect(() => {
    let live = true;
    const q = new URLSearchParams({ moves: path.join(','), from: String(from), to: String(to), kinds });
    get<OpeningNode>(`${runUrl(run.file)}/tree?${q}`).then((n) => live && (setNode(n), setError(null)), (e) => live && setError(String(e)));
    return () => {
      live = false;
    };
  }, [run.file, path, from, to, kinds]);

  const generations = Array.from({ length: last + 1 }, (_, i) => i);
  // the shown position is the last one loaded, which lags `path` while the next one loads
  const shown = node?.line.map((m) => m.uci) ?? [];
  const ply = shown.length;
  const number = (i: number) => (i % 2 === 0 ? `${i / 2 + 1}.` : '');
  const childNumber = ply % 2 === 0 ? `${ply / 2 + 1}.` : `${Math.ceil(ply / 2)}…`;

  return (
    <section className="panel openings" data-testid="openings">
      <div className="run-head">
        <h3>Opening tree</h3>
        <div className="actions tree-filters">
          <label className="pick"><span className="muted">Generations</span>
            <select aria-label="From generation" value={from} onChange={(e) => { const v = Number(e.target.value); setFrom(v); if (v > to) setTo(v); }}>
              {generations.map((g) => <option key={g} value={g}>{g}</option>)}
            </select>
            <span className="muted">to</span>
            <select aria-label="To generation" value={to} onChange={(e) => { const v = Number(e.target.value); setTo(v); if (v < from) setFrom(v); }}>
              {generations.map((g) => <option key={g} value={g}>{g}</option>)}
            </select>
          </label>
          <div className="seg small" role="group" aria-label="Which games">
            {KINDS.map(([k, label]) => <button key={k} type="button" className={kinds === k ? 'on' : ''} onClick={() => setKinds(k)}>{label}</button>)}
          </div>
        </div>
      </div>
      <p className="muted">
        Which moves the run's players chose, how those games ended, and how often each generation chose them. Click a move to follow it.
        {run.settings.openingPlies
          ? ` Every game started from ${run.settings.openingPlies} random moves, so the first ${run.settings.openingPlies} plies were not chosen.`
          : ' Every game started from an opening of the suite; those moves are marked "opening".'}
      </p>
      {error && <p className="error">Could not load the tree: {error}</p>}
      {node && (
        <div className="tree-layout">
          <div className="tree-board">
            <div className="replay-board">
              <Board fen={node.fen} orientation="white" legal={EMPTY} lastMove={ply === 0 ? null : node.line[ply - 1].uci}
                checkSquares={[]} hint={hover} onMove={() => {}} onSelect={() => {}} onIllegal={() => {}}
                premoveColor={null} premoves={[]} onPremove={() => {}} animate={false} />
            </div>
            <div className="nav" role="group" aria-label="Tree line">
              <button type="button" className="icon" aria-label="Start position" disabled={ply === 0} onClick={() => setPath([])}><Icon name="first" /></button>
              <button type="button" className="icon" aria-label="Back one move" disabled={ply === 0} onClick={() => setPath(shown.slice(0, -1))}><Icon name="prev" /></button>
            </div>
            <p className="sans mono" data-testid="tree-line">
              {ply === 0 ? <span className="muted">Start position</span> : node.line.map((m, i) => (
                <button key={i} type="button" className={'mv' + (i + 1 === ply ? ' current' : '')} onClick={() => setPath(shown.slice(0, i + 1))}>
                  {number(i)}{m.san}
                </button>
              ))}
            </p>
          </div>
          <div className="tree-moves">
            <p className="muted"><strong>{node.tally.games}</strong> games reached this position. <ResultBar tally={node.tally} /></p>
            {node.children.length === 0 ? (
              <p className="muted">{node.tally.games === 0 ? 'No game in these generations reached this position.' : 'The tree stops here.'}</p>
            ) : (
              <div className="table-wrap">
                <table className="tree" data-testid="tree">
                  <thead><tr><th>Move</th><th>Games</th><th>Result (White · draw · Black)</th><th title="Share of this position's games that went on with the move, generation by generation">Over the generations</th></tr></thead>
                  <tbody>
                    {node.children.map((c) => (
                      <tr key={c.uci} onClick={() => { setHover(null); setPath([...shown, c.uci]); }}
                        onMouseEnter={() => setHover(c.uci)} onMouseLeave={() => setHover(null)}>
                        <td className="mono"><button type="button" className="link" onFocus={() => setHover(c.uci)} onBlur={() => setHover(null)}>{childNumber}{c.san}</button>
                          {c.forced > 0 && <span className="tag" title={`${c.forced} of its ${c.tally.games} games had it in their fixed opening`}>{c.forced === c.tally.games ? 'opening' : `opening ${pct(c.forced / c.tally.games)}`}</span>}
                        </td>
                        <td>{c.tally.games} <span className="muted">({pct(c.tally.games / node.tally.games)})</span></td>
                        <td><ResultBar tally={c.tally} /></td>
                        <td><Trend branch={c} node={node} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}
    </section>
  );
}

/** White wins, draws and Black wins as one bar, with the shares as text for screen readers. */
function ResultBar({ tally: t }: { tally: Tally }) {
  if (t.games === 0) return null;
  const w = (100 * t.whiteWins) / t.games, d = (100 * t.draws) / t.games, b = (100 * t.blackWins) / t.games;
  const label = `White ${Math.round(w)}%, draws ${Math.round(d)}%, Black ${Math.round(b)}%`;
  return (
    <span className="result-bar" role="img" aria-label={label} title={label}>
      <span className="w" style={{ width: `${w}%` }}>{w >= 18 ? `${Math.round(w)}%` : ''}</span>
      <span className="d" style={{ width: `${d}%` }}>{d >= 18 ? `${Math.round(d)}%` : ''}</span>
      <span className="b" style={{ width: `${b}%` }}>{b >= 18 ? `${Math.round(b)}%` : ''}</span>
    </span>
  );
}

/** The move's share of the position's games, generation by generation (pooled into at most BUCKETS bars). */
function Trend({ branch, node }: { branch: OpeningBranch; node: OpeningNode }) {
  const n = node.byGeneration.length;
  const size = Math.max(1, Math.ceil(n / BUCKETS));
  const bars: { share: number; first: number; last: number }[] = [];
  for (let i = 0; i < n; i += size) {
    let mine = 0, all = 0;
    for (let j = i; j < Math.min(n, i + size); j++) {
      mine += branch.byGeneration[j];
      all += node.byGeneration[j];
    }
    bars.push({ share: all === 0 ? -1 : mine / all, first: node.firstGeneration + i, last: node.firstGeneration + Math.min(n, i + size) - 1 });
  }
  const W = 120, H = 24, bw = W / Math.max(1, bars.length);
  return (
    <svg className="trend" viewBox={`0 0 ${W} ${H}`} role="img" aria-label={`From ${pct(Math.max(0, bars[0]?.share ?? 0))} to ${pct(Math.max(0, bars.at(-1)?.share ?? 0))} of the games`}>
      {bars.map((b, i) => (
        <rect key={i} x={i * bw + 0.5} width={Math.max(1, bw - 1)} y={b.share < 0 ? H - 1 : H - Math.max(1, b.share * H)} height={b.share < 0 ? 1 : Math.max(1, b.share * H)}
          className={b.share < 0 ? 'none' : ''}>
          <title>{b.first === b.last ? `Generation ${b.first}` : `Generations ${b.first}–${b.last}`}: {b.share < 0 ? 'no games' : pct(b.share)}</title>
        </rect>
      ))}
    </svg>
  );
}
