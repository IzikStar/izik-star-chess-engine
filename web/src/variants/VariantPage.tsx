import { useEffect, useMemo, useState } from 'react';
import { artUrls, PieceArt, PieceSvg } from '../pieces';
import { placementOf, reach, type Reach } from '../reach';
import { BoardEditor } from './BoardEditor';
import { HealthPage } from './Health';
import { blankPiece, PieceEditor, type PictureProps } from './PieceEditor';
import { bankPictures, PieceBankDialog, saveToBank, type BankEntry } from './PieceBank';
import { CHESS_CASTLING, TABS, variantsHash, type GoalDef, type PieceDef, type Route, type RulesCheck, type Tab, type VariantDef } from './model';
import {
  CASTLING_SIDES, castlingMoves, castlingText, fairyText, forcedCaptureText, GOAL_KINDS, goalProblem, GOALS_TEXT, goalText,
  moveLimitText, newGoal, repetitionText, ROYAL_MODES, royalText, STALEMATES, stalemateText,
} from './rules';

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
  /**
   * Pictures given to pieces the server does not have yet (a new variant, a new piece or a new
   * letter), by code ('wA'); they are sent when the variant is saved.
   */
  pendingArt?: Record<string, File>;
}

/**
 * What the server makes of the variant as edited (POST /api/variant-rules): whether it can be played,
 * Fairy-Stockfish, and its castlings. Asked again a moment after each change.
 */
function useRulesCheck(v: VariantDef): RulesCheck | null {
  const [check, setCheck] = useState<RulesCheck | null>(null);
  const body = useMemo(() => {
    const { builtIn: _b, art: _a, family: _f, notes: _n, ...rest } = v;
    return JSON.stringify(rest);
  }, [v]);
  useEffect(() => {
    let live = true;
    const t = setTimeout(() => {
      fetch('/api/variant-rules', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body })
        .then((r) => (r.ok ? r.json() : r.json().then((e) => ({ error: e.error ?? 'not a variant', fairy: false, fairyReason: null, castlings: [] }))))
        .then((c: RulesCheck) => { if (live) setCheck(c); })
        .catch(() => {});
    }, 250);
    return () => { live = false; clearTimeout(t); };
  }, [body]);
  return check;
}

