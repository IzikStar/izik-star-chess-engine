/** Small line icons for the top bar, drawn in the text colour so they follow the theme. */
const PATHS: Record<string, string> = {
  sound: 'M4 9v6h4l5 4V5L8 9H4z M16 8.5a5 5 0 0 1 0 7 M18.5 6a8.5 8.5 0 0 1 0 12',
  mute: 'M4 9v6h4l5 4V5L8 9H4z M16.5 9.5l5 5 M21.5 9.5l-5 5',
  sun: 'M12 7.5a4.5 4.5 0 1 0 0 9a4.5 4.5 0 1 0 0-9z M12 2v2.5 M12 19.5V22 M2 12h2.5 M19.5 12H22 M4.9 4.9l1.8 1.8 M17.3 17.3l1.8 1.8 M4.9 19.1l1.8-1.8 M17.3 6.7l1.8-1.8',
  moon: 'M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z',
  gear: 'M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z M19.4 13a7.6 7.6 0 0 0 0-2l2-1.6-2-3.4-2.4 1a7.4 7.4 0 0 0-1.7-1L15 3.5h-4l-.4 2.5a7.4 7.4 0 0 0-1.7 1l-2.4-1-2 3.4 2 1.6a7.6 7.6 0 0 0 0 2l-2 1.6 2 3.4 2.4-1a7.4 7.4 0 0 0 1.7 1l.4 2.5h4l.4-2.5a7.4 7.4 0 0 0 1.7-1l2.4 1 2-3.4z',
  first: 'M6 6v12 M18 6l-8 6 8 6z',
  prev: 'M16 6l-9 6 9 6z',
  next: 'M8 6l9 6-9 6z',
  last: 'M18 6v12 M6 6l8 6-8 6z',
};

export function Icon({ name }: { name: keyof typeof PATHS }) {
  return (
    <svg className="ico" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" fill="none" stroke="currentColor"
      strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d={PATHS[name]} /></svg>
  );
}
