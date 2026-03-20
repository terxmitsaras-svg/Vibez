import { useState } from 'react';

export default function ColorSwatch({ color, size = 'md', showName = true, selected = false, onClick }) {
  const [copied, setCopied] = useState(false);

  const copy = (e) => {
    e.stopPropagation();
    navigator.clipboard.writeText(color.hex).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    });
  };

  const textColor = color.hsl.l > 55 ? '#1a1a1a' : '#ffffff';

  return (
    <div
      className={`swatch swatch-${size} ${selected ? 'swatch-selected' : ''}`}
      style={{ backgroundColor: color.hex, cursor: onClick ? 'pointer' : 'default' }}
      onClick={onClick}
      title={`${color.name} — ${color.hex}`}
    >
      {showName && (
        <div className="swatch-info" style={{ color: textColor }}>
          <span className="swatch-name">{color.name}</span>
          <button
            className="swatch-copy"
            style={{ color: textColor, borderColor: textColor + '55' }}
            onClick={copy}
          >
            {copied ? '✓' : color.hex}
          </button>
        </div>
      )}
    </div>
  );
}
