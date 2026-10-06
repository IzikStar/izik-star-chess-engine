import { useState } from 'react';
import type { Champion } from '../protocol';
import { algorithmName, duration, elo, goLab, player, post, runUrl, usePolled, type Job, type RunSummary } from './api';

/** The runs screen: every run in the folder as a card, the one playing first, with what to do with it. */
export function Runs({ job, onPlay }: { job: Job | null; onPlay: (champion: Champion) => void }) {
  const [runs, error, refresh] = usePolled<{ folder: string; runs: RunSummary[] }>('/api/lab/runs');
  const [busy, setBusy] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const act = async (file: string, what: 'resume' | 'stop' | 'stopNow' | 'delete') => {
    setBusy(file);
    setMessage(null);
    try {
      if (what === 'delete') {
        if (!confirm('Delete this run and all its games? This cannot be undone.')) return;
        await post(runUrl(file), undefined, 'DELETE');
      } else if (what === 'resume') await post(runUrl(file) + '/resume');
      else await post(runUrl(file) + '/stop', { now: what === 'stopNow' });
      refresh();
    } catch (e) {
      setMessage(String((e as Error).message ?? e));
    } finally {
      setBusy(null);
    }
  };

  if (error) return <section className="panel"><p className="muted">Could not load the runs: {error}</p></section>;
  if (!runs) return <section className="panel"><p className="muted loading-line"><span className="spinner" aria-hidden="true" />Loading the runs…</p></section>;
  const running = job?.running ? job.file : null;
  const sorted = [...runs.runs].sort((a, b) => (a.file === running ? -1 : b.file === running ? 1 : 0));
  return (
    <div className="lab-screen">
      {job?.error && <p className="error">The last run stopped with an error: {job.error}</p>}
      {message && <p className="error">{message}</p>}
      {sorted.length === 0 && (
        <section className="panel empty-lab" data-testid="lab-empty">
          <h2>No evolution runs yet</h2>
          <p>Start one with <button type="button" className="link" onClick={() => goLab('new')}>New run</button>: pick a game, an algorithm and
            how long it plays, and watch it here as it goes. Runs are recorded in <code>{runs.folder}</code>, where <code>lab.Cli</code> also writes them.</p>
          <p className="muted">New to this? Read <button type="button" className="link" onClick={() => goLab('guide')}>How it works</button> first.</p>
        </section>
      )}
      <div className="run-cards">
        {sorted.map((r) => {
          const isRunning = r.file === running;
          const finished = r.generationsDone >= r.settings.generations;
          const state = isRunning ? (job?.stopping ? 'stopping' : 'playing') : finished ? 'finished' : 'stopped';
          const share = Math.min(1, r.generationsDone / r.settings.generations);
          return (
            <section key={r.file} className={'panel run-card ' + state} data-testid="run-card">
              <div className="run-card-head">
                <h2><button type="button" className="link title" onClick={() => goLab('run', r.file)}>{r.name}</button></h2>
                <span className={'state ' + state}>{state}</span>
              </div>
              <p className="muted">
                {r.variantName} · {algorithmName(r.algorithm)} · depth {r.settings.depth}
                {r.settings.deepDepth ? `, some at ${r.settings.deepDepth}` : ''} · started {new Date(r.startedAt).toLocaleString()}
              </p>
              <div className="progress" role="progressbar" aria-valuenow={r.generationsDone} aria-valuemax={r.settings.generations} aria-label="Generations done">
                <div className="bar" style={{ width: `${share * 100}%` }} />
              </div>
              <p>
                <strong>{r.generationsDone}</strong> of {r.settings.generations} generations
                {isRunning && job?.generation !== undefined && job.generation >= 0 && <> · generation {job.generation}: {job.gamesDone}/{job.gamesPlanned} games</>}
                {isRunning && job?.startedAt && <> · playing for {duration(job.startedAt, new Date().toISOString())}</>}
                {r.lastYardstick && <> · last champion {elo(r.lastYardstick)} Elo against {player(r.settings.yardsticks?.[0] ?? 'default').toLowerCase()}</>}
              </p>
              <div className="actions">
                <button type="button" className="btn primary" onClick={() => goLab('run', r.file)}>Open</button>
                {isRunning && !job?.stopping && <button type="button" className="btn" disabled={busy === r.file} onClick={() => act(r.file, 'stop')}>Stop after this generation</button>}
                {isRunning && <button type="button" className="btn" disabled={busy === r.file} onClick={() => act(r.file, 'stopNow')}>Stop now</button>}
                {!isRunning && !finished && <button type="button" className="btn" disabled={busy === r.file || !!running} onClick={() => act(r.file, 'resume')}>Resume</button>}
                {r.generationsDone > 0 && (
                  <button type="button" className="btn" onClick={() => onPlay({ run: r.file, generation: r.generationsDone - 1, variant: r.variantId, label: `Champion of ${r.name}, generation ${r.generationsDone - 1}` })}>Play the champion</button>
                )}
                {!isRunning && <button type="button" className="btn ghost danger" disabled={busy === r.file} onClick={() => act(r.file, 'delete')}>Delete</button>}
              </div>
            </section>
          );
        })}
      </div>
    </div>
  );
}
