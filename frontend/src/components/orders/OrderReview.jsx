import { useId, useState } from "react";
import { FiCheck, FiStar } from "react-icons/fi";
import api from "../../backend/api";
import ModalFrame from "../shared/ModalFrame";

const photoTypes = ["image/jpeg", "image/png", "image/webp"];
const mb = 1024 * 1024;

function ReviewForm({ itemName, endpoint, onClose, onComplete, onConflict }) {
  const id = useId();
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState("");
  const [photos, setPhotos] = useState([]);
  const [video, setVideo] = useState(null);
  const [attachmentError, setAttachmentError] = useState("");
  const [submitError, setSubmitError] = useState("");
  const [busy, setBusy] = useState(false);

  function choosePhotos(event) {
    const selected = Array.from(event.target.files);
    event.target.value = "";
    if (photos.length + selected.length > 3) {
      setAttachmentError("You can attach up to 3 photos.");
    } else if (selected.some(file => !photoTypes.includes(file.type) || !file.size || file.size > 3 * mb)) {
      setAttachmentError("Choose JPG, PNG or WebP photos up to 3 MB each.");
    } else {
      setPhotos([...photos, ...selected]);
      setAttachmentError("");
    }
  }

  function chooseVideo(event) {
    const [file] = event.target.files;
    event.target.value = "";
    if (!file) return;
    if (file.type !== "video/mp4" || !file.size || file.size > 10 * mb) {
      setAttachmentError("Choose an MP4 video up to 10 MB.");
    } else {
      setVideo(file);
      setAttachmentError("");
    }
  }

  async function submit(event) {
    event.preventDefault();
    if (!rating || busy) return;
    const form = new FormData();
    form.append("rating", String(rating));
    form.append("text", comment);
    photos.forEach(photo => form.append("photos", photo));
    if (video) form.append("video", video);
    setBusy(true);
    setSubmitError("");
    try {
      await api.post(endpoint, form);
      onComplete();
    } catch (error) {
      if (error.response?.status === 409) onConflict();
      setSubmitError(error.response?.status === 413
        ? "The attachments are too large. Remove a file or choose a smaller one."
        : error.response?.data?.message || "Your review couldn’t be posted. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  return <ModalFrame title="Review item" onClose={() => { if (!busy) onClose(); }}>
    <form onSubmit={submit} className="review-form">
      <h2>Review item</h2>
      <p className="text-sm text-gray-600 break-words">{itemName}</p>
      <fieldset disabled={busy} className="review-form-fields">
        <fieldset>
          <legend className="review-field-label">Your rating</legend>
          <div className="review-star-picker">
            {[1, 2, 3, 4, 5].map(star => (
              <label key={star} className={star <= rating ? "review-star-selected" : ""}>
                <input type="radio" name="rating" className="sr-only" value={star} checked={rating === star}
                  onChange={() => setRating(star)} aria-label={star === 1 ? "1 star" : `${star} stars`} required />
                <FiStar aria-hidden="true" />
              </label>
            ))}
          </div>
        </fieldset>
        <div>
          <label className="review-field-label" htmlFor={id + "-comment"}>Your review <span>(optional)</span></label>
          <textarea id={id + "-comment"} rows={3} maxLength={2000} className="input w-full"
            value={comment} onChange={event => setComment(event.target.value)} />
        </div>
        <div className="review-attachment-fields">
          <div>
            <label className="review-field-label" htmlFor={id + "-photos"}>Photos <span>(optional)</span></label>
            <p id={id + "-photo-help"}>Up to 3 JPG, PNG or WebP files, 3 MB each.</p>
            <input id={id + "-photos"} type="file" accept={photoTypes.join(",")} multiple
              aria-describedby={id + "-photo-help"} disabled={photos.length === 3} onChange={choosePhotos} />
            {photos.length > 0 && <ul>
              {photos.map((photo, index) => <li key={index}>
                <span>{photo.name}</span>
                <button type="button" className="text-link" aria-label={`Remove photo ${index + 1}`}
                  onClick={() => { setPhotos(photos.filter((_, i) => i !== index)); setAttachmentError(""); }}>Remove</button>
              </li>)}
            </ul>}
          </div>
          <div>
            <label className="review-field-label" htmlFor={id + "-video"}>Video <span>(optional)</span></label>
            <p id={id + "-video-help"}>One MP4 file up to 10 MB.</p>
            <input id={id + "-video"} type="file" accept="video/mp4" aria-describedby={id + "-video-help"} onChange={chooseVideo} />
            {video && <ul><li>
              <span>{video.name}</span>
              <button type="button" className="text-link" aria-label="Remove video"
                onClick={() => { setVideo(null); setAttachmentError(""); }}>Remove</button>
            </li></ul>}
          </div>
          {attachmentError && <p className="review-error" role="alert">{attachmentError}</p>}
        </div>
      </fieldset>
      <p className="text-xs text-gray-600">Your review and attachments will be public. Your name won’t appear.</p>
      {submitError && <p className="review-error" role="alert">{submitError}</p>}
      <div className="review-form-actions">
        <button type="button" className="button button-outline" onClick={onClose} disabled={busy}>Close</button>
        <button type="submit" className="button button-dark" disabled={!rating || busy}>
          {busy ? "Posting review…" : "Post review"}
        </button>
      </div>
    </form>
  </ModalFrame>;
}

export default function OrderReview({ status, orderId, line, onComplete, refreshing }) {
  const [open, setOpen] = useState(false);
  if (status === "SUBMITTED") return <span className="review-submitted" role="status"><FiCheck aria-hidden="true" /> Review submitted</span>;
  if (status !== "AVAILABLE") return null;
  const itemName = line.product?.productName || "this item";
  return <>
    <button type="button" className="button button-outline" aria-label={`Review item: ${itemName}`}
      disabled={refreshing} onClick={() => setOpen(true)}>Review item</button>
    {open && <ReviewForm itemName={itemName}
      endpoint={`/user/orders/${encodeURIComponent(orderId)}/lines/${line.orderLineNumber}/review`}
      onClose={() => setOpen(false)} onConflict={onComplete}
      onComplete={() => { setOpen(false); onComplete(); }} />}
  </>;
}
