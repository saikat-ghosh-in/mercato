import { Link } from "react-router-dom";
import { useState } from "react";
import { FiImage } from "react-icons/fi";
import { formatCurrency } from "../../utils/formatCurrency";
import AddToCartControl from "../shared/AddToCartControl";
export default function ProductCard({ product, showPurchase = false }) {
  const [brokenImage, setBrokenImage] = useState(false);
  return <article className="product-card">
    <div className="product-image-wrap">
      <Link to={"/products/" + product.productId} tabIndex={-1} aria-hidden="true" className="product-image-link">
        {product.primaryImageUrl && !brokenImage ? <img src={product.primaryImageUrl} alt="" loading="lazy" width="600" height="600" onError={() => setBrokenImage(true)} /> : <div className="image-placeholder"><FiImage /><span>Image unavailable</span></div>}
      </Link>
      {product.availableQty <= 0 && <span className="stock-badge">Sold out</span>}
    </div>
    <div className="product-copy"><h3><Link to={"/products/" + product.productId}>{product.productName}</Link></h3><p className="product-category">{product.categoryName}</p>
      <p className="product-price"><strong>{formatCurrency(product.sellingPrice)}</strong>{Number(product.discountPercent) > 0 && <><del>{formatCurrency(product.retailPrice)}</del><span>{Math.round(product.discountPercent)}% off</span></>}</p>
      {showPurchase && <AddToCartControl product={product} compact showOnlyQty className="card-purchase" />}
    </div>
  </article>;
}
export const CardSkeleton = () => <div className="card-skeleton" aria-hidden="true"><div /><span /><span /><span /></div>;
