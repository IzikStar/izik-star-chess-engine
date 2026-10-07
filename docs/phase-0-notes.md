# Phase 0 — Safety net: build tooling + characterization tests

*Decision record: describes the code at the time of Phase 0; see [docs/architecture.md](architecture.md) for the current state.*

Status: **DONE and verified** (Maven 3.9.11 + OpenJDK 26, 2026-09-05).
Branch: `phase-0-maven-and-characterization-tests`. Everything here is one revertable unit.

Verified results:

| command | result |
|---|---|
| `./mvnw test` | **23 pass, 0 fail** — the "do not regress" suite |
| `./mvnw test -Pknown-bugs` | **4 fail (on purpose), build SUCCESS** — the documented bugs |
| `./mvnw package` | builds `target/izikstar-chess-3.1.0.jar` (8.7 MB, `Main-Class: main.Main`, bundles classes + `pieces.png` + sounds + deps) |
| IntelliJ | auto-imported `pom.xml`; `main.Main` compiles and launches from the Maven module |

This is the written record the [refactor guide](../REFACTOR_GUIDE.md) requires at the end of
every phase: what the research found, what was decided and why, what is deferred.

---

## 1. What changed

### 1.1 Build tool
- Added [`pom.xml`](../pom.xml). Maven, `maven.compiler.release = 26` (the JDK installed on
  this machine — `--release` keeps the bytecode level explicit and portable), UTF-8.
