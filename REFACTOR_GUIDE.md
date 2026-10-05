# Refactor Guide — IzikStar Chess 3.1

This is the macro-level plan for turning the current codebase (mapped in
[ARCHITECTURE.md](ARCHITECTURE.md)) into something with a real architecture. It is intentionally
written at the **phase** level, not the task/PR level — each phase covers weeks of the project's
attention, not a single sitting, and is expected to fill its own large context window once it's
actually being executed. Do not try to plan phase 3's implementation details while phase 0 is
still running; re-derive each phase's concrete plan at the time you start it, using the research
step below.

This document should be treated as living: update it (and re-sync ARCHITECTURE.md) at the end of
every phase, because finishing a phase changes the facts the next phase's research will start
from.

## How to use this guide

- **Work one phase at a time, in order.** Each phase assumes the previous ones are done. Skipping
  ahead (e.g. touching the board representation before there's a test suite to catch
  regressions) is how a legacy-but-working app becomes a broken one.
- **The app must stay runnable and playable at the end of every phase.** This is a strangler-fig
  refactor, not a rewrite: nothing here proposes throwing the project away and starting over. If
  a phase's plan would leave the game unplayable for an extended stretch, the phase is scoped
  too large — split it.
- **Every phase begins with a dedicated research pass, before any code is written.** This guide
  deliberately does not specify class names, method signatures, file layouts, or library choices
  for work that hasn't started yet — those decisions need fresh, deep investigation of the
  *current* state of the code at the time the phase begins (which will have changed since this
  guide was written, because earlier phases will have altered it). Treat the "Research required"
  section of each phase as a mandatory gate, not a suggestion. A phase that starts implementing
  before its research is done is the same mistake that produced the current codebase (see
  ARCHITECTURE.md §3 and §5.9 — most of today's mess is exactly this: code written without first
  fully accounting for what already existed).
- **Each phase ends with characterization tests passing and a short written record** of what was
  found during research, what was decided and why, and what was deliberately deferred. That
  record is what makes the *next* phase's research fast instead of starting from zero.

### The research step, generically

Before writing any implementation code for a phase, produce (as a short written artifact, not
just something held in your head):

1. **A full call-site audit** of every class/method the phase will touch or replace — not just
   the ones already named in ARCHITECTURE.md, which was a survey pass, not an exhaustive one.
   Assume ARCHITECTURE.md missed things and re-verify against the actual tree at that moment.
2. **The exact set of behaviors that must be preserved**, expressed as test cases where possible
   (see Phase 0) — including the currently-*buggy* behaviors, so you can tell "changed on
   purpose" from "regressed."
3. **The design forks that are genuinely open**, listed as explicit questions with the
   trade-offs of each option, for anyone directing the work to decide before implementation
   starts. Do not silently pick a library, data format, or pattern for a decision that has more
   than one reasonable answer — that habit is exactly how this project ended up with two
   competing minimax engines and two competing piece hierarchies (ARCHITECTURE.md §3).
4. **A rollback plan** — confirm the phase can be done on a branch and is revertable as a unit if
   it turns out worse than the status quo.
5. **A definition of done** restated in concrete, checkable terms (tests passing, specific dead
   code removed, specific coupling removed) — not "cleaner code."

---

## Phase 0 — Safety net: build tooling + characterization tests

> **Status: DONE & verified 2026-09-05** on branch `phase-0-maven-and-characterization-tests`.
> `./mvnw test` → 23 green; `./mvnw test -Pknown-bugs` → 4 red (documented); `./mvnw package`
> → runnable jar. Full write-up: [docs/phase-0-notes.md](docs/phase-0-notes.md). Decisions
> locked: Maven + committed wrapper (not Gradle); standard `src/main/java` layout;
> `--release 26` (installed JDK); vendored Stockfish source tree + Houdini PDF deleted;
> known-bug tests quarantined behind the `known-bugs` profile.
> **Owed manual smoke test — discharged in Phase 1** via a byte-level jar-vs-`master` diff, a
> clean boot of the packaged jar, and a new `@Tag("smoke")` end-to-end suite
> (`./mvnw test -Psmoke`: scripted game to mate + random bitboard game + save/load round-trip).
> See phase-1-notes.md §6.

**Goal.** Make it possible to verify chess-rules behavior without a human clicking through the
Swing UI, and pin down what the engine currently does — bugs included — before anything is
changed.

**Why this first.** Nothing in this codebase can be safely touched right now because there is no
way to know if a change broke something short of manually playing games (ARCHITECTURE.md §5.8).
Every later phase depends on this safety net to know whether it succeeded.

