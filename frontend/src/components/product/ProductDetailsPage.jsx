import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import { FiArrowRight } from "react-icons/fi";
import { setProductToDisplay } from "../../reduxStore/actions/productActions";
import { formatCurrency } from "../../utils/formatCurrency";
import ProductCard from "./ProductCard";
import AddToCartControl from "../shared/AddToCartControl";
import WishlistToggle from "../shared/WishlistToggle";
import api from "../../backend/api";
import ImageGallery from "./ImageGallery";

function ProductContent({ productId }) {
  const dispatch = useDispatch();
  const product = useSelector(s => s.products.byId?.[productId]);
  const cartItem = useSelector(s => s.cart.cartItems.find(i => i.productId === productId));
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [related, setRelated] = useState([]);
  useEffect(() => {
    const controller = new AbortController();
    dispatch(setProductToDisplay(productId, controller.signal)).catch(err => { if (!controller.signal.aborted) setError(err?.response?.status === 404 ? "This product is no longer available." : "We couldn’t refresh this product. Please try again."); });
    return () => controller.abort();
  }, [productId, dispatch, attempt]);
  useEffect(() => {
    if (!product?.categoryName) return;
    const controller = new AbortController();
    api.get("/public/products", { params: { category: product.categoryName, pageSize: 4 }, signal: controller.signal })
      .then(({ data }) => { if (!controller.signal.aborted) setRelated((data.content ?? []).filter(p => p.productId !== productId).slice(0, 3)); }).catch(() => {});
    return () => controller.abort();
  }, [productId, product?.categoryName]);
  const retry = () => { setError(""); setAttempt(a => a + 1); };
  if (!product) return <div className="site-container">{error ? <div className="state-panel" role="alert"><h1>{error}</h1><button className="button button-dark" onClick={retry}>Try again</button><Link className="text-link" to="/products">Back to shopping</Link></div> : <div className="pdp-layout" role="status" aria-label="Loading product"><div className="card-skeleton"><div /></div><div className="route-loading"><div /><div /></div></div>}</div>;
  const inStock = product.availableQty > 0 || (cartItem?.quantity > 0 && !cartItem?.outOfStock);
  const specs = [["Brand", product.brand], ["Product code", product.productCode || product.productId], ["Category", product.categoryName], ["Sold by", product.sellerName], ...(product.specifications ?? []).map(s => [s.label, s.value])].filter(([,value]) => value);
  return <div className="site-container">
    {error && <div className="inline-error" role="alert">{error}<button onClick={retry}>Try again</button></div>}
    <div className="pdp-layout">
      <ImageGallery images={product.allImages?.length ? product.allImages : [product.primaryImageUrl].filter(Boolean)} productName={product.productName} />
      <section className="pdp-info" aria-label="Product information">
        {product.brand && <p className="eyebrow">{product.brand}</p>}
        <h1>{product.productName}</h1><Link className="pdp-subtitle" to={"/products?category=" + encodeURIComponent(product.categoryName)}>{product.categoryName}</Link>
        <p className="product-price"><strong>{formatCurrency(product.sellingPrice)}</strong>{product.discountPercent > 0 && <><del>{formatCurrency(product.retailPrice)}</del><span>{product.discountPercent}% off</span></>}</p><p className="tax-note">Inclusive of applicable taxes</p>
        <p className="pdp-availability">{inStock ? "In stock" : "Currently sold out"}</p>
        <AddToCartControl product={product} className="w-full" />
        <WishlistToggle productId={productId} />
        {product.highlights?.length > 0 && <ul className="pdp-highlights">{product.highlights.map((text, i) => <li key={i}>{text}</li>)}</ul>}
        <p className="pdp-description">{product.description}</p>
        <details className="product-disclosure" open><summary>Product details</summary><dl className="product-specs">{specs.map(([label, value], i) => <div key={label + i}><dt>{label}</dt><dd>{value}</dd></div>)}</dl></details>
        {[[product.materials, "Materials"], [product.care, "Care instructions"], [product.warranty, "Warranty"], [product.packageContents, "What's in the box"], [product.deliveryReturns, "Delivery & returns"]].filter(([text]) => text?.trim()).map(([text,title]) => <details className="product-disclosure" key={title}><summary>{title}</summary><p>{text}</p></details>)}
        {!product.deliveryReturns && <p className="tax-note mt-6">Delivery charges are shown in your bag. <Link to="/help" className="text-link">Shopping help</Link></p>}
      </section>
    </div>
    {related.length > 0 && <section className="pdp-related"><div className="section-heading"><h2>You might also like</h2><Link className="text-link" to={"/products?category=" + encodeURIComponent(product.categoryName)}>See all <FiArrowRight /></Link></div><div className="product-grid">{related.map(p => <ProductCard product={p} key={p.productId} />)}</div></section>}
  </div>;
}
export default function ProductDetailsPage() {
  const { productId } = useParams();
  return <ProductContent key={productId} productId={productId} />;
}
