import { useMemo, useState } from 'react';
import { artUrls, PieceArt, PieceSvg } from '../pieces';
import { placementOf, reach } from '../reach';
import { BoardEditor } from './BoardEditor';
import { HealthPage } from './Health';
import { blankPiece, PieceEditor, type PictureProps } from './PieceEditor';
import { GOALS, goalName, TABS, variantsHash, type Goal, type PieceDef, type Route, type Tab, type VariantDef } from './model';
import { castlingText, DRAW_TEXT, forcedCaptureText, goalText, royalText } from './rules';

// One variant over several screens (#variants/<id>/...): Overview (name, family, notes, the rules
// explained), Board (the start position), Pieces (the list, then one piece), Health (self-play).
// The header with Play it / Make a copy / Delete / Save stays on all of them; the edited variant is
// held by the parent, so a change survives switching screens.

const FILES = 'abcdefgh';

export function withPlacement(fen: string, board: Record<string, string>): string {
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

/** The variant being looked at or edited, as the parent holds it. */
export interface OpenVariant {
  def: VariantDef;
  /** The pieces as last loaded or saved (a piece not among them is new: no pictures yet). */
  savedPieces: PieceDef[];
  /** Not saved yet: its id still follows its name. */
  fresh: boolean;
  /** Changed since it was opened or saved. */
  dirty: boolean;
}

export function VariantPage({ open, route, families, error, saved, onEdit, onPatch, onSave, onCopy, onDelete, onPlay, onRoute }: {
  open: OpenVariant;
  route: Route;
  /** The families the player's variants already use, offered when typing one. */
  families: string[];
  error: string | null;
  /** Just saved (the button says so until the next change). */
  saved: boolean;
  /** A change the player made (marks the variant changed). */
  onEdit: (def: VariantDef | ((now: VariantDef) => VariantDef)) => void;
  /** A change that is not the player's (Betza text written back, pictures): does not mark it changed. */
  onPatch: (f: (def: VariantDef) => VariantDef) => void;
  onSave: () => void;
  onCopy: () => void;
  onDelete: () => void;
  onPlay: () => void;
  /** Go to another screen of this variant; replace = without a history entry. */
  onRoute: (tab: Tab, letter?: string | null, replace?: boolean) => void;
}) {
  const v = open.def;
  const readOnly = !!v.builtIn;
  const { dirty, fresh } = open;
  const routeId = route.id ?? v.id;

  const [artError, setArtError] = useState<string | null>(null);
  const artMap = useMemo(() => artUrls(v.id, v.art ?? {}), [v.id, v.art]);

  return (
    <PieceArt.Provider value={artMap}>
      <div className="variant-editor variant-page" data-testid="variant-editor">
        <section className="panel variant-header">
          <div className="run-head">
            <div className="title-block">
              <a className="back-link" href="#variants">← All variants</a>
              <h2>{v.name || 'Untitled'}
                {readOnly && <span className="tag">Built in</span>}
                {fresh && <span className="tag">Not saved</span>}
                {!fresh && dirty && <span className="tag warn-tag">Unsaved changes</span>}
              </h2>
              {v.family && <span className="muted small">Family: {v.family}</span>}
            </div>
            <div className="filters">
              {!fresh && (
                <button type="button" className="btn primary" disabled={dirty} title={dirty ? 'Save it first' : undefined}
                  onClick={onPlay}>Play it</button>
              )}
              <button type="button" className="btn" onClick={onCopy}>Make a copy</button>
              {!readOnly && !fresh && <button type="button" className="btn ghost" onClick={onDelete}>Delete</button>}
              {!readOnly && <button type="button" className="btn primary" onClick={onSave}>{saved ? 'Saved' : 'Save'}</button>}
            </div>
          </div>
          {readOnly && (
            <div className="readonly-cta" data-testid="builtin-cta">
              <p>This is a built-in variant, shown read-only. To change anything, make your own copy: it keeps this as its family.</p>
              <button type="button" className="btn primary" onClick={onCopy}>Make your own copy</button>
            </div>
          )}
          {error && <p className="error" role="alert">{error}</p>}
          <nav className="sub-tabs" aria-label="Variant screens">
            {TABS.map(([key, label]) => (
              <a key={key} href={variantsHash(routeId, key)} className={'btn ghost' + (route.tab === key ? ' on' : '')}
                aria-current={route.tab === key ? 'page' : undefined}>{label}</a>
            ))}
          </nav>
        </section>

        {route.tab === 'overview' && <Overview v={v} readOnly={readOnly} families={families} onEdit={onEdit} />}
        {route.tab === 'board' && <BoardTab v={v} readOnly={readOnly} onEdit={onEdit} />}
        {route.tab === 'pieces' && (
          <PiecesTab open={open} route={route} readOnly={readOnly} artMap={artMap} artError={artError} setArtError={setArtError}
            onEdit={onEdit} onPatch={onPatch} onRoute={onRoute} />
        )}
        {route.tab === 'health' && <HealthPage variant={v} />}
      </div>
    </PieceArt.Provider>
  );
}

function Rule({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rule">
      <div className="rule-head">{title}</div>
      <div className="rule-text">{children}</div>
    </div>
  );
}

function Overview({ v, readOnly, families, onEdit }: { v: VariantDef; readOnly: boolean; families: string[]; onEdit: (d: VariantDef) => void }) {
  return (
    <>
      {!readOnly && <section className="panel">
        <h3>About</h3>
        <fieldset disabled={readOnly} className="plain">
          <div className="form-grid">
            <label>Name<input value={v.name} onChange={(e) => onEdit({ ...v, name: e.target.value })} /></label>
            <label>Family
              <input value={v.family ?? ''} list="variant-families" placeholder={readOnly ? '' : 'e.g. Amazon chess'} aria-label="Family"
                onChange={(e) => onEdit({ ...v, family: e.target.value })} />
              <datalist id="variant-families">{families.map((f) => <option key={f} value={f} />)}</datalist>
            </label>
          </div>
          <label className="notes">Notes
            <textarea value={v.notes ?? ''} rows={3} aria-label="Notes" placeholder={readOnly ? '' : 'What you are trying out, what to change next…'}
              onChange={(e) => onEdit({ ...v, notes: e.target.value })} />
          </label>
          <p className="muted small">A family groups variants that are small changes of one idea; the list shows them together.</p>
        </fieldset>
      </section>}
      <section className="panel" data-testid="rules">
        <h3>Rules</h3>
        <fieldset disabled={readOnly} className="plain">
          <div className="form-grid">
            <label>Goal
              <select value={v.goal} aria-label="Goal" onChange={(e) => {
                const goal = e.target.value as Goal;
                onEdit({ ...v, goal, checksToWin: goal === 'CHECKS' ? v.checksToWin || 3 : 0 });
              }}>
                {GOALS.map((g) => <option key={g.id} value={g.id}>{g.name}</option>)}
              </select>
            </label>
            {v.goal === 'CHECKS' && (
              <label>Checks to win<input type="number" min={1} max={20} value={v.checksToWin}
                onChange={(e) => onEdit({ ...v, checksToWin: Math.max(1, Number(e.target.value)) })} /></label>
            )}
            <label className="inline"><input type="checkbox" checked={v.forcedCapture} onChange={(e) => onEdit({ ...v, forcedCapture: e.target.checked })} /> Captures are forced</label>
            <label className="inline"><input type="checkbox" checked={v.castling} onChange={(e) => onEdit({ ...v, castling: e.target.checked })} /> Castling</label>
          </div>
        </fieldset>
        <div className="rules-explained" data-testid="rules-explained">
          <Rule title={`Goal: ${goalName(v.goal, v.checksToWin)}`}>
            <p data-testid="goal-text">{goalText(v)}</p>
            <p className="muted">{DRAW_TEXT}</p>
          </Rule>
          <Rule title="Royal pieces and check"><p data-testid="royal-text">{royalText(v)}</p></Rule>
          <Rule title={`Forced capture: ${v.forcedCapture ? 'on' : 'off'}`}><p data-testid="capture-text">{forcedCaptureText(v.forcedCapture)}</p></Rule>
          <Rule title={`Castling: ${v.castling ? 'on' : 'off'}`}>
            <div data-testid="castling-text">{castlingText(v).map((t) => <p key={t}>{t}</p>)}</div>
          </Rule>
        </div>
      </section>
    </>
  );
}

function BoardTab({ v, readOnly, onEdit }: { v: VariantDef; readOnly: boolean; onEdit: (d: VariantDef) => void }) {
  const board = useMemo(() => placementOf(v.start), [v.start]);
  /** The start-position square under the mouse: its piece's moves are shown. */
  const [hovered, setHovered] = useState<string | null>(null);
  const marks = useMemo(() => {
    const c = hovered ? board[hovered] : undefined;
    const p = c && v.pieces.find((q) => q.letter === c.toUpperCase());
    return p && hovered ? reach(p.atoms, hovered, c === c.toUpperCase(), false, board) : undefined;
  }, [hovered, board, v.pieces]);
  return (
    <section className="panel">
      <h3>Start position</h3>
      <p className="muted small">{readOnly ? 'Point at a piece to see where it goes.' : 'Click a square to put a piece there, drag pieces about, right-click to take one away. Point at a piece to see where it goes.'}</p>
      <BoardEditor board={board} pieces={v.pieces} label="Start position" testId="start-board" marks={marks} onHover={setHovered}
        onChange={readOnly ? undefined : (b) => onEdit({ ...v, start: withPlacement(v.start, b) })} />
      <label className="fen">FEN
        <input value={v.start} disabled={readOnly} spellCheck={false} aria-label="Start FEN" onChange={(e) => onEdit({ ...v, start: e.target.value })} />
      </label>
      <p className="muted small">After the pieces: who moves first, the castling rights (K, Q, k, q), the en passant square, and the move counters.</p>
    </section>
  );
}

function PiecesTab({ open, route, readOnly, artMap, artError, setArtError, onEdit, onPatch, onRoute }: {
  open: OpenVariant;
  route: Route;
  readOnly: boolean;
  artMap: Record<string, string>;
  artError: string | null;
  setArtError: (e: string | null) => void;
  onEdit: (def: VariantDef | ((now: VariantDef) => VariantDef)) => void;
  onPatch: (f: (def: VariantDef) => VariantDef) => void;
  onRoute: (tab: Tab, letter?: string | null, replace?: boolean) => void;
}) {
  const v = open.def;
  const letters = v.pieces.map((p) => p.letter);
  const picked = route.letter ? v.pieces.findIndex((p) => p.letter === route.letter) : -1;
  const piece = picked >= 0 ? v.pieces[picked] : null;

  const setPiece = (i: number, p: PieceDef) => {
    const old = v.pieces[i];
    let start = v.start;
    let pieces = v.pieces.map((q, j) => (j === i ? p : q));
    if (old.letter !== p.letter) {
      // a new letter: the start position, the promotions and the address follow it
      const b = placementOf(start);
      for (const sq in b) {
        if (b[sq] === old.letter) b[sq] = p.letter;
        else if (b[sq] === old.letter.toLowerCase()) b[sq] = p.letter.toLowerCase();
      }
      start = withPlacement(start, b);
      pieces = pieces.map((q) => ({ ...q, promotesTo: q.promotesTo.replaceAll(old.letter, p.letter) }));
      onRoute('pieces', p.letter, true);
    }
    onEdit({ ...v, start, pieces });
  };

  const addPiece = () => {
    const p = blankPiece(letters);
    onEdit({ ...v, pieces: [...v.pieces, p] });
    onRoute('pieces', p.letter);
  };

  const artCall = (side: 'w' | 'b', init: RequestInit) => {
    setArtError(null);
    return fetch(`/api/variants/${encodeURIComponent(v.id)}/art/${piece!.letter}/${side}`, init)
      .then(async (r) => {
        const body = await r.json().catch(() => ({}));
        if (!r.ok) throw new Error(body.error ?? `${r.status} ${r.statusText}`);
        onPatch((d) => ({ ...d, art: body }));
      })
      .catch((e: Error) => setArtError(e.message));
  };
  const pictures: PictureProps | null = readOnly || !piece ? null : {
    blocked: open.fresh ? 'Save the variant first, then add pictures.'
      : open.dirty && !open.savedPieces.some((p) => p.letter === piece.letter) ? 'Save the variant first: this piece is new.' : null,
    urls: artMap,
    error: artError,
    onUpload: (side, file) => artCall(side, { method: 'PUT', headers: { 'Content-Type': file.type || 'application/octet-stream' }, body: file }),
    onRemove: (side) => artCall(side, { method: 'DELETE' }),
  };

  const counts = useMemo(() => {
    const out: Record<string, number> = {};
    for (const c of Object.values(placementOf(v.start))) out[c] = (out[c] ?? 0) + 1;
    return out;
  }, [v.start]);

  return (
    <>
      <section className="panel">
        <div className="run-head">
          <h3>Pieces</h3>
          {!readOnly && v.pieces.length < 16 && <button type="button" className="btn" onClick={addPiece}>Add a piece</button>}
        </div>
        {!piece && <p className="muted small">Pick a piece to see or change how it moves. A variant has up to 16 kinds of piece.</p>}
        <div className={'piece-list' + (piece ? '' : ' piece-table')} role="group" aria-label="Pieces" data-testid="piece-list">
          {v.pieces.map((p, i) => (
            <button type="button" key={i} className={'piece-chip' + (i === picked ? ' on' : '')} aria-pressed={i === picked}
              onClick={() => onRoute('pieces', p.letter)}>
              <span className="chip-icon"><PieceSvg code={'w' + p.letter} /></span>
              <span>{p.name}</span>
              <span className="muted small">{p.betza ?? ''}</span>
              {!piece && (
                <span className="chip-facts muted small">
                  {p.letter} · {p.value}{p.royal ? ' · royal' : ''}{p.castlingRole !== 'NONE' ? ` · castling ${p.castlingRole.toLowerCase()}` : ''}
                  {p.promotesTo ? ` · promotes to ${p.promotesTo}` : ''} · {counts[p.letter] ?? 0}+{counts[p.letter.toLowerCase()] ?? 0} on the board
                </span>
              )}
            </button>
          ))}
        </div>
        {route.letter && !piece && <p className="error">This variant has no piece {route.letter}.</p>}
      </section>
      {piece && (
        <PieceEditor key={picked} piece={piece} letters={letters} readOnly={readOnly} pictures={pictures}
          onChange={(p) => setPiece(picked, p)}
          onAtoms={(atoms) => onEdit((now) => ({ ...now, pieces: now.pieces.map((q, j) => (j === picked ? { ...q, atoms } : q)) }))}
          onBetza={(text) => onPatch((d) => ({ ...d, pieces: d.pieces.map((q, j) => (j === picked ? { ...q, betza: text } : q)) }))}
          onRemove={() => {
            const letter = piece.letter;
            const b = placementOf(v.start);
            for (const sq in b) if (b[sq].toUpperCase() === letter) delete b[sq];
            onEdit({
              ...v,
              start: withPlacement(v.start, b),
              pieces: v.pieces.filter((_, j) => j !== picked).map((q) => ({ ...q, promotesTo: q.promotesTo.replaceAll(letter, '') })),
            });
            onRoute('pieces', null);
          }} />
      )}
    </>
  );
}
