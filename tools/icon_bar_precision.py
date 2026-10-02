#!/usr/bin/env python3
import json
import math


def rate(speed: float) -> float:
    return 1.6 - (max(0.0, min(100.0, speed)) / 100.0) * 1.2


def overshoot(bounce: float, tuned: float) -> float:
    value = 1.0 + (max(0.0, min(100.0, bounce)) / 100.0) * (tuned - 1.0) * 2.0
    return round(value, 3)


def stretch(from_p: float, from_s: float, to_p: float, to_s: float, dilate: float):
    start = min(from_p, to_p)
    end = max(from_p + from_s, to_p + to_s)
    grow = max(0.0, min(1.0, dilate / 100.0))
    return {
        "p": round(to_p + (start - to_p) * grow, 6),
        "s": round(to_s + (end - start - to_s) * grow, 6),
    }


slot = 38.0
pad = 7.0
gap = 4.0
count = 5
bar_width = 2 * pad + count * slot + (count - 1) * gap
bar_height = 2 * pad + slot
positions = [pad + i * (slot + gap) for i in range(count)]

result = {
    "bar": {"widthDp": bar_width, "heightDp": bar_height, "positionsDp": positions},
    "rate": {str(x): round(rate(x), 6) for x in (0, 50, 100)},
    "overshoot": {
        "move": {str(x): overshoot(x, 1.28) for x in (0, 50, 100)},
        "size": {str(x): overshoot(x, 1.34) for x in (0, 50, 100)},
    },
    "stretch": {
        "0_to_4_dilate_0": stretch(positions[0], slot, positions[4], slot, 0),
        "0_to_4_dilate_50": stretch(positions[0], slot, positions[4], slot, 50),
        "0_to_4_dilate_100": stretch(positions[0], slot, positions[4], slot, 100),
    },
    "durationsMs": {
        str(x): {
            "stretch": round(150 * rate(x)),
            "settle": round(420 * rate(x)),
        }
        for x in (0, 50, 100)
    },
    "androidDensityExamples": {
        str(d): {"widthPx": round(bar_width * d), "heightPx": round(bar_height * d)}
        for d in (1, 2, 3, 4)
    },
}

print(json.dumps(result, indent=2, sort_keys=True))
