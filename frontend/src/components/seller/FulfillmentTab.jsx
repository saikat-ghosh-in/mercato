import { LoadingLayout } from "../shared/LoadingStates";
import ActionArrow from "../shared/ActionArrow";
import { useEffect, useState } from "react";
import {
  MdLocalShipping, MdCheckCircle, MdAccessTime,
  MdCancel, MdHourglassEmpty,
  MdArrowBack, MdClose, MdPerson,
  MdLocationOn, MdSearch,
} from "react-icons/md";
import useWorkflowResource, { useWorkflowEpoch } from "../../hooks/useWorkflowResource";
import { actionMaximum, workflowLabel } from "../../utils/fulfillment";
import { QuantityCommand, WorkflowDeadlines, WorkflowProgress } from "../orders/WorkflowControls";
import { formatDate } from "../../utils/formatDate";

const SUBTABS = [
  { id: "active", label: "Active" },
  { id: "history", label: "History" },
];

const LINE_STATUS = {
  CREATED: { label: "Pending", color: "bg-yellow-100 text-yellow-700", icon: MdHourglassEmpty },
  CONFIRMED: { label: "Confirmed", color: "bg-blue-100 text-blue-700", icon: MdCheckCircle },
  PROCESSING: { label: "Processing", color: "bg-blue-100 text-blue-700", icon: MdAccessTime },
  PARTIALLY_PROCESSED: { label: "Part. Processed", color: "bg-blue-50 text-blue-500", icon: MdAccessTime },
  FULFILLED: { label: "Fulfilled", color: "bg-blue-100 text-blue-700", icon: MdCheckCircle },
  PARTIALLY_FULFILLED: { label: "Part. Fulfilled", color: "bg-blue-50 text-blue-700", icon: MdLocalShipping },
  CANCELLED: { label: "Cancelled", color: "bg-red-100 text-red-500", icon: MdCancel },
};
const getLineStatus = (raw) =>
  LINE_STATUS[raw?.toUpperCase()] ?? { label: raw ?? "—", color: "bg-gray-100 text-gray-500", icon: MdAccessTime };

const CardSkeleton = () => <LoadingLayout variant="list" label="Loading content" rows={1} />;

const SectionCard = ({ children, className = "" }) => (
  <div className={`surface overflow-hidden ${className}`}>
    {children}
  </div>
);

