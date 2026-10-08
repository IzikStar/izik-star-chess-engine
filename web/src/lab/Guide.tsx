import { goLab } from './api';

/** What the Lab's words mean, for someone who has not read the research docs. */
export function Guide() {
  return (
    <div className="lab-screen guide">
      <section className="panel">
        <h2>How the Lab works</h2>
        <p>The engine judges a position with an <strong>evaluation</strong>: a sum of weights, like "a knight is worth 320" or "a rook on an open
          file is worth 25 more". The Lab breeds those weights. It never changes how the engine searches, only what it thinks is good.</p>
        <h3>A run, generation by generation</h3>
        <ol>
          <li><strong>Population.</strong> A run holds a few sets of weights, its <em>members</em> (#0, #1, …). Each member is a complete engine.</li>
          <li><strong>Games.</strong> Every generation each pair of members plays a few openings, once with each colour, at a fixed search depth. A member's
            <em> score</em> is its points per game (a win 1, a draw ½).</li>
          <li><strong>Champion.</strong> The member with the best score. "Champion #7 scored 72%" means member 7 took 72% of the points available to it.</li>
          <li><strong>Selection and children.</strong> The algorithm decides who survives and how children are made (copies with small random changes,
            mixtures of two parents, brand-new random members). That is the next generation.</li>
          <li><strong>Yardsticks.</strong> Members only play each other, so their scores say who is better <em>within</em> the generation, not whether the run
            is getting anywhere. Every few generations the champion also plays fixed opponents that never change: the default weights, the classic
            hand-written ones, an engine with every weight at zero, or Stockfish at a chosen level. That is the number the progress chart follows.</li>
        </ol>
        <h3>Reading the numbers</h3>
        <ul>
          <li><strong>Elo</strong> turns a score into a strength difference: 50% is 0, 64% about +100, 76% about +200, 91% about +400. The bracket is the
            95% interval: with 40 games it is about ±120 wide, so a champion at "+60 (−60 to +180)" may be no better at all. Only an interval entirely
            above zero is a real gain, and that is when a champion enters the hall of fame on its own.</li>
          <li><strong>Depth</strong> is how many half-moves each side looks ahead. Depth 3 games take a fraction of a second; each extra ply costs about three
            times as much. A share of the games can be played deeper, more of them in later generations, so weights that only pay off with lookahead
            get their chance.</li>
          <li><strong>Game length</strong> and <strong>decisive games</strong> per generation show what kind of chess (or antichess) the population plays:
            all draws or all 300-move games mean the weights are not telling the engine anything yet.</li>
          <li><strong>Weights</strong> are shown as how far each champion moved from the default, biggest movers first, with a sparkline over the generations.
            For a game evolved from zero the defaults are all 0, so the table simply shows what the champion learned a piece is worth.</li>
          <li><strong>Openings</strong> is the tree of the members' games: from any position, which moves they went on with, how those games
            ended and how often each generation chose them. Games start from fixed openings (the suite in chess, a few random moves in other
            games), so those moves are marked as not chosen; the bars on the right show a move catching on or dying out as the run goes.</li>
        </ul>
        <h3>Games other than chess</h3>
        <p>Chess runs keep the tuned chess evaluation (499 weights: material, pawn structure, king safety, square tables…) and can start from
          the app's weights. Any other game (antichess, King of the Hill, three-check, your own variants) gets an evaluation built from its pieces:
          material, mobility and a value per square for each piece type. In antichess every weight starts at 0, so a run there learns the game
          from nothing, which is the point of the experiment: does evolution discover that in antichess pieces are a liability?</p>
        <h3>Where things live</h3>
        <p>Every run is one SQLite file in the runs folder with its settings, every member, every game and every result; any SQLite browser opens
          it, and <code>lab.Cli</code> starts, resumes and exports runs from the command line with the same files. The hall of fame is a folder of
          JSON files next to it. A run's champion or any hall of fame entry can be played from the New game dialog.</p>
        <p><button type="button" className="btn primary" onClick={() => goLab('new')}>Start a run</button></p>
      </section>
    </div>
  );
}