**Scope.**
- Introduce a real build tool (Maven or Gradle) to replace the hand-managed jars in `libs/` and
  the IDE-only project file, enough to support a test runner and dependency management going
  forward.
- Introduce a test suite (JUnit or equivalent) exercising the chess rules — legal move
  generation, check detection, checkmate, stalemate, the 50-move rule, threefold repetition,
  castling, en passant, promotion — against both existing rule paths (the object-oriented
  `CheckScanner`/`BoardState` path and the bitboard path) using known FEN positions with known
  correct answers, run headlessly with no Swing involved.
- Explicitly write tests that capture the *currently broken* behaviors described in the
  project's own history (draw detection failures, checkmate-avoidance) so that later phases have
  a concrete, automatable "is this fixed yet" signal, and a concrete "did I just fix it" moment
  to record.
- Do **not** yet change any production logic to make tests pass — this phase is about
  observation and infrastructure, not fixes. A test is allowed to start out red.

**Research required before implementing.**
- Survey what, if anything, in the current tree can already run headlessly (several classes have
  `public static void main` smoke tests — e.g. `Minimax.main`, `BoardState.main`,
  `BitBoard.main` — check whether these can inform starting fixtures, and confirm they have no
  hidden dependency on Swing/static UI state before relying on them).
  Confirm whether any of these currently even run to completion, given the deep dependence on
  `main.setting.ChoosePlayFormat` statics documented in ARCHITECTURE.md §4.1.
- Decide Maven vs. Gradle vs. another option — this is a genuine open fork; write down the
  trade-off (ecosystem familiarity, IDE support, migration effort for the existing jars in
  `libs/`, the Kotlin stdlib dependency pulled in transitively by Retrofit) rather than defaulting
  silently.
- Inventory every third-party jar currently sitted in `libs/` and confirm the equivalent
  Maven/Gradle coordinates and versions exist and match, so the migration doesn't silently change
  a dependency version.
- Build the list of "known-correct" chess test positions and expected results (a well-known
  public perft/EPD test suite is worth evaluating here rather than hand-authoring everything).

**Risks.**
- Build-tool migration can silently change classpath order or dependency versions in ways that
  affect runtime behavior (particularly around Swing/AWT and the Stockfish process launch) —
  verify the app still launches and plays a full game manually after the migration, before
  trusting the new build for anything else.

**Exit criteria.** The project builds and runs via the new build tool; a test suite exists and
runs headlessly in CI-able form; it has documented, named failures for the known bugs (not
silently skipped); a short written note records exactly which behaviors are locked in as
"currently correct, do not regress" vs. "currently broken, tracked for a later phase to fix."

---

## Phase 1 — Remove dead and parallel code

> **Status: DONE & verified 2026-09-05** on branch `phase-1-remove-dead-code`.
> Deleted (all confirmed zero live references, no reflection, no build/IDE-config references):
> the whole `player/` package (`Player`, `ai/MoveStrategy`, `ai/BoardEvaluator`, `ai/MiniMax`),
> `pieces/Piece2`, `pieces/PieceUT`, `ai/EvaluationLevel2`, `ai/ChessMoveConverter`, and the
> fully-commented-out `ChessServer/ChessServer.java`. ~250 lines, 2 packages removed.
> `./mvnw test` → 23 green (unchanged); `-Pknown-bugs` → 4 red (unchanged); `./mvnw package`
> → runnable jar. Heuristic ideas from `EvaluationLevel2` and the UCI-string→`Move` approach
> from `ChessMoveConverter` were captured as design notes first. Full write-up:
> [docs/phase-1-notes.md](docs/phase-1-notes.md).
> **Parity with `master` proven** (this also clears the smoke test Phase 0 left owed):
> extracted branch jar vs. `master` jar are byte-identical except the 8 deleted class files;
> the jar boots clean; a new `@Tag("smoke")` end-to-end suite (`./mvnw test -Psmoke`, 3 green)
> plays a full game to checkmate, a full random bitboard-engine game, and a save/load
> round-trip — all headless. See phase-1-notes.md §6.
> **Scope change vs. this guide's original text:** the `BoardState.getAllPossibleMoves()` /
> `getAllPossibleMovesForASide()` methods were **NOT** deleted — Phase 0 made
> `getAllPossibleMovesForASide()` a load-bearing anchor for the characterization suite (its
> buggy output is pinned there). They are deferred to Phase 2, which already plans to replace
> that move-gen family. See Phase 2's research section and phase-1-notes §4.
> **Not automated** (unchanged by this deletion, deferred to Phase 3/4): the interactive Swing
> paths — mouse-drag moves, the "New computer Game" self-running loop, the end-game dialog. A
> human play-through is still worthwhile before a *release* but is not a Phase 1 blocker.

