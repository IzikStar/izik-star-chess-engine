import { useEffect, useMemo, useRef, useState } from 'react';
import { PieceSvg } from '../pieces';
import { offsets, reach, type Atom, type Kind, type Mode, type Reach, type Symmetry } from '../reach';
import { PieceDrawerDialog } from './PieceDrawer';
import { api, type PieceDef } from './model';
import { PIECE_HINTS } from './rules';

// One piece of a variant: its settings, its pictures, and its moves (click the squares it reaches,
// or type its Betza text), with a preview board.

const MODES: { id: Mode; name: string; hint: string }[] = [
  { id: 'BOTH', name: 'Move + capture', hint: 'it may go there to an empty square or capture a piece there' },
  { id: 'MOVE', name: 'Move only', hint: 'only to an empty square, like a pawn going straight' },
  { id: 'CAPTURE', name: 'Capture only', hint: 'only to capture, like a pawn going diagonally' },
];

const SYMMETRIES: { id: Symmetry; name: string; hint: string }[] = [
  { id: 'ALL', name: 'All 8 ways', hint: 'the square you click and every rotation and mirror of it' },
  { id: 'SIDEWAYS', name: 'Mirror left/right', hint: 'the square you click and its mirror across the piece\'s file' },
  { id: 'ONE', name: 'Just this one', hint: 'only the square you click' },
];

/** How far a leap may go in the designer: up to 3 squares each way. */
const REACH = 3;
const FILES = 'abcdefgh';

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

export function MiniBoard({ board, marks, onSquare, onHover, label, testId }: {
  board: Record<string, string>;
  marks?: Map<string, Reach>;
  onSquare?: (square: string) => void;
  /** The square under the mouse, or null when it leaves the board. */
  onHover?: (square: string | null) => void;
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
          onClick={onSquare ? () => onSquare(sq) : undefined} tabIndex={onSquare ? 0 : -1}
          onMouseEnter={onHover ? () => onHover(sq) : undefined}>
          {c && <PieceSvg code={(c === c.toUpperCase() ? 'w' : 'b') + c.toUpperCase()} />}
        </button>,
      );
    }
  }
  return (
    <div className="mini-board" role="group" aria-label={label} data-testid={testId}
      onMouseLeave={onHover ? () => onHover(null) : undefined}>{squares}</div>
  );
}

