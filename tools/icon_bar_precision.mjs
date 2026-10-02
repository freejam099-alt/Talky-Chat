function rate(speed) {
  return 1.6 - (Math.max(0, Math.min(100, speed)) / 100) * 1.2;
}

function overshoot(bounce, tuned) {
  const value = 1 + (Math.max(0, Math.min(100, bounce)) / 100) * (tuned - 1) * 2;
  return Number(value.toFixed(3));
}

function stretch(fromP, fromS, toP, toS, dilate) {
  const start = Math.min(fromP, toP);
  const end = Math.max(fromP + fromS, toP + toS);
  const grow = Math.max(0, Math.min(1, dilate / 100));
  return {
    p: Number((toP + (start - toP) * grow).toFixed(6)),
    s: Number((toS + (end - start - toS) * grow).toFixed(6)),
  };
}

const slot = 38;
const pad = 7;
const gap = 4;
const count = 5;
const barWidth = 2 * pad + count * slot + (count - 1) * gap;
const barHeight = 2 * pad + slot;
const positions = Array.from({ length: count }, (_, i) => pad + i * (slot + gap));

const result = {
  bar: { widthDp: barWidth, heightDp: barHeight, positionsDp: positions },
  rate: Object.fromEntries([0, 50, 100].map(x => [String(x), Number(rate(x).toFixed(6))])),
  overshoot: {
    move: Object.fromEntries([0, 50, 100].map(x => [String(x), overshoot(x, 1.28)])),
    size: Object.fromEntries([0, 50, 100].map(x => [String(x), overshoot(x, 1.34)])),
  },
  stretch: {
    '0_to_4_dilate_0': stretch(positions[0], slot, positions[4], slot, 0),
    '0_to_4_dilate_50': stretch(positions[0], slot, positions[4], slot, 50),
    '0_to_4_dilate_100': stretch(positions[0], slot, positions[4], slot, 100),
  },
  durationsMs: Object.fromEntries([0, 50, 100].map(x => [String(x), {
    stretch: Math.round(150 * rate(x)),
    settle: Math.round(420 * rate(x)),
  }])),
  androidDensityExamples: Object.fromEntries([1, 2, 3, 4].map(d => [String(d), {
    widthPx: Math.round(barWidth * d),
    heightPx: Math.round(barHeight * d),
  }])),
};

console.log(JSON.stringify(result, null, 2));
