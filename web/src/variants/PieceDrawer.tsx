import { useEffect, useRef, useState, type KeyboardEvent as ReactKeyboardEvent, type PointerEvent as ReactPointerEvent } from 'react';
import { PieceArt, PieceSvg } from '../pieces';
import './editor.css';

// Draw a made piece's picture in the app: pen, eraser, line, ellipse, rectangle and fill bucket on
// a 256x256 canvas, for white and for black. The black one can be drawn too, or made from the white
// one (its light parts turned dark). Saving gives transparent PNGs, uploaded like a picked file.

type Tool = 'pen' | 'eraser' | 'line' | 'ellipse' | 'rect' | 'fill';
type Side = 'w' | 'b';
interface Point { x: number; y: number }

/** The pictures drawn: a side is left out when it was not changed. */
export interface DrawnPictures {
  w?: Blob;
  b?: Blob;
}

export interface PieceDrawerProps {
  /** Called with the changed sides' pictures (transparent PNGs). */
  onSave: (pictures: DrawnPictures) => void | Promise<void>;
  /** The pictures to start from (URLs of the piece's current pictures). */
  initial?: { w?: string; b?: string };
  onCancel?: () => void;
  /** Which side to show first. */
  side?: Side;
}

/** The canvas size in pixels; it is shown larger. */
const SIZE = 256;
const MAX_UNDO = 40;

const TOOLS: { id: Tool; name: string; key: string }[] = [
  { id: 'pen', name: 'Pen', key: 'p' },
  { id: 'eraser', name: 'Eraser', key: 'e' },
  { id: 'line', name: 'Line', key: 'l' },
  { id: 'ellipse', name: 'Ellipse', key: 'o' },
  { id: 'rect', name: 'Rectangle', key: 'r' },
  { id: 'fill', name: 'Fill', key: 'f' },
];

const PRESETS: { color: string; name: string }[] = [
  { color: '#ffffff', name: 'White' },
  { color: '#f4efe4', name: 'Ivory' },
  { color: '#d9d1bf', name: 'Cream shade' },
  { color: '#8c8c92', name: 'Grey' },
  { color: '#2a2a2a', name: 'Charcoal' },
  { color: '#0b0b0b', name: 'Black' },
  { color: '#c9a227', name: 'Gold' },
  { color: '#a8322d', name: 'Red' },
  { color: '#2f5f9e', name: 'Blue' },
];

function blank(): HTMLCanvasElement {
  const c = document.createElement('canvas');
  c.width = SIZE;
  c.height = SIZE;
  return c;
}

const ctxOf = (c: HTMLCanvasElement) => c.getContext('2d', { willReadFrequently: true })!;

