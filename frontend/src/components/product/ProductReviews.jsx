import { FiStar } from "react-icons/fi";

const reviewDate = new Intl.DateTimeFormat(undefined, { day: "numeric", month: "short", year: "numeric" });

function Stars({ rating }) {
  return <span className="review-stars" aria-hidden="true">
    {[1, 2, 3, 4, 5].map(star => <FiStar key={star} className={star <= Math.round(rating) ? "review-star-selected" : ""} />)}
  </span>;
}

export default function ProductReviews({ resource }) {
  const { result, loading, failed, retry, loadMore } = resource;
  return <section id="product-reviews" className="pdp-reviews" aria-labelledby="product-reviews-heading">
    <div className="section-heading">
      <h2 id="product-reviews-heading">Customer reviews</h2>
    </div>
    {result?.count > 0 && <p className="review-average">
      <Stars rating={result.average} />
      <strong>{result.average.toFixed(1)} out of 5</strong>
      <span>· {result.count} {result.count === 1 ? "rating" : "ratings"}</span>
    </p>}
    {result?.count === 0 && <p className="text-gray-600">No reviews yet. Customers can leave a review after delivery.</p>}
    {result?.reviews.map(review => <article key={review.id} className="pdp-review">
      <header>
        <span className="review-stars" role="img" aria-label={`${review.rating} out of 5 stars`}><Stars rating={review.rating} /></span>
        <strong>Verified buyer</strong>
        <time dateTime={review.createdAt}>{reviewDate.format(new Date(review.createdAt))}</time>
      </header>
      {review.text && <p className="review-text">{review.text}</p>}
      {(review.photos.length > 0 || review.video) && <div className="review-media">
        {review.photos.map((photo, index) => <a key={photo} href={photo} target="_blank" rel="noreferrer"
          aria-label={`Open review photo ${index + 1} in a new tab`}>
          <img src={photo} loading="lazy" alt={`Customer review photo ${index + 1}`} />
        </a>)}
        {review.video && <video controls playsInline preload="none" src={review.video} aria-label="Customer review video" />}
      </div>}
    </article>)}
    {failed && <p className="text-sm text-gray-600" role="status">Reviews couldn’t load. <button type="button" className="text-link" onClick={retry}>Retry</button></p>}
    {loading && !result && <p className="text-sm text-gray-600" role="status">Loading reviews…</p>}
    {result?.hasMore && !failed && <button type="button" className="button button-outline" onClick={loadMore} disabled={loading}>
      {loading ? "Loading more reviews…" : "Load more reviews"}
    </button>}
  </section>;
}
