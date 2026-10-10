# IzikStar Chess

A chess game in Java with its own engine, where pieces are data: legal move generation, alpha-beta search and
a hand-tuned evaluation. Stockfish can optionally take over the top difficulty levels. You play
in the browser: the jar starts a small local server and opens the game.

![IzikStar Chess in the browser: the board with the last move, a selected piece's moves and a hint arrow; the side panel shows the players, the status line and the move list](docs/images/web-ui.png)

*An Italian Game in progress. Yellow marks the last move, dots show where the selected bishop
can go, and the green arrow is a hint the player asked for (castle).*

I started this as a personal project in 2024 and rebuilt it in a planned, test-first refactor,
documented phase by phase in this repository (see
[Architecture and the refactor](#architecture-and-the-refactor)). This public repository
starts from a cleaned copy of the original private one; pull request numbers in the docs up to
Phase 4c (#2 to #6) refer to that repository.

## Features

- **Play against the computer or a friend**, or watch the engine play itself (each side at its
  own level). Pick White, Black or a random colour in the *New game* dialog.
- **Difficulty Levels 0-13**, 150-350 Elo apart as measured against Stockfish, with the Elo shown
  next to the level ([table](docs/difficulty-ladder.md)). The app opens on Level 5, a real but
  beatable opponent.
  - Level 0 plays random legal moves; Levels 1 and 2 are depth 1 with a quarter and an eighth of
    their moves random.
  - Levels 3-8 use the built-in engine at depth 1, 2, 3, 4, 6 and 7.
  - Levels 9-13 hand the move to Stockfish if it is installed (held to a UCI_Elo of 2150-2900,
    then full strength), and fall back to the built-in engine at Level 8 if it is not (the game
    tells you).
- **Full chess rules.** Castling, en passant, promotion (pick the piece on the board), check,
  checkmate and stalemate. Draws are detected by the 50-move rule, threefold repetition and
  insufficient material.
- **Variants.** Pick the game in the *New game* dialog: chess, Antichess (lose every piece to
  win; captures are forced), King of the Hill (or bring your king to the centre) or Three-check
  (or give check three times; each player's card counts the checks). The built-in engine plays
  them all, up to Level 8 (Stockfish plays chess only, so its levels, the evaluation bar and the
  analysis are for chess games). Saved games and PGN keep the variant (a `Variant` tag, as
  Lichess writes it). Every variant's rules are checked against Fairy-Stockfish.
- **Click or drag** to move; the legal moves of the selected piece are marked.
- **Game analysis** with Stockfish: an evaluation bar beside the board, a graph, a mark on every
  move (best, inaccuracy, mistake, blunder) and per side an accuracy and a rough Elo estimate
  ([how it is computed](docs/game-analysis.md)).
- **Premoves.** While the engine thinks, queue your next moves (as many as you like, and a
  promotion asks for its piece); one is played each time it is your turn, if it is still legal,
  and an illegal one drops the rest. Click an empty square to cancel them.
- **Planning marks.** Right-click a square to mark it, right-drag to draw an arrow; a left click
  or the next move clears them.
- **Hints.** *Hint* draws an arrow for the engine's suggested move.
- **Take-backs**, a **flip board** button, and a **status line** that says whose move it is
  and when the engine is thinking.
- **Chess clocks.** Pick a time control in the *New game* dialog: untimed (the default), 1+0,
  3+2, 5+0, 10+0 or 15+10. The server keeps the time; the clocks start with the first move, and a
  flag fall loses the game (or draws it when the other side has only a king, or a king and one
  minor piece).
- **Resign and offer a draw.** Resigning asks first. The engine accepts a draw when the
  position has been seen before or its evaluation is +0.3 pawns or less for its side; once it
  declines, you move before offering again. Between two players the other side accepts or
  declines (a move declines it).
- **PGN.** Copy or download the game as PGN, or paste a PGN to load it (it becomes a game between
  two players, to review or play on from its last position).
- **My games.** Every game you play, and every game you watch the engine play itself, is saved
  on your computer (in `games/`, one JSON file per game) after each move, so a game you leave
  unfinished is kept too (a loaded PGN is not saved). The *My games* tab lists them with a filter
  by opponent (a level, two players, or engine vs engine, shown with both sides' levels) and
  result, and your score against each level. Open one to step through
  it and analyse it with Stockfish, copy or download its PGN, or carry an unfinished game on with
  its clocks where they were.
- **Variants tab: invent your own.** Copy a variant and change its goal, forced captures,
  castling and start position (pick a piece, click squares), or invent pieces: click the squares
  a piece reaches (jump or slide, move and/or capture, mirrored all ways, left/right or not, first
  move only), or type its [Betza](https://www.gnu.org/software/xboard/Betza.html) text (`QN` is the
  Amazon). A board beside it shows where the piece goes. Variants are saved in `variants/`.
  Give a piece a picture (white and/or black; PNG, JPEG, WebP, GIF or SVG) and it is drawn with it
  on every board. Hold the mouse over an invented piece, in the designer or in a game, to see the
  squares it can go to.
  *Save to bank* keeps a piece, with its pictures, in a piece bank of its own; *From the bank*
  puts it into any variant.
  A *Health check* lets the engine play the variant against itself and says whether one side
  wins too often, games end too soon or never end, and how many moves there are to choose from.
- **Move list with review.** Click any move, or use the arrow keys, to see that position.
- **Captured pieces and material** on each player's card; the result in the side panel when
  the game ends.
- **Sound effects** (synthesised in the browser), **light and dark themes**, and a layout
  that works on a narrow window.

## How the engine works

A short summary; [docs/architecture.md](docs/architecture.md) has the details.

- **Pieces as data, one board.** A piece is a list of leaps and slides
  ([`ai/piece/`](src/main/java/ai/piece/)); the six chess pieces are defined that way in
  [`StandardPieces`](src/main/java/ai/piece/StandardPieces.java), and a new piece is a new
  definition, not new code. [`GenericBoard`](src/main/java/ai/board/GenericBoard.java) plays any
  such piece set with bitboards and immutable positions; everything else talks to the
  [`Board`](src/main/java/ai/board/Board.java) interface.
- **One rules authority.** The headless [`rules`](src/main/java/rules/) package wraps the board:
  give it a FEN, and `rules.Rules` returns the legal moves (in UCI, e.g. `e2e4`), the status
  (check, mate, stalemate or draw) and SAN. Perft tests check the move generator against
  published counts and Stockfish.
- **Search.** [`ai/Minimax.java`](src/main/java/ai/Minimax.java): alpha-beta with iterative
  deepening (stops at 5 s or when the game moves on), a transposition table, killer and history
  move ordering, quiescence through captures, and repetition detection. Depth comes from the
  level (1 ply at Levels 1-3 to 7 plies at Level 8, deeper once the board thins out); any root move
  within 0.2 pawn of the best may be played, so games vary.
- **Evaluation.** [`ai/eval/ChessEvaluate.java`](src/main/java/ai/eval/ChessEvaluate.java): 499
  named, bounded parameters in centipawns, each with a middlegame and an endgame value blended by
  the material left: material, piece-square tables, pawn structure, king safety, castling,
  development, piece placement, mobility and threats. The game plays with `tuned-v1` (fitted by
  Texel tuning) by default; `classic` (pawn 100, knight 300, bishop 330, rook 500, queen 900, the
  original hand-tuned engine) can be picked in the *New game* dialog.
- **Stockfish bridge.** [`engine/StockfishEngine.java`](src/main/java/engine/StockfishEngine.java)
  keeps one Stockfish process for the session over UCI and checks every suggested move against
  the program's own rules. Levels 9-12 hold it to a UCI_Elo of 2150, 2400, 2650 and 2900 at
  500 ms a move; Level 13 plays full strength at 1 s, and hints full strength at 4 s. If
  Stockfish crashes it is restarted, and if it is missing or keeps failing the built-in engine
  takes over.

## Architecture and the refactor

```
src/main/java/
├── rules/          headless rules API: FEN in, legal moves / status / SAN out; one game's history
├── ai/             Minimax search, transposition table
│   ├── board/      the Board interface and the generic board that plays any piece set
│   ├── piece/      pieces as data: atoms, piece types, the six chess pieces, compiled tables, Betza
│   ├── variant/    a variant: pieces, start position, rules (chess, antichess, made ones)
│   └── eval/       the evaluations: parameter vectors, the chess evaluation, any piece set, the network
├── engine/         Engine interface: the built-in search, Stockfish, and which one plays a level
├── game/           GameSession: turn-taking, the engine thread, events for any front end;
│                   saved games, the player's variants
├── analysis/       game analysis with Stockfish
├── arena/          engine against engine: matches, tournaments, openings, Elo, outside players
├── evolution/      the Evolution API and the algorithms (FromZero, MaterialExperiment, the example)
├── lab/            runs: the runner, the SQLite record, the hall of fame, the opening tree, Texel tuning, training data,
│                   the CLI, the variant health check, the fun test
├── cloud/          sharing runs, games and variants between copies through Cloudflare D1
└── web/            local web server: the browser UI's files, the game over one WebSocket, and the
                    HTTP APIs (games, variants, health check, analysis, fun test, sync, the Lab)
web/                the browser UI: React + TypeScript (Vite), board by react-chessboard;
                    web/src/lab/ is the Lab's screens
```

Lower layers never import higher ones, and nothing below `web` imports Swing, AWT or the web
server; a test (`architecture.LayeringTest`) fails the build otherwise. The browser never
decides what is legal: the server sends it the legal moves with every position.

The code started in 2024 as a single-developer IntelliJ project that grew features faster than
structure, and was rebuilt in a planned, test-first refactor:

- **[docs/architecture.md](docs/architecture.md)** maps the code as it is now.
- **[REFACTOR_GUIDE.md](REFACTOR_GUIDE.md)** is the phased plan. Each phase required a research
  step, a test safety net and explicit exit criteria.
- **[docs/history/architecture-before-refactor.md](docs/history/architecture-before-refactor.md)**
  is the map of the code as found, with its flaws ranked, and how each phase changed it.
- **[docs/history/original-code.md](docs/history/original-code.md)** says where the original
  2024 code is kept unchanged (branch `legacy-original` of the private legacy repository).

| Phase | Goal | Status |
|---|---|---|
| 0 | Maven build + characterization test suite | Done ([notes](docs/phase-0-notes.md)) |
| 1 | Remove dead and duplicate code | Done ([notes](docs/phase-1-notes.md)) |
| 2 | One board model and one rules engine; fix draw detection | Done ([research](docs/phase-2-research.md)) |
| 3 | Decouple the UI from the rules; retire global state | Done ([research](docs/phase-3-research.md)) |
| 4 | One concurrency model; a proper Stockfish session | Done ([research](docs/phase-4-research.md)) |
| 4b | Fix the move generator's rule bugs; make the search fast enough for Levels 6-7 | Done ([research](docs/phase-4b-research.md)) |
| 4c | Replace the Swing screens with a browser UI | Done ([research](docs/ui-research.md)) |
| 5 | Groundwork for an engine that learns by self-play evolution | Done: parameters, quiescence, arena, run record, Lab ([research](docs/phase-5-research.md)) |
| 5b | A working transposition table and move ordering | Done ([research](docs/phase-5b-research.md)) |
| 6 | Pieces as data: any piece set and variant on one board, a piece designer, a health check | Done R1-R5 ([research](docs/phase-6-research.md)) |
| 6, stage 2 | The Lab runs experiments on any game from the browser; evolution from zero on antichess | Done ([research](docs/phase-6-research.md) §6) |
| 7 | A small neural network as the evaluation | Step 1 (features, training data, `net:` player) done; the trainer, written by the owner, is next ([research](docs/phase-7-research.md)) |

Phase 2 is a good example of the approach:

- **Before:** three independent check/mate/draw implementations that could disagree with each
  other.
- **Process:** characterization tests first, then the three implementations collapsed into one.
- **Result:** four draw-detection bugs and two bitboard move-generation bugs fixed (queen
  attack rays, and en passant).

## Build and run

You need JDK 21 or newer (`maven.compiler.release` in `pom.xml`). The Maven wrapper is included, so you do not need to install Maven.

```bash
./mvnw package                            # build target/izikstar-chess-3.1.0.jar (runs the tests)
java -jar target/izikstar-chess-3.1.0.jar # play: opens http://localhost:7070/ in your browser
```

On Windows use `.\mvnw.cmd` (PowerShell needs the `.\`); double-clicking the jar works too. The first `package` downloads its
own Node.js into `target/` to build the browser UI (`-Dskip.web=true` skips that step). The
server listens on this computer only; stop it with Ctrl+C or by closing its console. Options:
`--port N`, `--no-browser`, `--games DIR` (where your games are saved, default `games`),
`--variants DIR` (the variants you make, default `variants`), `--runs DIR` (the Lab's runs,
default `runs`), `--lan` (see below), `--host ADDR` (listen on that one address only).

**Playing from a phone.** The engine, Stockfish and the lab keep running on the computer; the phone
only shows the page. Start the jar with `--lan` and it prints the address to open, for example:

```bash
java -jar target/izikstar-chess-3.1.0.jar --lan
# --lan: open it from your phone at http://192.168.1.20:7070/
```

- **At home:** the phone must be on the same Wi-Fi. Open the printed `192.168.x.x` address in its
  browser. The first time, Windows asks whether Java may use the network: allow it on private
  networks. "Add to Home screen" in the phone's browser gives it an icon.
- **Away from home:** install [Tailscale](https://tailscale.com) (free for personal use) on the
  computer and on the phone and sign in to the same account on both. The computer then also gets a
  `100.x.x.x` address, which `--lan` prints too; open that one on the phone, from any network. The
  computer has to be on and running the jar. Nothing is opened to the internet: only your own
  devices on the tailnet can reach it.
- There is no password, so anyone on the same network can open the game; use `--lan` on networks
  you trust. Phone and computer share one game, the same as two browser tabs.

**On a cloud server, without the computer.** The same jar runs on a small cloud machine (Oracle
Cloud's free tier is enough), reached through Tailscale only, with Lab runs that keep going while
the phone is off. One script installs it: see [docs/cloud-server.md](docs/cloud-server.md).

Run the jar from the repository root if you want it to find Stockfish at the default path.

**Working on the browser UI:** run the jar (`--no-browser`), then `npm run dev` in `web/` and
open <http://localhost:5173/>; changes show up as you save.

## Arena: engine against engine

The arena plays two sets of evaluation weights against each other over a suite of about fifty
openings, each opening once with each colour, several games at a time, and reports the score with
an Elo difference and its 95% interval:

```bash
./mvnw package -DskipTests
java -cp target/izikstar-chess-3.1.0.jar arena.Cli match default my-weights.json --depth 3
```

`default` is the built-in weights; a JSON file names the parameters it changes (the rest keep
their defaults). Options: `--depth`, `--openings`, `--threads`, `--max-plies`, `--variety`,
`--seed` (the same seed replays the same games), `--old-search A|B` (that side searches without
quiescence).

## Evolution runs and the Lab

An evolution run lets an algorithm breed sets of evaluation weights: each generation plays a
tournament, and the algorithm builds the next generation from the results. The algorithm is a
class that implements [`evolution.Evolution`](src/main/java/evolution/Evolution.java). Three
ship: [`FromZero`](src/main/java/evolution/FromZero.java), a genetic algorithm with every choice
a setting (start from nothing, random or the defaults; survivors, immigrants, tournament
selection, crossover, a mutation step that shrinks over the run) that plays any game;
[`MaterialExperiment`](src/main/java/evolution/MaterialExperiment.java), the design of the first
chess experiment; and the naive
[`RandomMutationExample`](src/main/java/evolution/RandomMutationExample.java). Every member,
game and result goes into one SQLite file per run.

A run plays chess or any variant (built-in or made in the *Variants* tab). Chess runs evolve the
tuned chess evaluation; any other game evolves an evaluation built from its pieces (material,
mobility, a value per square), which in antichess starts from all zeros, so the engine learns the
game from nothing. Variants start from random openings. Stockfish plays chess only; in the
built-in variants, and in made variants that can be written as a Fairy-Stockfish config
(`arena.FairyConfig`: an 8x8 board, the base game's king and pawn, three checks if any), the
yardstick from above is **Fairy-Stockfish** (`fsf`, full strength at a fixed number of nodes a move), and from below the **random mover** (`random`, any legal move),
which every evaluation should beat.

The **Lab** tab of the web app runs the experiments:

- **Runs**: every run as a card with its game, algorithm, progress and state; open, stop (after
  the generation or at once), resume or delete it; play its champion.
- **New run**: every setting of a run with a line saying what it does, the algorithm's own
  settings, the yardsticks to measure against, and what it adds up to (games and a rough time).
- **A run**: progress bar and live game count; the champion's Elo against each yardstick with its
  interval; charts of the champion's score, decisive games and game length per generation; every
  generation in a table; how the champions' weights moved; the opening tree (which moves the
  members chose from any position, how those games ended, and how often each generation chose
  them, with the run's fixed opening moves marked); the settings, with the equivalent
  command line. **A generation**: the champion against the yardsticks, standings of every member
  with what it thinks each piece is worth and its weights as a download, every game (replay on
  the board), keep a member in the hall of fame.
- **Hall of fame** and **How it works**, a plain-words guide to generations, champions,
  yardsticks and Elo intervals.

The same runs from the command line (the Lab's *Settings* screen prints the command for any run):

```bash
java -cp target/izikstar-chess-3.1.0.jar lab.Cli run runs/anti.db --algorithm evolution.FromZero --variant antichess \
     --generations 30 --depth 3 --options population=16,start=zero,evolve=material+mobility --yardsticks zero
java -cp target/izikstar-chess-3.1.0.jar lab.Cli resume runs/anti.db   # after Ctrl+C or a stop from the Lab
java -cp target/izikstar-chess-3.1.0.jar lab.Cli export runs/anti.db positions.csv   # fen,result,score of every quiet position, by its own rules
java -cp target/izikstar-chess-3.1.0.jar lab.Cli selfplay anti.csv --variant antichess --player fsf:5000 --games 1000
java -cp target/izikstar-chess-3.1.0.jar lab.Cli features anti.csv anti.bin --variant antichess   # the network's training file
java -cp target/izikstar-chess-3.1.0.jar lab.Cli champion runs/anti.db 29 champion.json
java -cp target/izikstar-chess-3.1.0.jar arena.Cli match champion.json fsf:20000 --variant antichess --depth 4
```

[docs/evolution-guide.md](docs/evolution-guide.md) explains the API, the numbers and the traps;
[docs/experiments/](docs/experiments/) holds the write-ups of the experiments run so far:
[Material 1](docs/experiments/material-1.md) (chess piece values),
[Antichess from zero 1](docs/experiments/antichess-zero-1.md) and its
[yardstick follow-up](docs/experiments/antichess-zero-1-yardsticks.md).

### A network as the evaluation

The engine can also play with a small neural network instead of the hand-written evaluation:
768 inputs (one per side, piece and square, seen from the player to move), one hidden layer, one
score ([`ai.eval.NetEvaluate`](src/main/java/ai/eval/NetEvaluate.java)). The data comes from the
repository (`lab.Cli selfplay` writes `fen,result,score`, `lab.Cli features` encodes it), the
training happens in Python ([`tools/net/`](tools/net/) reads the files and checks a trained
network against Java), and a trained network plays as `net:nets/first.json` wherever a player is
named. [docs/net-training-guide.md](docs/net-training-guide.md) has the formats and the recipe.

![A generation in the Lab](docs/images/lab.png)

## Tests

```bash
./mvnw test               # main suite: must be green
./mvnw test -Psmoke       # end-to-end smoke tests: must be green
./mvnw test -Pstress      # long runs, several minutes: must be green
```

- **Characterization tests** ([`src/test/java/characterization/`](src/test/java/characterization/))
  were written *before* the refactor started. They pin down what the program actually does in
  well-known positions, so any behaviour change during the refactor shows up as a test failure.
  The positions include the start position, Fool's mate, back-rank mate, stalemate, castling, en
  passant, promotion, repetition, insufficient material, the 50-move rule and SAN `+`/`#`
  suffixes. The tests check both entry points to the rules (the game API and the search's board)
  and require them to agree.
- **Rules tests** ([`src/test/java/rules/RulesTest.java`](src/test/java/rules/RulesTest.java))
  cover the headless `rules` API directly.
- **Perft tests** ([`RulesPerftTest`](src/test/java/rules/RulesPerftTest.java),
  [`SearchPerftTest`](src/test/java/ai/board/SearchPerftTest.java)) count every legal move
  sequence to a fixed depth from 23 positions, through the `rules` API and through the search's
  own move list, and compare with published counts and Stockfish.
  [`MoveGenerationBugsTest`](src/test/java/rules/MoveGenerationBugsTest.java) has one test per
  move-generator bug that perft found in Phase 4b.
- **Same-move test** ([`SameMoveTest`](src/test/java/engine/SameMoveTest.java)) holds the
  engine's move at depths 1-4 in 56 positions, so a change meant only to make the search faster
  cannot quietly change how it plays.
- **Stress tests** (`-Pstress`) play unattended engine-vs-engine games, storm the game with moves,
  take-backs and hint requests, and run perft as deep as the reference counts go. They also
  compare 300 random games with Stockfish's legal moves at every ply, and time Levels 6 and 7
  ([`SearchSpeedTest`](src/test/java/engine/SearchSpeedTest.java)).
- **Smoke tests** ([`AppSmokeTest`](src/test/java/characterization/AppSmokeTest.java)) play a
  scripted game to checkmate, and play a full random game on the search's board.
- **Web server tests** ([`WebServerTest`](src/test/java/web/WebServerTest.java)) drive whole
  games through the WebSocket the way the browser does, with no browser.
- **Browser tests** ([`web/e2e/`](web/e2e/)) play real games in Chromium against the packaged
  jar: checkmate, drag and click moves, promotion, the engine's reply, take-back, review,
  clocks and a flag fall, resigning, draw offers, PGN copy, download and load, and saved games
  (listed, reviewed and carried on).
  After `./mvnw package`, run them in `web/` with `npx playwright install chromium` (once) and
  `npm run e2e`.

## Optional: Stockfish

Stockfish is **not** included in this repository. It is a large third-party GPLv3 binary.
Without it the game is fully playable: levels 9-13 play the built-in engine at Level 8 and hints
use the built-in engine. The New game dialog and the player card say so when Stockfish is missing,
and the server prints which Stockfish it found when it starts.

The easiest way: press **Download Stockfish** in the game (it appears in the New game dialog at
Levels 9-13 and when you analyse a game). The game fetches Stockfish 17.1 from Stockfish's official
GitHub releases into `engine/stockfish/` and uses it at once, no restart. Or do it by hand:

1. Download Stockfish for your OS from <https://stockfishchess.org/download/>.
2. Unzip it into the `engine/` folder of the repository. Any file whose name starts with
   `stockfish` is found, also one folder down, so the download's own
   `engine/stockfish/stockfish-windows-x86-64-avx2.exe` works as it is. The `engine/` folder is
   looked for next to the directory you run from and next to the jar, so double-clicking
   `target/izikstar-chess-3.1.0.jar` finds it too.
3. Or install it so that `stockfish` is on your `PATH` (Linux: `sudo apt install stockfish`;
   macOS: `brew install stockfish`).
4. Or name the file yourself: `java -Dstockfish.path=/path/to/stockfish -jar target/izikstar-chess-3.1.0.jar`,
   or the `STOCKFISH_PATH` environment variable.

### Fairy-Stockfish (variants)

Fairy-Stockfish, the Stockfish fork that plays antichess, King of the Hill, three-check and
dozens of other variants (and, through a generated config file, most of the variants you make), is the yardstick for the Lab's variant runs and for
`arena.Cli match ... --variant antichess` (players `fsf` and `fsf:NODES`). The game never
downloads it: take the build for your OS from
<https://github.com/fairy-stockfish/Fairy-Stockfish/releases> (e.g.
`fairy-stockfish-largeboard_x86-64.exe` on Windows) and drop it into `engine/`. Any file whose
name starts with `fairy-stockfish` is found there, or name it with `-Dfairy.path=...` or
`FAIRY_STOCKFISH_PATH`, or put `fairy-stockfish` on your `PATH`. It is GPLv3 and not distributed
here. Without it, those yardsticks are refused with a message and everything else works.

## Roadmap

- **Done:** every evaluation weight a parameter, a self-play arena, a record of every run, and a
  Lab that runs experiments on any game and plays their champions; pieces as data with a
  designer for new pieces and variants.
- **Now:** a small neural network that reads the board. The data pipeline, the inputs, the
  network file and the Java side are in ([docs/net-training-guide.md](docs/net-training-guide.md));
  the owner is writing the trainer; then the network in the Lab and the web game, and the data loop
  over the engine's own games. Decisions in [docs/phase-7-research.md](docs/phase-7-research.md).
- **Next:** wider variants (fairy pieces, other boards).
- **Longer term:** split the headless `rules`/engine core into a backend service behind the
  web front end that Phase 4c started.

## License

MIT, see [LICENSE](LICENSE).

The chess pieces are original artwork drawn for this project and covered by the same MIT
license: the board's glossy black-and-ivory set is drawn in code by
[`web/src/pieces.tsx`](web/src/pieces.tsx), and an earlier original set is kept in
[`docs/art/pieces.svg`](docs/art/pieces.svg). The sound effects are synthesised in the browser by
[`web/src/sounds.ts`](web/src/sounds.ts), so the repository ships no third-party sound files.

Stockfish and Fairy-Stockfish are separate projects licensed under the GPLv3 and are not distributed here.
