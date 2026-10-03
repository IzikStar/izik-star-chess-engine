# engine/

Drop a Stockfish executable here. It is not committed to the repository
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
