import { useState } from 'react';
import { hexToRgb, rgbToHsl, getColorName } from '../utils/colorExtractor';

const PRESETS = [
  '#e07a5f','#c44d58','#ff6b9d','#f472b6',
  '#f2cc8f','#fbbf24','#84cc16','#22c55e',
  '#4ecdc4','#38bdf8','#6c5ce7','#a78bfa',
  '#3d405b','#64748b','#d1d5db','#ffffff',
];

export default function ColorPicker({ onColorSelect }) {
  const [hex, setHex]       = useState('#a78bfa');
  const [active, setActive] = useState('#a78bfa');
  const [error, setError]   = useState('');

  const apply = (val) => {
    const clean = val.startsWith('#') ? val : '#' + val;
    if (/^#[0-9a-fA-F]{6}$/.test(clean)) {
      const rgb = hexToRgb(clean);
      if (!rgb) return;
      const hsl = rgbToHsl(rgb.r, rgb.g, rgb.b);
      setActive(clean);
      setError('');
      onColorSelect({ hex: clean, rgb, hsl, name: getColorName(rgb.r, rgb.g, rgb.b) });
    } else if (val.length >= 7) {
      setError('Enter a valid HEX (e.g. #ff6b6b)');
    }
  };

  const handleHexInput = (val) => {
    setHex(val);
    setError('');
    apply(val);
  };

  const handleNative = (e) => {
    setHex(e.target.value);
    apply(e.target.value);
  };

  return (
    <div className="color-picker">
      <p className="picker-title">Pick a color</p>

      <div className="picker-row">
        <input
          type="color"
          className="native-picker"
          value={active.startsWith('#') && active.length === 7 ? active : '#a78bfa'}
          onChange={handleNative}
          title="Open color picker"
        />
        <input
          type="text"
          className="hex-input"
          value={hex}
          onChange={(e) => handleHexInput(e.target.value)}
          placeholder="#rrggbb"
          maxLength={7}
          spellCheck={false}
        />
      </div>

      {error && <p className="picker-error">{error}</p>}

      <p className="presets-label">Quick picks</p>
      <div className="presets-grid">
        {PRESETS.map((p) => (
          <button
            key={p}
            className={`preset-dot ${active === p ? 'preset-active' : ''}`}
            style={{ backgroundColor: p }}
            onClick={() => { setHex(p); apply(p); }}
            title={p}
          />
        ))}
      </div>
    </div>
  );
}
