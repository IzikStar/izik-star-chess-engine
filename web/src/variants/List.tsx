import { useMemo, useState } from 'react';
import { PieceSvg } from '../pieces';
import { goalsName, type VariantRow } from './model';

// The home screen of the variant designer (#variants): the player's variants first, grouped by
// family, with a search box and a "New variant" button; the built-in ones small, below.

/** "3 min ago", "yesterday", "12 Mar". */
export function ago(ms: number, now = Date.now()): string {
  if (!ms) return '';
  const s = Math.max(0, (now - ms) / 1000);
  if (s < 60) return 'just now';
  if (s < 3600) return `${Math.floor(s / 60)} min ago`;
  if (s < 86400) return `${Math.floor(s / 3600)} h ago`;
  if (s < 2 * 86400) return 'yesterday';
  if (s < 7 * 86400) return `${Math.floor(s / 86400)} days ago`;
  return new Date(ms).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: s > 300 * 86400 ? 'numeric' : undefined });
}

/** The start position, small; nothing for a board too big to read at this size. */
function Thumb({ row }: { row: VariantRow }) {
  const cells = useMemo(() => {
    const out: string[] = [];
    for (const r of row.start.trim().split(/\s+/)[0].split('/')) {
      for (const c of r.match(/\d+|./g) ?? []) {
        if (/^\d+$/.test(c)) for (let i = 0; i < Number(c); i++) out.push('');
        else out.push(c);
      }
    }
    return out;
  }, [row.start]);
  if (row.width > 12 || row.height > 12 || cells.length !== row.width * row.height) {
    return <span className="thumb thumb-size" aria-hidden="true">{row.width}×{row.height}</span>;
  }
  return (
    <span className="thumb" aria-hidden="true" style={{ gridTemplateColumns: `repeat(${row.width}, 1fr)`, aspectRatio: `${row.width} / ${row.height}` }}>
      {cells.map((c, i) => {
        const x = i % row.width;
        const y = Math.floor(i / row.width);
        return (
          <span key={i} className={'t-sq ' + ((x + y) % 2 === 1 ? 'dark' : 'light')}>
            {c && <PieceSvg code={(c === c.toUpperCase() ? 'w' : 'b') + c.toUpperCase()} />}
          </span>
        );
      })}
    </span>
  );
}

function matches(r: VariantRow, q: string): boolean {
  if (!q) return true;
  const hay = [r.name, r.id, r.family, r.notes, goalsName(r.goals)].join(' ').toLowerCase();
  return q.toLowerCase().split(/\s+/).every((w) => hay.includes(w));
}

export function VariantList({ rows, folder, error, onOpen, onNew }: {
  rows: VariantRow[] | null;
  folder: string;
  error: string | null;
  onOpen: (id: string) => void;
  onNew: () => void;
}) {
  const [query, setQuery] = useState('');
  const mine = (rows ?? []).filter((r) => !r.builtIn);
  const shown = mine.filter((r) => matches(r, query.trim()));
  const builtIns = (rows ?? []).filter((r) => r.builtIn && matches(r, query.trim()));
  const groups = useMemo(() => {
    const by = new Map<string, VariantRow[]>();
    for (const r of shown) {
      const f = r.family.trim();
      by.set(f, [...(by.get(f) ?? []), r]);
    }
    for (const list of by.values()) list.sort((a, b) => b.modified - a.modified);
    return [...by.entries()].sort(([a, la], [b, lb]) => (a === '' ? 1 : b === '' ? -1 : 0)
      || Math.max(...lb.map((r) => r.modified)) - Math.max(...la.map((r) => r.modified)) || a.localeCompare(b));
  }, [shown]);
  const now = Date.now();

  return (
    <section className="variant-home" data-testid="variant-list">
      <div className="home-head">
        <h2>Variants</h2>
        <input type="search" className="search" placeholder="Search by name, family or notes" aria-label="Search variants"
          value={query} onChange={(e) => setQuery(e.target.value)} />
        <button type="button" className="btn primary" onClick={onNew}>New variant</button>
      </div>
      {error && <p className="error" role="alert">{error}</p>}
      {!rows && !error && <p className="muted">Loading…</p>}
      {rows && (
        <>
          <h3 className="section-head">Your variants <span className="muted count">{mine.length}</span></h3>
          {mine.length === 0 && (
            <div className="panel empty-state">
              <p>You have not made a variant yet. Start from a chess board and change what you like: the pieces, how they move, the start position, the goal.</p>
              <button type="button" className="btn primary" onClick={onNew}>Make your first variant</button>
            </div>
          )}
          {mine.length > 0 && shown.length === 0 && <p className="muted">None of your variants matches “{query}”.</p>}
          {groups.map(([family, list]) => (
            <div key={family || '-'} className="family" data-testid="variant-family" data-family={family}>
              <h4 className="family-head">{family || 'No family'} <span className="muted count">{list.length}</span></h4>
              <div className="variant-cards">
                {list.map((r) => (
                  <button type="button" key={r.id} className="variant-card" data-id={r.id} onClick={() => onOpen(r.id)}>
                    <Thumb row={r} />
                    <span className="card-text">
                      <span className="card-name">{r.name}</span>
                      <span className="card-meta">{goalsName(r.goals)}</span>
                      <span className="card-meta">{r.pieces} piece{r.pieces === 1 ? '' : 's'} · {r.width}×{r.height}</span>
                      {r.modified > 0 && <span className="card-meta muted">Changed {ago(r.modified, now)}</span>}
                    </span>
                  </button>
                ))}
              </div>
            </div>
          ))}
          <div className="builtins">
            <h3 className="section-head small-head">Built-in <span className="muted count">{builtIns.length}</span></h3>
            <p className="muted small">Read-only. Open one to see its rules, or to start your own copy from it.</p>
            <div className="builtin-chips">
              {builtIns.map((r) => (
                <button type="button" key={r.id} className="btn ghost builtin-chip" onClick={() => onOpen(r.id)}>
                  {r.name}<span className="muted small"> · {goalsName(r.goals)}</span>
                </button>
              ))}
            </div>
          </div>
          {folder && <p className="muted small">Your variants are kept in <code>{folder}</code>.</p>}
        </>
      )}
    </section>
  );
}
