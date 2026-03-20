import { useRef, useState } from 'react';

export default function ImageUploader({ onImageLoad }) {
  const fileInputRef   = useRef(null);
  const cameraInputRef = useRef(null);
  const [dragging, setDragging]   = useState(false);

  const processFile = (file) => {
    if (!file || !file.type.startsWith('image/')) return;
    onImageLoad(URL.createObjectURL(file));
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setDragging(false);
    processFile(e.dataTransfer.files[0]);
  };

  return (
    <div className="uploader-container">
      <div
        className={`drop-zone ${dragging ? 'dragging' : ''}`}
        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        onDrop={handleDrop}
        onClick={() => fileInputRef.current.click()}
      >
        <div className="drop-zone-inner">
          <div className="upload-icon">◈</div>
          <p className="drop-title">Drop image to scan</p>
          <p className="drop-sub">or tap to browse your gallery</p>
        </div>
      </div>

      <div className="upload-actions">
        <button className="btn-action" onClick={() => fileInputRef.current.click()}>
          <span>▤</span> Gallery
        </button>
        <button className="btn-action btn-camera" onClick={() => cameraInputRef.current.click()}>
          <span>◎</span> Camera
        </button>
      </div>

      <input ref={fileInputRef}   type="file" accept="image/*"           style={{ display:'none' }} onChange={e => processFile(e.target.files[0])} />
      <input ref={cameraInputRef} type="file" accept="image/*" capture="environment" style={{ display:'none' }} onChange={e => processFile(e.target.files[0])} />
    </div>
  );
}
