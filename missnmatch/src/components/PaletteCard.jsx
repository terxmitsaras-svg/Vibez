import { useState } from 'react';
import ColorSwatch from './ColorSwatch';

export default function PaletteCard({ palette, isContext = false }) {
  const [expanded, setExpanded] = useState(false);
  const [copiedAll, setCopiedAll] = useState(false);

  const copyAll = () => {
    const hexList = palette.colors.map(c => c.hex).join(', ');
    navigator.clipboard.writeText(hexList).then(() => {
      setCopiedAll(true);
      setTimeout(() => setCopiedAll(false), 1800);
    });
  };

  return (
    <div className={`palette-card ${isContext ? 'palette-card-context' : ''}`}>
      <div className="palette-header" onClick={() => setExpanded(!expanded)}>
        <div className="palette-title-row">
          {isContext && <span className="palette-icon">{palette.icon}</span>}
          <div>
            <h3 className="palette-name">{palette.name}</h3>
            <p className="palette-desc">{palette.description}</p>
          </div>
        </div>
        <span className="palette-chevron">{expanded ? '▲' : '▼'}</span>
      </div>

      {/* Color strip preview */}
      <div className="palette-strip">
        {palette.colors.map((color, i) => (
          <div
            key={i}
            className="palette-strip-seg"
            style={{ backgroundColor: color.hex, flex: 1 }}
            title={`${color.name} ${color.hex}`}
          />
        ))}
      </div>

      {expanded && (
        <div className="palette-expanded">
          <div className="palette-swatches">
            {palette.colors.map((color, i) => (
              <ColorSwatch key={i} color={color} size="sm" showName={true} />
            ))}
          </div>
          <button className="btn-copy-all" onClick={copyAll}>
            {copiedAll ? '✓ Copied!' : '📋 Copy all HEX codes'}
          </button>
        </div>
      )}
    </div>
  );
}
