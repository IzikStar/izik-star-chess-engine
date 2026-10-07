# The original code

*Historical: where to find the game as it was before the refactor.*

This repository starts from a cleaned copy of the history, so it does not hold the original
2024 code as its author wrote it by hand. That code is kept, unchanged, in the private legacy
repository `IzikStar/IzikStar-chess_3.1`:

- **Branch `legacy-original`** points at commit `ad9ca32` (2024-08-25), the last commit before
  Phase 0 of the refactor. It is the Java/Swing game written between June and August 2024:
  the board, the rules, the sounds, the Stockfish connection and the first minimax engine.
- The legacy repository's `master` continues from that commit through Phases 0-4c; the 91
  commits up to it (2024-06-21 to 2024-08-25) are the original work, with commit messages in Hebrew.

To look at it: `git clone -b legacy-original https://github.com/IzikStar/IzikStar-chess_3.1`
(needs access to the private repository).

[architecture-before-refactor.md](architecture-before-refactor.md) maps that code and its
flaws, and how each phase changed it.
