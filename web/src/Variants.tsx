import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { PieceSvg } from './pieces';

// "Variants" (Phase 6 R5c): the variant designer. Look at the built-in variants, make your own from
// a copy, and invent pieces: click the squares a piece reaches, or type its Betza text. The server
// keeps made variants as files (web.VariantsApi, game.VariantStore) and checks them on save.

type Kind = 'LEAP' | 'SLIDE';
type Mode = 'MOVE' | 'CAPTURE' | 'BOTH';
type Symmetry = 'ONE' | 'SIDEWAYS' | 'ALL';
type Goal = 'CHECKMATE' | 'LOSE_EVERYTHING' | 'KING_OF_THE_HILL' | 'CHECKS';

export interface Atom {
  kind: Kind;
  forward: number;
  right: number;
  symmetry: Symmetry;
  mode: Mode;
  /** For a slide: at most this many steps; 0 = to the edge. */
  range: number;
  firstMoveOnly: boolean;
}

export interface PieceDef {
  name: string;
  letter: string;
  value: number;
  royal: boolean;
  promotesTo: string;
  enPassant: boolean;
  castlingRole: 'NONE' | 'KING' | 'ROOK';
  atoms: Atom[];
  betza?: string;
}

export interface VariantDef {
  id: string;
  name: string;
  width: number;
  height: number;
  start: string;
  goal: Goal;
  checksToWin: number;
  forcedCapture: boolean;
  castling: boolean;
  pieces: PieceDef[];
  builtIn?: boolean;
}

interface VariantRow {
  id: string;
  name: string;
  builtIn: boolean;
  goal: Goal;
}

const GOALS: { id: Goal; name: string }[] = [
  { id: 'CHECKMATE', name: 'Checkmate' },
  { id: 'LOSE_EVERYTHING', name: 'Lose everything' },
  { id: 'KING_OF_THE_HILL', name: 'King to the centre' },
  { id: 'CHECKS', name: 'Give N checks' },
];

const MODES: { id: Mode; name: string }[] = [
  { id: 'BOTH', name: 'Move + capture' },
  { id: 'MOVE', name: 'Move only' },
  { id: 'CAPTURE', name: 'Capture only' },
];

const SYMMETRIES: { id: Symmetry; name: string }[] = [
  { id: 'ALL', name: 'All 8 ways' },
  { id: 'SIDEWAYS', name: 'Mirror left/right' },
  { id: 'ONE', name: 'Just this one' },
];

/** How far a leap may go in the designer: up to 3 squares each way. */
const REACH = 3;
const FILES = 'abcdefgh';

/** The {forward, right} offsets an atom covers, each once (ai.piece.Atom.offsets). */
export function offsets(a: Atom): [number, number][] {
  const seen = new Map<string, [number, number]>();
  const add = (f: number, r: number) => seen.set(`${f},${r}`, [f, r]);
  if (a.symmetry === 'ONE') add(a.forward, a.right);
  else if (a.symmetry === 'SIDEWAYS') {
    add(a.forward, a.right);
    add(a.forward, -a.right);
  } else {
    for (const [f, r] of [[a.forward, a.right], [a.right, a.forward]]) {
      for (const sf of [1, -1]) for (const sr of [1, -1]) add(f * sf, r * sr);
    }
  }
  return [...seen.values()];
}

function gcd(a: number, b: number): number {
  return b === 0 ? Math.abs(a) : gcd(b, a % b);
}

/** The atom covering {forward, right} in the designer grid, if any (a slide covers each of its steps). */
function atomAt(atoms: Atom[], f: number, r: number): number {
  return atoms.findIndex((a) => offsets(a).some(([df, dr]) => {
    if (a.kind === 'LEAP') return df === f && dr === r;
    for (let k = 1; k <= REACH * 2; k++) {
      if (a.range > 0 && k > a.range) break;
      if (df * k === f && dr * k === r) return true;
    }
    return false;
  }));
}

type Reach = 'move' | 'capture' | 'both';