**Goal.** Delete code that has no live call path, so every later phase's research is working
against only the code that actually runs.

**Why this order.** This is the lowest-risk phase in the entire plan (nothing calls this code,
by construction — see ARCHITECTURE.md §3) and it materially shrinks the surface area every
subsequent phase's research has to account for. It's sequenced after Phase 0 rather than before
it only so the test suite exists to prove nothing was actually reachable through some path the
research missed.

**Scope.** Candidates identified in ARCHITECTURE.md §3: the second minimax/evaluator
implementation under `player/`, the unused alternate `Piece` classes, the unreferenced
`EvaluationLevel2`/`ChessMoveConverter`, the fully commented-out `ChessServer`, and the unused
`BoardState` methods with no external callers. The opening-book/Lichess client is *not* in scope
for deletion here even though it's currently unreachable — it's slated for completion in Phase 5,
not removal.

> **Amended after Phase 0/1:** the `BoardState.getAllPossibleMoves*` methods were removed from
> this scope. Phase 0's characterization suite now calls `getAllPossibleMovesForASide()` and
> pins its currently-buggy output; deleting it would break the safety net for no gain, since
> Phase 2 already plans to replace this move generator. Deferred to Phase 2. Everything else in
> the list above was deleted — see the Status block and phase-1-notes.md.

**Research required before implementing.**
- Re-run the call-site audit from ARCHITECTURE.md §3 against the current tree (not the snapshot
  in that document) — confirm each candidate is still genuinely dead, including reflection-based
  or string-based references (e.g. anything loaded by class name) that a plain import grep would
  miss.
- Check the build output/artifacts (`out/artifacts/`) and any run configurations for references
  to the classes slated for deletion, in case something outside `src/` targets them directly.
- Confirm with whoever is directing the work whether any of the "dead" code (particularly the
  second minimax engine under `player/`) contains an idea worth preserving in a design note
  before the code itself is deleted — deleting code is easy to reverse via git, but the *reason*
  a parallel implementation was started is not always captured in the code itself.

**Risks.** Low. Primary risk is deleting something with a non-obvious dynamic call path; the
Phase 0 test suite plus a full manual smoke-test of the app after deletion is the mitigation.

**Exit criteria.** The dead classes are gone, the project still builds and passes Phase 0's test
suite, and a manual playthrough (human vs. computer, computer vs. computer, save/load) still
works exactly as before.

---

## Phase 2 — Unify the board representation and the rules engine

