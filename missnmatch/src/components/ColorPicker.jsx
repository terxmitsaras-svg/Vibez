import { useState } from 'react';
import { hexToRgb, rgbToHsl, getColorName, rgbToHex } from '../utils/colorExtractor';

export default function ColorPicker({ onColorSelect }) {
  const [hex, setHex] = useState('#e07a5f');
  const [error, setError] = useState('');

  const handleHexChange = (val) => {
    setHex(val);
    setError('');
    const clean = val.startsWith('#') ? val : '#' + val;
    if (/^#[0-9a-fA-F]{6}$/.test(clean)) {
      const rgb = hexToRgb(clean);
      if (rgb) {
        const hsl = rgbToHsl(rgb.r, rgb.g, rgb.b);
        onColorSelect({
          hex: clean,
          rgb,
          hsl,
          name: getColorName(rgb.r, rgb.g, rgb.b),
        });
      }
    } else if (val.length >= 7) {
      setError('Enter a valid HEX (e.g. #ff6b6b)');
    }
  };

  const handleNativeChange = (e) => {
    const val = e.target.value;
    setHex(val);
    setError('');
    const rgb = hexToRgb(val);
    if (rgb) {
      const hsl = rgbToHsl(rgb.r, rgb.g, rgb.b);
      onColorSelect({
        hex: val,
        rgb,
        hsl,
        name: getColorName(rgb.r, rgb.g, rgb.b),
      });
    }
  };

  // Preset swatches
  const presets = [
    '#e07a5f', '#3d405b', '#81b29a', '#f2cc8f',
    '#ff6b9d', '#c44d58', '#4ecdc4', '#f7dc6f',
    '#6c5ce7', '#a29bfe', '#fd79a8', '#00b894',
    '#e17055', '#74b9ff', '#fab1d3', '#55efc4',
  ];

  return (
    <div className="color-picker">
      <h3 className="picker-title">Or pick a color manually</h3>

      <div className="picker-row">
        <input
          type="color"
          className="native-picker"
          value={hex.startsWith('#') && hex.length === 7 ? hex : '#e07a5f'}
          onChange={handleNativeChange}
          title="Open color picker"
        />
        <input
          type="text"
          className="hex-input"
          value={hex}
          onChange={(e) => handleHexChange(e.target.value)}
          placeholder="#rrggbb"
          maxLength={7}
          spellCheck={false}
        />
      </div>
      {error && <p className="picker-error">{error}</p>}

      <div className="presets-grid">
        {presets.map((p) => (
          <button
            key={p}
            className="preset-dot"
            style={{ backgroundColor: p }}
            onClick={() => handleHexChange(p)}
            title={p}
          />
        ))}
      </div>
    </div>
  );
}