/** Where a piece on an empty 8x8 board at (file, rank) of white can go, by square name. */
export function reach(atoms: Atom[], file: number, rank: number, moved: boolean): Map<string, Reach> {
  const out = new Map<string, Reach>();
  const mark = (fl: number, rk: number, mode: Mode) => {
    const sq = FILES[fl] + (rk + 1);
    const was = out.get(sq);
    const now: Reach = mode === 'BOTH' ? 'both' : mode === 'MOVE' ? 'move' : 'capture';
    out.set(sq, !was || was === now ? now : 'both');
  };
  for (const a of atoms) {
    if (a.firstMoveOnly && moved) continue;
    for (const [f, r] of offsets(a)) {
      for (let k = 1; ; k++) {
        const fl = file + r * k;
        const rk = rank + f * k;
        if (fl < 0 || fl > 7 || rk < 0 || rk > 7) break;
        mark(fl, rk, a.mode);
        if (a.kind === 'LEAP' || (a.range > 0 && k >= a.range)) break;
      }
    }
  }
  return out;
}

/** The FEN board field as square name -> letter. */
function placementOf(fen: string): Record<string, string> {
  const board: Record<string, string> = {};
  fen.trim().split(/\s+/)[0].split('/').forEach((row, i) => {
    let file = 0;
    for (const c of row) {
      if (/\d/.test(c)) file += Number(c);
      else board[FILES[file++] + (8 - i)] = c;
    }
  });
  return board;
}

function withPlacement(fen: string, board: Record<string, string>): string {
  const rows: string[] = [];
  for (let rank = 8; rank >= 1; rank--) {
    let row = '';
    let empty = 0;
    for (const f of FILES) {
      const c = board[f + rank];
      if (c) {
        row += (empty || '') + c;
        empty = 0;
      } else empty++;
    }
    rows.push(row + (empty || ''));
  }
  const rest = fen.trim().split(/\s+/).slice(1);
  return [rows.join('/'), ...(rest.length ? rest : ['w', '-', '-', '0', '1'])].join(' ');
}

/** "My Amazon chess" -> "my-amazon-chess". */
export function slug(name: string): string {
  return name.toLowerCase().normalize('NFKD').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || 'variant';
}

/** An id for a variant called {@code name} that no other variant has. */
function freeId(name: string, taken: Set<string>): string {
  const base = slug(name);
  let id = base;
  for (let n = 2; taken.has(id); n++) id = `${base}-${n}`;
  return id;
}

async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, { ...init, headers: { 'Content-Type': 'application/json' } });
  if (res.status === 204) return undefined as T;
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(body.error ?? `${res.status} ${res.statusText}`);
  return body as T;
}

function MiniBoard({ board, marks, onSquare, label, testId }: {
  board: Record<string, string>;
  marks?: Map<string, Reach>;
  onSquare?: (square: string) => void;
  label: string;
  testId?: string;
}) {
  const squares = [];
  for (let rank = 8; rank >= 1; rank--) {
    for (let f = 0; f < 8; f++) {
      const sq = FILES[f] + rank;
      const c = board[sq];
      const mark = marks?.get(sq);
      squares.push(
        <button type="button" key={sq} data-square={sq} aria-label={sq + (c ? ' ' + c : '') + (mark ? ' ' + mark : '')}
          className={'mini-sq ' + ((f + rank) % 2 === 1 ? 'dark' : 'light') + (mark ? ' r-' + mark : '')}
          onClick={onSquare ? () => onSquare(sq) : undefined} tabIndex={onSquare ? 0 : -1}>
          {c && <PieceSvg code={(c === c.toUpperCase() ? 'w' : 'b') + c.toUpperCase()} />}
        </button>,
      );
    }
  }
  return <div className="mini-board" role="group" aria-label={label} data-testid={testId}>{squares}</div>;
}

