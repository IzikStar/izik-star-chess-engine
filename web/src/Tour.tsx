import { useEffect, useLayoutEffect, useState } from 'react';

/** One stop of the tour: the element it points at and what it says there. */
const STEPS: { target: string; title: string; text: string }[] = [
  { target: '[data-tour="new-game"]', title: 'Play', text: 'New game: against the computer at 14 levels, a friend on the same board, or watch the engine play itself. Pick chess or any variant.' },
  { target: '[data-tour="games"]', title: 'My games', text: 'Every game is saved on its own. Review it, let Stockfish analyse it, or carry on where you stopped.' },
  { target: '[data-tour="variants"]', title: 'Variants', text: 'Invent your own game: new pieces, a start position, a goal. The health check plays it a hundred times and tells you if it is fun.' },
  { target: '[data-tour="lab"]', title: 'Lab', text: 'Evolution: sets of engine weights play each other, the best breed. Watch the charts, then play the champion.' },
  { target: '[data-tour="settings"]', title: 'Settings', text: 'Hints, the evaluation bar, sound and dark mode. The tour lives here too, if you want it again.' },
];

function seen(): boolean {
  try {
    return localStorage.getItem('tour') === 'done';
  } catch {
    return true; // no storage: do not nag on every visit
  }
}

function remember() {
  try {
    localStorage.setItem('tour', 'done');
  } catch {
    // private window: it shows again next time
  }
}

/**
 * The first visit's welcome: a quiet line under the top bar offering a short tour, and the tour
 * itself, a card that walks the top bar's buttons one by one. Nothing blocks the page.
 */
export function Tour({ open, onOpen, onClose }: { open: boolean; onOpen: () => void; onClose: () => void }) {
  const [offer, setOffer] = useState(() => !seen());
  const [step, setStep] = useState(0);
  const [box, setBox] = useState<DOMRect | null>(null);

  useEffect(() => {
    if (open) setStep(0);
  }, [open]);

  // point at the step's element, and follow it when the window changes size
  useLayoutEffect(() => {
    if (!open) return;
    const el = document.querySelector(STEPS[step].target);
    el?.classList.add('tour-target');
    const place = () => setBox(el ? el.getBoundingClientRect() : null);
    place();
    window.addEventListener('resize', place);
    return () => {
      el?.classList.remove('tour-target');
      window.removeEventListener('resize', place);
    };
  }, [open, step]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') finish();
      else if (e.key === 'ArrowRight' && step < STEPS.length - 1) setStep(step + 1);
      else if (e.key === 'ArrowLeft' && step > 0) setStep(step - 1);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  const finish = () => {
    remember();
    setOffer(false);
    onClose();
  };

  if (!open) {
    if (!offer) return null;
    return (
      <div className="tour-offer" role="note" data-testid="tour-offer">
        <span>New here? A short tour shows what each screen is for.</span>
        <button type="button" className="btn primary" onClick={() => { setOffer(false); onOpen(); }}>Show me around</button>
        <button type="button" className="btn ghost" aria-label="No thanks" onClick={() => { remember(); setOffer(false); }}>✕</button>
      </div>
    );
  }

  const s = STEPS[step];
  const width = Math.min(320, window.innerWidth - 24);
  const left = box ? Math.max(12, Math.min(window.innerWidth - width - 12, box.left + box.width / 2 - width / 2)) : 12;
  const top = box ? box.bottom + 12 : 80;
  return (
    <section className="tour-card" role="dialog" aria-label={`Tour: ${s.title}`} data-testid="tour" style={{ left, top, width }}>
      <div className="tour-step">{step + 1} / {STEPS.length}</div>
      <h3>{s.title}</h3>
      <p>{s.text}</p>
      <div className="row">
        <button type="button" className="btn ghost" onClick={finish}>Skip</button>
        <span className="spacer" />
        {step > 0 && <button type="button" className="btn" onClick={() => setStep(step - 1)}>Back</button>}
        {step < STEPS.length - 1
          ? <button type="button" className="btn primary" onClick={() => setStep(step + 1)}>Next</button>
          : <button type="button" className="btn primary" onClick={finish}>Done</button>}
      </div>
    </section>
  );
}
