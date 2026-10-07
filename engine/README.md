# engine/

Put Stockfish and Fairy-Stockfish here. Neither is committed to the repository (both are large
third-party GPLv3 binaries).

- **Stockfish** plays Levels 9-13, hints and game analysis. The game can fetch it itself
  (**Download Stockfish** in the New game dialog or the analysis panel), or unzip a download from
  <https://stockfishchess.org/download/> here: any file whose name starts with `stockfish`, here
  or one folder down, is found. Without it, Levels 9-13 play the built-in engine at Level 8.
- **Fairy-Stockfish** is the yardstick for variant runs in the Lab and `arena.Cli --variant`. Any
  file whose name starts with `fairy-stockfish` is found.

The other ways to point at them (`PATH`, `-Dstockfish.path`, `STOCKFISH_PATH`, `-Dfairy.path`,
`FAIRY_STOCKFISH_PATH`) are in the [README](../README.md#optional-stockfish).