/** Click squares to say where the piece goes; the tools say how. */
function MoveGrid({ atoms, letter, onChange }: { atoms: Atom[]; letter: string; onChange: (atoms: Atom[]) => void }) {
  const [kind, setKind] = useState<Kind>('LEAP');
  const [mode, setMode] = useState<Mode>('BOTH');
  const [symmetry, setSymmetry] = useState<Symmetry>('ALL');
  const [range, setRange] = useState(0);
  const [firstMove, setFirstMove] = useState(false);

  const click = (f: number, r: number) => {
    const i = atomAt(atoms, f, r);
    if (i >= 0) {
      onChange(atoms.filter((_, j) => j !== i));
      return;
    }
    const g = kind === 'SLIDE' ? gcd(f, r) : 1;
    onChange([...atoms, { kind, forward: f / g, right: r / g, symmetry, mode, range: kind === 'SLIDE' ? range : 0, firstMoveOnly: firstMove }]);
  };

  const cells = [];
  for (let f = REACH; f >= -REACH; f--) {
    for (let r = -REACH; r <= REACH; r++) {
      const centre = f === 0 && r === 0;
      const i = centre ? -1 : atomAt(atoms, f, r);
      const a = i >= 0 ? atoms[i] : null;
      cells.push(
        <button type="button" key={`${f},${r}`} disabled={centre}
          className={'grid-sq' + (centre ? ' centre' : '') + (a ? ' r-' + a.mode.toLowerCase() + (a.kind === 'SLIDE' ? ' slide' : '') + (a.firstMoveOnly ? ' first' : '') : '')}
          aria-label={centre ? 'the piece' : `${f} forward, ${r} right${a ? ': ' + a.kind.toLowerCase() + ' ' + a.mode.toLowerCase() : ''}`}
          data-offset={`${f},${r}`} onClick={() => click(f, r)}>
          {centre ? <PieceSvg code={'w' + letter} /> : a?.kind === 'SLIDE'
            ? <span className="ray" style={{ transform: `rotate(${Math.atan2(-f, r)}rad)` }}>→</span> : ''}
        </button>,
      );
    }
  }

  return (
    <div className="move-grid-wrap">
      <div className="tools">
        <div className="seg small" role="group" aria-label="Kind">
          <button type="button" className={kind === 'LEAP' ? 'on' : ''} onClick={() => setKind('LEAP')}>Jump</button>
          <button type="button" className={kind === 'SLIDE' ? 'on' : ''} onClick={() => setKind('SLIDE')}>Slide</button>
        </div>
        <div className="seg small" role="group" aria-label="Mode">
          {MODES.map((m) => <button type="button" key={m.id} className={mode === m.id ? 'on' : ''} onClick={() => setMode(m.id)}>{m.name}</button>)}
        </div>
        <div className="seg small" role="group" aria-label="Directions">
          {SYMMETRIES.map((s) => <button type="button" key={s.id} className={symmetry === s.id ? 'on' : ''} onClick={() => setSymmetry(s.id)}>{s.name}</button>)}
        </div>
        <div className="tool-row">
          {kind === 'SLIDE' && (
            <label className="inline">Steps
              <select value={range} onChange={(e) => setRange(Number(e.target.value))} aria-label="Slide steps">
                <option value={0}>to the edge</option>
                {[2, 3, 4, 5, 6].map((n) => <option key={n} value={n}>at most {n}</option>)}
              </select>
            </label>
          )}
          <label className="inline"><input type="checkbox" checked={firstMove} onChange={(e) => setFirstMove(e.target.checked)} /> First move only</label>
        </div>
      </div>
      <div className="move-grid" role="group" aria-label="Where the piece goes (up is forward)" data-testid="move-grid">{cells}</div>
      <p className="muted small">Up is forward for the piece's owner. Click a square to add a move with the tools above; click a lit square to take it away.</p>
    </div>
  );
}