const FulfillmentDetail = ({ fulfillmentId, onBack }) => {
  const {data:fo, loading, error, refresh:fetchFo, receivedAt, epoch} = useWorkflowResource(`/seller/fulfillment-orders/${encodeURIComponent(fulfillmentId)}`);

  if (loading && !fo) return (
    <div className="space-y-4">
      <CardSkeleton />
      <CardSkeleton />
    </div>
  );

  if (!fo) return <div className="state-panel" role="alert"><h2>Fulfillment details couldn’t load.</h2><p>Check your connection and try again.</p><button className="button button-dark" onClick={fetchFo}>Try again</button><button className="text-link" onClick={onBack}>Back to fulfillments</button></div>;

  return (
    <div className="space-y-4">
      {error && <p role="alert" className="text-sm text-red-700">{error} <button className="text-link" onClick={fetchFo}>Retry refresh</button></p>}
      <div className="flex items-center gap-3">
        <button
          onClick={onBack}
          className="control flex items-center gap-1.5 text-xs font-semibold text-blue-500 hover:text-blue-700 bg-blue-50 hover:bg-blue-100 px-3 py-1.5 rounded-xl transition-colors cursor-pointer"
        >
          <MdArrowBack size={13} /> Back
        </button>
        <div>
          <h2 className="text-sm font-bold text-gray-900">Fulfillment {fo.fulfillmentId}</h2>
          <p className="text-xs text-gray-400">Order #{fo.orderId} · {formatDate(fo.createdAt)}</p>
        </div>
        <button className="button button-outline ml-auto" onClick={fetchFo} disabled={loading}>{loading ? 'Refreshing…' : 'Refresh'}</button>
      </div>

      <WorkflowProgress record={fo} />
      <WorkflowDeadlines workflow={fo.workflow} receivedAt={receivedAt} onRelease={fetchFo} />
      <p className="text-xs text-gray-600">Accept within 48 hours after seller actions unlock. Preparation review is independent. Physical shipment is recorded by Mercato logistics after handoff.</p>
      <QuantityCommand key={`${epoch}:${fulfillmentId}:cancel-all`} label="Cancel all remaining" whole cancellation maximum={fo.orderLines?.reduce((total,line) => total + actionMaximum(line, 'CANCEL'),0) || 0} version={fo.workflow?.version} endpoint="/seller/fulfillment/cancellations" makeBody={(_qty,reason) => ({orderId:fo.orderId,fulfillmentId,reason})} onComplete={fetchFo} disabled={loading} />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 items-start">
        {/* Order lines */}
        <div className="lg:col-span-2">
          <SectionCard>
            <div className="px-5 py-4 border-b border-gray-50">
              <h3 className="text-xs font-bold text-gray-400 ">Order Lines</h3>
            </div>
            <div className="divide-y divide-gray-50">
              {fo.orderLines?.map(line => {
                const { color, icon: StatusIcon } = getLineStatus(line.orderLineStatus);

                return (
                  <div key={`${epoch}:${fulfillmentId}:${line.orderLineNumber}`} className="px-5 py-4">
                    <div className="flex items-start gap-3">
                      <div className="w-12 h-12 rounded-xl bg-gray-50 border border-gray-100 overflow-hidden shrink-0">
                        {line.product?.primaryImageUrl
                          ? <img src={line.product.primaryImageUrl} alt="" className="w-full h-full object-cover" />
                          : <div className="w-full h-full flex items-center justify-center text-gray-200 text-xl">◈</div>
                        }
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-start justify-between gap-2">
                          <p className="text-sm font-semibold text-gray-900 line-clamp-1">
                            {line.product?.productName ?? "Product"}
                          </p>
                          <span className={`text-xs font-bold px-2 py-0.5 rounded-full shrink-0 flex items-center gap-1 ${color}`}>
                            <StatusIcon size={9} /> {workflowLabel(line)}
                          </span>
                        </div>
                        <div className="mt-2"><WorkflowProgress record={line} showState={false} /></div>
                        <div className="flex items-center gap-2 mt-2.5 flex-wrap">
                          {[['ACCEPT','Accept'],['START_PREPARING','Start preparing'],['MARK_PACKED','Mark as packed'],['CANCEL','Cancel units']].map(([action,label]) => <QuantityCommand key={action} label={label} maximum={actionMaximum(line,action)} version={fo.workflow?.version} cancellation={action==='CANCEL'} endpoint="/seller/fulfillment/commands" makeBody={(qty,reason) => ({fulfillmentId:line.fulfillmentId || fulfillmentId,orderLineNumber:line.orderLineNumber,action,qty,...(action==='CANCEL' ? {reason} : {})})} onComplete={fetchFo} disabled={loading} />)}
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </SectionCard>
        </div>

        {/* Customer + address */}
        <div className="space-y-4">
          <SectionCard>
            <div className="p-5">
              <h3 className="text-xs font-bold text-gray-400  mb-3 flex items-center gap-1.5">
                <MdPerson size={13} /> Customer
              </h3>
              <p className="text-sm font-semibold text-gray-900">{fo.customer?.name}</p>
              <p className="text-xs text-gray-400 mt-0.5">{fo.customer?.email}</p>
            </div>
          </SectionCard>
          <SectionCard>
            <div className="p-5">
              <h3 className="text-xs font-bold text-gray-400  mb-3 flex items-center gap-1.5">
                <MdLocationOn size={13} /> Delivery Address
              </h3>
              {fo.deliveryAddress ? (
                <div className="space-y-1 text-xs text-gray-600">
                  <p className="font-bold text-gray-900">{fo.deliveryAddress.recipientName}</p>
                  <p>{fo.deliveryAddress.addressLine1}</p>
                  {fo.deliveryAddress.addressLine2 && <p>{fo.deliveryAddress.addressLine2}</p>}
                  <p>{fo.deliveryAddress.city}, {fo.deliveryAddress.state} — {fo.deliveryAddress.pincode}</p>
                  <p className="pt-1 text-gray-500">Phone: {fo.deliveryAddress.recipientPhone}</p>
                </div>
              ) : (
                <p className="text-xs text-gray-400">No address available</p>
              )}
            </div>
          </SectionCard>
        </div>
      </div>
    </div>
  );
};

const FulfillmentCard = ({ fo, onClick }) => {
  const lineCount = fo.orderLines?.length ?? 0;
  const pendingLines = fo.orderLines?.filter(line => ['ACCEPT','START_PREPARING','MARK_PACKED','CANCEL'].some(action => actionMaximum(line,action) > 0)).length ?? 0;

  return (
    <button type="button" aria-label={"View fulfillment " + fo.fulfillmentId}
      onClick={onClick}
      className="group surface fulfillment-card w-full text-left cursor-pointer p-5"
    >
      <div className="flex items-start justify-between gap-3 mb-3">
        <div>
          <p className="text-sm font-bold text-gray-900">{fo.fulfillmentId}</p>
          <p className="text-xs text-gray-400 mt-0.5">Order #{fo.orderId}</p>
        </div>
        <ActionArrow size={16} className="text-gray-300 group-hover:text-blue-500 transition-colors shrink-0 mt-0.5" />
      </div>

      <div className="flex flex-wrap items-center gap-3 text-xs text-gray-500 mb-3">
        <span>{fo.customer?.name}</span>
        <span className="text-gray-200">·</span>
        <span>{formatDate(fo.createdAt)}</span>
      </div>

      <div className="flex items-center gap-2 flex-wrap">
        {fo.orderLines?.slice(0, 3).map((line, i) => {
          const { color } = getLineStatus(line.orderLineStatus);
          return (
            <span key={i} className={`text-xs font-semibold px-2 py-0.5 rounded-full ${color}`}>
              {workflowLabel(line)}
            </span>
          );
        })}
        {lineCount > 3 && (
          <span className="text-xs text-gray-400">+{lineCount - 3} more</span>
        )}
        {pendingLines > 0 && (
          <span className="ml-auto text-xs font-bold text-amber-600 bg-amber-50 px-2 py-0.5 rounded-full">
            {pendingLines} pending action
          </span>
        )}
      </div>
    </button>
  );
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
      <section className="surface p-4 space-y-1" aria-label="Acceptance performance"><h3 className="text-sm font-semibold">On-time acceptance</h3>{metrics.data ? <><p className="text-sm tabular-nums">{metrics.data.acceptance?.onTimeAcceptanceRate == null ? 'No mature units in this period' : `${(metrics.data.acceptance.onTimeAcceptanceRate * 100).toFixed(1)}% on time`} · {metrics.data.acceptance?.acceptanceDenominatorQty ?? 0} eligible units · {metrics.data.acceptance?.timeoutCancelledQty ?? 0} units cancelled for seller delay</p><p className="text-xs text-gray-600">Confirmed or prospectively adopted cohorts with elapsed acceptance deadlines, {formatDate(metrics.data.coverage?.fromInclusive)} to {formatDate(metrics.data.coverage?.toExclusive)} (end exclusive). Customer cancellations before the deadline are excluded; seller cancellations remain in the denominator.</p></> : <p className="text-xs text-gray-600">{metrics.error || 'Loading acceptance performance…'}</p>}</section>
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
