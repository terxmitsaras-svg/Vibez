import { useState } from 'react';

export default function ColorSwatch({ color, index = 0, selected = false, onClick }) {
  const [copied, setCopied] = useState(false);

  const handleClick = () => {
    if (onClick) onClick();
  };

  const handleCopy = (e) => {
    e.stopPropagation();
    navigator.clipboard.writeText(color.hex).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1400);
    });
  };

  return (
    <div
      className={`swatch-circle ${selected ? 'swatch-selected' : ''}`}
      style={{ '--delay': `${index * 0.06}s` }}
      onClick={handleClick}
      title={`${color.name} — ${color.hex}`}
    >
      <div
        className="swatch-circle-disc"
        style={{ backgroundColor: color.hex }}
        onClick={handleCopy}
      >
        {copied ? (
          <div className="swatch-copy-tick">✓</div>
        ) : (
          <div className="swatch-copy-tick">⧉</div>
        )}
      </div>
      <span className="swatch-circle-name">{color.name}</span>
      <span className="swatch-circle-hex">{color.hex}</span>
    </div>
  );
}