> **Status: DONE — merged to `master` 2026-09-06** (`--no-ff`, branch
> `phase-2-unify-rules-engine`, 7 commits). Full research artifact + increment log:
> [docs/phase-2-research.md](docs/phase-2-research.md). Result: one rules authority (the
> bitboard, behind the new headless `rules` package), `CheckScanner` and the PGN
> simulate-and-revert path deleted, all four `-Pknown-bugs` draw-detection reds fixed
> (`-Pknown-bugs` now empty). `./mvnw test` **49 green**, `-Psmoke` **3 green**,
> `./mvnw package` builds the jar. The hands-on play-through (increment 7) was **waived by the
> project owner** in favour of the green `-Psmoke` end-to-end suite (the Phase 1 stand-in).
> One reported gameplay bug — *the engine stops playing when it's losing / mate is near* — is
> **documented, not fixed** (owner deferred it): root cause is the `ChoosePlayFormat` statics
> flipped around an async search + a swallowed NPE; fix is Phase 3 (retire the statics) +
> Phase 4 (one concurrency model). See [ARCHITECTURE.md](ARCHITECTURE.md) §2.4.
>
> Locked decisions (2026-09-05):
> (1) the **bitboard becomes the single rules authority**, wrapped behind a new Swing-free
> `rules` package speaking **FEN in / FEN + status + legal-move-list out, moves as UCI**;
> (2) the OO `BoardState`/`Piece` become a delegating render-only view-model — `CheckScanner`,
> the PGN simulate-and-revert path, and the dead `BoardState.makeMoveAndGet*` / `getAllPossibleMoves*`
> / `cancelMove` / `main()` are deleted this phase, but full OO removal + the UI type switch stay
> Phase 3; (3) **draw-rule correctness is in scope** — the unified `status()` fixes the 50-move
> threshold, threefold repetition and insufficient material, and the 4 `-Pknown-bugs` reds are
> expected to go green; (4) the rules module ships with no `main.*`/`GUI`/Swing/AWT imports.
> Landing in 7 increments (see the research doc). **Increments 1–3 done** on the branch:
> the new headless `rules` package (`Square`/`ChessMove`/`Position`/`GameStatus`/`Rules`/`Game`
> + an `ai.BitBoard.BitBoardRules` bridge) as a working FEN/UCI slice; three real bitboard bug
> fixes it surfaced (`BitQueen.getAttackedTiles()` up-left diagonal → Scholar's-Mate check
> undetected; `BitPawn.getEnPassantMoves()` dropped the capturing pawn; `BitBoard.getStatus()`
> 50-move threshold per-ply); and **`BoardState`'s `isValidMove` / status API + `Board`'s
> check/game-over display now delegate to the facade** (per-position cache). **All four
> draw-detection bugs are fixed** — the `-Pknown-bugs` suite is empty, its tests are now green
> in `characterization.DrawDetectionTest`. `./mvnw test` 46 green, `-Psmoke` 3 green.
> Increment 4 done: `Move`'s SAN `+`/`#`/`1/2-1/2` suffix now queries `rules.Rules` about the
> position after the move; `BoardState.makeMoveAndGetStatus` (last of the triplicated "Path 3")
> deleted.
> **Increment 5 done: `main.CheckScanner` deleted (−213 lines)** — `myEngine` uses
> `isValidMove` / `getIsCheck`, `King.canCastle` is geometry-only. `BoardState` lost
> `getAllPossibleMoves*`, `makeMoveToCheckIt`, `makeMoveAndGet*`, `cancelMove`, `main` (net
> ≈ −200 lines); new `getLegalMoves()` → `rules.Rules`. The two "characterized bug" test
> assertions are re-pointed (start move count now asserts the correct **20**; the CME
> characterization is gone with the method). `Minimax` already ran on the canonical bitboard,
> so nothing to re-point there. `./mvnw test` 49 green, `-Psmoke` 3 green.
> Left for later: `Piece.isValidMovement` / `moveCollidesWithPiece` / `King.canCastle` are now
> dead but still present — a Phase 3 `pieces`-restructure cleanup.
> **Increment 6 done: [ARCHITECTURE.md](ARCHITECTURE.md) re-synced** to the unified state
> (§2.1–2.4, §3, §4, §5 #2/#6 RESOLVED, §6). A reported gameplay bug — *the engine stops
> playing when it's losing / mate is near* — is documented in ARCHITECTURE.md §2.4 with its
> root cause (`ChoosePlayFormat` statics flipped around an async search + a swallowed NPE);
> the fix belongs to Phase 3 (retire the statics) + Phase 4 (concurrency).
> **Increment 7: the hands-on play-through was waived by the project owner; Phase 2 merged to
> `master` on the green `-Psmoke` suite.**

**Goal.** Collapse the two independent board models and the three independent
check/checkmate/draw implementations (ARCHITECTURE.md §2.1, §2.3) into one canonical
representation and one rules engine that everything else consults.

**Why this matters most.** This phase directly targets the root cause of the reported gameplay
bugs — the engine and the UI can currently disagree about whether a position is check, mate, or a
draw because they compute it independently (ARCHITECTURE.md §5.2). This is very likely the single
highest-value phase in the whole plan; it's also the riskiest and most invasive, which is why it
only happens once Phases 0–1 have de-risked the ground under it.

**Scope.** This phase does not yet touch the UI layer's *structure* (that's Phase 3) — its job is
purely to make there be one rules authority, which the existing UI code can be pointed at with
targeted, mechanical call-site updates. Whether that authority ends up being the current bitboard
engine, the current object model, or a new representation entirely is exactly the kind of decision
this guide is not making in advance — that's what the research step is for.

**Research required before implementing.**
- Full audit of every place any of the three check/status paths (`CheckScanner`,
  `BitBoard.isCheckOn`/`getStatus`, and the `Move.getStatusString`/`BoardState.makeMoveAndGetStatus`
  simulate-and-revert path) is invoked, with the actual current behavior of each documented
  side-by-side against the Phase 0 test fixtures — including the specific positions where they're
  suspected (or, after Phase 0, proven) to disagree.
- Full audit of every rule implemented in *only one* of the two board models (ARCHITECTURE.md
  §2.2 flags castling as one concrete example) so nothing gets silently dropped when one model is
  retired.
- **Carried over from Phase 1:** `BoardState.getAllPossibleMoves()` /
  `getAllPossibleMovesForASide()` and `BoardState.main()` are dead-except-for-tests and were
  left in place for this phase to delete together with the move-generator rewrite. The Phase 0
  characterization tests (`CheckmateStalemateTest`, `SpecialMovesTest`, `StartingPositionTest`,
  `CharacterizationTestBase`) currently pin `getAllPossibleMovesForASide()`'s buggy output
  (12 moves from the start; CME when a capture exists) — those assertions must be re-pointed at
  the unified generator and flipped from "characterized bug" to "correct" as part of this phase.