function PieceEditor({ piece, letters, readOnly, onChange, onAtoms, onBetza, onRemove }: {
  piece: PieceDef;
  letters: string[];
  readOnly: boolean;
  onChange: (p: PieceDef) => void;
  /** New moves for the piece, from its Betza text (applied to the piece as it is by then). */
  onAtoms: (atoms: Atom[]) => void;
  /** The piece's moves as Betza text, once the server has written them ('' when Betza has no letter for them). */
  onBetza: (text: string) => void;
  onRemove: () => void;
}) {
  const [text, setText] = useState(piece.betza ?? '');
  const [betzaError, setBetzaError] = useState<string | null>(null);
  /** The Betza text last read or written, so leaving the field unchanged reads nothing. */
  const synced = useRef(piece.betza ?? '');
  const [from, setFrom] = useState('d4');
  const [moved, setMoved] = useState(false);

  // the atoms changed (or another piece was picked): write them as Betza
  useEffect(() => {
    let live = true;
    api<{ text: string }>('/api/betza', { method: 'POST', body: JSON.stringify({ atoms: piece.atoms }) })
      .then((r) => { if (live) { setText(r.text); synced.current = r.text; setBetzaError(null); onBetza(r.text); } })
      .catch(() => { if (live) { setText(''); synced.current = ''; onBetza(''); } });
    return () => { live = false; };
  }, [piece.atoms]);

  const readText = () => {
    if (text === synced.current) return;
    synced.current = text;
    api<{ atoms: Atom[] }>('/api/betza', { method: 'POST', body: JSON.stringify({ text }) })
      .then((r) => { setBetzaError(null); onAtoms(r.atoms); })
      .catch((e: Error) => setBetzaError(e.message));
  };

  const file = FILES.indexOf(from[0]);
  const rank = Number(from[1]) - 1;
  const marks = useMemo(() => reach(piece.atoms, file, rank, moved), [piece.atoms, file, rank, moved]);
  const otherLetters = letters.filter((l) => l !== piece.letter);

  return (
    <section className="panel piece-editor" aria-label={`Piece ${piece.name}`} data-testid="piece-editor">
      <div className="run-head">
        <h3>{piece.name || 'New piece'}</h3>
        {!readOnly && <button type="button" className="btn ghost" onClick={onRemove}>Remove piece</button>}
      </div>
      <fieldset disabled={readOnly} className="plain">
      <div className="form-grid">
        <label>Name<input value={piece.name} onChange={(e) => onChange({ ...piece, name: e.target.value })} /></label>
        <label>Letter<input value={piece.letter} maxLength={1} aria-label="Letter"
          onChange={(e) => {
            const l = e.target.value.toUpperCase();
            if (/^[A-Z]$/.test(l) && !otherLetters.includes(l)) onChange({ ...piece, letter: l });
          }} /></label>
        <label>Value<input type="number" value={piece.value} step={10} onChange={(e) => onChange({ ...piece, value: Number(e.target.value) })} /></label>
        <label>Promotes to<input value={piece.promotesTo} placeholder="none" aria-label="Promotes to"
          onChange={(e) => onChange({ ...piece, promotesTo: e.target.value.toUpperCase().replace(/[^A-Z]/g, '') })} /></label>
        <label className="inline"><input type="checkbox" checked={piece.royal} onChange={(e) => onChange({ ...piece, royal: e.target.checked })} /> Royal (must not be captured)</label>
        <label className="inline"><input type="checkbox" checked={piece.enPassant} onChange={(e) => onChange({ ...piece, enPassant: e.target.checked })} /> En passant</label>
        <label>Castling
          <select value={piece.castlingRole} onChange={(e) => onChange({ ...piece, castlingRole: e.target.value as PieceDef['castlingRole'] })}>
            <option value="NONE">No</option>
            <option value="KING">Castles (like a king)</option>
            <option value="ROOK">Castled with (like a rook)</option>
          </select>
        </label>
      </div>
      </fieldset>
      <div className="designer">
        <fieldset disabled={readOnly} className="plain">
          <MoveGrid atoms={piece.atoms} letter={piece.letter} onChange={(atoms) => onChange({ ...piece, atoms })} />
        </fieldset>
        <div className="preview">
          <MiniBoard board={{ [from]: piece.letter }} marks={marks} label="Preview: click a square to move the piece"
            onSquare={setFrom} testId="piece-preview" />
          <label className="inline"><input type="checkbox" checked={moved} onChange={(e) => setMoved(e.target.checked)} /> It has moved already</label>
          <p className="muted small legend"><span className="key r-both" /> move or capture <span className="key r-move" /> move only <span className="key r-capture" /> capture only</p>
        </div>
      </div>
      <label className="betza">Betza
        <input value={text} aria-label="Betza" spellCheck={false} readOnly={readOnly}
          onChange={(e) => setText(e.target.value)} onBlur={readOnly ? undefined : readText}
          onKeyDown={(e) => { if (e.key === 'Enter' && !readOnly) readText(); }} />
      </label>
      {betzaError && <p className="error small">{betzaError}</p>}
      <p className="muted small">Betza is the usual way to write fairy pieces: W one step straight, F one diagonal, N the knight, R/B/Q riders, a digit limits the steps (R2), m = move only, c = capture only, f/b/l/r = forward/back/left/right, i = first move only. QN is the Amazon.</p>
    </section>
  );
}

