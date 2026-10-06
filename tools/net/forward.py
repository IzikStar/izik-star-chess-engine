"""The network's forward pass in numpy, to check a trained file against Java (ai.eval.NetEvaluate).

    score = b2 + sum_h w2[h] * relu(b1[h] + sum_{i on} w1[h][i])         (centipawns, side to move)
    expected result = 1 / (1 + 10 ** (-score / 400))                      (the Texel curve)

    python3 tools/net/forward.py nets/first.json "FEN" ...     # the score of each position
    python3 tools/net/forward.py nets/first.json positions.bin  # the error over a training file

Java prints the same score with `lab.Cli net nets/first.json "FEN"`; the two must agree to a few
hundredths of a centipawn (float rounding).
"""
import json
import sys

import numpy as np

import data
import encode


def load(path):
    with open(path) as f:
        net = json.load(f)
    if net.get("format") != "net-v1":
        raise ValueError(f"{path}: expected format net-v1, got {net.get('format')}")
    return {"w1": np.array(net["w1"], dtype=np.float32), "b1": np.array(net["b1"], dtype=np.float32),
            "w2": np.array(net["w2"], dtype=np.float32), "b2": np.float32(net["b2"]),
            "inputs": net.get("inputs", len(net["w1"][0])), "variant": net.get("variant", "chess")}


def score_active(net, active):
    """The score of one position given the inputs that are on."""
    hidden = net["b1"] + net["w1"][:, active].sum(axis=1)
    return float(net["b2"] + net["w2"] @ np.maximum(hidden, 0))


def score_batch(net, active_rows):
    """Scores of [batch, slots] index rows (-1 = padding)."""
    x = data.dense(active_rows, net["inputs"])
    hidden = np.maximum(x @ net["w1"].T + net["b1"], 0)
    return hidden @ net["w2"] + net["b2"]


def expected(score):
    return 1 / (1 + 10 ** (-np.asarray(score, dtype=np.float64) / 400))


if __name__ == "__main__":
    net = load(sys.argv[1])
    for arg in sys.argv[2:]:
        if arg.endswith(".bin"):
            d = data.load(arg)
            scores = np.concatenate([score_batch(net, d["active"][i:i + 4096]) for i in range(0, len(d["result"]), 4096)])
            err = np.mean((expected(scores) - d["result"]) ** 2)
            print(f"{arg}: {len(scores):,} positions, mean squared error against the result {err:.4f}"
                  f" (a net that always says 0 would get {np.mean((0.5 - d['result']) ** 2):.4f})")
        else:
            active = encode.active(arg)
            print(f"{score_active(net, active):.2f}  {arg}")