- A genuine, written-out comparison of the options for the canonical representation (keep
  bitboard only and make everything else a derived view; keep the object model and rebuild its
  correctness; introduce a third, clean representation designed for this specifically) with
  performance, correctness, and migration-cost trade-offs for each — this is a real design fork,
  not a foregone conclusion, even though the bitboard is the more likely candidate given
  ARCHITECTURE.md's findings.
- Confirm how deeply the object-model `Piece` objects are relied on for rendering (sprite,
  position, animation state) versus rules, since ARCHITECTURE.md §5.7 notes they currently carry
  both — untangling that is part of this phase's research, not an afterthought.

**Risks.** This is the highest-risk phase in the plan: it touches the code every other feature
depends on. Mitigations: do it entirely behind the Phase 0 test suite, expand that suite further
as edge cases surface during research, and land it in reviewable increments behind a branch
rather than as one large change, keeping the app playable (even if temporarily slower or missing
a nice-to-have) at each landing point.

**Exit criteria.** There is exactly one code path that answers "is this legal / is this check /
is this mate / is this a draw," it is covered by the Phase 0 test suite (expanded to cover every
divergence found during research), and the previously-triplicated logic has been deleted, not
just deprecated.

---

## Phase 3 — Extract a headless rules API and decouple the UI

> **Status: DONE — merged to `master` 2026-10-02** (PR #2). Decisions
> and the per-increment log are in [docs/phase-3-research.md](docs/phase-3-research.md) §8–§9.
> Exit criteria met: `pieces` is deleted, `rules`/`ai`/`engine`/`game` import nothing from
> `main`/`GUI`/Swing/AWT (enforced by `architecture.LayeringTest`), `Board` is a renderer with no
> rules logic, the static settings are replaced by an immutable `GameConfig`, and the app was
> driven under Xvfb with the engine answering at several levels, go back, play-as-black and
> two-player flip all working. Engine bugs A/B/C and a false-repetition bug in the search hash are
> fixed. Owed: a manual play-through by the owner on a real screen (animation feel, sounds).
> Moved to Phase 4: search speed at levels ≥ 10 (see the research doc's last log entry).

**Goal.** Give the unified rules engine from Phase 2 a real, Swing-free interface, and turn
`Board`/`Input`/`Main` into consumers of that interface instead of being the rules engine
themselves (ARCHITECTURE.md §2.6, §4.1, §5.1).

**Why this order.** This only becomes tractable once Phase 2 has produced one rules engine to put
behind an interface — doing this before Phase 2 would mean building a clean API in front of two
still-disagreeing implementations.

**Scope.** Define and introduce a headless API surface for the game (making a move, querying
legal moves, querying game status, serializing/deserializing position) with no `javax.swing`/
`java.awt` imports anywhere in its implementation. Migrate `Board` to a pure renderer driven by
that API and by game-state-change notifications, rather than a class that mutates board state
directly. Retire the `main.setting.ChoosePlayFormat`/`SettingPanel` static-global pattern
(ARCHITECTURE.md §5.4) in favor of explicit configuration passed into the API, including inside
the search — since ARCHITECTURE.md documents the search itself currently reading those statics
mid-recursion.

**Research required before implementing.**
- Full audit of every read and write of `ChoosePlayFormat.*` and `SettingPanel.skillLevel`
  (ARCHITECTURE.md §5.4 lists examples, not an exhaustive list) to design what explicit
  configuration needs to replace them, including the "flip a static flag, do work, flip it back"
  hack pattern used to reuse hint/opponent-move logic — that pattern needs a real, non-hacky
  replacement, not just a relocation of the same flag.
- Full audit of every place `Board`'s fields (e.g. `Board.selectedPiece`, referenced from deep
  inside `CheckScanner` per ARCHITECTURE.md §4.1) or methods are reached into from outside the UI
  package, since those are exactly the seams this phase needs to sever.
- Decide how animation and audio should be triggered under the new structure (an event/listener
  model reacting to state changes is the likely direction, but confirm this against how
  `ChessAnimation` and `AudioPlayer` are actually invoked today before assuming it).
- Confirm what, if anything, outside `main` currently needs direct UI access (e.g. the engine
  layer calling back into `Board.repaint()`/showing dialogs per ARCHITECTURE.md §2.4) and design
  the inverted, event-driven replacement for those call sites specifically.

**Risks.** Behavioral regressions in UI responsiveness/feel (animation timing, hint highlighting,
board-flip-on-color-switch) are easy to introduce when the state that drives them moves from
ad hoc field mutation to an explicit API — cover these with manual test scripts even where
automated UI testing isn't practical.

**Exit criteria.** `pieces`, and the rules engine underneath it, no longer import anything from
`main` or `GUI`; the `main ↔ ai ↔ pieces` circular dependency documented in ARCHITECTURE.md §4.1
is gone; `Board` contains no chess-rules logic; the app is playable with feature parity.

---

## Phase 4 — Fix concurrency and the Stockfish integration

> **Status: DONE — merged to `master` 2026-10-02** (PR #3). Research,
> decisions and the increment log are in [docs/phase-4-research.md](docs/phase-4-research.md).
> Exit criteria met: one documented pattern for every engine move and hint (cancellable jobs on
> one engine thread, results on the EDT); one Stockfish process per session; no Swing repaint off
> the EDT (checked under Xvfb with a checking `RepaintManager`); `-Pstress` runs unattended
> engine-vs-engine games (built-in and Stockfish) and a take-back/hint storm without a hang.
> Split out as **Phase 4b**: faster move generation and the transposition table (Fork D1).

**Goal.** Replace the four coexisting ad hoc concurrency patterns (ARCHITECTURE.md §5.5) with one
consistent approach, and fix Stockfish's process/session handling so it stops restarting its UCI
handshake every move (ARCHITECTURE.md §2.5) — very likely the actual cause of "Stockfish plays
badly," as opposed to engine strength.

**Why this order.** This is scoped after Phase 3 because a clean, event-driven headless API makes
"engine computes a move, then hands it back" a natural single seam to thread correctly — trying
to fix concurrency while the engine layer still reaches directly into Swing components would mean
solving this twice.

**Scope.** One coherent threading model for "compute a move off the UI thread, then apply it,"
replacing the raw `Thread`, `ExecutorService`+`Future`, `SwingWorker`+`CountDownLatch`+polling,
and inline sleep-sequenced-sound patterns identified in ARCHITECTURE.md §2.6 and §5.5. A
persistent Stockfish process per game with an asynchronous output reader (rather than
sleep-then-poll), proper position tracking (incremental `position ... moves ...` rather than
resending full FEN and restarting UCI negotiation every call), and a real time-management policy
replacing the current fixed-150ms-plus-retry-loop approach (ARCHITECTURE.md §2.5).

**Research required before implementing.**
- Full trace of every current caller of `StockfishEngine` and `myEngine` to enumerate every
  behavior the new engine-orchestration layer needs to reproduce (hints, forced/auto-play modes,
  computer-vs-computer mode, skill-level-based engine selection) — ARCHITECTURE.md §2.4–§2.6
  covers the main ones but this needs to be exhaustive before the old paths are removed.
  Note the "compute a move" module now imports the interface from Phase 3, so also confirm the
  Phase 3 API expresses everything the engine side needs (e.g. FEN export, legality checks for a
  proposed UCI move) before assuming it does.
- Investigate what time controls / strength behavior is actually desired per skill level (this is
  a product decision, not purely technical — write it down explicitly rather than inheriting the
  current ad hoc thresholds unexamined).
- Decide the concurrency primitive to standardize on (a single background executor with
  callback-based completion is a reasonable default to evaluate, but treat this as an open
  question to research against the specific needs surfaced above, not a foregone conclusion).

**Risks.** Getting engine/UI thread-safety subtly wrong is a classic source of intermittent,
hard-to-reproduce bugs — budget real testing time (including stress-testing computer-vs-computer
mode, which most aggressively exercises the concurrency) rather than treating "it played a few
games fine" as sufficient proof.

**Exit criteria.** One documented concurrency pattern is used everywhere an engine move is
computed; Stockfish keeps one process/session per game; no Swing component is touched off the
EDT; computer-vs-computer mode can run unattended for many games without hanging or crashing.

---

## Phase 4b — Make the built-in search fast enough for its levels

> **Status: DONE — merged to `master` 2026-10-02** (PR #5). Research,
> decisions and the increment log are in [docs/phase-4b-research.md](docs/phase-4b-research.md).
> The perft safety net found nine bugs in the move generator (eight visible to a player); they
> were fixed first, test first, one commit per cause, and the engine plays better for it
> (research §2.5). Then the search got faster without changing the moves it picks.
> Exit criteria met: perft matches the published counts and Stockfish, in the rules API and in
> the search's own move list; Levels 6 and 7 finish depth 5 and 6 within 1 s and 5 s in six
> benchmark positions on the cloud container, in a 300 MB heap (`-Pstress`); the
> characterization tests pass unchanged, and `engine.SameMoveTest` shows the speed work kept the
> engine's moves. Still open: the owner's play test of Levels 6-7. Left for a later phase by
> decision (Fork B2): the transposition table, which is unsound as written, and best-move-first
> ordering; both change the engine's moves.

**Goal.** Let Levels 6-7 reach their intended depth (5-6 plies) inside the 5 s cap set in Phase 4.
Today they usually finish only depth 4 in the middlegame, so they play like Level 5
(docs/phase-4-research.md §3.3 and the increment 2+3 log).

**Why separate.** Split out of Phase 4 by decision (docs/phase-4-research.md Fork D1): the cost is
in move generation, which is the Phase 2 rules authority, so it needs its own safety net and
should not ride along with a threading change.

**Scope.** Cheaper legality checking (a JFR profile puts 68% of search time in
`BitBoard.isCheckOn` / `getAllAttackedTiles` while building child boards), switching on the
transposition table that exists but is commented out, and the remaining debug string building in
move generation.

**Research required before implementing.** A perft suite (move counts to fixed depths from
standard positions) as the safety net for any move-generator change; a profile per change;
whether the transposition table changes the moves the engine picks at a given depth (it can,
through move ordering and mate scores), and if so how that is tested.

**Exit criteria.** Perft counts match the published values; Levels 6 and 7 finish depth 5 and 6
in typical middlegames within the cap on the owner's machine; the characterization tests keep
their expected moves, or each change is justified in the phase notes.

---

## Phase 4c — Web UI

> **Status: DONE — merged 2026-10-02** (PR #6 in the original private repository; this public
> repository carries the same history from Phase 3 on). The owner
> played it and asked for changes, which are in; the Swing UI (`main/`, `GUI/`, its sprites,
> sounds and the FlatLaf dependency) is deleted, so the browser is the only UI. Research, decisions
> (U1-U7, all approved by the owner) and the increment log are in
> [docs/ui-research.md](docs/ui-research.md).

**Goal.** Replace the Swing screens, which the owner called the weakest part of the app, with a
browser UI in the shape of lichess: one screen with a scalable board, a side panel (players,
move list with review, controls), a status line, a new-game dialog and an in-panel game-over
result.

**Why now, and before Phase 5.** Phase 3 left the UI talking to the core only through
`game.GameSession` / `GameListener`, so a server can wrap the session without touching rules or
engines. Phase 5's opening names and games browser need UI; building it in Swing first would
mean building it twice. This is also the first step towards the long-term backend + React
architecture, kept to one local process and one user.

**Scope.** A local Javalin server in the same JVM (JSON over one WebSocket; the server stays the
only rules authority, the browser only shows the legal moves it is sent); a React + TypeScript
+ Vite app using react-chessboard, built by Maven into the jar; the packaged jar starts the
server and opens the browser. Swing stays runnable (`main.Main`) until the owner confirms parity,
then its classes are deleted in the phase's last commit.

**Exit criteria.** The packaged jar opens the new UI; a full game against the engine and against
a friend can be played, reviewed and restarted in the browser; API tests and a browser
end-to-end test green; the existing suites unchanged; Swing UI deleted with the owner's sign-off.

---

## Phase 5 — Build the originally-requested features

> **Re-scoped 2026-10-02.** The owner defined "learning from past games" as evolving the
> engine's parameters by self-play, and wants to write the evolution himself. Phase 5 is now the
> groundwork for that (parameters, arena, record, lab page); the opening book moves later. All
> decisions (E1-E12) are approved; see [docs/phase-5-research.md](docs/phase-5-research.md).
> **Built on branch `phase-5-evolution` (PR #1):** parameters, quiescence search, move variety,
> arena, run record and runner, lab page with "play the champion", and the owner's
> [evolution guide](docs/evolution-guide.md). The text below is the original plan, kept for the
> history of why.

**Goal.** Now that there's a clean, tested, headless rules/engine core, deliver the two
capabilities that motivated this project in the first place: real opening-book integration and a
structured game database that can eventually support learning from past games.

**Why last.** Both of these are additive features layered on top of the engine API from Phase 3;
building them earlier would mean building them against the unstable, duplicated foundation this
whole plan exists to remove, and likely re-doing the work.

**Scope.**
- Finish wiring the existing `ai/openingBook` Retrofit/Lichess client and binary opening-book
  format (currently fully built but never called, per ARCHITECTURE.md §2.7/§3) into the engine's
  move-selection path from Phase 4, or replace it if research finds a better-fitting approach.
- Design and introduce real structured persistence for games (metadata: opponents, result, date,
  time control, move list; not just a flat FEN list per ARCHITECTURE.md §2.7) sufficient to
  support future learning/analysis work, replacing `SaveGame`/`LoadGame`'s flat-file approach.

**Research required before implementing.**
- Re-evaluate the existing `ai/openingBook` code against current Lichess API documentation before
  reusing it as-is — it was written against some prior state of that API and may need updates;
  confirm rate limits, licensing/attribution requirements, and offline-fallback behavior (what
  happens with no network) before wiring it into the live move path.
- A genuine evaluation of persistence options for the game database (embedded SQL like SQLite,
  a structured file format, or something else) against the actual future "learn from past games"
  goal — this determines the schema, so it's worth deliberately researching rather than defaulting
  to whatever's fastest to bolt on.
- Define, concretely, what "learning from past games" is expected to mean (statistics/analysis
  tooling? feeding an opening-book generator? training a position evaluator?) before designing the
  schema — the schema needed differs a lot depending on the answer, and this is a product question
  the earlier phases haven't needed to resolve.

**Risks.** Scope creep — "a database for learning from games" can expand arbitrarily. Keep this
phase's first landing scoped to "structured storage of completed games," and treat any actual
learning/ML component as a follow-on phase of its own once this guide's core refactor is
finished.

**Exit criteria.** Games are recorded in structured, queryable storage; the opening book is
consulted during real move selection (with a documented fallback when it has no data for a
position); both features are covered by tests in the Phase 0 suite's style.

---

## Phase 5b — Transposition table and move ordering

> **Status: in review** (branch `phase-5b-search-tt`). Research, decisions and numbers are in
> [docs/phase-5b-research.md](docs/phase-5b-research.md).

**Goal.** Win back the speed the Phase 5 quiescence search cost, so Level 7 finishes depth 6
inside the 5 s cap again. Picks up what Phase 4b left for later (Fork B2).

**Scope.** A sound transposition table (bounds, mate scores, a key that covers castling, en
passant and what the evaluation reads), shared by the depths of one search; move ordering by the
table's move, MVV-LVA captures, killers and history. Engine code only; search stays per call,
thread-safe and repeatable with a seed.

**Exit criteria.** Same root score as the search without them at every tested depth; Level 7
within the 5 s cap in `SearchSpeedTest`; equal or better in the arena at equal depth and stronger at
equal time; every changed move in `same-moves.txt` explained.

---

## Phase 6 — Pieces as data

> **Status: R1-R5 done** (PRs #25-#40): the engine plays on `GenericBoard`, pieces as data, and
> four variants (chess, Antichess, King of the Hill, Three-check) are played end to end in the
> app, checked against Fairy-Stockfish. The *Variants* tab invents pieces (grid or Betza text) and
> variants, runs a self-play health check, and plays them; invented pieces are checked against
> Fairy-Stockfish too. **Stage 2 done** (2026-10-05): evolution runs play any variant, the Lab
> starts, stops and resumes runs from the browser over several screens, and the first experiment,
> evolution from zero on antichess, ran from it (`docs/experiments/`). Next: R6 extensions, only
> when an experiment needs them, and the small value network. Research, target architecture and
> steps are in [docs/phase-6-research.md](docs/phase-6-research.md).

**Goal.** Let the owner invent pieces and variants: piece types become data read by one move
generator, behind a `Board` interface that the search, the evaluation and the rules use, so
later boards (other geometry, more players, squares with behaviour) fit without touching them.

**Scope.** R1 interface, R2 piece types as data, R3 generic board replacing `BitBoard`, R4 a
variant end to end, R5 piece designer in the UI. R6 (wider extensions) only when an experiment
needs it.

**Exit criteria.** Each step keeps perft, `SameMoveTest` and `SearchSpeedTest` as they were for
standard chess; at the end the owner designs a piece in the UI, plays it and gets a health
report on the variant.

---

## Cross-phase notes

- **Do not parallelize phases 2–4.** They each assume the previous phase's exit criteria are
  actually met, not just started. Phase 0 (tests) and Phase 1 (dead code removal) are the
  exception — Phase 1 can start as soon as Phase 0's test suite exists, since it doesn't depend on
  Phase 0 being "complete" in every other sense.
- **Re-sync ARCHITECTURE.md after Phase 2 and again after Phase 3.** Those two phases change the
  actual structure the document describes; leaving it stale defeats its purpose for anyone
  planning the next phase.
- **If a phase's research turns up something that invalidates this guide's assumptions**
  (e.g. Phase 2's research finds the object model is load-bearing in a way that makes retiring it
  much more expensive than expected), that's a reason to update this guide's plan for that phase,
  not a reason to route around the research step.
