# IzikStar Chess

A chess game in Java with its own bitboard engine: legal move generation, alpha-beta search and
a hand-tuned evaluation. Stockfish can optionally take over the top difficulty levels. You play
in the browser: the jar starts a small local server and opens the game.

![IzikStar Chess in the browser: the board with the last move, a selected piece's moves and a hint arrow; the side panel shows the players, the status line and the move list](docs/images/web-ui.png)

*Mid-game against the engine. Yellow marks the last move, dots show where the selected piece
can go, and the green arrow is a hint the player asked for.*

I started this as a personal project in 2024. It is now going through a planned, test-first
refactor, which is documented phase by phase in this repository (see
[Architecture and refactor](#architecture-and-the-ongoing-refactor)).

## Features

- **Play against the computer or a friend**, or watch the engine play itself. Pick White,
  Black or a random colour in the *New game* dialog.
- **10 difficulty levels.**
  - Level 1 plays random legal moves.
  - Levels 2-7 use the built-in engine and search deeper as the level rises.
  - Levels 8-10 hand the move to Stockfish if it is installed, and fall back to the built-in
    engine if it is not.
- **Full chess rules.** Castling, en passant, promotion (pick the piece on the board), check,
  checkmate and stalemate. Draws are detected by the 50-move rule, threefold repetition and
  insufficient material.
- **Click or drag** to move; the legal moves of the selected piece are marked.
- **Hints.** *Hint* draws an arrow for the engine's suggested move.
- **Take-backs**, a **flip board** button, and a **status line** that says whose move it is
  and when the engine is thinking.
- **Move list with review.** Click any move, or use the arrow keys, to see that position.
- **Captured pieces and material** on each player's card; the result in the side panel when
  the game ends.
- **Sound effects, light and dark themes**, and a layout that works on a narrow window.

## How the engine works

Everything below describes code that runs today. Parts that exist but are not wired in yet are
marked as such.

### Board representation: bitboards

[`ai/BitBoard/BitBoard.java`](src/main/java/ai/BitBoard/BitBoard.java) stores a position as
twelve 64-bit `long` masks, one per piece type and colour. It also stores combined occupancy
masks, side to move, castling rights, the en-passant square and the move clocks. Making a move
returns a **new** `BitBoard`, so positions are immutable. That keeps the search free of
make/unmake bugs, at some cost in allocation.

### Move generation

Each piece type has a generator in [`ai/BitBoard/BitPiece/`](src/main/java/ai/BitBoard/BitPiece/):

- Knights, kings and pawns use shifts and file masks.
- Bishops, rooks and queens walk their rays one square at a time until they hit a piece.
- Castling, en passant and promotion are handled as special cases.

The generators first produce pseudo-legal moves. Any move that leaves the mover's own king on
an attacked square is then dropped, so only legal moves remain. Whether a square is attacked
comes from [`Attacks`](src/main/java/ai/BitBoard/Attacks.java): lookup tables for knights, kings
and pawns, and ray walks for bishops, rooks and queens.

Perft tests count the moves to a fixed depth in 23 positions and compare the totals with the
published values and with Stockfish (Phase 4b).

Since Phase 2 of the refactor, this generator is the **only** rules authority in the program.
The headless [`rules`](src/main/java/rules/) package wraps it in a small API with no Swing or
AWT dependency: give it a FEN string, and `rules.Rules` returns the legal moves (in UCI notation,
e.g. `e2e4`), the position status (check, mate, stalemate or draw) and the position after a move.

### Search

[`ai/Minimax.java`](src/main/java/ai/Minimax.java) runs a **minimax search with alpha-beta
pruning** over bitboard positions:

- **Move ordering.** Child positions are sorted by a cheap move score before they are searched,
  which makes cut-offs happen sooner. Checks come first, then captures by the value of the
  captured piece. Moves that give up castling rights score lower.
- **Depth and time.** Depth comes from the difficulty level, from 1 ply at level 2 to 6 plies at
  level 7, and 1-2 plies deeper once the board thins out to 12 or fewer pieces. The search
  deepens one ply at a time and stops after 5 seconds, playing the move of the deepest depth it
  finished; it also stops at once when the game moves on (take-back, new game). Since Phase 4b,
  Levels 6 and 7 finish their full depth within that time in typical positions (Level 7 takes
  up to about 4 s on a 4-core test machine).
- **Repetition.** Positions get Zobrist hashes
  ([`ZobristHashing`](src/main/java/ai/BitBoard/ZobristHashing.java)). A per-branch stack
  ([`BoardStateTracker`](src/main/java/ai/BoardStateTracker.java)) uses them to spot threefold
  repetition inside the search tree.
- **Variety.** When several root moves share the best score, the engine picks one at random so
  it does not repeat the same game every time.

**Not wired in yet:**

- A [`TranspositionTable`](src/main/java/ai/TranspositionTable.java) keyed by the same Zobrist
  hash exists, but its calls in `minimax()` are commented out. As written it would return wrong
  scores, and fixing it changes the moves the engine picks, so it is left for a later phase
  ([Phase 4b research](docs/phase-4b-research.md) §4).
- There is no quiescence search yet.

### Evaluation

[`ai/BitBoard/BitBoardEvaluate.java`](src/main/java/ai/BitBoard/BitBoardEvaluate.java) scores a
position with hand-written terms, mostly computed with bit masks and popcounts:

- material (P=10, N=30, B=33, R=50, Q=90)
- pawn advancement, with a bonus for central pawns
- king placement in the opening (castled-side squares are preferred)
- castling, and castling rights lost
- mobility and threats: how many squares each side attacks, and which enemy and own pieces are
  under attack
- development: knights and bishops off the back rank, an early queen sortie penalised, knights
  on their natural squares

Checkmate and stalemate are scored as terminal values.

### Opening book

There is an [`ai/openingBook`](src/main/java/ai/openingBook/) package: a file-backed book
format and a Retrofit/OkHttp client for the Lichess API. **It is not used during play yet.** Nothing
calls it, and wiring it in is planned for Phase 5.

### Stockfish bridge

[`engine/StockfishEngine.java`](src/main/java/engine/StockfishEngine.java) starts a Stockfish
process and talks to it over the UCI protocol through stdin and stdout. The suggested move is
checked against the program's own rules before it is played. One Stockfish process serves the
whole session: the UCI handshake runs once, and each move sends the game's moves so far.
Levels 8-10 set Stockfish's skill level to 13, 15 and 17 and let it think 300, 600 and 1000 ms;
hints use full strength and 1000 ms. If Stockfish crashes it is restarted, and if it is missing or
keeps failing the built-in engine takes over.

## Architecture and the ongoing refactor

```
src/main/java/
├── rules/          headless rules API: FEN in, legal moves / status / SAN out; one game's history
├── ai/             Minimax search and evaluation over bitboards
│   ├── BitBoard/   bitboard position, per-piece move generators, attack tables
│   └── openingBook/  (not wired in yet)
├── engine/         Engine interface: the built-in search, Stockfish, and which one plays a level
├── game/           GameSession: turn-taking, the engine thread, events for any front end
├── web/            local web server: the browser UI's files, and the game over one WebSocket
├── main/           the old Swing UI (kept until the web UI is signed off, then deleted)
└── GUI/            Swing audio, sprites, animation, custom buttons
web/                the browser UI: React + TypeScript (Vite), board by react-chessboard
```

Lower layers never import higher ones, and nothing below `web`/`main` imports Swing or the web
server; a test (`architecture.LayeringTest`) fails the build otherwise. The browser never
decides what is legal: the server sends it the legal moves with every position.

The code started as a single-developer IntelliJ project that grew features faster than
structure. Two documents describe it honestly instead of hiding the problems:

- **[ARCHITECTURE.md](ARCHITECTURE.md)** maps the system as it actually is, including its
  flaws, ranked: UI and game logic mixed together, global mutable state reaching into the
  search, and inconsistent concurrency.
- **[REFACTOR_GUIDE.md](REFACTOR_GUIDE.md)** is the phased plan to fix it. Each phase requires
  a research step, a test safety net and explicit exit criteria.

| Phase | Goal | Status |
|---|---|---|
| 0 | Maven build + characterization test suite | Done ([notes](docs/phase-0-notes.md)) |
| 1 | Remove dead and duplicate code | Done ([notes](docs/phase-1-notes.md)) |
| 2 | One board model and one rules engine; fix draw detection | Done ([research](docs/phase-2-research.md)) |
| 3 | Decouple the UI from the rules; retire global state | Done ([research](docs/phase-3-research.md)) |
| 4 | One concurrency model; a proper Stockfish session | Done ([research](docs/phase-4-research.md)) |
| 4b | Fix the move generator's rule bugs; make the search fast enough for Levels 6-7 | Done ([research](docs/phase-4b-research.md)) |
| 4c | Replace the Swing screens with a browser UI | In progress ([research](docs/ui-research.md)) |
| 5 | Opening book in play; structured game database | Planned |

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

On Windows use `mvnw.cmd`; double-clicking the jar works too. The first `package` downloads its
own Node.js into `target/` to build the browser UI (`-Dskip.web=true` skips that step). The
server listens on this computer only; stop it with Ctrl+C or by closing its console. Options:
`--port N`, `--no-browser`.

Run the jar from the repository root if you want it to find Stockfish at the default path.

The old Swing UI still runs, until it is retired at the end of Phase 4c:
`java -cp target/izikstar-chess-3.1.0.jar main.Main` (some of its messages are in Hebrew).

**Working on the browser UI:** run the jar (`--no-browser`), then `npm run dev` in `web/` and
open <http://localhost:5173/>; changes show up as you save.

## Tests

```bash
./mvnw test               # main suite: must be green
./mvnw test -Psmoke       # end-to-end smoke tests: must be green
./mvnw test -Pstress      # long runs, several minutes: must be green
./mvnw test -Pknown-bugs  # tests that pin known bugs (currently none; see below)
```

- **Characterization tests** ([`src/test/java/characterization/`](src/test/java/characterization/))
  were written *before* the refactor started. They pin down what the program actually does in
  well-known positions, so any behaviour change during the refactor shows up as a test failure.
  The positions include the start position, Fool's mate, back-rank mate, stalemate, castling, en
  passant, promotion, repetition, insufficient material, the 50-move rule and SAN `+`/`#`
  suffixes. The tests check both entry points to the rules (the object model and the bitboard)
  and require them to agree.
- **Rules tests** ([`src/test/java/rules/RulesTest.java`](src/test/java/rules/RulesTest.java))
  cover the headless `rules` API directly.
- **Perft tests** ([`RulesPerftTest`](src/test/java/rules/RulesPerftTest.java),
  [`SearchPerftTest`](src/test/java/ai/BitBoard/SearchPerftTest.java)) count every legal move
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
  scripted game to checkmate, play a full random game inside the bitboard engine, and
  round-trip a saved game.
- **Known-bug tests** are tagged `known-bug`. They assert the *correct* behaviour and are
  expected to fail until a phase fixes the bug. The four from Phase 0 were all fixed in Phase 2
  and became regular tests, so the profile is currently empty.

- **Web server tests** ([`WebServerTest`](src/test/java/web/WebServerTest.java)) drive whole
  games through the WebSocket the way the browser does, with no browser.
- **Browser tests** ([`web/e2e/`](web/e2e/)) play real games in Chromium against the packaged
  jar: checkmate, drag and click moves, promotion, the engine's reply, take-back, review.
  After `./mvnw package`, run them in `web/` with `npx playwright install chromium` (once) and
  `npm run e2e`.

Tests run with `-Djava.awt.headless=true`, so no display is needed.

## Optional: Stockfish

Stockfish is **not** included in this repository. It is a large third-party GPLv3 binary.
Without it the game is fully playable: levels 8-10 and hints fall back to the built-in engine.

1. Download Stockfish for your OS from <https://stockfishchess.org/download/>.
2. Tell the game where it is, using any one of these:
   - put the Windows build at `engine/stockfish-windows-x86-64.exe` (the default path, relative
     to the directory you run from);
   - pass a system property: `java -Dstockfish.path=/path/to/stockfish -jar target/izikstar-chess-3.1.0.jar`;
   - set an environment variable: `export STOCKFISH_PATH=/path/to/stockfish`.

On Linux, `sudo apt install stockfish` installs it at `/usr/games/stockfish`.

## Roadmap

- **Phase 5:** use the opening book during play, and store games in a structured, queryable
  form.
- **Longer term:** split the headless `rules`/engine core into a backend service behind the
  web front end that Phase 4c started.

## License

There is no license file yet, so all rights are reserved by the author for now. If you would
like to use the code, please open an issue.

Stockfish is a separate project licensed under the GPLv3 and is not distributed here.
