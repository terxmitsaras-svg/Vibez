/**
 * Extracts dominant colors from an image using Canvas API
 */

export function extractColorsFromImage(imageElement, numColors = 6) {
  const canvas = document.createElement('canvas');
  const ctx = canvas.getContext('2d');

  // Scale down for performance
  const maxSize = 200;
  const scale = Math.min(maxSize / imageElement.width, maxSize / imageElement.height);
  canvas.width = imageElement.width * scale;
  canvas.height = imageElement.height * scale;

  ctx.drawImage(imageElement, 0, 0, canvas.width, canvas.height);

  const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
  const pixels = imageData.data;

  // Sample pixels and collect colors
  const colorMap = new Map();
  const step = 4; // sample every Nth pixel

  for (let i = 0; i < pixels.length; i += 4 * step) {
    const r = pixels[i];
    const g = pixels[i + 1];
    const b = pixels[i + 2];
    const a = pixels[i + 3];

    if (a < 128) continue; // skip transparent

    // Quantize to reduce color space
    const qr = Math.round(r / 16) * 16;
    const qg = Math.round(g / 16) * 16;
    const qb = Math.round(b / 16) * 16;

    const key = `${qr},${qg},${qb}`;
    colorMap.set(key, (colorMap.get(key) || 0) + 1);
  }

  // Sort by frequency and take top colors
  const sorted = Array.from(colorMap.entries())
    .sort((a, b) => b[1] - a[1])
    .slice(0, numColors * 4); // take more for dedup

  // Filter similar colors (min distance)
  const selected = [];
  for (const [key] of sorted) {
    const [r, g, b] = key.split(',').map(Number);
    const rgb = { r, g, b };

    const tooClose = selected.some(c => colorDistance(c, rgb) < 40);
    if (!tooClose) {
      selected.push(rgb);
    }
    if (selected.length >= numColors) break;
  }

  return selected.map(rgb => ({
    rgb,
    hex: rgbToHex(rgb.r, rgb.g, rgb.b),
    hsl: rgbToHsl(rgb.r, rgb.g, rgb.b),
    name: getColorName(rgb.r, rgb.g, rgb.b),
  }));
}

function colorDistance(c1, c2) {
  return Math.sqrt(
    Math.pow(c1.r - c2.r, 2) +
    Math.pow(c1.g - c2.g, 2) +
    Math.pow(c1.b - c2.b, 2)
  );
}

export function rgbToHex(r, g, b) {
  return '#' + [r, g, b].map(v => v.toString(16).padStart(2, '0')).join('');
}

export function hexToRgb(hex) {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  return result ? {
    r: parseInt(result[1], 16),
    g: parseInt(result[2], 16),
    b: parseInt(result[3], 16),
  } : null;
}

export function rgbToHsl(r, g, b) {
  r /= 255; g /= 255; b /= 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b);
  let h, s, l = (max + min) / 2;

  if (max === min) {
    h = s = 0;
  } else {
    const d = max - min;
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
    switch (max) {
      case r: h = ((g - b) / d + (g < b ? 6 : 0)) / 6; break;
      case g: h = ((b - r) / d + 2) / 6; break;
      case b: h = ((r - g) / d + 4) / 6; break;
    }
  }
  return { h: Math.round(h * 360), s: Math.round(s * 100), l: Math.round(l * 100) };
}

export function hslToRgb(h, s, l) {
  h /= 360; s /= 100; l /= 100;
  let r, g, b;

  if (s === 0) {
    r = g = b = l;
  } else {
    const hue2rgb = (p, q, t) => {
      if (t < 0) t += 1;
      if (t > 1) t -= 1;
      if (t < 1/6) return p + (q - p) * 6 * t;
      if (t < 1/2) return q;
      if (t < 2/3) return p + (q - p) * (2/3 - t) * 6;
      return p;
    };
    const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
    const p = 2 * l - q;
    r = hue2rgb(p, q, h + 1/3);
    g = hue2rgb(p, q, h);
    b = hue2rgb(p, q, h - 1/3);
  }

  return {
    r: Math.round(r * 255),
    g: Math.round(g * 255),
    b: Math.round(b * 255),
  };
}

export function hslToHex(h, s, l) {
  const { r, g, b } = hslToRgb(h, s, l);
  return rgbToHex(r, g, b);
}

// Approximate color naming
export function getColorName(r, g, b) {
  const { h, s, l } = rgbToHsl(r, g, b);

  if (l < 10) return 'Noir';
  if (l > 92) return 'Blanc';
  if (s < 12) {
    if (l < 35) return 'Charcoal';
    if (l < 65) return 'Stone Gray';
    return 'Silver';
  }

  if (h < 15 || h >= 345) return s > 50 ? 'Crimson Red' : 'Dusty Rose';
  if (h < 30) return s > 50 ? 'Burnt Orange' : 'Terracotta';
  if (h < 45) return s > 50 ? 'Amber' : 'Warm Beige';
  if (h < 65) return s > 50 ? 'Golden Yellow' : 'Champagne';
  if (h < 80) return s > 50 ? 'Lime' : 'Sage Mist';
  if (h < 150) return s > 50 ? 'Forest Green' : 'Sage Green';
  if (h < 170) return s > 50 ? 'Emerald' : 'Mint';
  if (h < 195) return s > 50 ? 'Teal' : 'Seafoam';
  if (h < 225) return s > 50 ? 'Sky Blue' : 'Powder Blue';
  if (h < 255) return s > 50 ? 'Cobalt Blue' : 'Periwinkle';
  if (h < 285) return s > 50 ? 'Indigo' : 'Lavender';
  if (h < 315) return s > 50 ? 'Violet' : 'Mauve';
  return s > 50 ? 'Fuchsia' : 'Blush';
}
