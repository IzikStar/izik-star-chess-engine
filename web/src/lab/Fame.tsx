import type { Champion } from '../protocol';
import { goLab, usePolled, type FameEntry } from './api';

/** Every individual kept from every run: play it, download its games, see how it did. */
export function Fame({ onPlay }: { onPlay: (champion: Champion) => void }) {
  const [fame, error] = usePolled<{ entries: FameEntry[] }>('/api/lab/fame', 10_000);
  if (error) return <section className="panel"><p className="muted">Could not load the hall of fame: {error}</p></section>;
  if (!fame) return <section className="panel"><p className="muted">Loading…</p></section>;
  return (
    <div className="lab-screen">
      <section className="panel fame" data-testid="fame">
        <h2>Hall of fame</h2>
        <p className="muted">Champions that beat a yardstick (their whole Elo interval above zero), each run's last champion, and any member you keep by hand
          land here, with their weights and games. A kept entry can be a yardstick for a later run (<code>hof:NAME</code>), and the arena plays it by that name.</p>
        {fame.entries.length === 0 && <p className="muted">Nothing kept yet.</p>}
        {fame.entries.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Name</th><th>Why it is here</th><th>From</th><th>Against the yardsticks</th><th>Kept</th><th></th></tr></thead>
              <tbody>
                {fame.entries.map((e) => (
                  <tr key={e.name}>
                    <td className="mono">{e.name}</td>
                    <td>{e.reason}</td>
                    <td>{e.runName ? <button type="button" className="link" onClick={() => e.run && goLab('run', e.run, 'gen', String(e.generation))}>{e.runName}, generation {e.generation}, #{e.member}</button> : ''}</td>
                    <td>{e.yardsticks.length === 0 ? <span className="muted">not measured</span> : <ul className="plain">{e.yardsticks.map((y) => <li key={y}>{y}</li>)}</ul>}</td>
                    <td>{new Date(e.savedAt).toLocaleString()}</td>
                    <td className="fame-actions">
                      <button type="button" className="link" onClick={() => onPlay({ run: `hof:${e.name}`, generation: 0, label: `Hall of fame: ${e.name}` })}>Play</button>
                      {e.games > 0 && <a className="link" href={`/api/lab/fame/${encodeURIComponent(e.name)}/pgn`} download>PGN ({e.games})</a>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
