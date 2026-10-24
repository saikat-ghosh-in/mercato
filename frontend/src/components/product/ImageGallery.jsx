import { useState, useRef } from "react";
import { FiChevronLeft, FiChevronRight, FiImage } from "react-icons/fi";
export default function ImageGallery({ images = [], productName }) {
  const [selected, setSelected] = useState(0);
  const [failed, setFailed] = useState({});
  const start = useRef(null);
  const choose = index => { if (images.length) setSelected((index + images.length) % images.length); };
  return <div className="gallery">
    {images.length > 1 && <div className="gallery-thumbnails" aria-label="Product photographs">{images.map((src, index) => <button key={src + index} aria-label={"View image " + (index + 1)} aria-pressed={selected === index} onClick={() => choose(index)}><img src={src} alt="" width="80" height="100" /></button>)}</div>}
    <div className="gallery-stage" tabIndex={images.length > 1 ? 0 : undefined} aria-label="Product gallery" onKeyDown={e => { if (e.key === "ArrowRight") { e.preventDefault(); choose(selected + 1); } if (e.key === "ArrowLeft") { e.preventDefault(); choose(selected - 1); } }} onTouchStart={e => { start.current = e.touches.length === 1 ? e.touches[0].clientX : null; }} onTouchEnd={e => { const distance = start.current == null ? 0 : start.current - e.changedTouches[0].clientX; if (Math.abs(distance) > 50 && images.length > 1) choose(selected + (distance > 0 ? 1 : -1)); start.current = null; }}>
      {images.map((src, index) => <div key={src + index} className="gallery-image" hidden={index !== selected}>{failed[src] ? <div className="image-placeholder"><FiImage />Image unavailable</div> : <img src={src} alt={productName + ", view " + (index + 1)} width="900" height="1100" onError={() => setFailed(s => ({ ...s, [src]: true }))} />}</div>)}
      {!images.length && <div className="image-placeholder"><FiImage />Image unavailable</div>}
      {images.length > 1 && <div className="gallery-controls"><span aria-live="polite">{selected + 1} / {images.length}</span><button className="icon-button" aria-label="Previous image" onClick={() => choose(selected - 1)}><FiChevronLeft /></button><button className="icon-button" aria-label="Next image" onClick={() => choose(selected + 1)}><FiChevronRight /></button></div>}
    </div>
  </div>;
}
