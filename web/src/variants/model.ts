import type { Atom } from '../reach';

// The variant designer's data: a variant as the server sends it (web.VariantsApi), the list rows,
// the hash routes below "#variants", and a guard that asks before unsaved changes are dropped.

export type Goal = 'CHECKMATE' | 'LOSE_EVERYTHING' | 'KING_OF_THE_HILL' | 'CHECKS';

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
  /** The family it belongs to (variants that are small changes of one idea share one); '' for none. */
  family?: string;
  /** The maker's own notes. */
  notes?: string;
  /** The pieces' pictures: letter -> side ("w", "b") -> when saved. */
  art?: Record<string, Record<string, number>>;
}

/** One variant in the list (GET /api/variants). */
export interface VariantRow {
  id: string;
  name: string;
  builtIn: boolean;
  goal: Goal;
  checksToWin: number;
  family: string;
  notes: string;
  /** How many piece types. */
  pieces: number;
  width: number;
  height: number;
  start: string;
  /** When a made variant last changed (epoch millis); 0 for a built-in. */
  modified: number;
}

export const GOALS: { id: Goal; name: string }[] = [
  { id: 'CHECKMATE', name: 'Checkmate' },
  { id: 'LOSE_EVERYTHING', name: 'Lose everything' },
  { id: 'KING_OF_THE_HILL', name: 'King to the centre' },
  { id: 'CHECKS', name: 'Give N checks' },
];

export function goalName(goal: Goal, checksToWin = 0): string {
  return goal === 'CHECKS' ? `Give ${checksToWin || 'N'} checks` : GOALS.find((g) => g.id === goal)?.name ?? goal;
}

/** "My Amazon chess" -> "my-amazon-chess". */
export function slug(name: string): string {
  return name.toLowerCase().normalize('NFKD').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || 'variant';
}

/** An id for a variant called {@code name} that no other variant has. */
export function freeId(name: string, taken: Set<string>): string {
  const base = slug(name);
  let id = base;
  for (let n = 2; taken.has(id); n++) id = `${base}-${n}`;
  return id;
}

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, { ...init, headers: { 'Content-Type': 'application/json' } });
  if (res.status === 204) return undefined as T;
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(body.error ?? `${res.status} ${res.statusText}`);
  return body as T;
}

// ---- routes ---------------------------------------------------------------------------------------

/** The variant's screens: #variants/<id>, #variants/<id>/board, /pieces, /pieces/<letter>, /health. */
export type Tab = 'overview' | 'board' | 'pieces' | 'health';
export const TABS: [Tab, string][] = [['overview', 'Overview'], ['board', 'Board'], ['pieces', 'Pieces'], ['health', 'Health']];

/** The id of the variant being made and not saved yet. */
export const NEW_ID = 'new';

export interface Route {
  /** The open variant, or null for the list. */
  id: string | null;
  tab: Tab;
  /** On the pieces tab: the piece shown, by letter. */
  letter: string | null;
}

export function variantsRoute(hash = location.hash): Route {
  const h = hash.replace(/^#/, '');
  if (!h.startsWith('variants/')) return { id: null, tab: 'overview', letter: null };
  const parts = h.slice('variants/'.length).split('/').map(decodeURIComponent);
  const tab = (TABS.some(([t]) => t === parts[1]) ? parts[1] : 'overview') as Tab;
  return { id: parts[0] || null, tab, letter: tab === 'pieces' && parts[2] ? parts[2] : null };
}

export function variantsHash(id: string | null, tab: Tab = 'overview', letter: string | null = null): string {
  if (!id) return '#variants';
  return '#variants/' + encodeURIComponent(id) + (tab === 'overview' ? '' : '/' + tab) + (tab === 'pieces' && letter ? '/' + encodeURIComponent(letter) : '');
}

export function goVariants(id: string | null, tab: Tab = 'overview', letter: string | null = null) {
  location.hash = variantsHash(id, tab, letter);
}

// ---- leaving with unsaved changes -----------------------------------------------------------------

interface Guard {
  dirty: () => boolean;
  discard: () => void;
  name: () => string;
}

let guard: Guard | null = null;

/** The open variant registers here while it has changes that a page change would drop. */
export function setLeaveGuard(g: Guard | null) {
  guard = g;
}

/** Whether the open variant may be left: true with no unsaved changes, else the player is asked. */
export function mayLeave(): boolean {
  if (!guard || !guard.dirty()) return true;
  const ok = confirm(`${guard.name()} has unsaved changes. Leave and lose them?`);
  if (ok) guard.discard();
  return ok;
}
