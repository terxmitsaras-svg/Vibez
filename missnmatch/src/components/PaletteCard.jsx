import { useState } from 'react';

export default function PaletteCard({ palette, isContext = false, index = 0 }) {
  const [copiedIdx, setCopiedIdx] = useState(null);
  const [copiedAll, setCopiedAll] = useState(false);

  const copyStrip = (e, color, i) => {
    e.stopPropagation();
    navigator.clipboard.writeText(color.hex).then(() => {
      setCopiedIdx(i);
      setTimeout(() => setCopiedIdx(null), 1000);
    });
  };

  const copyAll = () => {
    const hexList = palette.colors.map(c => c.hex).join('  ');
    navigator.clipboard.writeText(hexList).then(() => {
      setCopiedAll(true);
      setTimeout(() => setCopiedAll(false), 1600);
    });
  };

  return (
    <div
      className="palette-card"
      style={{
        '--anim-dur': '0.5s',
        '--anim-delay': `${index * 0.07}s`,
      }}
    >
      <div className="palette-card-header">
        <div className="palette-card-title-row">
          {isContext && <span className="palette-ctx-icon">{palette.icon}</span>}
          <div>
            <div className="palette-card-name">{palette.name}</div>
            <div className="palette-card-desc">{palette.description}</div>
          </div>
        </div>
        <button
          className={`palette-copy-all ${copiedAll ? 'copied' : ''}`}
          onClick={copyAll}
        >
          {copiedAll ? '✓ copied' : 'copy all'}
        </button>
      </div>

      <div className="palette-strips">
        {palette.colors.map((color, i) => (
          <div
            key={i}
            className="palette-strip-item"
            style={{
              backgroundColor: color.hex,
              '--s-dur': '0.55s',
              '--s-delay': `${index * 0.07 + i * 0.05}s`,
            }}
            onClick={(e) => copyStrip(e, color, i)}
            title={`Copy ${color.hex}`}
          >
            {copiedIdx === i && (
              <div className="strip-copied-flash">✓</div>
            )}
            <div className="palette-strip-info">
              <div className="palette-strip-name">{color.name}</div>
              <div className="palette-strip-hex">{color.hex}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
