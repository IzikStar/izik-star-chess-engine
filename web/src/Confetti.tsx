import { useEffect, useMemo, useState } from 'react';

const COLORS = ['#2f6f5e', '#5fbfa3', '#f0a528', '#e8614f', '#4a7fd4', '#f4f4f1'];

/**
 * A short burst of confetti over the board when you win: fun, gone after three seconds, and
 * skipped altogether for people who asked their system for less motion.
 */
export function Confetti() {
  const [done, setDone] = useState(false);
  const bits = useMemo(() => Array.from({ length: 70 }, (_, i) => ({
    left: Math.random() * 100,
    delay: Math.random() * 0.35,
    drift: (Math.random() - 0.5) * 160,
    spin: (Math.random() - 0.5) * 900,
    color: COLORS[i % COLORS.length],
    wide: Math.random() < 0.5,
  })), []);
  useEffect(() => {
    const t = setTimeout(() => setDone(true), 3100);
    return () => clearTimeout(t);
  }, []);
  if (done) return null;
  return (
    <div className="confetti" aria-hidden="true" data-testid="confetti">
      {bits.map((b, i) => (
        <i key={i} style={{
          left: `${b.left}%`, background: b.color, animationDelay: `${b.delay}s`,
          width: b.wide ? 10 : 6, height: b.wide ? 6 : 12,
          ['--drift' as string]: `${b.drift}px`, ['--spin' as string]: `${b.spin}deg`,
        }} />
      ))}
    </div>
  );
}
