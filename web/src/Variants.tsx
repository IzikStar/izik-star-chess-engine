import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { VariantList } from './variants/List';
import { VariantPage, type OpenVariant } from './variants/VariantPage';
import {
  api, freeId, mayLeave, NEW_ID, putArt, setLeaveGuard, slug, variantsHash, variantsRoute,
  type Route, type Tab, type VariantDef, type VariantRow,
} from './variants/model';
import './variants/pages.css';


// "Variants": the variant designer, over several screens chosen by the hash below "#variants" (like
// the Lab): the list of variants (#variants), and one variant's Overview, Board, Pieces and Health
// (#variants/<id>/...). Most of it is about making new variants; the built-in ones can be looked at
// and copied. The server keeps made variants as files (web.VariantsApi, game.VariantStore) and
// checks them on save.

/** The route's key for "is this still the same variant": its id, or "new" for the unsaved one. */
const keyOf = (r: Route) => r.id;

export function Variants({ onPlay }: { onPlay: (id: string) => void }) {
  const [rows, setRows] = useState<VariantRow[] | null>(null);
  const [folder, setFolder] = useState('');
  const [listError, setListError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [route, setRoute] = useState<Route>(() => variantsRoute());
  const [open, setOpen] = useState<OpenVariant | null>(null);
  const [saved, setSaved] = useState(false);

  // the guard reads the latest state through refs: it is asked from event handlers
  const openRef = useRef(open);
  openRef.current = open;
  const routeRef = useRef(route);
  routeRef.current = route;
  /** The hash we are on, to go back to when the player stays with unsaved changes. */
  const hashRef = useRef(location.hash);
  /** Set for one navigation the page itself started (a copy, a delete): no question asked. */
  const skipGuard = useRef(false);

  useEffect(() => {
    setLeaveGuard({
      dirty: () => !!openRef.current?.dirty,
      discard: () => { if (openRef.current) openRef.current = { ...openRef.current, dirty: false }; setOpen((o) => (o ? { ...o, dirty: false } : o)); },
      name: () => openRef.current?.def.name || 'This variant',
    });
    return () => setLeaveGuard(null);
  }, []);

  useEffect(() => {
    const onHash = () => {
      if (!location.hash.startsWith('#variants')) return; // another page: App asks
      const next = variantsRoute();
      if (keyOf(next) !== keyOf(routeRef.current) && !skipGuard.current && !mayLeave()) {
        history.replaceState(null, '', hashRef.current);
        return;
      }
      skipGuard.current = false;
      hashRef.current = location.hash;
      setRoute(next);
    };
    window.addEventListener('hashchange', onHash);
    return () => window.removeEventListener('hashchange', onHash);
  }, []);

  // closing the tab or reloading with unsaved changes: the browser asks
  useEffect(() => {
    if (!open?.dirty) return;
    const ask = (e: BeforeUnloadEvent) => { e.preventDefault(); e.returnValue = ''; };
    window.addEventListener('beforeunload', ask);
    return () => window.removeEventListener('beforeunload', ask);
  }, [open?.dirty]);

  const go = (hash: string, opts: { force?: boolean; replace?: boolean } = {}) => {
    if (opts.replace) {
      history.replaceState(null, '', hash);
      hashRef.current = hash;
      setRoute(variantsRoute(hash));
      return;
    }
    if (location.hash === hash) return;
    skipGuard.current = !!opts.force;
    location.hash = hash;
  };

  const load = useCallback(() => {
    api<{ folder: string; variants: VariantRow[] }>('/api/variants')
      .then((r) => { setRows(r.variants); setFolder(r.folder); setListError(null); })
      .catch((e: Error) => setListError(e.message));
  }, []);
  useEffect(load, [load]);

  const taken = useMemo(() => new Set(rows?.map((r) => r.id)), [rows]);
  const families = useMemo(() => [...new Set((rows ?? []).filter((r) => !r.builtIn && r.family).map((r) => r.family))].sort(), [rows]);

  /** A new variant, not saved yet, made from {@code from}. */
  const startNew = useCallback((from: VariantDef, name: string, family: string) => {
    setError(null);
    setSaved(false);
    setOpen({
      def: { ...from, id: freeId(name, taken), name, builtIn: false, family, notes: from.builtIn ? '' : from.notes ?? '', art: {} },
      savedPieces: [],
      fresh: true,
      dirty: true,
    });
  }, [taken]);

  const blankName = useCallback(() => {
    let name = 'New variant';
    for (let n = 2; taken.has(slug(name)); n++) name = `New variant ${n}`;
    return name;
  }, [taken]);

  // open what the route names: a saved variant from the server, or the unsaved new one
  useEffect(() => {
    const id = route.id;
    if (!id) {
      if (openRef.current) setOpen(null);
      return;
    }
    const now = openRef.current;
    if (id === NEW_ID) {
      if (now?.fresh) return;
      if (!rows) return; // the name needs the ids taken
      api<VariantDef>('/api/variants/chess').then((chess) => startNew(chess, blankName(), '')).catch((e: Error) => setError(e.message));
      return;
    }
    if (now && !now.fresh && now.def.id === id) return;
    let live = true;
    setError(null);
    api<VariantDef>(`/api/variants/${encodeURIComponent(id)}`)
      .then((v) => {
        if (!live) return;
        setSaved(false);
        setOpen({ def: v, savedPieces: v.pieces, fresh: false, dirty: false });
      })
      .catch((e: Error) => { if (live) { setOpen(null); setError(e.message); } });
    return () => { live = false; };
  }, [route.id, rows, startNew, blankName]);

  const edit = (x: VariantDef | ((now: VariantDef) => VariantDef)) => {
    setSaved(false);
    setOpen((o) => {
      if (!o) return o;
      let def = typeof x === 'function' ? x(o.def) : x;
      if (o.fresh && def.name !== o.def.name) def = { ...def, id: freeId(def.name, taken) };
      return { ...o, def, dirty: true };
    });
  };
  const patch = (f: (def: VariantDef) => VariantDef) => setOpen((o) => (o ? { ...o, def: f(o.def) } : o));
  const pendingArt = (f: (now: Record<string, File>) => Record<string, File>) => {
    setSaved(false);
    setOpen((o) => (o ? { ...o, pendingArt: f(o.pendingArt ?? {}), dirty: true } : o));
  };

  const save = () => {
    if (!open) return;
    setError(null);
    const wasFresh = open.fresh;
    api<VariantDef>(`/api/variants/${encodeURIComponent(open.def.id)}`, { method: 'PUT', body: JSON.stringify(open.def) })
      .then(async (r) => {
        // the pictures given to pieces before they were saved go up now that the pieces are there
        const pending = Object.entries(open.pendingArt ?? {}).filter(([code]) => r.pieces.some((p) => p.letter === code[1]));
        const left: Record<string, File> = {};
        let art = r.art;
        for (const [code, file] of pending) {
          try {
            art = await putArt(r.id, code[1], code[0] as 'w' | 'b', file);
          } catch (e) {
            left[code] = file;
            setError(`The ${code[0] === 'w' ? 'white' : 'black'} picture of ${code[1]} was not saved: ${(e as Error).message}`);
          }
        }
        const done = Object.keys(left).length === 0;
        // keep the Betza text the editor wrote; the server's answer has the rest
        setOpen((o) => ({
          def: { ...r, art, pieces: r.pieces.map((p, i) => ({ ...p, betza: p.betza ?? o?.def.pieces[i]?.betza })) },
          savedPieces: r.pieces, fresh: false, dirty: !done, pendingArt: left,
        }));
        setSaved(done);
        load();
        if (wasFresh) go(variantsHash(r.id, routeRef.current.tab, routeRef.current.letter), { replace: true });
      })
      .catch((e: Error) => setError(e.message));
  };

  const copy = () => {
    if (!open) return;
    const v = open.def;
    const base = v.builtIn ? `My ${v.name}` : `${v.name} copy`;
    let name = base;
    for (let n = 2; taken.has(slug(name)); n++) name = `${base} ${n}`;
    startNew(v, name, v.family?.trim() || v.name);
    go(variantsHash(NEW_ID, routeRef.current.tab === 'health' ? 'overview' : routeRef.current.tab, routeRef.current.letter), { force: true });
  };

  const remove = () => {
    if (!open) return;
    const v = open.def;
    if (!confirm(`Delete ${v.name}? Games played with it keep their copy.`)) return;
    api(`/api/variants/${encodeURIComponent(v.id)}`, { method: 'DELETE' })
      .then(() => { setOpen(null); load(); go('#variants', { force: true }); })
      .catch((e: Error) => setError(e.message));
  };

  const onRoute = (tab: Tab, letter: string | null = null, replace = false) => {
    const id = routeRef.current.id ?? NEW_ID;
    go(variantsHash(id, tab, letter), { replace });
  };

  if (!route.id) {
    return (
      <main className="lab-page variants-page">
        <VariantList rows={rows} folder={folder} error={listError}
          onOpen={(id) => go(variantsHash(id))}
          onNew={() => go(variantsHash(NEW_ID))} />
      </main>
    );
  }
  return (
    <main className="lab-page variants-page">
      {!open && error && (
        <section className="panel">
          <p className="error" role="alert">{error}</p>
          <a className="back-link" href="#variants">← All variants</a>
        </section>
      )}
      {!open && !error && <p className="muted">Loading…</p>}
      {open && (
        <VariantPage open={open} route={route} families={families} error={error} saved={saved}
          onEdit={edit} onPatch={patch} onPendingArt={pendingArt} onSave={save} onCopy={copy} onDelete={remove}
          onPlay={() => onPlay(open.def.id)} onRoute={onRoute} />
      )}
    </main>
  );
}
