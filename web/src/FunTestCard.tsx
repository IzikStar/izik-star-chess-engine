import { useEffect, useState } from 'react';

const PLAYER_KEY = 'izikstar.funTest.player';

function loadPlayer(): string {
  try {
    return localStorage.getItem(PLAYER_KEY) ?? '';
  } catch {
    return '';
  }
}

/**
 * The blind fun test's one question (docs/fun-test.md), under the result of a game of a
 * fun-test-* variant: who played, and would they play it again tomorrow (1 to 5).
 */
export function FunTestCard({ variant, game }: { variant: string; game: string | null }) {
  const [player, setPlayer] = useState(loadPlayer);
  const [sent, setSent] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  // a new game asks again
  useEffect(() => {
    setSent(null);
    setError(null);
  }, [game]);

  const rate = async (rating: number) => {
    const name = player.trim();
    if (!name) {
      setError('Write your name first.');
      return;
    }
    try {
      localStorage.setItem(PLAYER_KEY, name);
    } catch {
      // the name is asked again next time
    }
    setError(null);
    const r = await fetch('/api/fun-test/ratings', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ player: name, variant, game: game ?? `${variant}-${Date.now()}`, rating }),
    }).catch(() => null);
    if (r?.ok) {
      setSent(rating);
    } else {
      setError('The rating was not saved. Try again.');
    }
  };

  return (
    <section className="fun-test" aria-label="Fun test" data-testid="fun-test">
      <label className="fun-test-name">
        <span>Your name</span>
        <input id="fun-test-player" value={player} maxLength={40} autoComplete="nickname"
          onChange={(e) => setPlayer(e.target.value)} />
      </label>
      <p className="fun-test-q">Would you play this again tomorrow?</p>
      <div className="fun-test-scale" role="group" aria-label="1 = no, 5 = gladly">
        {[1, 2, 3, 4, 5].map((n) => (
          <button key={n} type="button" className={'btn' + (sent === n ? ' primary' : '')} aria-pressed={sent === n}
            onClick={() => rate(n)}>{n}</button>
        ))}
      </div>
      <p className="fun-test-hint">{error ?? (sent !== null ? 'Thanks, saved.' : '1 = no · 5 = gladly')}</p>
    </section>
  );
}