function rgbOf(hex: string): [number, number, number] {
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

/** Paint bucket: the area of about the clicked pixel's colour, reached in four directions, takes the colour. */
function floodFill(c: HTMLCanvasElement, x: number, y: number, hex: string) {
  const ctx = ctxOf(c);
  const img = ctx.getImageData(0, 0, SIZE, SIZE);
  const d = img.data;
  const at = (y * SIZE + x) * 4;
  const target = [d[at], d[at + 1], d[at + 2], d[at + 3]];
  const [r, g, b] = rgbOf(hex);
  if (target[0] === r && target[1] === g && target[2] === b && target[3] === 255) return;
  const near = (i: number) => {
    // two transparent pixels match whatever their colour channels say
    if (target[3] === 0 && d[i + 3] < 40) return true;
    return Math.abs(d[i] - target[0]) + Math.abs(d[i + 1] - target[1]) + Math.abs(d[i + 2] - target[2]) + Math.abs(d[i + 3] - target[3]) <= 96;
  };
  const seen = new Uint8Array(SIZE * SIZE);
  const stack = [x, y];
  while (stack.length) {
    const py = stack.pop()!;
    const px = stack.pop()!;
    if (seen[py * SIZE + px]) continue;
    let lx = px;
    while (lx >= 0 && !seen[py * SIZE + lx] && near((py * SIZE + lx) * 4)) lx--;
    lx++;
    let up = false;
    let down = false;
    for (let cx = lx; cx < SIZE; cx++) {
      const p = py * SIZE + cx;
      if (seen[p] || !near(p * 4)) break;
      seen[p] = 1;
      d[p * 4] = r;
      d[p * 4 + 1] = g;
      d[p * 4 + 2] = b;
      d[p * 4 + 3] = 255;
      for (const [ny, flag] of [[py - 1, 'up'], [py + 1, 'down']] as const) {
        if (ny < 0 || ny >= SIZE) continue;
        const q = ny * SIZE + cx;
        const open = !seen[q] && near(q * 4);
        const was = flag === 'up' ? up : down;
        if (open && !was) stack.push(cx, ny);
        if (flag === 'up') up = open;
        else down = open;
      }
    }
  }
  ctx.putImageData(img, 0, 0);
}

/** The black piece from the white one: light pixels turn dark (keeping their hue), dark ones stay. */
function darkened(src: HTMLCanvasElement): ImageData {
  const img = ctxOf(src).getImageData(0, 0, SIZE, SIZE);
  const d = img.data;
  for (let i = 0; i < d.length; i += 4) {
    if (!d[i + 3]) continue;
    const max = Math.max(d[i], d[i + 1], d[i + 2]);
    const min = Math.min(d[i], d[i + 1], d[i + 2]);
    const l = (max + min) / 510;
    if (l <= 0.5) continue;
    const k = Math.min(l, 1.05 - l) / l;
    d[i] = Math.round(d[i] * k);
    d[i + 1] = Math.round(d[i + 1] * k);
    d[i + 2] = Math.round(d[i + 2] * k);
  }
  return img;
}

function loadImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error('The picture could not be read'));
    img.src = url;
  });
}

/** Draws an image onto the canvas, as large as fits and centred. */
function drawContained(c: HTMLCanvasElement, img: HTMLImageElement) {
  const w = img.naturalWidth || SIZE;
  const h = img.naturalHeight || SIZE;
  const k = Math.min(SIZE / w, SIZE / h);
  ctxOf(c).drawImage(img, (SIZE - w * k) / 2, (SIZE - h * k) / 2, w * k, h * k);
}

