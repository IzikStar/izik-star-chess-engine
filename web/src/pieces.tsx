import type { CSSProperties } from 'react';
import sprite from '../../docs/art/pieces.svg?raw';

// The project's own piece set (docs/art/pieces.svg, MIT like the rest of the repo), used by the
// board and the promotion picker. The sheet is 6 columns (king, queen, bishop, knight, rook, pawn)
// by 2 rows (white, black) of 100×100 cells; each cell is cut out into its own small SVG.

const ORDER = ['K', 'Q', 'B', 'N', 'R', 'P'];

function cells(): Record<string, string> {
  const doc = new DOMParser().parseFromString(sprite, 'image/svg+xml');
  const xml = new XMLSerializer();
  const defs = doc.querySelector('defs');
  const defsXml = defs ? xml.serializeToString(defs) : '';
  const out: Record<string, string> = {};
  for (const g of Array.from(doc.documentElement.children)) {
    const m = /translate\((\d+)\s+(\d+)\)/.exec(g.getAttribute('transform') ?? '');
    if (g.tagName !== 'g' || !m) continue;
    const key = (Number(m[2]) === 0 ? 'w' : 'b') + ORDER[Number(m[1]) / 100];
    const inner = Array.from(g.children).map((c) => xml.serializeToString(c)).join('');
    out[key] = defsXml + inner;
  }
  return out;
}

const CELLS = cells();

export type PieceKey = `${'w' | 'b'}${'K' | 'Q' | 'B' | 'N' | 'R' | 'P'}`;

export function PieceSvg({ piece, style }: { piece: string; style?: CSSProperties }) {
  return (
    <svg
      viewBox="0 0 100 100"
      width="100%"
      height="100%"
      style={style}
      aria-hidden="true"
      dangerouslySetInnerHTML={{ __html: CELLS[piece] ?? '' }}
    />
  );
}

/** The board's piece renderers, keyed like react-chessboard's ("wK", "bP", …). */
export const PIECES = Object.fromEntries(
  Object.keys(CELLS).map((key) => [key, (props?: { svgStyle?: CSSProperties }) => <PieceSvg piece={key} style={props?.svgStyle} />]),
);
