# engine/

Drop a Stockfish executable here. It is not committed to the repository
(it is a ~70 MB third-party GPLv3 binary); download it from
<https://stockfishchess.org/download/>.

By default the game looks for `engine/stockfish-windows-x86-64.exe`, relative to the
directory you launch it from. To use any other file name or location (for example the
Linux or macOS build), point the game at it with either:

```
java -Dstockfish.path=/path/to/stockfish -jar target/izikstar-chess-3.1.0.jar
# or
export STOCKFISH_PATH=/path/to/stockfish
```

Without Stockfish the game still runs: the top difficulty levels fall back to the
built-in engine.