export function PieceDrawer({ onSave, initial, onCancel, side: firstSide = 'w' }: PieceDrawerProps) {
  const [layers] = useState(() => ({ w: blank(), b: blank() }));
  const history = useRef<Record<Side, { past: ImageData[]; future: ImageData[] }>>({ w: { past: [], future: [] }, b: { past: [], future: [] } });
  const view = useRef<HTMLCanvasElement>(null);
  const [side, setSide] = useState<Side>(firstSide);
  const [tool, setTool] = useState<Tool>('pen');
  const [size, setSize] = useState(6);
  const [color, setColor] = useState('#2a2a2a');
  const [filled, setFilled] = useState(true);
  const [bg, setBg] = useState<'light' | 'dark' | 'checks'>(firstSide === 'w' ? 'dark' : 'light');
  /** The sides changed since opening: only they are saved. */
  const [touched, setTouched] = useState<Record<Side, boolean>>({ w: false, b: false });
  const [tick, setTick] = useState(0);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  /** The stroke or shape being drawn. */
  const stroke = useRef<{ start: Point; last: Point; id: number } | null>(null);

  const layer = layers[side];
  const bump = () => setTick((t) => t + 1);

  /** The canvas on screen: the side's layer and, while one is dragged out, the shape. */
  const paint = (preview?: (ctx: CanvasRenderingContext2D) => void) => {
    const c = view.current;
    if (!c) return;
    const ctx = c.getContext('2d')!;
    ctx.clearRect(0, 0, SIZE, SIZE);
    ctx.drawImage(layer, 0, 0);
    if (preview) preview(ctx);
  };
  useEffect(() => paint(), [side, tick]);

  // the piece's current pictures, to change them
  useEffect(() => {
    let live = true;
    for (const s of ['w', 'b'] as const) {
      const url = initial?.[s];
      if (!url) continue;
      loadImage(url).then((img) => {
        if (!live) return;
        drawContained(layers[s], img);
        bump();
      }).catch(() => {});
    }
    return () => { live = false; };
  }, []);

  /** Keeps the layer as it is for Undo, before it changes. */
  const remember = (s: Side = side) => {
    const h = history.current[s];
    h.past = [...h.past.slice(-(MAX_UNDO - 1)), ctxOf(layers[s]).getImageData(0, 0, SIZE, SIZE)];
    h.future = [];
    setTouched((t) => (t[s] ? t : { ...t, [s]: true }));
  };
  const undo = () => {
    const h = history.current[side];
    const prev = h.past.pop();
    if (!prev) return;
    h.future.push(ctxOf(layer).getImageData(0, 0, SIZE, SIZE));
    ctxOf(layer).putImageData(prev, 0, 0);
    bump();
  };
  const redo = () => {
    const h = history.current[side];
    const next = h.future.pop();
    if (!next) return;
    h.past.push(ctxOf(layer).getImageData(0, 0, SIZE, SIZE));
    ctxOf(layer).putImageData(next, 0, 0);
    bump();
  };

  const style = (ctx: CanvasRenderingContext2D, t: Tool) => {
    ctx.globalCompositeOperation = t === 'eraser' ? 'destination-out' : 'source-over';
    ctx.strokeStyle = color;
    ctx.fillStyle = color;
    ctx.lineWidth = size;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
  };
  const shape = (ctx: CanvasRenderingContext2D, a: Point, b: Point) => {
    style(ctx, tool);
    ctx.beginPath();
    if (tool === 'line') {
      ctx.moveTo(a.x, a.y);
      ctx.lineTo(b.x, b.y);
      ctx.stroke();
      return;
    }
    if (tool === 'rect') ctx.rect(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.abs(b.x - a.x), Math.abs(b.y - a.y));
    else ctx.ellipse((a.x + b.x) / 2, (a.y + b.y) / 2, Math.abs(b.x - a.x) / 2, Math.abs(b.y - a.y) / 2, 0, 0, Math.PI * 2);
    if (filled) ctx.fill();
    else ctx.stroke();
  };
  const segment = (a: Point, b: Point) => {
    const ctx = ctxOf(layer);
    style(ctx, tool);
    ctx.beginPath();
    if (a.x === b.x && a.y === b.y) {
      ctx.arc(a.x, a.y, size / 2, 0, Math.PI * 2);
      ctx.fill();
    } else {
      ctx.moveTo(a.x, a.y);
      ctx.lineTo(b.x, b.y);
      ctx.stroke();
    }
    ctx.globalCompositeOperation = 'source-over';
  };

  const point = (e: ReactPointerEvent<HTMLCanvasElement>): Point => {
    const r = e.currentTarget.getBoundingClientRect();
    return { x: ((e.clientX - r.left) / r.width) * SIZE, y: ((e.clientY - r.top) / r.height) * SIZE };
  };
  const down = (e: ReactPointerEvent<HTMLCanvasElement>) => {
    if (e.button !== 0) return;
    e.preventDefault();
    const p = point(e);
    if (tool === 'fill') {
      remember();
      floodFill(layer, Math.min(SIZE - 1, Math.max(0, Math.floor(p.x))), Math.min(SIZE - 1, Math.max(0, Math.floor(p.y))), color);
      bump();
      return;
    }
    e.currentTarget.setPointerCapture(e.pointerId);
    stroke.current = { start: p, last: p, id: e.pointerId };
    if (tool === 'pen' || tool === 'eraser') {
      remember();
      segment(p, p);
      paint();
    }
  };
  const move = (e: ReactPointerEvent<HTMLCanvasElement>) => {
    const s = stroke.current;
    if (!s || s.id !== e.pointerId) return;
    const p = point(e);
    if (tool === 'pen' || tool === 'eraser') {
      segment(s.last, p);
      paint();
    } else paint((ctx) => shape(ctx, s.start, p));
    s.last = p;
  };
  const up = (e: ReactPointerEvent<HTMLCanvasElement>) => {
    const s = stroke.current;
    if (!s || s.id !== e.pointerId) return;
    stroke.current = null;
    if (tool !== 'pen' && tool !== 'eraser') {
      remember();
      shape(ctxOf(layer), s.start, point(e));
      ctxOf(layer).globalCompositeOperation = 'source-over';
    }
    bump();
  };

  /** One of the set's pieces, drawn as it is shown, as the start of the picture. */
  const startFrom = (svg: SVGSVGElement | null) => {
    if (!svg) return;
    const copy = svg.cloneNode(true) as SVGSVGElement;
    copy.setAttribute('width', String(SIZE));
    copy.setAttribute('height', String(SIZE));
    const url = URL.createObjectURL(new Blob([new XMLSerializer().serializeToString(copy)], { type: 'image/svg+xml' }));
    loadImage(url).then((img) => {
      remember();
      ctxOf(layer).clearRect(0, 0, SIZE, SIZE);
      ctxOf(layer).drawImage(img, 0, 0, SIZE, SIZE);
      bump();
    }).catch((e: Error) => setError(e.message)).finally(() => URL.revokeObjectURL(url));
  };

  const blackFromWhite = () => {
    remember('b');
    ctxOf(layers.b).putImageData(darkened(layers.w), 0, 0);
    setSide('b');
    setBg('light');
    bump();
  };

  const save = async () => {
    setSaving(true);
    setError(null);
    try {
      const out: DrawnPictures = {};
      for (const s of ['w', 'b'] as const) {
        if (!touched[s]) continue;
        const blob = await new Promise<Blob | null>((resolve) => layers[s].toBlob(resolve, 'image/png'));
        if (blob) out[s] = blob;
      }
      await onSave(out);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const onKey = (e: ReactKeyboardEvent) => {
    if ((e.target as HTMLElement).tagName === 'INPUT') return;
    const mod = e.ctrlKey || e.metaKey;
    if (mod && e.key.toLowerCase() === 'z') e.shiftKey ? redo() : undo();
    else if (mod && e.key.toLowerCase() === 'y') redo();
    else if (!mod && !e.altKey && TOOLS.some((t) => t.key === e.key)) setTool(TOOLS.find((t) => t.key === e.key)!.id);
    else return;
    e.preventDefault();
  };

  const h = history.current[side];
  const shapeTool = tool === 'line' || tool === 'ellipse' || tool === 'rect';

  return (
    <div className="pd" data-testid="piece-drawer" onKeyDown={onKey}>
      <div className="pd-canvas-col">
        <div className="seg small" role="group" aria-label="Side">
          <button type="button" className={side === 'w' ? 'on' : ''} aria-pressed={side === 'w'} onClick={() => setSide('w')}>White piece</button>
          <button type="button" className={side === 'b' ? 'on' : ''} aria-pressed={side === 'b'} onClick={() => setSide('b')}>Black piece</button>
        </div>
        <canvas ref={view} width={SIZE} height={SIZE} className={'pd-canvas bg-' + bg} aria-label={`Drawing of the ${side === 'w' ? 'white' : 'black'} piece`}
          data-testid="piece-canvas" onPointerDown={down} onPointerMove={move} onPointerUp={up} onPointerCancel={up}
          onContextMenu={(e) => e.preventDefault()} />
        <div className="pd-row">
          <span className="muted small">Behind it:</span>
          <div className="seg small" role="group" aria-label="Background">
            {(['light', 'dark', 'checks'] as const).map((b) => (
              <button type="button" key={b} className={bg === b ? 'on' : ''} onClick={() => setBg(b)}>
                {b === 'light' ? 'Light square' : b === 'dark' ? 'Dark square' : 'See-through'}
              </button>
            ))}
          </div>
        </div>
      </div>
      <div className="pd-tools">
        <div className="pd-row" role="group" aria-label="Tool">
          <span className="pd-label">Tool</span>
          {TOOLS.map((t) => (
            <button type="button" key={t.id} className={'pd-tool' + (tool === t.id ? ' on' : '')} aria-pressed={tool === t.id}
              title={`${t.name} (${t.key.toUpperCase()})`} onClick={() => setTool(t.id)}>{t.name}</button>
          ))}
        </div>
        <div className="pd-row">
          <label className="pd-row" style={{ flex: 1 }}>
            <span className="muted small">Size</span>
            <input type="range" min={1} max={40} value={size} aria-label="Size" onChange={(e) => setSize(Number(e.target.value))} />
            <span className="small" style={{ width: 22 }}>{size}</span>
          </label>
          {shapeTool && tool !== 'line' && (
            <label className="inline small"><input type="checkbox" checked={filled} onChange={(e) => setFilled(e.target.checked)} /> Filled</label>
          )}
        </div>
        <div className="pd-row" role="group" aria-label="Colour">
          <span className="pd-label">Colour</span>
          {PRESETS.map((p) => (
            <button type="button" key={p.color} className={'pd-swatch' + (color === p.color ? ' on' : '')} style={{ background: p.color }}
              aria-label={p.name} title={p.name} aria-pressed={color === p.color} onClick={() => setColor(p.color)} />
          ))}
          <input type="color" className="pd-color" value={color} aria-label="Pick a colour" onChange={(e) => setColor(e.target.value)} />
        </div>
        <div className="pd-row">
          <span className="pd-label">Start from a piece</span>
          <PieceArt.Provider value={{}}>
            {['P', 'N', 'B', 'R', 'Q', 'K'].map((l) => (
              <button type="button" key={l} className="pd-base" aria-label={`Start from the ${{ P: 'pawn', N: 'knight', B: 'bishop', R: 'rook', Q: 'queen', K: 'king' }[l]}`}
                onClick={(e) => startFrom(e.currentTarget.querySelector('svg'))}>
                <PieceSvg code={side + l} />
              </button>
            ))}
          </PieceArt.Provider>
        </div>
        <div className="pd-row">
          <button type="button" className="btn small-btn" onClick={undo} disabled={!h.past.length} title="Undo (Ctrl+Z)">Undo</button>
          <button type="button" className="btn small-btn" onClick={redo} disabled={!h.future.length} title="Redo (Ctrl+Y)">Redo</button>
          <button type="button" className="btn small-btn" onClick={() => { remember(); ctxOf(layer).clearRect(0, 0, SIZE, SIZE); bump(); }}>Clear</button>
        </div>
        <div className="pd-row">
          <button type="button" className="btn small-btn" onClick={blackFromWhite}>Make the black one from the white one</button>
        </div>
        <p className="muted small" style={{ margin: 0 }}>
          A transparent background looks best: leave the space around the piece empty. Draw the black piece too, or let
          the white one's light parts turn dark. Without a black picture, Black uses the white one darkened.
        </p>
      </div>
      <div className="pd-foot" style={{ width: '100%' }}>
        {error && <p className="error small" role="alert">{error}</p>}
        {onCancel && <button type="button" className="btn ghost" onClick={onCancel}>Cancel</button>}
        <button type="button" className="btn primary" disabled={saving || (!touched.w && !touched.b)} onClick={save}>
          {saving ? 'Saving…' : 'Save pictures'}
        </button>
      </div>
    </div>
  );
}

/** The drawer in a dialog of its own. */
export function PieceDrawerDialog({ title, onClose, ...drawer }: Omit<PieceDrawerProps, 'onCancel'> & { title: string; onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = dialog.current;
    if (d && !d.open) d.showModal();
  }, []);
  return (
    <dialog ref={dialog} className="dialog drawer-dialog" aria-labelledby="drawer-title" onCancel={(e) => {
      e.preventDefault();
      onClose();
    }}>
      <div className="dialog-body">
        <h2 id="drawer-title">{title}</h2>
        <PieceDrawer {...drawer} onCancel={onClose} />
      </div>
    </dialog>
  );
}
