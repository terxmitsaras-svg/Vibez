import { hslToHex, hslToRgb, rgbToHsl, getColorName, rgbToHex } from './colorExtractor';

/**
 * Generates color palettes from a base color using color theory
 */

function makeColor(h, s, l) {
  // Clamp values
  h = ((h % 360) + 360) % 360;
  s = Math.min(100, Math.max(0, s));
  l = Math.min(95, Math.max(5, l));
  const hex = hslToHex(h, s, l);
  const rgb = hslToRgb(h, s, l);
  return {
    hex,
    rgb,
    hsl: { h, s, l },
    name: getColorName(rgb.r, rgb.g, rgb.b),
  };
}

export function generatePalettes(baseColor) {
  const { h, s, l } = baseColor.hsl;

  return {
    complementary: generateComplementary(h, s, l),
    analogous: generateAnalogous(h, s, l),
    triadic: generateTriadic(h, s, l),
    splitComplementary: generateSplitComplementary(h, s, l),
    monochromatic: generateMonochromatic(h, s, l),
    tetradic: generateTetradic(h, s, l),
  };
}

function generateComplementary(h, s, l) {
  return {
    name: 'Complementary',
    description: 'Opposite colors on the wheel — bold, high contrast',
    colors: [
      makeColor(h, s, l),
      makeColor(h, s, Math.max(l - 15, 10)),
      makeColor(h + 180, s, l),
      makeColor(h + 180, s, Math.min(l + 15, 90)),
      makeColor(h, 20, 90),
    ],
  };
}

function generateAnalogous(h, s, l) {
  return {
    name: 'Analogous',
    description: 'Neighboring hues — harmonious, serene',
    colors: [
      makeColor(h - 30, s, l),
      makeColor(h - 15, s, Math.min(l + 10, 90)),
      makeColor(h, s, l),
      makeColor(h + 15, s, Math.min(l + 10, 90)),
      makeColor(h + 30, s, l),
    ],
  };
}

function generateTriadic(h, s, l) {
  return {
    name: 'Triadic',
    description: 'Three evenly spaced hues — vibrant, balanced',
    colors: [
      makeColor(h, s, l),
      makeColor(h, s, Math.min(l + 20, 92)),
      makeColor(h + 120, s, l),
      makeColor(h + 120, s, Math.min(l + 20, 92)),
      makeColor(h + 240, s, l),
    ],
  };
}

function generateSplitComplementary(h, s, l) {
  return {
    name: 'Split Complementary',
    description: 'Softer than complementary — sophisticated contrast',
    colors: [
      makeColor(h, s, l),
      makeColor(h + 150, s, l),
      makeColor(h + 150, s, Math.min(l + 15, 90)),
      makeColor(h + 210, s, l),
      makeColor(h + 210, s, Math.min(l + 15, 90)),
    ],
  };
}

function generateMonochromatic(h, s, l) {
  return {
    name: 'Monochromatic',
    description: 'Same hue, varying lightness — elegant, cohesive',
    colors: [
      makeColor(h, s, Math.max(l - 30, 8)),
      makeColor(h, s, Math.max(l - 15, 15)),
      makeColor(h, s, l),
      makeColor(h, Math.max(s - 20, 10), Math.min(l + 18, 88)),
      makeColor(h, Math.max(s - 35, 5), Math.min(l + 35, 95)),
    ],
  };
}

function generateTetradic(h, s, l) {
  return {
    name: 'Tetradic',
    description: 'Four colors — rich, complex, eye-catching',
    colors: [
      makeColor(h, s, l),
      makeColor(h + 90, s, l),
      makeColor(h + 180, s, l),
      makeColor(h + 270, s, l),
      makeColor(h, 15, Math.min(l + 30, 95)),
    ],
  };
}

// Context-specific curated palettes
export function getContextPalettes(baseColor, context) {
  const { h, s, l } = baseColor.hsl;

  if (context === 'home') {
    return getHomePalettes(h, s, l);
  } else {
    return getFashionPalettes(h, s, l);
  }
}

