import { createContext, useContext, useId, type CSSProperties } from 'react';
import type { PieceRenderObject } from 'react-chessboard';

/*
 * The piece set: glossy Staunton pieces in a flat vector style, drawn on a 100x100 grid with
 * the base on y=92. Black is near-black with light-grey highlights; White is the same drawing
 * in ivory with soft grey shading. Every turned part gets its own left-to-right gradient, which
 * gives each collar, stem and skirt the bright streak down its left side.
 */

/** body: shaded part; line: highlight; spec: glint; cut: carved-in slit; detail: eye, nostril; stroke: drawn line; ridge: the mane's ridges. */
type Kind = 'body' | 'line' | 'spec' | 'cut' | 'detail' | 'stroke' | 'ridge';
interface Part { d: string; kind: Kind }

const C = 50;
const f = (n: number) => +n.toFixed(2);

/** A turned part between two widths, its sides bowed in by `bow` (0 = straight). */
function taper(y1: number, w1: number, y2: number, w2: number, bow = 0.6): Part {
  const [l1, r1, l2, r2] = [C - w1 / 2, C + w1 / 2, C - w2 / 2, C + w2 / 2];
  // control point: from the midpoint of the side towards the corner (narrow x, wide y)
  const side = (xa: number, xb: number, sign: number) => {
    const narrowX = sign * Math.min(sign * xa, sign * xb);
    const wideY = Math.abs(xa - C) > Math.abs(xb - C) ? y1 : y2;
    return [f((xa + xb) / 2 + (narrowX - (xa + xb) / 2) * bow), f((y1 + y2) / 2 + (wideY - (y1 + y2) / 2) * bow)];
  };
  const [rcx, rcy] = side(r1, r2, 1);
  const [lcx, lcy] = side(l1, l2, -1);
  return { kind: 'body', d: `M${f(l1)} ${y1}H${f(r1)}Q${rcx} ${rcy} ${f(r2)} ${y2}H${f(l2)}Q${lcx} ${lcy} ${f(l1)} ${y1}Z` };
}

/** A flat ring or plate with rounded ends, and its lit top edge. */
function band(y1: number, y2: number, w: number, r = 1.6): Part[] {
  const [l, rt] = [C - w / 2, C + w / 2];
  return [
    { kind: 'body', d: `M${l + r} ${y1}H${rt - r}Q${rt} ${y1} ${rt} ${y1 + r}V${y2 - r}Q${rt} ${y2} ${rt - r} ${y2}H${l + r}Q${l} ${y2} ${l} ${y2 - r}V${y1 + r}Q${l} ${y1} ${l + r} ${y1}Z` },
    { kind: 'line', d: `M${l + r + 1} ${y1 + 1}H${rt - r - 1}` },
  ];
}

/** Base plate, skirt and the ring above it: the foot every piece but the knight stands on. */
function foot(w: number, skirtTop: number, skirtW: number, ringW: number, ringH = 3): Part[] {
  return [taper(skirtTop, skirtW, 86, w - 6, 0.7), ...band(skirtTop - ringH, skirtTop, ringW), ...band(86, 92, w, 2)];
}

const pawn: Part[] = [
  taper(49, 17, 72, 27, 0.5),
  ...foot(60, 72, 30, 40, 4),
  ...band(45.5, 49, 31),
  ...band(43, 45.5, 22),
  { kind: 'body', d: 'M50 17a13.5 13.5 0 1 1 0 27a13.5 13.5 0 1 1 0-27Z' },
  { kind: 'spec', d: 'M41.5 25.5a3.6 3 -40 1 1 5 -4.5a3.6 3 -40 1 1 -5 4.5Z' },
];

const rook: Part[] = [
  taper(39, 31, 72, 40, 0.4),
  ...foot(68, 74, 46, 52, 3),
  ...band(35.5, 39, 46),
  ...band(33, 35.5, 40),
  { kind: 'body', d: 'M25 31V14H33.5V19.5H42V14H58V19.5H66.5V14H75V31Q75 33 73 33H27Q25 33 25 31Z' },
  { kind: 'line', d: 'M27 15.2H32.3M43.2 15.2H56.8M67.7 15.2H73.8' },
  { kind: 'line', d: 'M27.5 27.5H72.5' },
];

/** Neck rings under the king's and queen's crowns. */
const neck: Part[] = [...band(43, 46.5, 36), ...band(40.5, 43, 27), ...band(37, 40.5, 38)];

const king: Part[] = [
  taper(46, 19, 74, 34, 0.5),
  ...foot(66, 76, 38, 46),
  ...neck,
  taper(24, 40, 37, 22, 0.5),
  ...band(20.5, 24, 42),
  ...band(18, 20.5, 14),
  { kind: 'body', d: 'M47.2 2H52.8V6.8L57.8 6.2V12.8L52.8 12.2V18H47.2V12.2L42.2 12.8V6.2L47.2 6.8Z' },
  { kind: 'line', d: 'M48.5 3.5V16.5' },
];

