import { useState } from 'react';
import ColorSwatch from './ColorSwatch';
import PaletteCard from './PaletteCard';
import { generatePalettes, getContextPalettes } from '../utils/paletteEngine';

const TABS = [
  { id: 'theory', label: 'Color Theory' },
  { id: 'home', label: '🏠 Home Decor' },
  { id: 'fashion', label: '👗 Fashion' },
];

export default function ResultsPanel({ extractedColors, selectedColor, onSelectColor }) {
  const [activeTab, setActiveTab] = useState('theory');

  const palettes = selectedColor ? generatePalettes(selectedColor) : null;
  const contextPalettes = selectedColor ? getContextPalettes(selectedColor, activeTab === 'home' ? 'home' : 'fashion') : null;

  const theoryPalettes = palettes
    ? Object.values(palettes)
    : [];

  return (
    <div className="results-panel">
      {/* Extracted colors */}
      {extractedColors.length > 0 && (
        <section className="section">
          <h2 className="section-title">Colors found in your image</h2>
          <p className="section-sub">Tap a color to generate its matching palettes</p>
          <div className="extracted-swatches">
            {extractedColors.map((color, i) => (
              <ColorSwatch
                key={i}
                color={color}
                size="lg"
                showName={true}
                selected={selectedColor?.hex === color.hex}
                onClick={() => onSelectColor(color)}
              />
            ))}
          </div>
        </section>
      )}

      {selectedColor && (
        <>
          {/* Selected color hero */}
          <section className="selected-hero">
            <div className="selected-swatch-big" style={{ backgroundColor: selectedColor.hex }}>
              <div
                className="selected-swatch-label"
                style={{ color: selectedColor.hsl.l > 55 ? '#1a1a1a' : '#ffffff' }}
              >
                <span className="selected-name">{selectedColor.name}</span>
                <span className="selected-hex">{selectedColor.hex}</span>
                <span className="selected-hsl">
                  HSL {selectedColor.hsl.h}° {selectedColor.hsl.s}% {selectedColor.hsl.l}%
                </span>
              </div>
            </div>
          </section>

          {/* Tab nav */}
          <div className="tab-nav">
            {TABS.map(tab => (
              <button
                key={tab.id}
                className={`tab-btn ${activeTab === tab.id ? 'tab-active' : ''}`}
                onClick={() => setActiveTab(tab.id)}
              >
                {tab.label}
              </button>
            ))}
          </div>

          {/* Palettes */}
          <div className="palettes-list">
            {activeTab === 'theory' && theoryPalettes.map((p, i) => (
              <PaletteCard key={i} palette={p} />
            ))}
            {(activeTab === 'home' || activeTab === 'fashion') && contextPalettes.map((p, i) => (
              <PaletteCard key={i} palette={p} isContext={true} />
            ))}
          </div>
        </>
      )}

      {extractedColors.length === 0 && !selectedColor && (
        <div className="empty-state">
          <div className="empty-icon">🌈</div>
          <p>Upload an image or pick a color<br />to discover your perfect palette</p>
        </div>
      )}
    </div>
  );
}
