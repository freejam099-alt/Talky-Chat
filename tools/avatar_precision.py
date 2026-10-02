#!/usr/bin/env python3
"""Precision checks for the native BotAvatar renderer.

The renderer uses a 100x100 body coordinate space. Android avatar sizes are
specified in dp, while Canvas and graphicsLayer operate in pixels, so this
script records the exact nominal pixel sizes at common Android densities.
"""
from decimal import Decimal, ROUND_HALF_UP

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
SIZES_DP = [16, 28, 36, 40, 56, 64]


def round_px(value: float) -> int:
    return int(Decimal(str(value)).quantize(Decimal("1"), rounding=ROUND_HALF_UP))


def java_hash(text: str) -> int:
    value = 0
    for ch in text:
        value = (31 * value + ord(ch)) & 0xFFFFFFFF
    return value if value < 2**31 else value - 2**32


def stable_seed(java_hash_value: int) -> float:
    mixed = ((java_hash_value & 0xFFFFFFFF) * 0x9E3779B9 + 0x85EBCA6B) & 0xFFFFFFFF
    return mixed / 4294967295.0


if __name__ == "__main__":
    print("Body space: 100 units")
    print("Nominal dp -> px:")
    for density_name, density in DENSITIES.items():
        values = {dp: round_px(dp * density) for dp in SIZES_DP}
        print(density_name, values)

    print("\nStable avatar seeds:")
    for user_id in ["me", "sam", "maya"]:
        h = java_hash(user_id)
        print(f"{user_id:>5}: hash={h:>8}, seed={stable_seed(h):.7f}")