interface HealthReport {
  games: number;
  whiteWins: number;
  blackWins: number;
  draws: number;
  averagePlies: number;
  shortest: number;
  longest: number;
  movesPerTurn: number;
  whiteScore: number;
  decisiveShare: number;
  endings: Record<string, number>;
  notes: string[];
}

interface HealthState {
  running: boolean;
  cancelled?: boolean;
  variantId?: string;
  variantName?: string;
  games?: number;
  depth?: number;
  done?: number;
  report?: HealthReport;
  error?: string;
}

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
  PLY_CAP: 'Still going at move 150',
};

const pct = (n: number, of: number) => (of ? Math.round((100 * n) / of) : 0) + '%';

/** Self-play of the built-in engine: is the variant balanced, decisive, long enough, rich in choices? */
function HealthCheck({ variant }: { variant: VariantDef }) {
  const [games, setGames] = useState(100);
  const [depth, setDepth] = useState(2);
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
    api<HealthState>('/api/health', { method: 'POST', body: JSON.stringify({ variant, games, depth }) })
      .then(setState)
      .catch((e: Error) => setError(e.message));
  };

  const mine = state && state.variantId === variant.id ? state : null;
  const r = mine?.report;
  return (
    <section className="panel health" aria-label="Health check" data-testid="health">
      <div className="run-head">
        <h3>Health check</h3>
        <div className="filters">
          <label className="inline">Games
            <select value={games} onChange={(e) => setGames(Number(e.target.value))} aria-label="Games">
              {[20, 50, 100, 200].map((n) => <option key={n} value={n}>{n}</option>)}
            </select>
          </label>
          <label className="inline">Depth
            <select value={depth} onChange={(e) => setDepth(Number(e.target.value))} aria-label="Depth">
              {[1, 2, 3].map((n) => <option key={n} value={n}>{n}</option>)}
            </select>
          </label>
          {mine?.running
            ? <button type="button" className="btn" onClick={() => api<HealthState>('/api/health', { method: 'DELETE' }).then(setState)}>Stop</button>
            : <button type="button" className="btn primary" onClick={start}>Run</button>}
        </div>
      </div>
      <p className="muted small">The engine plays this variant against itself (a few random moves first, so the games differ) and the games show whether it is fun to play: balanced, decisive, not over at once, with moves to choose from. Unsaved changes are checked too.</p>
      {error && <p className="error" role="alert">{error}</p>}
      {state?.running && !mine && <p className="muted small">A check of {state.variantName} is running; Run stops it.</p>}
      {mine?.running && (
        <div className="progress" role="progressbar" aria-valuemin={0} aria-valuemax={mine.games} aria-valuenow={mine.done}>
          <div style={{ width: pct(mine.done ?? 0, mine.games ?? 1) }} />
          <span>{mine.done} of {mine.games} games</span>
        </div>
      )}
      {mine?.error && <p className="error">{mine.error}</p>}
      {r && (
        <div className="health-report" data-testid="health-report">
          <div className="score-bar" aria-label={`White ${r.whiteWins}, draws ${r.draws}, black ${r.blackWins}`}>
            <div className="w" style={{ flex: r.whiteWins }} />
            <div className="d" style={{ flex: r.draws }} />
            <div className="b" style={{ flex: r.blackWins }} />
          </div>
          <table>
            <tbody>
              <tr><th>White wins</th><td>{r.whiteWins} ({pct(r.whiteWins, r.games)})</td><th>Games</th><td>{r.games} at depth {mine?.depth}</td></tr>
              <tr><th>Black wins</th><td>{r.blackWins} ({pct(r.blackWins, r.games)})</td><th>Length</th><td>{Math.round(r.averagePlies / 2)} moves on average ({Math.ceil(r.shortest / 2)} to {Math.ceil(r.longest / 2)})</td></tr>
              <tr><th>Draws</th><td>{r.draws} ({pct(r.draws, r.games)})</td><th>Choices</th><td>{r.movesPerTurn.toFixed(1)} moves per turn</td></tr>
            </tbody>
          </table>
          <p className="small"><b>Endings:</b> {Object.entries(r.endings).sort((a, b) => b[1] - a[1]).map(([k, n]) => `${ENDINGS[k] ?? k} ${n}`).join(' · ')}</p>
          {r.notes.length
            ? <ul className="notes">{r.notes.map((n) => <li key={n}>{n}</li>)}</ul>
            : <p className="ok">Nothing stands out: balanced, decisive enough, and with choices.</p>}
        </div>
      )}
    </section>
  );
}

