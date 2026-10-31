import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { FiArrowLeft, FiArrowRight, FiPackage } from 'react-icons/fi';
import { formatDate } from '../../utils/formatDate';

const trackingLabels={SHIPPED:'Handed off to courier',IN_TRANSIT:'In transit',OUT_FOR_DELIVERY:'Out for delivery',DELIVERED:'Delivered',DELIVERY_ATTEMPTED:'Delivery attempted',EXCEPTION:'Delivery delayed',LOST_IN_TRANSIT:'Lost in transit',RETURNED_TO_SENDER:'Returned to sender'};
const trackingDate=value=>new Intl.DateTimeFormat(undefined,{day:'numeric',month:'short',year:'numeric',hour:'numeric',minute:'2-digit'}).format(new Date(value));

export default function PackageCard({parcel,scope,busy,loading,canRefresh,onRefresh,onCorrect,onDelivered,onStatus}){
  const [details,setDetails]=useState(false);
  const see=useRef(null),back=useRef(null);
  const show=()=>{setDetails(true);window.requestAnimationFrame(()=>back.current?.focus({preventScroll:true}));};
  const hide=()=>{setDetails(false);window.requestAnimationFrame(()=>see.current?.focus({preventScroll:true}));};
  const terminal=['DELIVERED','LOST_IN_TRANSIT','RETURNED_TO_SENDER'].includes(parcel.status);
  const events=[...(parcel.history||[])].filter(e=>e.providerAt&&!Number.isNaN(Date.parse(e.providerAt))).sort((a,b)=>Date.parse(b.providerAt)-Date.parse(a.providerAt));
  const courier=parcel.courierName|| (parcel.provider==='EASYSHIP'?'Easyship sandbox':parcel.provider==='DELHIVERY'?'Delhivery':null);
  return <article className="package-card" data-status={parcel.status}>
    {details?<div className="package-details">
      <div className="package-detail-top"><button ref={back} type="button" className="text-link" onClick={hide}><FiArrowLeft aria-hidden="true"/> Back to package</button><button type="button" className="button button-outline" disabled={!canRefresh||busy||loading||terminal} onClick={onRefresh}>{busy?'Checking…':'Refresh tracking'}</button></div>
      <h3>Tracking updates</h3><p className="package-courier">{courier}{courier?' · ':''}<span className="break-all">{parcel.waybill}</span></p>
      {parcel.manualTracking&&<p className="text-xs text-gray-500">Updated by Mercato. Automatic test updates are paused.</p>}
      {parcel.eta&&!terminal&&<p className="text-sm">Estimated delivery {formatDate(parcel.eta)}</p>}
      <ol aria-label="Tracking timeline" className="package-timeline">{events.length?events.map((event,index)=><li key={`${event.code}:${event.providerAt}:${index}`}><span className="package-timeline-dot" aria-hidden="true"/><div><p>{event.description}</p><time dateTime={event.providerAt}>{trackingDate(event.providerAt)}</time></div></li>):<li><span className="package-timeline-dot" aria-hidden="true"/><div><p>Handed off to courier</p>{parcel.handedOffAt&&<time dateTime={parcel.handedOffAt}>{trackingDate(parcel.handedOffAt)}</time>}<p className="text-xs text-gray-500 mt-2">Waiting for the next courier update.</p></div></li>}</ol>
      {parcel.message&&<p className="text-xs text-gray-600">{parcel.message}</p>}
      {parcel.lastCheckedAt&&<p className="text-xs text-gray-500">Last checked {formatDate(parcel.lastCheckedAt)}</p>}
      {scope==='admin'&&!terminal&&<div className="flex flex-wrap gap-2"><button type="button" className="button button-outline" disabled={busy} onClick={onCorrect}>Correct tracking</button>{onStatus&&<button type="button" className="button button-dark" disabled={busy} onClick={onStatus}>Update tracking status</button>}<button type="button" className="button button-outline" disabled={busy} onClick={onDelivered}>Mark delivered</button></div>}
    </div>:<>
      <div className="package-status-row"><div><h3 className="package-status">{trackingLabels[parcel.status]||'Tracking updates pending'}</h3>{courier&&<p className="package-courier">{courier}</p>}</div><button ref={see} type="button" className="text-link package-see-updates" onClick={show}>See updates <FiArrowRight aria-hidden="true"/></button></div>
      <ul className="package-items">{parcel.items?.map(item=><li key={item.lineNumber}>{item.productId?<Link className="package-item-image" to={`/products/${item.productId}`} aria-label={`View ${item.productName}`}>{item.primaryImageUrl?<img src={item.primaryImageUrl} alt="" width="48" height="56" loading="lazy"/>:<FiPackage aria-hidden="true"/>}</Link>:<span className="package-item-image"><FiPackage aria-hidden="true"/></span>}<div className="min-w-0">{item.productId?<Link className="package-product-name" to={`/products/${item.productId}`}>{item.productName}</Link>:<p className="package-product-name">{item.productName}</p>}<p className="package-quantity">Qty shipped {item.qty}</p></div></li>)}</ul>
    </>}
  </article>;
}