const queen: Part[] = [
  taper(46, 19, 74, 34, 0.5),
  ...foot(66, 76, 38, 46),
  ...neck,
  taper(25, 34, 37, 21, 0.5),
  {
    kind: 'body',
    d: 'M31 25.5L28.5 20.5Q33 22 36 19.5Q39 22.5 43 20Q46.5 22.5 50 19.5Q53.5 22.5 57 20Q61 22.5 64 19.5Q67 22 71.5 20.5L69 25.5Q50 28.5 31 25.5Z',
  },
  { kind: 'body', d: 'M43.5 20.5Q44 16 50 15.5Q56 16 56.5 20.5Q50 22 43.5 20.5Z' },
  { kind: 'body', d: 'M50 6.5a4.6 4.6 0 1 1 0 9.2a4.6 4.6 0 1 1 0-9.2Z' },
  { kind: 'spec', d: 'M47.6 9.4a1.3 1.1 -40 1 1 1.8 -1.6a1.3 1.1 -40 1 1 -1.8 1.6Z' },
];

const bishop: Part[] = [
  taper(49, 18, 74, 31, 0.5),
  ...foot(64, 76, 34, 42),
  ...band(46, 49, 34),
  ...band(43.5, 46, 25),
  ...band(40, 43.5, 33),
  { kind: 'body', d: 'M37 40Q33 32 36.5 24.5Q40.5 16.5 50 12.5Q59.5 16.5 63.5 24.5Q67 32 63 40Z' },
  // the mitre's slit, cut in from the right
  { kind: 'cut', d: 'M63.6 24.5Q58 26.5 54 32.5Q52.6 34.4 51.2 33.3Q50 32.2 51.2 30.4Q55 24 60.4 19Q62.6 21.4 63.6 24.5Z' },
  { kind: 'body', d: 'M50 6a3.6 3.6 0 1 1 0 7.2a3.6 3.6 0 1 1 0-7.2Z' },
];

const knight: Part[] = [
  taper(76, 46, 86, 56, 0.7),
  ...band(73, 76, 50),
  ...band(86, 92, 64, 2),
  {
    kind: 'body',
    d: 'M31 73Q30 63 36 55.5Q39.5 51.5 40.5 48.5Q37 50.5 31 50Q27 50 24.5 51.5Q20.5 54 17 52.5Q13.5 51 13 47.5Q12.5 44 15 40.5Q19 35 24 30.5Q29 25.5 33 21.5L36 13.5L39.5 18L41.5 9.5L46 17Q57 16 65 23.5Q73.5 31.5 74 45Q74.5 60 70 73Z',
  },
  // mane: the ridged crest down the back of the neck
  { kind: 'ridge', d: 'M46 17.5Q57 17 64.5 24Q72.5 31.5 73 45Q73.5 59 69 73', },
  { kind: 'line', d: 'M45 21Q55 20.5 62 26.5Q69.5 33.5 70 45.5Q70.5 58 66.5 71' },
  // eye, nostril, mouth and the bright line down the cheek
  { kind: 'detail', d: 'M27.5 31Q30.5 28.5 34 29.5Q31.5 32.5 27.5 31Z' },
  { kind: 'detail', d: 'M17.3 42.5Q18.8 41 20.5 41.8Q19.2 43.6 17.3 42.5Z' },
  { kind: 'stroke', d: 'M13.8 47.2Q17 47.6 20 46.6' },
  { kind: 'line', d: 'M38.5 33Q44 35 44.5 41.5Q45 47 40.5 49M39 57Q35 62 34.5 70' },
];

const SHAPES: Record<string, Part[]> = { P: pawn, R: rook, N: knight, B: bishop, Q: queen, K: king };

interface Palette {
  /** Gradient stops across each part, left to right. */
  stops: [number, string][];
  outline: string;
  line: string;
  detail: string;
}

const BLACK: Palette = {
  stops: [[0, '#333336'], [0.1, '#404044'], [0.13, '#c4c4ca'], [0.24, '#8c8c92'], [0.29, '#323235'], [0.6, '#1c1c1e'], [0.84, '#1b1b1d'], [0.87, '#4a4a4f'], [1, '#5c5c61']],
  outline: '#050505',
  line: '#b4b4ba',
  detail: '#9a9aa0',
};

const WHITE: Palette = {
  stops: [[0, '#e2dbcb'], [0.09, '#efe9dd'], [0.11, '#ffffff'], [0.22, '#ffffff'], [0.25, '#f4efe4'], [0.6, '#e6e0d2'], [0.78, '#ddd6c6'], [0.81, '#c3bba9'], [1, '#cfc7b6']],
  outline: '#2a2a2a',
  line: '#ffffff',
  detail: '#5a564f',
};

/**
 * A piece with no drawing of its own (a variant's new piece): its letter in a circle, in the
 * set's colours, so any piece a variant defines can stand on the board.
 */
