import { useEffect, useRef, useState } from 'react';
import { PieceArt, PieceSvg } from '../pieces';
import { api, type PieceDef } from './model';

// The piece bank (web.PieceBankApi): pieces kept apart from any variant. A piece of any variant can
// be saved into it with its pictures, and a piece from it put into any variant.

export interface BankEntry {
  id: string;
  piece: PieceDef;
  /** Its pictures: side ("w", "b") -> when saved. */
  art: Record<string, number>;
}

function artUrl(e: BankEntry, side: string): string {
  return `/api/piece-bank/${encodeURIComponent(e.id)}/art/${side}?v=${e.art[side]}`;
}

/**
 * Saves a piece into the bank with its pictures (URLs by side, none for a side without one). A bank
 * piece with the same name is replaced when the player agrees; answers false when they do not.
 */
export async function saveToBank(piece: PieceDef, pictures: { w?: string; b?: string }): Promise<boolean> {
  const { pieces } = await api<{ pieces: BankEntry[] }>('/api/piece-bank');
  const same = pieces.find((e) => e.piece.name.trim().toLowerCase() === piece.name.trim().toLowerCase());
  if (same && !confirm(`The bank already has a piece called ${same.piece.name}. Replace it?`)) return false;
  const { betza: _b, ...body } = piece;
  const saved = await api<BankEntry>(same ? `/api/piece-bank/${encodeURIComponent(same.id)}` : '/api/piece-bank',
    { method: same ? 'PUT' : 'POST', body: JSON.stringify(body) });
  for (const side of ['w', 'b'] as const) {
    const url = pictures[side];
    const path = `/api/piece-bank/${encodeURIComponent(saved.id)}/art/${side}`;
    const res = url
      ? await fetch(url).then((r) => r.blob()).then((blob) => fetch(path, { method: 'PUT', headers: { 'Content-Type': blob.type || 'application/octet-stream' }, body: blob }))
      : await fetch(path, { method: 'DELETE' });
    if (!res.ok) throw new Error((await res.json().catch(() => ({}))).error ?? `${res.status} ${res.statusText}`);
  }
  return true;
}

/** A bank piece's pictures as files, by side, to give a variant's piece. */
export async function bankPictures(e: BankEntry): Promise<Partial<Record<'w' | 'b', File>>> {
  const out: Partial<Record<'w' | 'b', File>> = {};
  for (const side of ['w', 'b'] as const) {
    if (!e.art[side]) continue;
    const blob = await fetch(artUrl(e, side)).then((r) => r.blob());
    out[side] = new File([blob], `${e.id}-${side}`, { type: blob.type });
  }
  return out;
}

/** The bank, to pick a piece to put into the variant (or to delete pieces from it). */
export function PieceBankDialog({ onPick, onClose }: { onPick: (e: BankEntry) => void | Promise<void>; onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [entries, setEntries] = useState<BankEntry[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = () => api<{ pieces: BankEntry[] }>('/api/piece-bank')
    .then((r) => setEntries(r.pieces))
    .catch((e: Error) => setError(e.message));
  useEffect(() => {
    const d = dialog.current;
    if (d && !d.open) d.showModal();
    load();
  }, []);

  const remove = (e: BankEntry) => {
    if (!confirm(`Delete ${e.piece.name} from the bank? Variants that use it keep their copy.`)) return;
    api(`/api/piece-bank/${encodeURIComponent(e.id)}`, { method: 'DELETE' }).then(load).catch((x: Error) => setError(x.message));
  };

  return (
    <dialog ref={dialog} className="dialog wide" aria-labelledby="bank-title" onCancel={(e) => { e.preventDefault(); onClose(); }}>
      <div className="dialog-body">
      <h2 id="bank-title">Piece bank</h2>
      <p className="muted small">Pieces kept apart from any variant. Save one here with "Save to bank" on a piece; pick one to add it to this variant with its pictures.</p>
      {error && <p className="error small" role="alert">{error}</p>}
      {!entries && !error && <p className="muted">Loading…</p>}
      {entries?.length === 0 && <p className="muted" data-testid="bank-empty">The bank is empty.</p>}
      {entries && entries.length > 0 && (
        <ul className="bank-list" data-testid="bank-list">
          {entries.map((e) => (
            <li key={e.id} className="bank-row">
              <PieceArt.Provider value={Object.fromEntries(Object.keys(e.art).map((side) => [side + e.piece.letter, artUrl(e, side)]))}>
                <span className="bank-icon"><PieceSvg code={'w' + e.piece.letter} /></span>
                <span className="bank-icon"><PieceSvg code={'b' + e.piece.letter} /></span>
              </PieceArt.Provider>
              <span className="bank-name">{e.piece.name}</span>
              <span className="muted small">{e.piece.betza ?? ''} · {e.piece.value}</span>
              <span className="bank-actions">
                <button type="button" className="btn small-btn" disabled={busy} aria-label={`Add ${e.piece.name}`}
                  onClick={async () => {
                    setBusy(true);
                    try {
                      await onPick(e);
                    } catch (x) {
                      setError((x as Error).message);
                      setBusy(false);
                    }
                  }}>Add</button>
                <button type="button" className="btn ghost small-btn" disabled={busy} aria-label={`Delete ${e.piece.name}`} onClick={() => remove(e)}>Delete</button>
              </span>
            </li>
          ))}
        </ul>
      )}
      <div className="row end">
        <button type="button" className="btn" onClick={onClose}>Close</button>
      </div>
      </div>
    </dialog>
  );
}
