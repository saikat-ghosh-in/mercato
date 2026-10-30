import { LoadingLayout } from "../shared/LoadingStates";
import ShipmentPanel, { ShipmentLineAction } from "../orders/ShipmentPanel";
import { Link } from "react-router-dom";
import ActionArrow from "../shared/ActionArrow";
import { useEffect, useState } from "react";
import { MdLocalShipping, MdArrowBack, MdClose, MdSearch } from "react-icons/md";
import InfoHint from "../shared/InfoHint";
import { formatCurrency } from "../../utils/formatCurrency";
import useWorkflowResource, { useWorkflowEpoch } from "../../hooks/useWorkflowResource";
import { actionMaximum } from "../../utils/fulfillment";
import { QuantityCommand, WorkflowDeadlines, WorkflowProgress, WorkflowBadge } from "../orders/WorkflowControls";
import { formatDate } from "../../utils/formatDate";

const SUBTABS = [
  { id: "active", label: "Active" },
  { id: "history", label: "History" },
];

const CardSkeleton = () => <LoadingLayout variant="list" label="Loading content" rows={1} />;

const SectionCard = ({ children, className = "" }) => (
  <div className={`surface overflow-hidden ${className}`}>
    {children}
  </div>
);

const ACTIONS=[['ACCEPT','Accept'],['START_PREPARING','Start preparing'],['MARK_PACKED','Mark as packed'],['CANCEL','Cancel units']];
const workRemaining=lines=>(lines||[]).reduce((sum,line)=>sum+actionMaximum(line,'ACCEPT')+actionMaximum(line,'START_PREPARING')+actionMaximum(line,'MARK_PACKED')+Math.max(0,(line.workflow?.packedQty||0)-(line.shippedQty||0)),0);

