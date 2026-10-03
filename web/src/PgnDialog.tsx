import { useEffect, useRef, useState } from 'react';

/** Copy or download the game as PGN, or load a game from PGN (it becomes a two-player game). */
export function PgnDialog({ pgn, error, loading, onLoad, onClose }: {
  pgn: string;
  /** Why the last PGN could not be loaded, from the server. */
  error: string | null;
  loading: boolean;
  onLoad: (pgn: string) => void;
  onClose: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const exported = useRef<HTMLTextAreaElement>(null);
  const [paste, setPaste] = useState('');
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    const d = dialog.current;
    if (d && !d.open) d.showModal();
  }, []);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(pgn);
    } catch {
      // no clipboard permission (plain http on another host): select it for Ctrl+C instead
      exported.current?.select();
      document.execCommand('copy');
    }
    setCopied(true);
  };

  const download = () => {
    const url = URL.createObjectURL(new Blob([pgn], { type: 'application/x-chess-pgn' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = `izikstar-${new Date().toISOString().slice(0, 10)}.pgn`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <dialog ref={dialog} className="dialog wide" aria-labelledby="pgn-title" onCancel={(e) => {
      e.preventDefault();
      onClose();
    }}>
      <div className="dialog-body">
        <h2 id="pgn-title">PGN</h2>
        <div className="field">
          <label htmlFor="pgn-out">This game</label>
          <textarea id="pgn-out" ref={exported} className="pgn" readOnly rows={10} value={pgn} />
          <div className="row end">
            {copied && <span className="muted" role="status">Copied</span>}
            <button type="button" className="btn" onClick={copy}>Copy</button>
            <button type="button" className="btn" onClick={download}>Download .pgn</button>
          </div>
        </div>
        <form className="field" onSubmit={(e) => {
          e.preventDefault();
          if (paste.trim()) onLoad(paste);
        }}>
          <label htmlFor="pgn-in">Load a game</label>
          <textarea id="pgn-in" className="pgn" rows={6} value={paste} placeholder="Paste a game in PGN, e.g. 1. e4 e5 2. Nf3 Nc6"
            onChange={(e) => setPaste(e.target.value)} />
          <p className="muted small">It replaces the current game and continues as a game between two players.</p>
          {error && <p className="error" role="alert">{error}</p>}
          <div className="row end">
            <button type="button" className="btn" onClick={onClose}>Close</button>
            <button type="submit" className="btn primary" disabled={!paste.trim() || loading}>Load game</button>
          </div>
        </form>
      </div>
    </dialog>
  );
}
