import { useEffect, useRef, useState } from 'react';

// One click to download Stockfish into the game's engine/ folder (web.StockfishApi). The server
// switches to it as soon as it is unpacked: no restart.

interface Status {
  available: boolean;
  path: string | null;
  installing: boolean;
  progress: number | null;
  error: string | null;
  canInstall: boolean;
}

export function StockfishInstall({ why, onInstalled }: { why: string; onInstalled?: () => void }) {
  const [status, setStatus] = useState<Status | null>(null);
  const timer = useRef<number | undefined>(undefined);
  const done = useRef(onInstalled);
  done.current = onInstalled;

  const poll = async (method: 'GET' | 'POST') => {
    try {
      const res = await fetch(method === 'POST' ? '/api/stockfish/install' : '/api/stockfish', { method });
      const s: Status = await res.json();
      setStatus(s);
      if (s.installing) timer.current = window.setTimeout(() => poll('GET'), 400);
      else if (s.available && method === 'GET') done.current?.();
    } catch {
      setStatus((old) => old && { ...old, installing: false, error: 'Could not reach the game server.' });
    }
  };

  useEffect(() => {
    poll('GET');
    return () => window.clearTimeout(timer.current);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!status) return null;
  if (status.available && !status.installing) {
    return <p className="ok-note" data-testid="stockfish-ready">Stockfish is installed and ready.</p>;
  }
  return (
    <div className="warn-note stockfish-install" data-testid="stockfish-install">
      <p>{why}</p>
      {status.installing ? (
        <div className="install-progress" role="progressbar" aria-label="Downloading Stockfish"
          aria-valuenow={status.progress === null ? undefined : Math.round(status.progress * 100)} aria-valuemin={0} aria-valuemax={100}>
          <div style={{ width: `${Math.round((status.progress ?? 0.05) * 100)}%` }} />
          <span>Downloading Stockfish… {status.progress === null ? '' : `${Math.round(status.progress * 100)}%`}</span>
        </div>
      ) : status.canInstall ? (
        <button type="button" className="btn primary" onClick={() => poll('POST')}>Download Stockfish</button>
      ) : (
        <p>There is no ready-made Stockfish for this computer; see engine/README.md.</p>
      )}
      {status.error && <p className="install-error">{status.error}</p>}
      {!status.installing && status.canInstall && (
        <p className="muted small">A one-time download (about 70 MB) from Stockfish's official GitHub page into the game's engine/ folder.</p>
      )}
    </div>
  );
}