const FulfillmentDetail = ({ fulfillmentId, onBack }) => {
  const [shipmentState,setShipmentState]=useState(null);
  const [shipLine,setShipLine]=useState(null);
  const {data:fo, loading, error, refresh:fetchFo, receivedAt, epoch} = useWorkflowResource(`/seller/fulfillment-orders/${encodeURIComponent(fulfillmentId)}`);
  if (loading && !fo) return <div className="space-y-4"><CardSkeleton /><CardSkeleton /></div>;
  if (!fo) return <div className="state-panel" role="alert"><h2>Fulfillment details couldn’t load.</h2><p>Check your connection and try again.</p><button className="button button-dark" onClick={fetchFo}>Try again</button><button className="text-link" onClick={onBack}>Back to fulfillments</button></div>;
  return <div className="fulfillment-workspace space-y-5">
    {error&&<p role="alert" className="text-sm text-red-700">{error} <button className="text-link" onClick={fetchFo}>Retry refresh</button></p>}
    <div className="fulfillment-detail-heading">
      <button onClick={onBack} className="button button-outline"><MdArrowBack aria-hidden="true" size={15}/> Back</button>
      <div className="min-w-0"><p className="fulfillment-eyebrow">Order fulfillment</p><h2 className="fulfillment-order-id">{fo.orderId}</h2><p className="text-xs text-gray-500 mt-1 break-all">{fo.fulfillmentId} · {formatDate(fo.createdAt)}</p></div>
      <button className="button button-outline fulfillment-refresh" onClick={fetchFo} disabled={loading}>{loading?'Refreshing…':'Refresh'}</button>
    </div>
    <div className="fulfillment-overview surface"><WorkflowProgress record={fo}/><p className="text-xs text-gray-500">Update each item as it moves through fulfillment. Quantities are selected for you.</p></div>
    <div className="fulfillment-detail-grid">
      <div className="min-w-0 space-y-5">
        <SectionCard><div className="fulfillment-section-heading"><div><h3>Items to fulfil</h3><p>Accept → Prepare → Pack → Ship</p></div><span className="text-xs text-gray-500">{fo.orderLines?.length||0} items</span></div>
          <div className="divide-y divide-gray-100">{fo.orderLines?.map(line=><article key={`${epoch}:${fulfillmentId}:${line.orderLineNumber}`} className="fulfillment-item">
            <Link to={`/products/${line.product?.productId}`} aria-label={`View ${line.product?.productName || "product"}`} className="fulfillment-product-image">{line.product?.primaryImageUrl?<img src={line.product.primaryImageUrl} alt="" loading="lazy"/>:<MdLocalShipping aria-hidden="true" size={23}/>}</Link>
            <div className="min-w-0"><div className="fulfillment-item-heading"><h4><Link to={`/products/${line.product?.productId}`}>{line.product?.productName||'Product'}</Link></h4><WorkflowBadge record={line}/></div>
              {line.product?.unitPrice!=null&&<p className="text-xs text-gray-500 mt-1">{formatCurrency(line.product.unitPrice)} each</p>}
              <div className="mt-3"><WorkflowProgress record={line} showState={false}/></div>
            </div>
            <div className="fulfillment-item-actions">
              {ACTIONS.map(([action,label])=><QuantityCommand key={action} label={label} maximum={actionMaximum(line,action)} version={fo.workflow?.version} cancellation={action==='CANCEL'} endpoint="/seller/fulfillment/commands" makeBody={(qty,reason)=>({fulfillmentId:line.fulfillmentId||fulfillmentId,orderLineNumber:line.orderLineNumber,action,qty,...(action==='CANCEL'?{reason}:{})})} onComplete={fetchFo} disabled={loading}/>)}
              <ShipmentLineAction data={shipmentState} fulfillmentId={line.fulfillmentId||fulfillmentId} lineNumber={line.orderLineNumber} onOpen={setShipLine} disabled={loading}/>
            </div>
          </article>)}</div>
        </SectionCard>
        <ShipmentPanel scope="seller" recordId={fulfillmentId} version={fo.workflow?.version} onChanged={fetchFo} onData={setShipmentState} lineRequest={shipLine}/>
      </div>
      <aside className="space-y-4 min-w-0">
        <SectionCard><div className="p-5"><div className="flex items-center gap-2 mb-3"><h3 className="text-sm font-semibold">Order timing</h3><InfoHint label="Fulfillment timing">Orders appear after the customer's one-hour cancellation period. Accept available units within 48 hours. Preparation review is a reminder, not an automatic cancellation of prepared items.</InfoHint></div><WorkflowDeadlines workflow={fo.workflow} receivedAt={receivedAt} onRelease={fetchFo}/></div></SectionCard>
        <SectionCard><div className="p-5"><h3 className="fulfillment-eyebrow mb-3">Deliver to</h3><p className="text-sm font-semibold break-words">{fo.customer?.name}</p>{fo.deliveryAddress?<div className="space-y-1 text-sm text-gray-600 mt-2 break-words"><p>{fo.deliveryAddress.recipientName}</p><p>{fo.deliveryAddress.addressLine1}</p>{fo.deliveryAddress.addressLine2&&<p>{fo.deliveryAddress.addressLine2}</p>}<p>{fo.deliveryAddress.city}, {fo.deliveryAddress.state} — {fo.deliveryAddress.pincode}</p><p>{fo.deliveryAddress.recipientPhone}</p></div>:<p className="text-xs text-gray-500 mt-2">No delivery address available.</p>}<p className="text-xs text-gray-500 mt-3 break-all">{fo.customer?.email}</p></div></SectionCard>
        <SectionCard><div className="p-5 space-y-3"><h3 className="text-sm font-semibold">Order options</h3><p className="text-xs text-gray-500">Need to cancel only some units? Use the action beside that item.</p><QuantityCommand key={`${epoch}:${fulfillmentId}:cancel-all`} label="Cancel all remaining" whole cancellation maximum={fo.orderLines?.reduce((total,line)=>total+actionMaximum(line,'CANCEL'),0)||0} version={fo.workflow?.version} endpoint="/seller/fulfillment/cancellations" makeBody={(_qty,reason)=>({orderId:fo.orderId,fulfillmentId,reason})} onComplete={fetchFo} disabled={loading}/></div></SectionCard>
      </aside>
    </div>
  </div>;
};