- **Maven Wrapper committed** (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`,
  `only-script` type, Maven 3.9.11). `./mvnw …` needs only a JDK — it downloads Maven itself.
- Dependencies now come from Maven Central instead of jars hand-checked into `libs/`
  (deleted) and `.idea/flatlaf-3.0.jar` (left in place, see §5).
- `maven-shade-plugin` builds the runnable fat jar, replacing `out/artifacts/chessGame_3.jar`.
- `exec-maven-plugin` wired to `main.Main` for `./mvnw exec:java`.
- `target/` added to `.gitignore`.
- Plugin versions bumped for JDK 26: compiler 3.14.0, surefire 3.5.2, shade 3.6.0.

### 1.2 Standard Maven source layout
All sources moved (via `git mv`, history preserved) from `src/<pkg>` to `src/main/java/<pkg>`:

| was | now |
|---|---|
| `src/ai`, `src/GUI`, `src/main`, `src/pieces`, `src/player`, `src/ChessServer` | `src/main/java/…` (same package names) |
| `src/res/pieces.png`, `src/res/sounds/` | `src/main/resources/` |
| `src/res/stockfish/stockfish-windows-x86-64.exe` | `engine/stockfish-windows-x86-64.exe` |
| `src/pgn/` (sample games + `1.docx`) | `samples/pgn/` |

Deleted ("Trim" option chosen): the vendored Stockfish **source tree + wiki** (~70 files under
`src/res/stockfish/`, not needed to run the bundled `.exe`), the unreferenced 24 MB
`src/res/learn/Houdini3_eng.pdf`, and `src/res/META-INF/MANIFEST.MF` (shade generates the
manifest now). All recoverable from git history.

### 1.3 Minimal production-code edits (resource loading only — no chess logic touched)
The move broke resources loaded by **relative filesystem path**. Fixed:

| file | change |
|---|---|
| `GUI/AudioPlayer.java` | `playAudio()` resolves sounds from the **classpath** (`sounds/x.wav`), falling back to a real file. The `"src/res/sounds/…"` string constants are unchanged; the `src/res/` prefix is stripped before the classpath lookup. |
| `ai/StockfishEngine.java` | new `DEFAULT_ENGINE_PATH` = `System.getProperty("stockfish.path", "engine/stockfish-windows-x86-64.exe")`; replaces two hard-coded `src\res\stockfish\…` literals. |
| `main/Input.java` | `pathToStockfish` now references `StockfishEngine.DEFAULT_ENGINE_PATH`. |
| `pieces/Piece.java`, `main/PromotionDialog.java` | `ClassLoader.getSystemResourceAsStream("pieces.png")` → `X.class.getResourceAsStream("/pieces.png")` (robust under the Surefire classloader and inside the fat jar). |

`GUI/SoundPlayer.java` left untouched — dead (only its own `main` calls it).

### 1.4 Characterization test suite
`src/test/java/characterization/` — JUnit 5, headless, no Swing shown.

- `StartingPositionTest`, `CheckmateStalemateTest`, `SpecialMovesTest`,
  `RepetitionAndMaterialTest` — the **"currently correct, do not regress"** set (23 tests). Run
  by default.
- `KnownBugsTest` — the **"currently broken, tracked for a later phase"** set (4 tests). Tagged
  `@Tag("known-bug")`, **excluded from the default run**, executed via `./mvnw test -Pknown-bugs`
  (build still succeeds; the failure report is the deliverable). Each test asserts the *correct*
  answer and is red today.

Surefire runs with **assertions disabled** (`enableAssertions=false`) to mirror how the app
actually runs — Surefire enables `-ea` by default, which trips a stray
`assert (p1 != null && p2 != null)` in `BoardState.sameTeam()` that the very next line already
handles gracefully (`if (p1 == null || p2 == null) return false;`). Callers pass `null`
legitimately (a non-capturing `Move` has `captured == null`).

Both rule paths are exercised: the OO path (`BoardState` + `main.CheckScanner`) and the
bitboard path (`BitBoard` — the representation the minimax search runs on).

Profile/property wiring: the base surefire config reads `${surefire.groups}` /
`${surefire.excludedGroups}` / `${surefire.testFailureIgnore}`; the `known-bugs` profile just
overrides those three properties (no fragile plugin-config merge).

---

## 2. Research findings

### 2.1 What can run headlessly
- `BoardState`, `CheckScanner`, `BitBoard`, all of `pieces/*`, `ChoosePlayFormat`,
  `SettingPanel`, `SavedStatesForDraws` load and run without a display. `Board` and
  `SettingPanel` extend `JPanel` but their **static initializers do no Swing work**
  (`Board.tileSize = 85` etc.); the tests never call their constructors. Confirmed by a
  green run under `-Djava.awt.headless=true`.
- **Coupling the tests must feed:** constructing any `Piece` reads `Board.tileSize` and
  `ChoosePlayFormat.isPlayingWhite`, decodes `pieces.png`, and calls `getScaledInstance`
  — so `pieces.png` must be on the test classpath (it is, from `src/main/resources`).
  `CheckScanner.isGameOver` / `isMoveCausesCheck` read **and write** `main.Board.selectedPiece`
  (a static). The test base resets `ChoosePlayFormat`, `SettingPanel.skillLevel`,
  `SavedStatesForDraws`, and `Board.selectedPiece` before each test.
- The scattered `public static void main` smoke tests (`Minimax.main`, `BoardState.main`,
  `BitBoard.main`, …) don't depend on Swing; `BoardState.main`'s FEN seeded a fixture. Not
  wired into the suite.

### 2.2 `libs/` → Maven coordinates

| old jar | Maven coordinate | note |
|---|---|---|
| `retrofit-2.9.0.jar` | `com.squareup.retrofit2:retrofit:2.9.0` | |
| `converter-gson-2.9.0.jar` | `com.squareup.retrofit2:converter-gson:2.9.0` | |
| `gson-2.8.9.jar` | `com.google.code.gson:gson:2.8.9` | pinned (converter-gson would otherwise pull 2.8.5) |
| `okhttp-4.9.3.jar` | `com.squareup.okhttp3:okhttp:4.9.3` | overrides Retrofit 2.9.0's transitive OkHttp 3.14.9 |
| `kotlin-stdlib-1.8.0.jar` | `org.jetbrains.kotlin:kotlin-stdlib:1.8.0` | pinned (OkHttp would pull 1.4.10) |
| `okio-3.2.0.jar` | **not pinned** | see below |
| `.idea/flatlaf-3.0.jar` | `com.formdev:flatlaf:3.0` | |

**Deliberate deviation — okio.** The checked-in `okio-3.2.0.jar` is a 25 KB stub incompatible
with OkHttp 4.9.3. The only code touching this stack is `ai/openingBook/*`, which is **never
invoked**. The Maven build resolves OkHttp's correct transitive `okio:2.8.0`. Revisit when the
opening book is wired up (guide Phase 5).

### 2.3 Call-site audit for the resource move
Every hard-coded resource path found and handled:
- sounds: 14 constants in `AudioPlayer.java` — classpath-first loading.
- Stockfish exe: `StockfishEngine.java` ×2, `Input.java` ×1 — `DEFAULT_ENGINE_PATH`.
- `pieces.png`: `Piece.java`, `PromotionDialog.java` — classpath, name unchanged.
- Dead code with absolute `D:\…` paths in `main()`: `openingBook/BinaryFileReader.java`,
  `openingBook/OpeningBookConverter.java` — left as-is (dead; guide Phase 1/5).

---

## 3. Behaviors locked as "currently correct — do not regress"

Covered by the default `./mvnw test` run (23 tests):

| area | position(s) | assertion |
|---|---|---|
| move gen (bitboard) | initial | exactly 20 legal moves (`getNextStates`) |
| status (OO + bitboard) | initial | in-progress (`1`), not check |
| FEN round-trip | initial | piece-placement field survives `convertPiecesToFEN` |
| checkmate (OO) | fool's mate, back-rank mate | 0 legal moves, in check, `getAccurateStatus == MAX_VALUE`, `getStatus == 0` |
| checkmate (bitboard) | fool's mate / back-rank | `getStatus == MAX_VALUE` (White mated) / `MIN_VALUE` (Black mated) |
| stalemate (OO + bitboard) | K+Q vs K | 0 legal moves, **not** check, status `0` |
| check ≠ game over (OO) | rook check with escapes | `getAccurateStatus == 2`, moves > 0 |
| castling (OO) | `r3k2r/…/R3K2R` | `isValidMove` accepts O-O **and** O-O-O |
| en passant (OO) | `…3pPp2…  f6` | `isValidMove` accepts `e5xf6 e.p.` |
| promotion (OO) | `8/P6k/…` | `isValidMove` accepts `a7-a8` |
| threefold history mechanics | `SavedStatesForDraws` | 3× key ⇒ repetition; `removeLastState` undoes it |
| `insufficientMaterial()` mechanics | K, K+B, K+B+B, K+P | current true/false results pinned |

Three **characterized quirks** (asserted at their current value so a change is visible, not
claimed correct):
- **`BoardState.getAllPossibleMovesForASide()` is broken dead code** (ARCHITECTURE §3 flagged
  it as having no live callers):
  - returns **12** moves from the initial position, not 20 — `Pawn.getValidMoves()` only ever
    generates the one-square push, never the two-square opening push;
  - throws `ConcurrentModificationException` on any position where the side to move has a
    capture available — `makeMoveToCheckIt()` calls `capture()` / `loadPiecesFromFen()`, which
    structurally mutate `pieceList` while the outer loop iterates it.
  Phase 2 replaces this with the unified move generator; it is pinned, not fixed.
- `insufficientMaterial()` reports K+B+B and K+N+N as *sufficient* (rule is "no Q/R/P and
  fewer than 3 pieces"), and only inspects one colour.

---

## 4. Behaviors captured as "currently broken — fix tracked for a later phase"

`./mvnw test -Pknown-bugs` — 4 tests, all red on purpose, build SUCCESS:

| # | bug | test asserts (the correct behavior) | actual today | phase |
|---|---|---|---|---|
| 1 | `BoardState.loadPiecesFromFen` reads only the **first digit** of the FEN half-move clock (`Character.getNumericValue(parts[4].charAt(0))`) | `"… - 50 100"` ⇒ `50` | `5` | 2 |
| 2 | OO status path (`getAccurateStatus`/`getStatus`) **never applies the 50-move rule** | half-move clock ≥ 100 ⇒ draw (`0`) | `1` | 2 |
| 3 | `BitBoard.getStatus` tests `numOfTurnsWithoutCaptureOrPawnMove >= 50` on a **per-ply** counter ⇒ 50-move draw declared at move 25 | still in progress at 35 moves ⇒ `1` | `0` | 2 |
| 4 | OO status path does **not** recognise a dead position | K vs K ⇒ draw (`0`) | `1` | 2 |

These are the "my engine doesn't recognize draws" symptom from commit `ad9ca32`, made concrete.

### Reported but NOT reproduced here (need a runnable engine + real positions)
Commit `ad9ca32` also cites *"Stockfish plays worse than it should"* and *"sometimes avoids
checkmate."*
- **Stockfish:** the UCI-handshake-per-move / 150 ms-budget problem — guide Phase 4. Not a
  rules bug; no characterization test.
- **"avoids checkmate/draw":** most plausibly the three-way disagreement between `CheckScanner`,
  `BitBoard.isCheckOn`, and the `Move.getStatusString` simulate-and-revert path (ARCHITECTURE
  §2.3). Reproducing it reliably needs `Minimax.getBestMove` run against the specific positions
  from the user's saved games — **Phase 2 research**: diff OO-path vs bitboard-path status over
  those positions and add a red test per divergence found.

---

## 5. Follow-ups / deferred

- **`.idea/flatlaf-3.0.jar` and `chessGame_3.iml`** left in place for the pre-import IDE state.
  IntelliJ has since auto-imported the pom and dropped `chessGame_3.iml` / `.idea/modules.xml`
  itself (those deletions are in this branch). `.idea/flatlaf-3.0.jar` is now redundant and can
  be removed whenever.
- **`out/`** (stale IDE build output) is git-ignored, not tracked; left on disk, harmless.
- `samples/pgn/` was reference data, unused by code (removed in the 2026-10 cleanup; see git history).
- Local Maven install used to verify: `C:\Users\Itschak_Shteren\apache-maven-3.9.11` (not in
  the repo; the committed wrapper is what the project uses).

---

## 6. How to build & test

Anywhere with a JDK 22+ (JDK 26 used here). The wrapper fetches Maven on first use.

```
./mvnw test                 # "do not regress" suite — must be GREEN (23 tests)
./mvnw test -Pknown-bugs    # documented known bugs — 4 RED, build still SUCCESS
./mvnw test -Psmoke         # end-to-end game + save/load smoke — must be GREEN (3 tests; added Phase 1)
./mvnw package              # build target/izikstar-chess-3.1.0.jar
java -jar target/izikstar-chess-3.1.0.jar        # run the game
./mvnw exec:java                                  # or run it via Maven
#   Stockfish path defaults to engine/… ; override with -Dstockfish.path=…
```

In IntelliJ: the `pom.xml` is auto-imported; run the `characterization` package from the test
tool window, or use the Maven tool window (`-Pknown-bugs` is a profile checkbox).

**Owed manual smoke test — discharged in Phase 1** (2026-09-05). For that phase (a pure
deletion) parity with `master` was proven three ways: the extracted branch jar is
byte-identical to `master`'s except the deleted classes; the packaged jar boots clean; and a
new headless `@Tag("smoke")` suite (`./mvnw test -Psmoke`) plays a full game to checkmate, a
full random bitboard-engine game, and a `SaveGame`/`LoadGame` round-trip. See
[phase-1-notes.md](phase-1-notes.md) §6. The interactive Swing paths (mouse-drag moves, the
computer-vs-computer self-loop, the end-game dialog) are still only manually testable — a full
human play-through remains worthwhile before a release.

---

## 7. Rollback

Everything is on `phase-0-maven-and-characterization-tests`; `master` is untouched.
Abandon: `git checkout master`. Undo after merge: revert the merge/squash commit.
