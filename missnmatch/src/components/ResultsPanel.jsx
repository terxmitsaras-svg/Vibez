import { useState } from 'react';
import ColorSwatch from './ColorSwatch';
import PaletteCard from './PaletteCard';
import { generatePalettes, getContextPalettes } from '../utils/paletteEngine';

const TABS = [
  { id: 'theory', label: 'Color Theory' },
  { id: 'home',   label: '🏠 Home' },
  { id: 'fashion', label: '👗 Fashion' },
];

export default function ResultsPanel({ extractedColors, selectedColor, onSelectColor }) {
  const [activeTab, setActiveTab] = useState('theory');

  const theoryPalettes = selectedColor ? Object.values(generatePalettes(selectedColor)) : [];
  const contextPalettes = selectedColor
    ? getContextPalettes(selectedColor, activeTab === 'home' ? 'home' : 'fashion')
    : [];

  const textColor = selectedColor
    ? (selectedColor.hsl.l > 55 ? '#0a0a14' : '#ffffff')
    : '#ffffff';

  return (
    <div className="results-panel">

      {/* ── Extracted colors ── */}
      {extractedColors.length > 0 && (
        <div className="extracted-section">
          <div className="section-label">
            <div className="section-label-line" />
            <span className="section-label-text">Colors detected</span>
            <div className="section-label-line" />
          </div>
          <div className="extracted-swatches">
            {extractedColors.map((color, i) => (
              <ColorSwatch
                key={i}
                color={color}
                index={i}
                selected={selectedColor?.hex === color.hex}
                onClick={() => onSelectColor(color)}
              />
            ))}
          </div>
        </div>
      )}

      {/* ── Selected color hero ── */}
      {selectedColor && (
        <>
          <div className="selected-hero">
            <div
              className="selected-hero-bg"
              style={{ backgroundColor: selectedColor.hex }}
            >
              <div className="selected-hero-info" style={{ color: textColor }}>
                <span className="selected-name">{selectedColor.name}</span>
                <span className="selected-hex">{selectedColor.hex.toUpperCase()}</span>
                <div className="selected-meta">
                  <span className="selected-meta-chip">H {selectedColor.hsl.h}°</span>
                  <span className="selected-meta-chip">S {selectedColor.hsl.s}%</span>
                  <span className="selected-meta-chip">L {selectedColor.hsl.l}%</span>
                  <span className="selected-meta-chip">
                    rgb({selectedColor.rgb.r},{selectedColor.rgb.g},{selectedColor.rgb.b})
                  </span>
                </div>
              </div>

              {/* HSL mini-bars */}
              <div className="hsl-bars" style={{ color: textColor }}>
                {[
                  { label: 'H', val: selectedColor.hsl.h / 360, bg: 'rgba(255,255,255,0.4)' },
                  { label: 'S', val: selectedColor.hsl.s / 100, bg: 'rgba(255,255,255,0.4)' },
                  { label: 'L', val: selectedColor.hsl.l / 100, bg: 'rgba(255,255,255,0.4)' },
                ].map(({ label, val, bg }) => (
                  <div key={label} className="hsl-bar-row">
                    <span className="hsl-bar-label" style={{ opacity: 0.6 }}>{label}</span>
                    <div className="hsl-bar-track">
                      <div
                        className="hsl-bar-fill"
                        style={{ width: `${val * 100}%`, background: bg }}
                      />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* ── Tab nav ── */}
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

          {/* ── Palettes ── */}
          <div className="palettes-list">
            {activeTab === 'theory' && theoryPalettes.map((p, i) => (
              <PaletteCard key={p.name} palette={p} index={i} />
            ))}
            {(activeTab === 'home' || activeTab === 'fashion') && contextPalettes.map((p, i) => (
              <PaletteCard key={p.name} palette={p} isContext={true} index={i} />
            ))}
          </div>
        </>
      )}

      {/* ── Empty state ── */}
      {extractedColors.length === 0 && !selectedColor && (
        <div className="empty-state">
          <div className="empty-ring">🎨</div>
          <p className="empty-text">
            Upload an image or pick a color<br />to discover your perfect palette
          </p>
        </div>
      )}
    </div>
  );
}