const FulfillmentCard = ({ fo, onClick }) => {
  const lines=fo.orderLines||[],remaining=workRemaining(lines);
  const counts=lines.map(line=>line.orderedQty??line.quantity);
  const units=counts.length&&counts.every(value=>Number.isSafeInteger(value)&&value>=0)?counts.reduce((sum,value)=>sum+value,0):null;
  return <article className="group surface fulfillment-card w-full text-left">
    <button type="button" aria-label={'View fulfillment '+fo.fulfillmentId} onClick={onClick} className="fulfillment-card-heading w-full text-left"><div className="min-w-0"><p className="fulfillment-eyebrow">{formatDate(fo.createdAt)}</p><h3 className="fulfillment-order-id">{fo.orderId}</h3></div><ActionArrow size={18} className="shrink-0 text-gray-400"/></button>
    <div className="fulfillment-card-products"><div className="fulfillment-card-thumbnails">{lines.slice(0,3).map(line=><Link key={line.orderLineNumber} to={`/products/${line.product?.productId}`} aria-label={`View ${line.product?.productName || "product"}`}>{line.product?.primaryImageUrl?<img src={line.product.primaryImageUrl} alt="" loading="lazy"/>:<MdLocalShipping aria-hidden="true" size={19}/>}</Link>)}</div><div className="min-w-0"><p className="text-sm font-medium">{fo.customer?.name||'Customer'}</p><p className="text-xs text-gray-500 mt-1">{lines.length} {lines.length===1?'item':'items'}{units!==null?` · Qty ${units}`:''}</p></div></div>
    <div className="fulfillment-card-footer"><WorkflowBadge record={fo}/><span className={remaining?'fulfillment-action-needed':'text-xs text-gray-500'}>{remaining?`${remaining} ${remaining===1?'unit needs':'units need'} attention`:'No action needed'}</span></div>
  </article>;
};

