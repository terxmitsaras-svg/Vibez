import { useState, useRef, useCallback } from 'react';
import ImageUploader from './components/ImageUploader';
import ColorPicker from './components/ColorPicker';
import ResultsPanel from './components/ResultsPanel';
import { extractColorsFromImage } from './utils/colorExtractor';
import './App.css';

export default function App() {
  const [imageUrl, setImageUrl] = useState(null);
  const [extractedColors, setExtractedColors] = useState([]);
  const [selectedColor, setSelectedColor] = useState(null);
  const [loading, setLoading] = useState(false);
  const [inputMode, setInputMode] = useState('image');

  const handleImageLoad = useCallback((url) => {
    setImageUrl(url);
    setLoading(true);
    setExtractedColors([]);
    setSelectedColor(null);

    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      const colors = extractColorsFromImage(img, 8);
      setExtractedColors(colors);
      if (colors.length > 0) setSelectedColor(colors[0]);
      setLoading(false);
    };
    img.onerror = () => setLoading(false);
    img.src = url;
  }, []);

  const handleColorSelect = useCallback((color) => {
    setSelectedColor(color);
  }, []);

  const handleReset = () => {
    setImageUrl(null);
    setExtractedColors([]);
    setSelectedColor(null);
  };

  return (
    <div className="app">
      <header className="app-header">
        <div className="logo-block">
          <span className="logo-miss">Miss</span>
          <span className="logo-n">N</span>
          <span className="logo-match">MatcH</span>
        </div>
        <p className="tagline">Your personal color stylist — for home &amp; fashion</p>
      </header>

      <main className="app-main">
        <section className="input-section">
          <div className="input-mode-tabs">
            <button
              className={`mode-tab ${inputMode === 'image' ? 'mode-active' : ''}`}
              onClick={() => setInputMode('image')}
            >
              📸 From Image
            </button>
            <button
              className={`mode-tab ${inputMode === 'picker' ? 'mode-active' : ''}`}
              onClick={() => setInputMode('picker')}
            >
              🎨 Pick Color
            </button>
          </div>

          {inputMode === 'image' && (
            <>
              {imageUrl ? (
                <div className="image-preview-container">
                  <img src={imageUrl} alt="Uploaded" className="image-preview" />
                  <button className="btn-reset" onClick={handleReset}>✕ Try another</button>
                  {loading && (
                    <div className="loading-overlay">
                      <div className="spinner" />
                      <span>Extracting colors...</span>
                    </div>
                  )}
                </div>
              ) : (
                <ImageUploader onImageLoad={handleImageLoad} />
              )}
            </>
          )}

          {inputMode === 'picker' && (
            <ColorPicker onColorSelect={handleColorSelect} />
          )}
        </section>

        <ResultsPanel
          extractedColors={extractedColors}
          selectedColor={selectedColor}
          onSelectColor={handleColorSelect}
        />
      </main>

      <footer className="app-footer">
        <p>MissNMatcH — Find your perfect palette ✦</p>
      </footer>
    </div>
  );
}
