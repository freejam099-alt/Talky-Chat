#!/usr/bin/env node
/* Precision calculations mirroring voice_note_precision.py for the JS/CSS side. */

const H = 56;
const IDLE_W = 216;
const OPEN_W = 316;
const FRAME_W = 336;
const FRAME_H = 156;
const SAMPLE_MS = 70;
const CANCEL_DP = 90;
const MIN_MS = 500;
const MAX_MS = 30000;
const BAR_DP = 3;
const MIN_BAR_DP = 4;
const MAX_BAR_DP = 32;
const DENSITIES = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 };

const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
const innerRadius = (corner) => {
  const r = clamp(corner, 0, H / 2);
  return Math.max(0, r - 6 * Math.min(1, r / 12));
};
const gainMultiplier = (gain) => 0.4 + (clamp(gain, 0, 100) / 100) * 1.2;
const waveWidth = (total, leftExtra, rightExtra, fixedChildren, gaps) =>
  Math.max(0, total - leftExtra - rightExtra - fixedChildren - gaps);
const barGap = (width, bars) => bars <= 1 ? 0 : Math.max(0, (width - bars * BAR_DP) / (bars - 1));
const px = (value, density) => Math.round(value * density);

const bars = 28;
const recWave = waveWidth(OPEN_W, 22, 22, 9 + 38, 24);
const clipWave = waveWidth(OPEN_W, 6, 10, 44 + 38 + 32, 36);
const report = {
  dimensions_dp: { frame: [FRAME_W, FRAME_H], idleWidth: IDLE_W, openWidth: OPEN_W, height: H },
  recording: { sampleMs: SAMPLE_MS, minMs: MIN_MS, maxMs: MAX_MS, cancelDp: CANCEL_DP },
  bars: { count: bars, barWidthDp: BAR_DP, recordWaveWidthDp: recWave, recordGapDp: barGap(recWave, bars), clipWaveWidthDp: clipWave, clipGapDp: barGap(clipWave, bars), minHeightDp: MIN_BAR_DP, maxHeightDp: MAX_BAR_DP },
  gain: { default: gainMultiplier(60), min: gainMultiplier(0), max: gainMultiplier(100) },
  innerRadiusDp: innerRadius(28),
  densityPx: Object.fromEntries(Object.entries(DENSITIES).map(([name, d]) => [name, { cancel: px(CANCEL_DP, d), height: px(H, d), idleWidth: px(IDLE_W, d), openWidth: px(OPEN_W, d), barMin: px(MIN_BAR_DP, d), barMax: px(MAX_BAR_DP, d) }])),
};
console.log(JSON.stringify(report, null, 2));
