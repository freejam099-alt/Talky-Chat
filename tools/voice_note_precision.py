#!/usr/bin/env python3
"""Precision calculations for the native VoiceNote Compose port.

The Bencho CSS uses CSS pixels and a 100% flexible wave. Android uses dp for
layout and physical pixels in Canvas/pointer coordinates. These calculations
keep the source dimensions in logical dp, then convert only at the Canvas/input
boundary.
"""
from decimal import Decimal, ROUND_HALF_UP
import json

H = Decimal("56")
IDLE_W = Decimal("216")
OPEN_W = Decimal("316")
FRAME_W = Decimal("336")
FRAME_H = Decimal("156")
SAMPLE_MS = Decimal("70")
CANCEL_DP = Decimal("90")
MIN_MS = Decimal("500")
MAX_MS = Decimal("30000")
BAR_DP = Decimal("3")
MIN_BAR_DP = Decimal("4")
MAX_BAR_DP = Decimal("32")

DENSITIES = {"mdpi": Decimal("1"), "hdpi": Decimal("1.5"), "xhdpi": Decimal("2"), "xxhdpi": Decimal("3"), "xxxhdpi": Decimal("4")}


def clamp(v: Decimal, a: Decimal, b: Decimal) -> Decimal:
    return min(b, max(a, v))


def inner_radius(corner: Decimal) -> Decimal:
    r = clamp(corner, Decimal("0"), H / 2)
    return max(Decimal("0"), r - Decimal("6") * min(Decimal("1"), r / Decimal("12")))


def gain_multiplier(gain: Decimal) -> Decimal:
    g = clamp(gain, Decimal("0"), Decimal("100"))
    return Decimal("0.4") + (g / Decimal("100")) * Decimal("1.2")


def wave_width(total: Decimal, bars: int, left_extra: Decimal, right_extra: Decimal, fixed_children: Decimal, gaps: Decimal) -> Decimal:
    # CSS/Compose Row equivalent: total - horizontal padding - fixed children - gaps.
    return max(Decimal("0"), total - left_extra - right_extra - fixed_children - gaps)


def bar_gap(width: Decimal, bars: int) -> Decimal:
    if bars <= 1:
        return Decimal("0")
    return max(Decimal("0"), (width - Decimal(bars) * BAR_DP) / Decimal(bars - 1))


def px(value: Decimal, density: Decimal) -> int:
    return int((value * density).quantize(Decimal("1"), rounding=ROUND_HALF_UP))


def main() -> None:
    bars = 28
    rec_wave = wave_width(OPEN_W, bars, Decimal("22"), Decimal("22"), Decimal("9") + Decimal("38"), Decimal("24"))
    clip_wave = wave_width(OPEN_W, bars, Decimal("6"), Decimal("10"), Decimal("44") + Decimal("38") + Decimal("32"), Decimal("36"))
    report = {
        "dimensions_dp": {"frame": [int(FRAME_W), int(FRAME_H)], "idleWidth": int(IDLE_W), "openWidth": int(OPEN_W), "height": int(H)},
        "recording": {"sampleMs": int(SAMPLE_MS), "minMs": int(MIN_MS), "maxMs": int(MAX_MS), "cancelDp": int(CANCEL_DP)},
        "bars": {"count": bars, "barWidthDp": float(BAR_DP), "recordWaveWidthDp": float(rec_wave), "recordGapDp": float(bar_gap(rec_wave, bars)), "clipWaveWidthDp": float(clip_wave), "clipGapDp": float(bar_gap(clip_wave, bars)), "minHeightDp": float(MIN_BAR_DP), "maxHeightDp": float(MAX_BAR_DP)},
        "gain": {"default": float(gain_multiplier(Decimal("60"))), "min": float(gain_multiplier(Decimal("0"))), "max": float(gain_multiplier(Decimal("100")))},
        "innerRadiusDp": float(inner_radius(Decimal("28"))),
        "densityPx": {name: {"cancel": px(CANCEL_DP, d), "height": px(H, d), "idleWidth": px(IDLE_W, d), "openWidth": px(OPEN_W, d), "barMin": px(MIN_BAR_DP, d), "barMax": px(MAX_BAR_DP, d)} for name, d in DENSITIES.items()},
    }
    print(json.dumps(report, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