/** The marks on the move grid, explained. */
function GridLegend() {
  return (
    <div className="grid-legend muted small" data-testid="grid-legend">
      <span><span className="gkey r-both" /> move or capture</span>
      <span><span className="gkey r-move" /> move only</span>
      <span><span className="gkey r-capture" /> capture only</span>
      <span><span className="gkey r-both"><span className="ray">→</span></span> slides on (arrow points the way)</span>
      <span><span className="gkey r-both first" /> first move only</span>
    </div>
  );
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

  const modeHint = MODES.find((m) => m.id === mode)!.hint;
  const symHint = SYMMETRIES.find((s) => s.id === symmetry)!.hint;
  return (
    <div className="move-grid-wrap">
      <ol className="steps">
        <li>
          <span className="step-head">1. How it gets there</span>
          <div className="seg small" role="group" aria-label="Kind">
            <button type="button" className={kind === 'LEAP' ? 'on' : ''} onClick={() => setKind('LEAP')}>Jump</button>
            <button type="button" className={kind === 'SLIDE' ? 'on' : ''} onClick={() => setKind('SLIDE')}>Slide</button>
          </div>
          <span className="hint">{kind === 'LEAP'
            ? 'Jump: straight to that square, over anything in between (like a knight).'
            : 'Slide: step after step in that direction until blocked (like a rook).'}</span>
          {kind === 'SLIDE' && (
            <label className="inline">Range
              <select value={range} onChange={(e) => setRange(Number(e.target.value))} aria-label="Slide steps">
                <option value={0}>to the edge</option>
                {[2, 3, 4, 5, 6].map((n) => <option key={n} value={n}>at most {n} steps</option>)}
              </select>
            </label>
          )}
        </li>
        <li>
          <span className="step-head">2. Mode</span>
          <div className="seg small" role="group" aria-label="Mode">
            {MODES.map((m) => <button type="button" key={m.id} className={mode === m.id ? 'on' : ''} onClick={() => setMode(m.id)}>{m.name}</button>)}
          </div>
          <span className="hint">{modeHint[0].toUpperCase() + modeHint.slice(1)}.</span>
        </li>
        <li>
          <span className="step-head">3. Symmetry</span>
          <div className="seg small" role="group" aria-label="Directions">
            {SYMMETRIES.map((s) => <button type="button" key={s.id} className={symmetry === s.id ? 'on' : ''} onClick={() => setSymmetry(s.id)}>{s.name}</button>)}
          </div>
          <span className="hint">One click adds {symHint}.</span>
          <label className="inline"><input type="checkbox" checked={firstMove} onChange={(e) => setFirstMove(e.target.checked)} /> First move only (like a pawn's double step)</label>
        </li>
        <li>
          <span className="step-head">4. Click the squares the piece reaches</span>
          <span className="hint">The piece is in the middle; up is forward for its owner. Click a lit square to take that move away.</span>
        </li>
      </ol>
      <div className="move-grid" role="group" aria-label="Where the piece goes (up is forward)" data-testid="move-grid">{cells}</div>
      <GridLegend />
    </div>
  );
}

/** What the piece editor needs for the piece's pictures. */
export interface PictureProps {
  /** A word on where the pictures go (kept until the variant is saved), or null. */
  note: string | null;
  urls: Record<string, string>;
  onUpload: (side: 'w' | 'b', file: File) => void | Promise<void>;
  onRemove: (side: 'w' | 'b') => void;
  error: string | null;
}

/** Upload, see and remove the piece's picture for white and for black. */
function Pictures({ letter, pictures }: { letter: string; pictures: PictureProps }) {
  const [drawing, setDrawing] = useState<'w' | 'b' | null>(null);
  return (
    <div className="pictures" data-testid="pictures">
      <span className="pictures-label">Picture</span>
      {(['w', 'b'] as const).map((side) => {
        const url = pictures.urls[side + letter];
        return (
          <div key={side} className="picture">
            <div className={'picture-box ' + (side === 'w' ? 'light' : 'dark')}>
              {url ? <img src={url} alt={`${side === 'w' ? 'White' : 'Black'} picture`} /> : <PieceSvg code={side + letter} />}
            </div>
            <div className="picture-actions">
              <span className="small">{side === 'w' ? 'White' : 'Black'}</span>
              <label className="btn small-btn">
                {url ? 'Change' : 'Upload'}
                <input type="file" accept="image/png,image/jpeg,image/webp,image/gif,image/svg+xml" hidden
                  aria-label={`${side === 'w' ? 'White' : 'Black'} picture`}
                  onChange={(e) => {
                    const f = e.target.files?.[0];
                    if (f) pictures.onUpload(side, f);
                    e.target.value = '';
                  }} />
              </label>
              <button type="button" className="btn small-btn" onClick={() => setDrawing(side)}>Draw it</button>
              {url && <button type="button" className="btn ghost small-btn" onClick={() => pictures.onRemove(side)}>Remove</button>}
            </div>
          </div>
        );
      })}
      <p className="muted small picture-note">
        {pictures.note && <>{pictures.note} </>}{'PNG, JPEG, WebP, GIF or SVG, up to 1 MB; a transparent background looks best. With one side only, the other side uses it darkened or lightened.'}
      </p>
      {pictures.error && <p className="error small" role="alert">{pictures.error}</p>}
      {drawing && (
        <PieceDrawerDialog title={`Draw the ${letter}`} side={drawing}
          initial={{ w: pictures.urls['w' + letter], b: pictures.urls['b' + letter] }}
          onClose={() => setDrawing(null)}
          onSave={async (drawn) => {
            for (const side of ['w', 'b'] as const) {
              const blob = drawn[side];
              if (blob) await pictures.onUpload(side, new File([blob], `${letter}-${side}.png`, { type: 'image/png' }));
            }
            setDrawing(null);
          }} />
      )}
    </div>
  );
}

export function PieceEditor({ piece, letters, readOnly, pictures, onChange, onAtoms, onBetza, onRemove }: {
  /** Null for a built-in variant (no pictures). */
  pictures: PictureProps | null;
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

  const marks = useMemo(() => reach(piece.atoms, from, true, moved), [piece.atoms, from, moved]);
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
        <label title={PIECE_HINTS.value}>Value<input type="number" value={piece.value} step={10} onChange={(e) => onChange({ ...piece, value: Number(e.target.value) })} /></label>
        <label title={PIECE_HINTS.promotesTo}>Promotes to<input value={piece.promotesTo} placeholder="none" aria-label="Promotes to"
          onChange={(e) => onChange({ ...piece, promotesTo: e.target.value.toUpperCase().replace(/[^A-Z]/g, '') })} /></label>
        <label title={PIECE_HINTS.castling}>Castling
          <select value={piece.castlingRole} onChange={(e) => onChange({ ...piece, castlingRole: e.target.value as PieceDef['castlingRole'] })}>
            <option value="NONE">No</option>
            <option value="KING">Castles (like a king)</option>
            <option value="ROOK">Castled with (like a rook)</option>
          </select>
        </label>
      </div>
      <div className="check-row">
        <label className="inline"><input type="checkbox" checked={piece.royal} onChange={(e) => onChange({ ...piece, royal: e.target.checked })} /> Royal (must not be captured)</label>
        <label className="inline"><input type="checkbox" checked={piece.enPassant} onChange={(e) => onChange({ ...piece, enPassant: e.target.checked })} /> En passant</label>
      </div>
      </fieldset>
      <details className="hints">
        <summary>What these settings mean</summary>
        <ul className="small">
          {Object.values(PIECE_HINTS).map((h) => <li key={h}>{h}</li>)}
        </ul>
      </details>
      {pictures && <Pictures letter={piece.letter} pictures={pictures} />}
      <h4 className="moves-head">Moves</h4>
      <div className="designer">
        <fieldset disabled={readOnly} className="plain">
          <MoveGrid atoms={piece.atoms} letter={piece.letter} onChange={(atoms) => onChange({ ...piece, atoms })} />
        </fieldset>
        <div className="preview">
          <span className="step-head">Preview on a board</span>
          <span className="hint">Click a square to put the piece there.</span>
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

export function blankPiece(letters: string[]): PieceDef {
  const letter = 'ACDEFGHIJLMOSTUVWXYZ'.split('').find((l) => !letters.includes(l)) ?? 'Z';
  return { name: 'New piece', letter, value: 300, royal: false, promotesTo: '', enPassant: false, castlingRole: 'NONE', atoms: [] };
}
