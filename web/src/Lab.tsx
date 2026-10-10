import { useEffect, useState } from 'react';
import type { Champion } from './protocol';
import { goLab, jobPlace, labRoute, usePolled, type Job } from './lab/api';
import { Runs } from './lab/Runs';
import { NewRun } from './lab/NewRun';
import { Run } from './lab/Run';
import { Fame } from './lab/Fame';
import { Guide } from './lab/Guide';

/**
 * The Lab: evolution runs (web.LabApi, web.LabJobs) over several screens, chosen by the hash below
 * "#lab": the runs, a new run, one run (and one of its generations), the hall of fame, the guide.
 */
export function Lab({ onPlay }: { onPlay: (champion: Champion) => void }) {
  const [route, setRoute] = useState<string[]>(labRoute);
  useEffect(() => {
    const onHash = () => setRoute(labRoute());
    window.addEventListener('hashchange', onHash);
    return () => window.removeEventListener('hashchange', onHash);
  }, []);
  const [job] = usePolled<Job>('/api/lab/job', 2000);

  const screen = route[0] ?? 'runs';
  const link = (name: string, label: string, parts: string[] = [name]) => (
    <button type="button" className={'btn ghost' + (screen === name ? ' on' : '')} aria-current={screen === name ? 'page' : undefined}
      onClick={() => goLab(...parts)}>{label}</button>
  );
  return (
    <main className="lab-page">
      <nav className="lab-nav" aria-label="Lab screens">
        {link('runs', 'Runs', [])}
        {link('new', 'New run')}
        {link('fame', 'Hall of fame')}
        {link('guide', 'How it works')}
        {job?.running && (
          <button type="button" className="btn ghost job-pill" onClick={() => goLab('run', job.file!)}>
            <span className="dot" aria-hidden="true" /> Playing: {job.name}
            {job.generation !== undefined && job.generation >= 0 && <> · generation {job.generation}, {job.gamesDone}/{job.gamesPlanned} games</>}
            {jobPlace(job) && <> · {jobPlace(job)}</>}
          </button>
        )}
      </nav>
      {screen === 'runs' && <Runs job={job} onPlay={onPlay} />}
      {screen === 'new' && <NewRun job={job} />}
      {screen === 'run' && route[1] && <Run file={route[1]} generation={route[2] === 'gen' ? Number(route[3]) : null} tab={route[2] === 'gen' ? 'generation' : route[2] ?? 'overview'} job={job} onPlay={onPlay} />}
      {screen === 'fame' && <Fame onPlay={onPlay} />}
      {screen === 'guide' && <Guide />}
    </main>
  );
}