export function LetterPiece({ code, style, className }: { code: string; style?: CSSProperties; className?: string }) {
  const pal = code[0] === 'w' ? WHITE : BLACK;
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="100%" height="100%" style={style}
      className={className} data-piece={code} role="img" aria-label={`${code[0] === 'w' ? 'white' : 'black'} piece ${code[1]}`}>
      <circle cx="50" cy="52" r="34" fill={code[0] === 'w' ? '#f4efe4' : '#2b2b2b'} stroke={pal.outline} strokeWidth={2.5} />
      <text x="50" y="53" textAnchor="middle" dominantBaseline="central" fontSize="40" fontWeight="700"
        fontFamily="system-ui, sans-serif" fill={code[0] === 'w' ? '#2a2a2a' : '#f4efe4'}>{code[1]}</text>
    </svg>
  );
}

/**
 * The pictures the player gave a made variant's pieces, by piece code ("wA", "bA") -> image URL.
 * A piece with a picture is drawn with it, on every board inside the provider.
 */
export const PieceArt = createContext<Record<string, string>>({});

/** The {@link PieceArt} map for a variant: letter -> side ("w", "b") -> when the picture was saved. */
export function artUrls(variantId: string, index: Record<string, Record<string, number>> | undefined): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [letter, sides] of Object.entries(index ?? {})) {
    for (const [side, saved] of Object.entries(sides)) {
      out[side + letter] = `/api/variants/${encodeURIComponent(variantId)}/art/${letter}/${side}?v=${saved}`;
    }
  }
  return out;
}

/** A piece drawn with the player's picture; one side's picture stands in for the other's, darkened or lightened. */
function ArtPiece({ code, url, standIn, style, className }: { code: string; url: string; standIn: boolean; style?: CSSProperties; className?: string }) {
  const filter = standIn ? (code[0] === 'b' ? 'brightness(0.45)' : 'brightness(1.7) saturate(0.6)') : undefined;
  return (
    <img src={url} alt={`${code[0] === 'w' ? 'white' : 'black'} piece ${code[1]}`} data-piece={code} draggable={false}
      className={className} style={{ width: '100%', height: '100%', objectFit: 'contain', filter, ...style }} />
  );
}

/** One piece as an SVG, e.g. `<PieceSvg code="wN" />`; a letter with no drawing gets {@link LetterPiece}. */
export function PieceSvg({ code, style, className }: { code: string; style?: CSSProperties; className?: string }) {
  const art = useContext(PieceArt);
  const id = 'pc' + useId().replace(/[^a-zA-Z0-9]/g, '');
  const own = art[code];
  const other = art[(code[0] === 'w' ? 'b' : 'w') + code[1]];
  if (own || other) return <ArtPiece code={code} url={own ?? other} standIn={!own} style={style} className={className} />;
  const pal = code[0] === 'w' ? WHITE : BLACK;
  const parts = SHAPES[code[1]];
  if (!parts) return <LetterPiece code={code} style={style} className={className} />;
  const name = { P: 'pawn', R: 'rook', N: 'knight', B: 'bishop', Q: 'queen', K: 'king' }[code[1]];
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 100 100"
      width="100%"
      height="100%"
      style={style}
      className={className}
      data-piece={code}
      role="img"
      aria-label={`${code[0] === 'w' ? 'white' : 'black'} ${name}`}
    >
      <defs>
        <linearGradient id={id} x1="0" x2="1" y1="0" y2="0">
          {pal.stops.map(([o, c]) => <stop key={o} offset={o} stopColor={c} />)}
        </linearGradient>
      </defs>
      {parts.map((p, i) => {
        switch (p.kind) {
          case 'body':
            return <path key={i} d={p.d} fill={`url(#${id})`} stroke={pal.outline} strokeWidth={1.5} strokeLinejoin="round" />;
          case 'line':
            return <path key={i} d={p.d} fill="none" stroke={pal.line} strokeWidth={1} strokeLinecap="round" opacity={0.85} />;
          case 'spec':
            return <path key={i} d={p.d} fill="#ffffff" opacity={0.9} />;
          case 'cut':
            return <path key={i} d={p.d} fill={pal.outline} stroke={pal.line} strokeWidth={0.7} strokeLinejoin="round" />;
          case 'detail':
            return <path key={i} d={p.d} fill={pal.detail} stroke={pal.outline} strokeWidth={0.8} strokeLinejoin="round" />;
          case 'stroke':
            return <path key={i} d={p.d} fill="none" stroke={pal.outline} strokeWidth={1.1} strokeLinecap="round" />;
          case 'ridge':
            return <path key={i} d={p.d} fill="none" stroke={pal.detail} strokeWidth={2.6} strokeDasharray="1 1.6" />;
        }
      })}
    </svg>
  );
}

/** The set in react-chessboard's shape: wP, wN, ... bK, and a lettered circle for every other letter. */
export const pieceSet: PieceRenderObject = Object.fromEntries(
  ['w', 'b'].flatMap((c) =>
    'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('').map((p) => [c + p, (props?: { svgStyle?: CSSProperties }) => <PieceSvg code={c + p} style={props?.svgStyle} />]),
  ),
);

/** A FEN letter (P, n, ...) as its react-chessboard code (wP, bN, ...). */
export const codeOf = (letter: string) => (letter === letter.toUpperCase() ? 'w' : 'b') + letter.toUpperCase();