function getHomePalettes(h, s, l) {
  return [
    {
      name: 'Living Room Warmth',
      icon: '🛋️',
      description: 'Cozy neutrals + your accent color',
      colors: [
        makeColor(h, s, l),
        makeColor(30, 25, 82),   // warm white
        makeColor(25, 30, 65),   // linen
        makeColor(20, 15, 42),   // driftwood
        makeColor(h + 10, 18, 30), // deep accent
      ],
    },
    {
      name: 'Bedroom Retreat',
      icon: '🛏️',
      description: 'Soothing tones for restful spaces',
      colors: [
        makeColor(h, Math.max(s - 20, 10), Math.min(l + 20, 88)),
        makeColor(200, 15, 88),  // misty blue-white
        makeColor(h, Math.max(s - 10, 15), l),
        makeColor(35, 20, 75),   // warm cream
        makeColor(h + 15, 12, 25), // deep grounding
      ],
    },
    {
      name: 'Kitchen & Dining',
      icon: '🍽️',
      description: 'Fresh, energizing, appetite-enhancing',
      colors: [
        makeColor(h, s, l),
        makeColor(40, 60, 85),   // warm cream
        makeColor(120, 20, 40),  // herb green
        makeColor(15, 35, 55),   // terracotta
        makeColor(35, 10, 95),   // off-white
      ],
    },
    {
      name: 'Bathroom Spa',
      icon: '🛁',
      description: 'Clean, serene, refreshing',
      colors: [
        makeColor(195, 40, 75),  // spa aqua
        makeColor(h, Math.max(s - 25, 5), Math.min(l + 25, 92)),
        makeColor(0, 0, 97),     // crisp white
        makeColor(35, 15, 80),   // warm stone
        makeColor(180, 20, 35),  // deep teal
      ],
    },
    {
      name: 'Home Office Focus',
      icon: '💼',
      description: 'Productive, grounding, inspiring',
      colors: [
        makeColor(h, s, l),
        makeColor(210, 15, 20),  // navy anchor
        makeColor(35, 20, 90),   // warm cream
        makeColor(120, 12, 45),  // muted sage
        makeColor(h + 180, 20, 65), // soft contrast
      ],
    },
  ];
}

function getFashionPalettes(h, s, l) {
  return [
    {
      name: 'Chic & Minimal',
      icon: '👗',
      description: 'Effortless sophistication, any season',
      colors: [
        makeColor(h, s, l),
        makeColor(0, 0, 10),     // black
        makeColor(0, 0, 97),     // white
        makeColor(25, 15, 75),   // cream
        makeColor(h, Math.max(s - 20, 10), 40),
      ],
    },
    {
      name: 'Street Style',
      icon: '✌️',
      description: 'Bold, urban, expressive',
      colors: [
        makeColor(h, s, l),
        makeColor(h + 180, s, l),
        makeColor(0, 0, 12),
        makeColor(50, 80, 55),   // mustard
        makeColor(h + 90, Math.min(s + 10, 90), l),
      ],
    },
    {
      name: 'Office Ready',
      icon: '👔',
      description: 'Professional with a personal touch',
      colors: [
        makeColor(h, Math.max(s - 15, 10), l),
        makeColor(210, 20, 25),  // navy
        makeColor(0, 0, 92),     // light gray
        makeColor(35, 20, 85),   // ivory
        makeColor(h + 180, 15, 55),
      ],
    },
    {
      name: 'Evening Glam',
      icon: '✨',
      description: 'Dramatic, luxurious, unforgettable',
      colors: [
        makeColor(h, Math.min(s + 15, 95), Math.max(l - 10, 20)),
        makeColor(0, 0, 8),      // near black
        makeColor(45, 85, 60),   // gold
        makeColor(h + 30, s, l),
        makeColor(300, 20, 80),  // blush
      ],
    },
    {
      name: 'Casual Weekend',
      icon: '☀️',
      description: 'Relaxed, fresh, feel-good combos',
      colors: [
        makeColor(h, Math.max(s - 10, 20), Math.min(l + 10, 85)),
        makeColor(200, 40, 75),  // sky blue
        makeColor(120, 20, 70),  // sage
        makeColor(35, 40, 80),   // sandy
        makeColor(h + 30, Math.max(s - 20, 15), 60),
      ],
    },
  ];
}
