"""Reads the network's training file (lab.NetData, written by `lab.Cli features POSITIONS.csv OUT.bin`).

    header   b"NETDATA1", int32 inputs, int32 slots, int32 rows, int32 0       (24 bytes)
    row      int16[slots] inputs that are on (-1 = padding), float32 result, float32 score,
             uint8 side to move, 3 bytes of padding

Everything is for the side to move: result 1 / 0.5 / 0, score in centipawns (NaN when the game
had no engine score for that position). Little-endian.

    import data
    d = data.load("positions.bin")
    d["active"]   # int16 [rows, slots], -1 where there is no piece
    d["result"]   # float32 [rows]
    d["score"]    # float32 [rows], NaN = none
    d["stm"]      # uint8 [rows]
    d["inputs"]   # 768 for chess

A dense batch for a trainer: X = dense(d["active"][i:j], d["inputs"]) is a float32 [batch, inputs]
matrix of 0/1. For a sparse layer, the index array itself is what you need.

    python3 tools/net/data.py positions.bin      # prints a summary
"""
import struct
import sys

import numpy as np

MAGIC = b"NETDATA1"


def load(path):
    with open(path, "rb") as f:
        header = f.read(24)
        magic, inputs, slots, rows, _ = struct.unpack("<8siiii", header)
        if magic != MAGIC:
            raise ValueError(f"{path} is not a {MAGIC.decode()} file")
        row = np.dtype([("active", "<i2", (slots,)), ("result", "<f4"), ("score", "<f4"), ("stm", "u1"), ("pad", "u1", (3,))])
        raw = np.frombuffer(f.read(), dtype=row, count=rows)
    return {"active": raw["active"], "result": raw["result"], "score": raw["score"], "stm": raw["stm"],
            "inputs": inputs, "slots": slots}


def dense(active, inputs):
    """0/1 rows from the index rows: [batch, slots] int16 (with -1 padding) -> [batch, inputs] float32."""
    x = np.zeros((active.shape[0], inputs), dtype=np.float32)
    rows = np.repeat(np.arange(active.shape[0]), active.shape[1])
    cols = active.reshape(-1)
    keep = cols >= 0
    x[rows[keep], cols[keep]] = 1
    return x


if __name__ == "__main__":
    d = load(sys.argv[1])
    n = len(d["result"])
    pieces = (d["active"] >= 0).sum(axis=1)
    has_score = ~np.isnan(d["score"])
    print(f"{n:,} positions, {d['inputs']} inputs, {d['slots']} slots, {pieces.mean():.1f} pieces on average")
    print(f"result for the side to move: win {np.mean(d['result'] == 1):.1%}, draw {np.mean(d['result'] == 0.5):.1%}, "
          f"loss {np.mean(d['result'] == 0):.1%}")
    if has_score.any():
        s = d["score"][has_score]
        print(f"score: {has_score.mean():.1%} of rows have one; mean {s.mean():+.0f}, std {s.std():.0f} cp, "
              f"range {s.min():+.0f} .. {s.max():+.0f}")
    print(f"side to move: white {np.mean(d['stm'] == 0):.1%}")