function blankPiece(letters: string[]): PieceDef {
  const letter = 'ACDEFGHIJLMOSTUVWXYZ'.split('').find((l) => !letters.includes(l)) ?? 'Z';
  return { name: 'New piece', letter, value: 300, royal: false, promotesTo: '', enPassant: false, castlingRole: 'NONE', atoms: [] };
}

function Editor({ start, taken, onSaved, onDeleted, onCopy, onPlay }: {
  start: VariantDef;
  /** Opens the New game dialog on this variant. */
  onPlay: (id: string) => void;
  /** The ids already used: a new variant's id follows its name, away from these. */
  taken: Set<string>;
  onSaved: (v: VariantDef) => void;
  onDeleted: () => void;
  onCopy: (v: VariantDef) => void;
}) {
  const [v, setV] = useState(start);
  const [picked, setPicked] = useState(0);
  const [paint, setPaint] = useState<string>('');
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  /** Changed since it was opened or saved. */
  const [dirty, setDirty] = useState(false);
  /** Not saved yet: the id (its file's name) still follows the name. */
  const [fresh, setFresh] = useState(!taken.has(start.id));
  const readOnly = !!start.builtIn;
  const letters = v.pieces.map((p) => p.letter);
  const board = useMemo(() => placementOf(v.start), [v.start]);

  const edit = (next: VariantDef) => {
    setV(next);
    setSaved(false);
    setDirty(true);
  };
  const setPiece = (i: number, p: PieceDef) => {
    const old = v.pieces[i];
    let start = v.start;
    let pieces = v.pieces.map((q, j) => (j === i ? p : q));
    if (old.letter !== p.letter) {
      // a new letter: the start position and the promotions follow it
      const b = placementOf(start);
      for (const sq in b) {
        if (b[sq] === old.letter) b[sq] = p.letter;
        else if (b[sq] === old.letter.toLowerCase()) b[sq] = p.letter.toLowerCase();
      }
      start = withPlacement(start, b);
      pieces = pieces.map((q) => ({ ...q, promotesTo: q.promotesTo.replaceAll(old.letter, p.letter) }));
    }
    edit({ ...v, start, pieces });
  };

  const save = () => {
    setError(null);
    api<VariantDef>(`/api/variants/${encodeURIComponent(v.id)}`, { method: 'PUT', body: JSON.stringify(v) })
      .then((r) => { setSaved(true); setDirty(false); setFresh(false); onSaved(r); })
      .catch((e: Error) => setError(e.message));
  };

  const piece = v.pieces[picked];

  return (
    <div className="variant-editor" data-testid="variant-editor">
      <section className="panel">
        <div className="run-head">
          <h2>{v.name}</h2>
          <div className="filters">
            {!fresh && (
              <button type="button" className="btn" disabled={dirty} title={dirty ? 'Save it first' : undefined}
                onClick={() => onPlay(v.id)}>Play it</button>
            )}
            <button type="button" className="btn" onClick={() => onCopy(v)}>Make a copy</button>
            {!readOnly && <button type="button" className="btn ghost" onClick={() => {
              if (!confirm(`Delete ${v.name}? Games played with it keep their copy.`)) return;
              api(`/api/variants/${encodeURIComponent(v.id)}`, { method: 'DELETE' }).then(onDeleted).catch((e: Error) => setError(e.message));
            }}>Delete</button>}
            {!readOnly && <button type="button" className="btn primary" onClick={save}>{saved ? 'Saved' : 'Save'}</button>}
          </div>
        </div>
        {readOnly && <p className="muted small">A built-in variant: make a copy to change it.</p>}
        {error && <p className="error" role="alert">{error}</p>}
        <fieldset disabled={readOnly} className="plain">
          <div className="form-grid">
            <label>Name<input value={v.name} onChange={(e) => edit({ ...v, name: e.target.value, id: fresh ? freeId(e.target.value, taken) : v.id })} /></label>
            <label>Goal
              <select value={v.goal} aria-label="Goal" onChange={(e) => {
                const goal = e.target.value as Goal;
                edit({ ...v, goal, checksToWin: goal === 'CHECKS' ? v.checksToWin || 3 : 0 });
              }}>
                {GOALS.map((g) => <option key={g.id} value={g.id}>{g.name}</option>)}
              </select>
            </label>
            {v.goal === 'CHECKS' && (
              <label>Checks to win<input type="number" min={1} max={20} value={v.checksToWin}
                onChange={(e) => edit({ ...v, checksToWin: Math.max(1, Number(e.target.value)) })} /></label>
            )}
            <label className="inline"><input type="checkbox" checked={v.forcedCapture} onChange={(e) => edit({ ...v, forcedCapture: e.target.checked })} /> Captures are forced</label>
            <label className="inline"><input type="checkbox" checked={v.castling} onChange={(e) => edit({ ...v, castling: e.target.checked })} /> Castling</label>
          </div>
        </fieldset>
      </section>

      <section className="panel">
        <h3>Start position</h3>
        <div className="start-edit">
          <MiniBoard board={board} label="Start position" testId="start-board"
            onSquare={readOnly ? undefined : (sq) => {
              const b = { ...board };
              if (paint) b[sq] = paint;
              else delete b[sq];
              edit({ ...v, start: withPlacement(v.start, b) });
            }} />
          {!readOnly && (
            <div className="palette" role="group" aria-label="Piece to place">
              <p className="muted small">Pick a piece, then click squares to place it.</p>
              <div className="palette-row">
                {v.pieces.map((p) => [p.letter, p.letter.toLowerCase()]).flat().map((c) => (
                  <button type="button" key={c} className={'pal' + (paint === c ? ' on' : '')} aria-pressed={paint === c}
                    aria-label={(c === c.toUpperCase() ? 'white ' : 'black ') + c.toUpperCase()} onClick={() => setPaint(c)}>
                    <PieceSvg code={(c === c.toUpperCase() ? 'w' : 'b') + c.toUpperCase()} />
                  </button>
                ))}
                <button type="button" className={'pal erase' + (paint === '' ? ' on' : '')} aria-pressed={paint === ''} onClick={() => setPaint('')}>Empty</button>
              </div>
            </div>
          )}
        </div>
        <label className="fen">FEN
          <input value={v.start} disabled={readOnly} spellCheck={false} aria-label="Start FEN" onChange={(e) => edit({ ...v, start: e.target.value })} />
        </label>
      </section>

      <section className="panel">
        <div className="run-head">
          <h3>Pieces</h3>
          {!readOnly && v.pieces.length < 16 && (
            <button type="button" className="btn" onClick={() => {
              edit({ ...v, pieces: [...v.pieces, blankPiece(letters)] });
              setPicked(v.pieces.length);
            }}>Add a piece</button>
          )}
        </div>
        <div className="piece-list" role="group" aria-label="Pieces">
          {v.pieces.map((p, i) => (
            <button type="button" key={i} className={'piece-chip' + (i === picked ? ' on' : '')} aria-pressed={i === picked} onClick={() => setPicked(i)}>
              <span className="chip-icon"><PieceSvg code={'w' + p.letter} /></span>
              <span>{p.name}</span>
              <span className="muted small">{p.betza ?? ''}</span>
            </button>
          ))}
        </div>
      </section>

      <HealthCheck variant={v} />

      {piece && (
          <PieceEditor key={picked} piece={piece} letters={letters} readOnly={readOnly}
            onChange={(p) => setPiece(picked, p)}
            onAtoms={(atoms) => {
              setV((now) => ({ ...now, pieces: now.pieces.map((q, j) => (j === picked ? { ...q, atoms } : q)) }));
              setSaved(false);
              setDirty(true);
            }}
            onBetza={(text) => setV((now) => ({ ...now, pieces: now.pieces.map((q, j) => (j === picked ? { ...q, betza: text } : q)) }))}
            onRemove={() => {
              const letter = piece.letter;
              const b = placementOf(v.start);
              for (const sq in b) if (b[sq].toUpperCase() === letter) delete b[sq];
              edit({
                ...v,
                start: withPlacement(v.start, b),
                pieces: v.pieces.filter((_, j) => j !== picked).map((q) => ({ ...q, promotesTo: q.promotesTo.replaceAll(letter, '') })),
              });
              setPicked(0);
            }} />
      )}
    </div>
  );
}

