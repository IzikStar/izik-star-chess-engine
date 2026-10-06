import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';

/**
 * A chart that opens large: an expand button shows on hover, focus and touch screens, and clicking
 * the chart (unless the chart's own click does something) or the button opens it in a dialog.
 */
export function ChartFrame({ title, large, note, clickOpens = true, inline = false, children }: {
  /** The dialog's name and heading. */
  title: string;
  /** The chart drawn large; rendered only while the dialog is open. */
  large: ReactNode;
  /** A line under the heading saying what the chart shows. */
  note?: ReactNode;
  /** False when a click on the small chart already does something (the eval graph picks a move). */
  clickOpens?: boolean;
  /** A chart inside a line of text or a table cell (a sparkline): the button sits beside it. */
  inline?: boolean;
  children: ReactNode;
}) {
  const [open, setOpen] = useState(false);
  return (
    <div className={'chart-frame' + (inline ? ' inline' : '') + (clickOpens ? ' opens' : '')}
      onClick={clickOpens ? () => setOpen(true) : undefined}>
      {children}
      <button type="button" className="chart-expand" aria-label="Open chart large" title={`Open “${title}” large`}
        onClick={(e) => {
          e.stopPropagation();
          setOpen(true);
        }}>
        <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M14 4h6v6 M20 4l-7 7 M10 20H4v-6 M4 20l7-7" />
        </svg>
      </button>
      {open && <ChartDialog title={title} note={note} onClose={() => setOpen(false)}>{large}</ChartDialog>}
    </div>
  );
}

function ChartDialog({ title, note, onClose, children }: { title: string; note?: ReactNode; onClose: () => void; children: ReactNode }) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = dialog.current;
    if (d && !d.open) d.showModal();
  }, []);
  return (
    // the frame's click opens it: clicks inside the dialog stay here
    <dialog ref={dialog} className="dialog chart-dialog" aria-label={title} data-testid="chart-dialog"
      onClick={(e) => {
        e.stopPropagation();
        // a click on the backdrop lands on the dialog itself, outside its body
        if (e.target === dialog.current) onClose();
      }}
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}>
      <div className="chart-dialog-body">
        <div className="chart-dialog-head">
          <div>
            <h2>{title}</h2>
            {note && <p className="muted small">{note}</p>}
          </div>
          <button type="button" className="btn ghost" aria-label="Close" onClick={onClose}>✕</button>
        </div>
        {children}
      </div>
    </dialog>
  );
}

export interface ChartPoint { x: number; y: number; low?: number; high?: number }

/** Round steps (1, 2, 2.5, 5 × a power of ten) about count of them from lo to hi, so labels never crowd. */
function ticks(lo: number, hi: number, count: number, whole = false): number[] {
  const raw = (hi - lo) / Math.max(1, count) || 1;
  const mag = 10 ** Math.floor(Math.log10(raw));
  let step = [1, 2, 2.5, 5, 10].map((f) => f * mag).find((s) => s >= raw)!;
  if (whole) step = Math.max(1, Math.round(step));
  const out: number[] = [];
  for (let t = Math.ceil(lo / step - 1e-9) * step; t <= hi + 1e-9; t += step) out.push(Number(t.toFixed(6)));
  return out;
}

/**
 * The large chart of a dialog: one line of values over generations or plies, with axes, labels,
 * gridlines, an optional error bar per point, and a tooltip with the value under the pointer.
 */
