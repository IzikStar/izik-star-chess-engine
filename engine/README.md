# engine/

The game can fill this folder itself: press **Download Stockfish** in the New game dialog (Levels
8-10) or in the analysis panel. Or drop a Stockfish executable here yourself. It is not committed to the repository
(it is a ~70 MB third-party GPLv3 binary); download it from
<https://stockfishchess.org/download/>.

Any file whose name starts with `stockfish` is found, here or one folder down, so you can
unzip the download as it comes (for example `engine/stockfish/stockfish-windows-x86-64-avx2.exe`).
This folder is looked for next to the directory you run from and next to the jar. Stockfish on
your `PATH` (`apt install stockfish`, `brew install stockfish`) is found too. To name the file
yourself:

```
java -Dstockfish.path=/path/to/stockfish -jar target/izikstar-chess-3.1.0.jar
# or
export STOCKFISH_PATH=/path/to/stockfish
```

The server prints which Stockfish it found when it starts. Without Stockfish the game still
runs: Levels 8-10 play the built-in engine at Level 7, and the New game dialog says so.

## Fairy-Stockfish

For the variant yardsticks (Lab runs and `arena.Cli --variant`), drop a Fairy-Stockfish build
from <https://github.com/fairy-stockfish/Fairy-Stockfish/releases> here (on Windows
`fairy-stockfish-largeboard_x86-64.exe`, on Linux `fairy-stockfish-largeboard_x86-64`). Any file
whose name starts with `fairy-stockfish` is found, here or one folder down; or use
`-Dfairy.path=...`, `FAIRY_STOCKFISH_PATH`, or `fairy-stockfish` on your `PATH`. It is never
mistaken for Stockfish (that search wants a name starting with `stockfish`).