export function Variants({ onPlay }: { onPlay: (id: string) => void }) {
  const [rows, setRows] = useState<VariantRow[] | null>(null);
  const [folder, setFolder] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState<VariantDef | null>(null);
  /** Changes when another variant is opened (not when the open one is renamed or saved). */
  const [openKey, setOpenKey] = useState(0);
  const openVariant = (v: VariantDef | null) => {
    setOpen(v);
    setOpenKey((k) => k + 1);
  };

  const load = useCallback(() => {
    api<{ folder: string; variants: VariantRow[] }>('/api/variants')
      .then((r) => { setRows(r.variants); setFolder(r.folder); })
      .catch((e: Error) => setError(e.message));
  }, []);
  useEffect(load, [load]);

  const show = (id: string) => {
    api<VariantDef>(`/api/variants/${encodeURIComponent(id)}`).then(openVariant).catch((e: Error) => setError(e.message));
  };

  const taken = useMemo(() => new Set(rows?.map((r) => r.id)), [rows]);
  const copyOf = (v: VariantDef) => {
    let name = v.builtIn ? `My ${v.name}` : `${v.name} copy`;
    for (let n = 2; taken.has(slug(name)); n++) name = (v.builtIn ? `My ${v.name}` : `${v.name} copy`) + ' ' + n;
    openVariant({ ...v, id: slug(name), name, builtIn: false });
  };

  useEffect(() => {
    if (rows && !open && rows.length) show(rows[0].id);
  }, [rows, open]);

  return (
    <main className="lab variants">
      <aside className="panel">
        <h2>Variants</h2>
        {error && <p className="error">{error}</p>}
        {!rows && !error && <p className="muted">Loading…</p>}
        {rows && (
          <ul className="variant-list" data-testid="variant-list">
            {rows.map((r) => (
              <li key={r.id}>
                <button type="button" className={'btn ghost' + (open?.id === r.id ? ' on' : '')} onClick={() => show(r.id)}>
                  {r.name}{r.builtIn && <span className="tag">Built in</span>}
                </button>
              </li>
            ))}
            {open && !rows.some((r) => r.id === open.id) && (
              <li><button type="button" className="btn ghost on">New variant<span className="tag">Not saved</span></button></li>
            )}
          </ul>
        )}
        {folder && <p className="muted small">Your variants are kept in <code>{folder}</code>.</p>}
      </aside>
      {open && (
        <Editor key={openKey} start={open} taken={taken}
          onSaved={(v) => { load(); setOpen(v); }}
          onDeleted={() => { openVariant(null); load(); }}
          onCopy={copyOf} onPlay={onPlay} />
      )}
    </main>
  );
}