const FulfillmentTab = () => {
  const [subtab, setSubtab] = useState("active");
  const epoch = useWorkflowEpoch();
  const active = useWorkflowResource('/seller/open-fulfillment-orders');
  const closed = useWorkflowResource('/seller/closed-fulfillment-orders');
  const metrics = useWorkflowResource('/seller/fulfillment/metrics');
  const activeFulfillments = active.data || [];
  const closedFulfillments = closed.data || [];
  const loading = active.loading || closed.loading;
  const [selection, setSelection] = useState(null);
  const selectedId = selection?.epoch === epoch ? selection.id : null;
  const setSelectedId = id => setSelection(id ? {id,epoch} : null);
  const [search, setSearch] = useState('');
  const refreshActive = active.refresh;
  const refreshClosed = closed.refresh;
  const refreshMetrics = metrics.refresh;
  const refreshLists = () => { refreshActive(); refreshClosed(); refreshMetrics(); };
  useEffect(() => {
    const refresh = () => {
      if (document.visibilityState === 'visible') { refreshActive(); refreshClosed(); refreshMetrics(); }
    };
    window.addEventListener('focus', refresh);
    document.addEventListener('visibilitychange', refresh);
    const interval = window.setInterval(refresh, 60000);
    return () => { window.removeEventListener('focus', refresh); document.removeEventListener('visibilitychange', refresh); window.clearInterval(interval); };
  }, [refreshActive, refreshClosed, refreshMetrics]);

  if (selectedId) return (
    <FulfillmentDetail
      key={`${epoch}:${selectedId}`} fulfillmentId={selectedId}
      onBack={() => { setSelectedId(null); refreshLists(); }}
    />
  );

  const list = subtab === "active" ? activeFulfillments : closedFulfillments;

  const filtered = list.filter(fo =>
    !search ||
    fo.fulfillmentId.toLowerCase().includes(search.toLowerCase()) ||
    fo.orderId.toLowerCase().includes(search.toLowerCase()) ||
    fo.customer?.name?.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3"><h2 className="text-base font-semibold">Fulfillments</h2><button className="button button-outline" onClick={refreshLists} disabled={loading}>Refresh</button></div>
      {(active.error || closed.error) && <p role="alert" className="text-sm text-red-700">{active.error || closed.error}</p>}
      <section className="fulfillment-performance surface" aria-label="Acceptance performance"><div className="flex items-center gap-2"><h3 className="text-sm font-semibold">On-time acceptance</h3><InfoHint label="On-time acceptance">Based on units whose acceptance deadline has passed. Customer cancellations before the deadline are excluded; seller cancellations remain included. {metrics.data?.coverage&&`Period: ${formatDate(metrics.data.coverage.fromInclusive)} to ${formatDate(metrics.data.coverage.toExclusive)}.`}</InfoHint></div>{metrics.data?<p className="text-sm"><strong className="tabular-nums">{metrics.data.acceptance?.onTimeAcceptanceRate==null?'No completed period yet':`${(metrics.data.acceptance.onTimeAcceptanceRate*100).toFixed(1)}%`}</strong><span className="text-gray-500"> · {metrics.data.acceptance?.acceptanceDenominatorQty??0} eligible units · {metrics.data.acceptance?.timeoutCancelledQty??0} delayed cancellations</span></p>:<p className="text-xs text-gray-500">{metrics.error||'Loading performance…'}</p>}</section>
      {/* Subtab switcher */}
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div className="workspace-subtabs">
          {SUBTABS.map(({ id, label }) => (
            <button
              key={id}
              aria-pressed={subtab === id}
              onClick={() => { setSubtab(id); setSearch(""); }}
              className={`control px-4 py-2 rounded-lg text-xs font-semibold transition-[transform,opacity,background-color] cursor-pointer flex items-center gap-1.5 ${subtab === id
                ? "bg-gray-950 text-white"
                : "text-gray-500 hover:text-gray-800 hover:bg-gray-50"
                }`}
            >
              {label}
              {id === "active" && activeFulfillments.length > 0 && (
                <span className={`text-xs font-bold px-1.5 py-0.5 rounded-full ${subtab === "active" ? "bg-white text-gray-900" : "bg-blue-500 text-white"
                  }`}>
                  {activeFulfillments.length}
                </span>
              )}
            </button>
          ))}
        </div>

        {/* Search */}
        <div className="dashboard-search min-w-48">
          <MdSearch size={14} className="text-gray-400 shrink-0" />
          <input
            type="text"
            aria-label="Search by ID or customer..." value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="ID or customer"
            className="text-xs text-gray-700 focus:outline-none w-full"
          />
          {search && (
            <button aria-label="Clear fulfillment search" onClick={() => setSearch("")} className="icon-action text-gray-300 hover:text-gray-500 cursor-pointer">
              <MdClose size={13} />
            </button>
          )}
        </div>
      </div>

      {/* List */}
      {loading && !activeFulfillments.length && !closedFulfillments.length ? (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {[1, 2, 3, 4].map(i => <CardSkeleton key={i} />)}
        </div>
      ) : filtered.length === 0 ? (
        <div className="surface p-10 text-center">
          <div className="w-16 h-16 rounded-2xl bg-gray-50 flex items-center justify-center mx-auto mb-4">
            <MdLocalShipping size={28} className="text-gray-300" />
          </div>
          <p className="text-sm font-semibold text-gray-700 mb-1">
            {subtab === "active" ? "No active fulfillments" : "No fulfillment history"}
          </p>
          <p className="text-xs text-gray-400">
            {subtab === "active" ? "All caught up!" : "Completed orders will appear here"}
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {filtered.map(fo => (
            <FulfillmentCard
              key={fo.fulfillmentId}
              fo={fo}
              onClick={() => setSelectedId(fo.fulfillmentId)}
            />
          ))}
        </div>
      )}
    </div>
  );
};

export default FulfillmentTab;
