#!/usr/bin/env python3
"""Precision calculations for the native ReorderList Compose port.

The Bencho source is authored in CSS pixels. The Android renderer keeps the same
logical values in dp and only converts to physical px at the interaction/drawing
boundary. This script calculates the geometry and the liquid spring constants used
by the Kotlin port so they can be checked independently.
"""
from decimal import Decimal, ROUND_HALF_UP
import json
import math

PILL = Decimal("196")
ROW_H = Decimal("44")
STEP = Decimal("50")
STAGE_W = Decimal("262")
STAGE_H = Decimal("200")
ARR_INSET = Decimal("6")
AVATAR = Decimal("32")
AVATAR_GAP = Decimal("11")
CORNER = Decimal("22")
GOO_BLUR = Decimal("2.6")
GOO_CUT = Decimal("28")
GIVE = Decimal("50")
LEAN = Decimal("18")
STIFFNESS = Decimal("220")
DAMPING = Decimal("14")
MASS = Decimal("0.5")

DENSITIES = {
    "mdpi": Decimal("1"), "hdpi": Decimal("1.5"), "xhdpi": Decimal("2"),
    "xxhdpi": Decimal("3"), "xxxhdpi": Decimal("4"),
}


def px(v: Decimal, d: Decimal) -> int:
    return int((v * d).quantize(Decimal("1"), rounding=ROUND_HALF_UP))


def zeta(k: Decimal, c: Decimal, m: Decimal) -> Decimal:
    return c / (Decimal(2) * Decimal(math.sqrt(float(k * m))))


def first_overshoot(z: Decimal) -> Decimal:
    zf = float(z)
    if not 0 <= zf < 1:
        return Decimal("0")
    return Decimal(str(math.exp(-math.pi * zf / math.sqrt(1 - zf * zf))))


def main() -> None:
    z = zeta(STIFFNESS, DAMPING, MASS)
    overshoot = first_overshoot(z)
    report = {
        "geometryDp": {
            "stage": [int(STAGE_W), int(STAGE_H)],
            "pill": int(PILL), "rowHeight": int(ROW_H), "step": int(STEP),
            "corner": int(CORNER), "arrInset": int(ARR_INSET),
            "avatar": int(AVATAR), "avatarGap": int(AVATAR_GAP),
            "gooBlur": float(GOO_BLUR), "gooCut": float(GOO_CUT),
        },
        "deformation": {
            "give": float(GIVE), "lean": float(LEAN),
            "maxScaleYAtVelocity3": float(Decimal(1) + Decimal(3) * (GIVE / Decimal(100)) * Decimal("0.12")),
            "minScaleXAtVelocity3": float(Decimal(1) / (Decimal(1) + Decimal(3) * (GIVE / Decimal(100)) * Decimal("0.12"))),
            "maxSkewY": float(Decimal(3) * (LEAN / Decimal(100)) * Decimal("2.6")),
        },
        "spring": {
            "stiffness": float(STIFFNESS), "damping": float(DAMPING), "mass": float(MASS),
            "dampingRatio": float(z), "firstOvershootFraction": float(overshoot),
        },
        "densityPx": {
            name: {
                "stage": [px(STAGE_W, d), px(STAGE_H, d)],
                "pill": px(PILL, d), "rowHeight": px(ROW_H, d), "step": px(STEP, d),
                "avatar": px(AVATAR, d), "inset": px(ARR_INSET, d),
            }
            for name, d in DENSITIES.items()
        },
    }
    print(json.dumps(report, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