export function LineChart({ points, xLabel, yLabel, yMin, yMax, xMax, reference, area = false, marker, describe, onPick, label }: {
  points: ChartPoint[];
  xLabel: string;
  yLabel: string;
  yMin?: number;
  yMax?: number;
  xMax?: number;
  /** A dashed line across, such as Elo 0 or a weight's default. */
  reference?: { y: number; label: string };
  /** Fills under the line (the eval graph: White's share). */
  area?: boolean;
  /** A vertical line at this x (the eval graph: the move on the board). */
  marker?: number;
  /** The tooltip of one point. */
  describe: (p: ChartPoint) => string;
  /** Clicking picks the nearest point's x (the eval graph: go to that move). */
  onPick?: (x: number) => void;
  label: string;
}) {
  const box = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState({ w: 0, h: 0 });
  const [hover, setHover] = useState<number | null>(null);
  useLayoutEffect(() => {
    const el = box.current;
    if (!el) return;
    const measure = () => setSize({ w: el.clientWidth, h: el.clientHeight });
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  const { w: W, h: H } = size;
  const narrow = W < 520;
  const L = narrow ? 52 : 68, R = 18, T = 16, B = narrow ? 44 : 52;
  const xs = points.map((p) => p.x);
  const x0 = Math.min(0, ...xs);
  const x1 = Math.max(xMax ?? 0, ...xs, x0 + 1);
  const ys = points.flatMap((p) => [p.y, p.low ?? p.y, p.high ?? p.y]).concat(reference ? [reference.y] : []);
  let lo = yMin ?? Math.min(...ys);
  let hi = yMax ?? Math.max(...ys);
  if (hi - lo < 1e-9) {
    lo -= 1;
    hi += 1;
  } else if (yMin === undefined || yMax === undefined) {
    // a little air above and below a line that is not pinned to its range
    const pad = (hi - lo) * 0.06;
    if (yMin === undefined) lo -= pad;
    if (yMax === undefined) hi += pad;
  }
  const yTicks = ticks(lo, hi, Math.max(3, Math.round((H - T - B) / 60)));
  const xTicks = ticks(x0, x1, Math.max(2, Math.round((W - L - R) / 70)), true);
  const x = (v: number) => L + ((W - L - R) * (v - x0)) / (x1 - x0);
  const y = (v: number) => T + ((H - T - B) * (hi - v)) / (hi - lo);
  const fmt = (v: number) => (Math.abs(v) >= 1000 || Number.isInteger(v) ? String(Math.round(v)) : String(Number(v.toFixed(2))));
  const line = points.map((p, i) => `${i ? 'L' : 'M'}${x(p.x).toFixed(1)},${y(p.y).toFixed(1)}`).join(' ');
  const hovered = hover === null ? null : points[hover];

  const nearest = (clientX: number, el: Element) => {
    const r = el.getBoundingClientRect();
    const at = x0 + ((clientX - r.left - L) / (W - L - R)) * (x1 - x0);
    let best = 0;
    points.forEach((p, i) => {
      if (Math.abs(p.x - at) < Math.abs(points[best].x - at)) best = i;
    });
    return best;
  };

  return (
    <div className="line-chart" ref={box}>
      {W > 0 && H > 0 && points.length > 0 && (
        <svg className={'chart large' + (onPick ? ' pick' : '')} width={W} height={H} role="img" aria-label={label}
          onPointerMove={(e) => setHover(nearest(e.clientX, e.currentTarget))}
          onPointerDown={(e) => setHover(nearest(e.clientX, e.currentTarget))}
          onPointerLeave={(e) => e.pointerType === 'mouse' && setHover(null)}
          onClick={(e) => onPick?.(points[nearest(e.clientX, e.currentTarget)].x)}>
          {yTicks.map((t) => (
            <g key={'y' + t}>
              <line x1={L} x2={W - R} y1={y(t)} y2={y(t)} className="grid" />
              <text x={L - 8} y={y(t) + 4} textAnchor="end">{fmt(t)}</text>
            </g>
          ))}
          {xTicks.map((t) => (
            <g key={'x' + t}>
              <line x1={x(t)} x2={x(t)} y1={T} y2={H - B} className="grid" />
              <text x={x(t)} y={H - B + 18} textAnchor="middle">{t}</text>
            </g>
          ))}
          <line x1={L} x2={L} y1={T} y2={H - B} className="axis" />
          <line x1={L} x2={W - R} y1={H - B} y2={H - B} className="axis" />
          <text className="axis-label" x={L + (W - L - R) / 2} y={H - 6} textAnchor="middle">{xLabel}</text>
          <text className="axis-label" transform={`translate(14 ${T + (H - T - B) / 2}) rotate(-90)`} textAnchor="middle">{yLabel}</text>
          {area && <path className="area" d={`${line} L${x(points.at(-1)!.x)},${y(lo)} L${x(points[0].x)},${y(lo)} Z`} />}
          {reference && (
            <g>
              <line className="zero" x1={L} x2={W - R} y1={y(reference.y)} y2={y(reference.y)} />
              <text className="ref-label" x={W - R - 4} y={y(reference.y) - 5} textAnchor="end">{reference.label}</text>
            </g>
          )}
          <path className="line" d={line} />
          {points.map((p, i) => p.low !== undefined && p.high !== undefined && (
            <line key={'e' + i} className="err" x1={x(p.x)} x2={x(p.x)} y1={y(p.high)} y2={y(p.low)} />
          ))}
          {/* dots only while they do not run together */}
          {points.length <= (W - L - R) / 14 && points.map((p, i) => <circle key={'d' + i} className="dot" cx={x(p.x)} cy={y(p.y)} r={3.5} />)}
          {marker !== undefined && <line className="marker" x1={x(marker)} x2={x(marker)} y1={T} y2={H - B} />}
          {hovered && (
            <g className="hover">
              <line x1={x(hovered.x)} x2={x(hovered.x)} y1={T} y2={H - B} />
              <circle cx={x(hovered.x)} cy={y(hovered.y)} r={6} />
            </g>
          )}
        </svg>
      )}
      {hovered && (
        <div className="chart-tip" role="status" data-testid="chart-tip"
          style={{ left: Math.min(Math.max(x(hovered.x), 90), W - 90), top: Math.max(0, y(hovered.y) - 14) }}>
          {describe(hovered)}
        </div>
      )}
    </div>
  );
}
