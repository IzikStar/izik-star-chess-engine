import { useEffect, useState } from 'react';
import { colorName, formatClock } from './chess';
import type { ClockState, Color } from './protocol';

/**
 * One side's clock. The server keeps the real time; this counts the running side down from the
 * last state it sent (receivedAt), so the display moves smoothly between messages.
 */
export function ClockFace({ clock, color, receivedAt }: { clock: ClockState; color: Color; receivedAt: number }) {
  const running = clock.running === color;
  const [, tick] = useState(0);
  useEffect(() => {
    if (!running) return;
    const id = window.setInterval(() => tick((n) => n + 1), 100);
    return () => window.clearInterval(id);
  }, [running]);
  const ms = Math.max(0, clock[color] - (running ? Math.max(0, performance.now() - receivedAt) : 0));
  const low = ms < Math.min(20_000, clock.initialMs / 5);
  return (
    <div
      className={'clock' + (running ? ' running' : '') + (low ? ' low' : '') + (ms === 0 ? ' flagged' : '')}
      role="timer"
      aria-label={`${colorName(color)}'s clock`}
      data-testid={`clock-${color}`}
    >
      {formatClock(ms)}
    </div>
  );
}
