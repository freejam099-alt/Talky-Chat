#!/usr/bin/env node
/* Precision calculations mirroring reorder_list_precision.py for JS/CSS values. */

const PILL = 196;
const ROW_H = 44;
const STEP = 50;
const STAGE_W = 262;
const STAGE_H = 200;
const ARR_INSET = 6;
const AVATAR = 32;
const AVATAR_GAP = 11;
const CORNER = 22;
const GOO_BLUR = 2.6;
const GOO_CUT = 28;
const GIVE = 50;
const LEAN = 18;
const STIFFNESS = 220;
const DAMPING = 14;
const MASS = 0.5;
const DENSITIES = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 };

const px = (v, d) => Math.round(v * d);
const dampingRatio = (k, c, m) => c / (2 * Math.sqrt(k * m));
const firstOvershootFraction = (z) => {
  if (z < 0 || z >= 1) return 0;
  return Math.exp((-Math.PI * z) / Math.sqrt(1 - z * z));
};

const z = dampingRatio(STIFFNESS, DAMPING, MASS);
const overshoot = firstOvershootFraction(z);
const report = {
  geometryDp: {
    stage: [STAGE_W, STAGE_H], pill: PILL, rowHeight: ROW_H, step: STEP,
    corner: CORNER, arrInset: ARR_INSET, avatar: AVATAR, avatarGap: AVATAR_GAP,
    gooBlur: GOO_BLUR, gooCut: GOO_CUT,
  },
  deformation: {
    give: GIVE, lean: LEAN,
    maxScaleYAtVelocity3: 1 + 3 * (GIVE / 100) * 0.12,
    minScaleXAtVelocity3: 1 / (1 + 3 * (GIVE / 100) * 0.12),
    maxSkewY: 3 * (LEAN / 100) * 2.6,
  },
  spring: {
    stiffness: STIFFNESS, damping: DAMPING, mass: MASS,
    dampingRatio: z, firstOvershootFraction: overshoot,
  },
  densityPx: Object.fromEntries(
    Object.entries(DENSITIES).map(([name, d]) => [name, {
      stage: [px(STAGE_W, d), px(STAGE_H, d)], pill: px(PILL, d), rowHeight: px(ROW_H, d),
      step: px(STEP, d), avatar: px(AVATAR, d), inset: px(ARR_INSET, d),
    }]),
  ),
};
console.log(JSON.stringify(report, null, 2));