export function VariantPage({ open, route, families, error, saved, onEdit, onPatch, onPendingArt, onSave, onCopy, onDelete, onPlay, onRoute }: {
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
  /** A change to the pictures waiting for the save (marks the variant changed). */
  onPendingArt: (f: (now: Record<string, File>) => Record<string, File>) => void;
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
  const pendingUrls = useMemo(() => {
    const out: Record<string, string> = {};
    for (const [code, file] of Object.entries(open.pendingArt ?? {})) out[code] = URL.createObjectURL(file);
    return out;
  }, [open.pendingArt]);
  useEffect(() => () => Object.values(pendingUrls).forEach((u) => URL.revokeObjectURL(u)), [pendingUrls]);
  const artMap = useMemo(() => ({ ...artUrls(v.id, v.art ?? {}), ...pendingUrls }), [v.id, v.art, pendingUrls]);
  const check = useRulesCheck(v);

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

        {route.tab === 'overview' && <Overview v={v} readOnly={readOnly} families={families} check={check} onEdit={onEdit} />}
        {route.tab === 'board' && <BoardTab v={v} readOnly={readOnly} check={check} onEdit={onEdit} />}
        {route.tab === 'pieces' && (
          <PiecesTab open={open} route={route} readOnly={readOnly} artMap={artMap} artError={artError} setArtError={setArtError}
            onEdit={onEdit} onPatch={onPatch} onPendingArt={onPendingArt} onRoute={onRoute} />
        )}
        {route.tab === 'health' && <HealthPage variant={v} fairy={check ? check.fairy : null} fairyReason={check?.fairyReason ?? null} />}
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

function Overview({ v, readOnly, families, check, onEdit }: {
  v: VariantDef; readOnly: boolean; families: string[]; check: RulesCheck | null; onEdit: (d: VariantDef) => void;
}) {
  const [adding, setAdding] = useState<GoalDef['kind']>('REACH_SQUARES');
  const setGoal = (i: number, g: GoalDef) => onEdit({ ...v, goals: v.goals.map((q, j) => (j === i ? g : q)) });
  const moveGoal = (i: number, by: number) => {
    const goals = [...v.goals];
    [goals[i], goals[i + by]] = [goals[i + by], goals[i]];
    onEdit({ ...v, goals });
  };
  const rule = v.castlingRule ?? CHESS_CASTLING;
  const setRule = (r: Partial<VariantDef['castlingRule']>) => onEdit({ ...v, castlingRule: { ...rule, ...r } });
  const setRole = (letter: string, role: PieceDef['castlingRole'], on: boolean) => onEdit({
    ...v,
    pieces: v.pieces.map((p) => (p.letter !== letter ? p : { ...p, castlingRole: on ? role : p.castlingRole === role ? 'NONE' : p.castlingRole })),
  });
  const moves = castlingMoves(check);
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
        <h3>How to win</h3>
        <p className="muted small">{GOALS_TEXT}</p>
        <fieldset disabled={readOnly} className="plain">
          <ol className="goal-list" data-testid="goal-text" aria-label="Ways to win">
            {v.goals.map((g, i) => (
              <GoalRow key={i + g.kind} g={g} v={v} index={i} count={v.goals.length} readOnly={readOnly}
                onChange={(n) => setGoal(i, n)} onMove={(by) => moveGoal(i, by)}
                onRemove={() => onEdit({ ...v, goals: v.goals.filter((_, j) => j !== i) })} />
            ))}
          </ol>
          {!v.goals.length && <p className="error">Add at least one way to win.</p>}
          {!readOnly && (
            <div className="goal-add">
              <select value={adding} aria-label="Way to win to add" onChange={(e) => setAdding(e.target.value as GoalDef['kind'])}>
                {GOAL_KINDS.map((k) => <option key={k.kind} value={k.kind}>{k.name}</option>)}
              </select>
              <button type="button" className="btn" onClick={() => onEdit({ ...v, goals: [...v.goals, newGoal(adding, v)] })}>Add a way to win</button>
            </div>
          )}
        </fieldset>
      </section>
      <section className="panel" data-testid="rule-settings">
        <h3>Rules</h3>
        <fieldset disabled={readOnly} className="plain">
          <div className="form-grid">
            <label>Royal pieces
              <select value={v.royalMode} aria-label="Royal mode" onChange={(e) => onEdit({ ...v, royalMode: e.target.value as VariantDef['royalMode'] })}>
                {ROYAL_MODES.map((m) => <option key={m.id} value={m.id}>{m.name}</option>)}
              </select>
            </label>
            <label>No legal move (stalemate) is
              <select value={v.stalemate} aria-label="Stalemate" onChange={(e) => onEdit({ ...v, stalemate: e.target.value as VariantDef['stalemate'] })}>
                {STALEMATES.map((m) => <option key={m.id} value={m.id}>{m.name}</option>)}
              </select>
            </label>
            <label>Move limit (0 = none)
              <input type="number" min={0} max={1000} value={v.moveLimit} aria-label="Move limit"
                onChange={(e) => onEdit({ ...v, moveLimit: Math.max(0, Math.min(1000, Math.round(Number(e.target.value) || 0))) })} />
            </label>
            <label className="inline"><input type="checkbox" checked={v.repetition} onChange={(e) => onEdit({ ...v, repetition: e.target.checked })} /> Threefold repetition draws</label>
            <label className="inline"><input type="checkbox" checked={v.forcedCapture} onChange={(e) => onEdit({ ...v, forcedCapture: e.target.checked })} /> Captures are forced</label>
            <label className="inline"><input type="checkbox" checked={v.castling} onChange={(e) => onEdit({ ...v, castling: e.target.checked })} /> Castling</label>
          </div>
          {v.castling && (
            <div className="castling-settings" data-testid="castling-settings">
              <div className="form-grid">
                <label>The castling piece moves
                  <select value={rule.steps} aria-label="Castling steps" onChange={(e) => setRule({ steps: Number(e.target.value) })}>
                    <option value={0}>The chess way (to the g- or c-file)</option>
                    {[1, 2, 3, 4, 5, 6].map((n) => <option key={n} value={n}>{n} square{n === 1 ? '' : 's'}</option>)}
                  </select>
                </label>
                <label>Its partner lands
                  <select value={rule.partner} aria-label="Partner lands" onChange={(e) => setRule({ partner: e.target.value as 'INSIDE' | 'OUTSIDE' })}>
                    <option value="INSIDE">Next to it, on the inside</option>
                    <option value="OUTSIDE">Next to it, on the outside</option>
                  </select>
                </label>
                <label>Sides
                  <select value={rule.sides} aria-label="Castling sides" onChange={(e) => setRule({ sides: e.target.value as VariantDef['castlingRule']['sides'] })}>
                    {CASTLING_SIDES.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                  </select>
                </label>
                <label className="inline"><input type="checkbox" checked={rule.safePassage} aria-label="No castling through check"
                  onChange={(e) => setRule({ safePassage: e.target.checked })} /> Not out of, through or into check</label>
              </div>
              <div className="role-pick">
                <span className="muted small">Castles (King role):</span>
                {v.pieces.map((p) => (
                  <label key={p.letter} className="chip-check"><input type="checkbox" checked={p.castlingRole === 'KING'}
                    aria-label={`${p.name} castles`} onChange={(e) => setRole(p.letter, 'KING', e.target.checked)} /> {p.name}</label>
                ))}
              </div>
              <div className="role-pick">
                <span className="muted small">With a partner (Rook role):</span>
                {v.pieces.map((p) => (
                  <label key={p.letter} className="chip-check"><input type="checkbox" checked={p.castlingRole === 'ROOK'}
                    aria-label={`${p.name} is a castling partner`} onChange={(e) => setRole(p.letter, 'ROOK', e.target.checked)} /> {p.name}</label>
                ))}
              </div>
            </div>
          )}
        </fieldset>
        {check?.error && <p className="error" data-testid="rules-error">This variant cannot be played yet: {check.error}</p>}
        <div className="rules-explained" data-testid="rules-explained">
          <Rule title={`Royal pieces: ${ROYAL_MODES.find((m) => m.id === v.royalMode)?.name}`}>
            <p data-testid="royal-mode-text">{ROYAL_MODES.find((m) => m.id === v.royalMode)?.text}</p>
            <p data-testid="royal-text">{royalText(v)}</p>
          </Rule>
          <Rule title={`Stalemate: ${STALEMATES.find((m) => m.id === v.stalemate)?.name.toLowerCase()}`}>
            <p data-testid="stalemate-text">{stalemateText(v)}</p>
          </Rule>
          <Rule title={`Repetition: ${v.repetition ? 'draws' : 'off'}`}><p data-testid="repetition-text">{repetitionText(v.repetition)}</p></Rule>
          <Rule title={`Move limit: ${v.moveLimit > 0 ? v.moveLimit + ' moves' : 'none'}`}><p data-testid="move-limit-text">{moveLimitText(v.moveLimit)}</p></Rule>
          <Rule title={`Forced capture: ${v.forcedCapture ? 'on' : 'off'}`}><p data-testid="capture-text">{forcedCaptureText(v.forcedCapture)}</p></Rule>
          <Rule title={`Castling: ${v.castling ? 'on' : 'off'}`}>
            <div data-testid="castling-text">{castlingText(v).map((t) => <p key={t}>{t}</p>)}</div>
            {v.castling && moves.length > 0 && (
              <ul className="castling-moves" data-testid="castling-moves">{moves.map((m) => <li key={m}>{m}</li>)}</ul>
            )}
          </Rule>
          <Rule title={check ? (check.fairy ? 'Fairy-Stockfish: plays it' : 'Our engine only') : 'Fairy-Stockfish'}>
            <p data-testid="fairy-text">{fairyText(check)}</p>
          </Rule>
        </div>
      </section>
    </>
  );
}

function GoalRow({ g, v, index, count, readOnly, onChange, onMove, onRemove }: {
  g: GoalDef; v: VariantDef; index: number; count: number; readOnly: boolean;
  onChange: (g: GoalDef) => void; onMove: (by: number) => void; onRemove: () => void;
}) {
  // the squares as typed, so a space can be typed before the next square
  const [squares, setSquares] = useState((g.squares ?? []).join(' '));
  const problem = goalProblem(g, v);
  const pick = (letter: string, on: boolean) => {
    const set = new Set(g.pieces ?? '');
    if (on) set.add(letter);
    else set.delete(letter);
    onChange({ ...g, pieces: v.pieces.map((p) => p.letter).filter((l) => set.has(l)).join('') });
  };
  const name = GOAL_KINDS.find((k) => k.kind === g.kind)?.name ?? g.kind;
  return (
    <li className="goal" data-testid="goal" data-kind={g.kind}>
      <div className="goal-head">
        <strong>{name}</strong>
        {g.kind === 'CHECKS' && (
          <label className="goal-param">Checks to win
            <input type="number" min={1} max={99} value={g.count ?? 3} aria-label="Checks to win"
              onChange={(e) => onChange({ ...g, count: Math.max(1, Math.min(99, Math.round(Number(e.target.value) || 1))) })} />
          </label>
        )}
        {g.kind === 'REACH_SQUARES' && (
          <label className="goal-param">Squares
            <input value={squares} aria-label="Goal squares" spellCheck={false} placeholder="d4 e4 d5 e5"
              onChange={(e) => {
                setSquares(e.target.value);
                onChange({ ...g, squares: e.target.value.toLowerCase().split(/[\s,]+/).filter(Boolean) });
              }} />
          </label>
        )}
        {!readOnly && (
          <span className="goal-tools">
            <button type="button" className="btn ghost small" aria-label="Move up" disabled={index === 0} onClick={() => onMove(-1)}>↑</button>
            <button type="button" className="btn ghost small" aria-label="Move down" disabled={index === count - 1} onClick={() => onMove(1)}>↓</button>
            <button type="button" className="btn ghost small" aria-label={`Remove ${name}`} onClick={onRemove}>✕</button>
          </span>
        )}
      </div>
      {(g.kind === 'REACH_SQUARES' || g.kind === 'CAPTURE_ALL_OF') && (
        <div className="role-pick">
          <span className="muted small">{g.kind === 'REACH_SQUARES' ? (g.pieces ? 'Pieces that count:' : 'Pieces that count: the royal pieces, or pick') : 'Capture all of:'}</span>
          {v.pieces.map((p) => (
            <label key={p.letter} className="chip-check"><input type="checkbox" checked={(g.pieces ?? '').includes(p.letter)}
              aria-label={`${name}: ${p.name}`} onChange={(e) => pick(p.letter, e.target.checked)} /> {p.name}</label>
          ))}
        </div>
      )}
      <p className="goal-sentence" data-testid="goal-sentence">{goalText(g, v)}</p>
      {problem && <p className="error small">{problem}</p>}
    </li>
  );
}

function BoardTab({ v, readOnly, check, onEdit }: { v: VariantDef; readOnly: boolean; check: RulesCheck | null; onEdit: (d: VariantDef) => void }) {
  const board = useMemo(() => placementOf(v.start), [v.start]);
  /** The start-position square under the mouse: its piece's moves are shown, and its castlings. */
  const [hovered, setHovered] = useState<string | null>(null);
  const castlings = useMemo(() => (v.castling ? check?.castlings ?? [] : []), [v.castling, check]);
  const marks = useMemo(() => {
    const c = hovered ? board[hovered] : undefined;
    const p = c && v.pieces.find((q) => q.letter === c.toUpperCase());
    const out: Map<string, Reach | 'castle' | 'partner'> | undefined =
      p && hovered ? new Map(reach(p.atoms, hovered, c === c.toUpperCase(), false, board)) : undefined;
    for (const k of castlings) {
      if (out && k.king === hovered) {
        out.set(k.kingTo, 'castle');
        if (k.rookTo !== k.kingTo) out.set(k.rookTo, 'partner');
      }
    }
    return out;
  }, [hovered, board, v.pieces, castlings]);
  return (
    <section className="panel">
      <h3>Start position</h3>
      <p className="muted small">{readOnly ? 'Point at a piece to see where it goes.' : 'Click a square to put a piece there, drag pieces about, right-click to take one away. Point at a piece to see where it goes.'}</p>
      <BoardEditor board={board} pieces={v.pieces} label="Start position" testId="start-board" marks={marks} onHover={setHovered}
        onChange={readOnly ? undefined : (b) => onEdit({ ...v, start: withPlacement(v.start, b) })} />
      {castlings.length > 0 && (
        <p className="muted small" data-testid="board-castlings">Castling (point at the castling piece; a ring marks where it lands, a dashed ring its partner): {castlingMoves(check).join('; ')}.</p>
      )}
      <label className="fen">FEN
        <input value={v.start} disabled={readOnly} spellCheck={false} aria-label="Start FEN" onChange={(e) => onEdit({ ...v, start: e.target.value })} />
      </label>
      <p className="muted small">After the pieces: who moves first, the castling rights (K, Q, k, q), the en passant square, and the move counters.</p>
    </section>
  );
}

/** Why the server would refuse a picture (VariantStore.saveArt), or null when it takes it. */
function artProblem(file: File): string | null {
  if (!['image/png', 'image/jpeg', 'image/webp', 'image/gif', 'image/svg+xml'].includes(file.type)) return `A picture is PNG, JPEG, WebP, GIF or SVG, not ${file.type || 'this file'}.`;
  if (file.size === 0 || file.size > 1 << 20) return 'A picture is at most 1024 KB.';
  return null;
}

function PiecesTab({ open, route, readOnly, artMap, artError, setArtError, onEdit, onPatch, onPendingArt, onRoute }: {
  open: OpenVariant;
  route: Route;
  readOnly: boolean;
  artMap: Record<string, string>;
  artError: string | null;
  setArtError: (e: string | null) => void;
  onEdit: (def: VariantDef | ((now: VariantDef) => VariantDef)) => void;
  onPatch: (f: (def: VariantDef) => VariantDef) => void;
  onPendingArt: (f: (now: Record<string, File>) => Record<string, File>) => void;
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
      if (Object.keys(open.pendingArt ?? {}).some((c) => c[1] === old.letter)) {
        onPendingArt((now) => Object.fromEntries(Object.entries(now).map(([c, f]) => [c[1] === old.letter ? c[0] + p.letter : c, f])));
      }
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
  // the server keeps pictures of saved pieces only; the others wait for the save
  const onServer = !!piece && !open.fresh && open.savedPieces.some((p) => p.letter === piece.letter);
  const pending = open.pendingArt ?? {};
  const pictures: PictureProps | null = readOnly || !piece ? null : {
    note: onServer ? null : 'The pictures are kept with the piece and saved when you save the variant.',
    urls: artMap,
    error: artError,
    onUpload: (side, file) => {
      if (onServer) return artCall(side, { method: 'PUT', headers: { 'Content-Type': file.type || 'application/octet-stream' }, body: file });
      const problem = artProblem(file);
      setArtError(problem);
      if (!problem) onPendingArt((now) => ({ ...now, [side + piece.letter]: file }));
    },
    onRemove: (side) => {
      const code = side + piece.letter;
      if (pending[code]) onPendingArt(({ [code]: _gone, ...rest }) => rest);
      else return artCall(side, { method: 'DELETE' });
    },
  };

  const counts = useMemo(() => {
    const out: Record<string, number> = {};
    for (const c of Object.values(placementOf(v.start))) out[c] = (out[c] ?? 0) + 1;
    return out;
  }, [v.start]);

  const [bankOpen, setBankOpen] = useState(false);
  const [bankNote, setBankNote] = useState<string | null>(null);
  useEffect(() => setBankNote(null), [picked]);

  /** A piece from the bank as a new piece of this variant: its letter if free, its pictures waiting for the save. */
  const fromBank = async (e: BankEntry) => {
    const files = await bankPictures(e);
    const letter = letters.includes(e.piece.letter) ? blankPiece(letters).letter : e.piece.letter;
    const known = new Set([...letters, letter]);
    const p: PieceDef = { ...e.piece, letter, promotesTo: [...e.piece.promotesTo].filter((l) => known.has(l)).join('') };
    onEdit({ ...v, pieces: [...v.pieces, p] });
    if (files.w || files.b) {
      onPendingArt((now) => {
        const next = { ...now };
        for (const side of ['w', 'b'] as const) {
          const f = files[side];
          if (f) next[side + letter] = f;
          else delete next[side + letter];
        }
        return next;
      });
    }
    setBankOpen(false);
    onRoute('pieces', letter);
  };

  const toBank = async (p: PieceDef) => {
    setBankNote(null);
    try {
      const pics = readOnly ? {} : { w: artMap['w' + p.letter], b: artMap['b' + p.letter] };
      if (await saveToBank(p, pics)) setBankNote(`${p.name} is in the piece bank now.`);
    } catch (x) {
      setBankNote(`Not saved to the bank: ${(x as Error).message}`);
    }
  };

  return (
    <>
      <section className="panel">
        <div className="run-head">
          <h3>Pieces</h3>
          {!readOnly && v.pieces.length < 16 && (
            <span className="head-actions">
              <button type="button" className="btn" onClick={() => setBankOpen(true)}>From the bank</button>
              <button type="button" className="btn" onClick={addPiece}>Add a piece</button>
            </span>
          )}
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
          onSaveToBank={() => toBank(piece)} bankNote={bankNote}
          onRemove={() => {
            const letter = piece.letter;
            if (Object.keys(pending).some((c) => c[1] === letter)) {
              onPendingArt((now) => Object.fromEntries(Object.entries(now).filter(([c]) => c[1] !== letter)));
            }
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
      {bankOpen && <PieceBankDialog onPick={fromBank} onClose={() => setBankOpen(false)} />}
    </>
  );
}
