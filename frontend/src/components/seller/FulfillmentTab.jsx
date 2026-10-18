import { useCallback, useEffect, useState } from "react";
import {
  MdLocalShipping, MdCheckCircle, MdAccessTime,
  MdCancel, MdHourglassEmpty, MdArrowForward,
  MdArrowBack, MdClose, MdPerson,
  MdLocationOn, MdSearch,
} from "react-icons/md";
import toast from "react-hot-toast";
import api from "../../backend/api";
import { formatDate } from "../../utils/formatDate";

const SUBTABS = [
  { id: "active", label: "Active Fulfillments" },
  { id: "history", label: "Fulfillment History" },
];

const LINE_STATUS = {
  CREATED: { label: "Pending", color: "bg-yellow-100 text-yellow-700", icon: MdHourglassEmpty },
  CONFIRMED: { label: "Confirmed", color: "bg-blue-100 text-blue-700", icon: MdCheckCircle },
  PROCESSING: { label: "Processing", color: "bg-indigo-100 text-indigo-700", icon: MdAccessTime },
  PARTIALLY_PROCESSED: { label: "Part. Processed", color: "bg-indigo-50 text-indigo-500", icon: MdAccessTime },
  FULFILLED: { label: "Fulfilled", color: "bg-blue-100 text-blue-700", icon: MdCheckCircle },
  PARTIALLY_FULFILLED: { label: "Part. Fulfilled", color: "bg-blue-50 text-blue-700", icon: MdLocalShipping },
  CANCELLED: { label: "Cancelled", color: "bg-red-100 text-red-500", icon: MdCancel },
};
const getLineStatus = (raw) =>
  LINE_STATUS[raw?.toUpperCase()] ?? { label: raw ?? "—", color: "bg-gray-100 text-gray-500", icon: MdAccessTime };

const CardSkeleton = () => (
  <div className="surface p-5 animate-pulse space-y-3">
    <div className="flex justify-between">
      <div className="h-3.5 bg-gray-100 rounded w-1/3" />
      <div className="h-5 bg-gray-100 rounded w-20" />
    </div>
    <div className="h-3 bg-gray-100 rounded w-1/4" />
    <div className="space-y-2 mt-2">
      <div className="h-10 bg-gray-100 rounded" />
      <div className="h-10 bg-gray-100 rounded" />
    </div>
  </div>
);

const SectionCard = ({ children, className = "" }) => (
  <div className={`surface overflow-hidden ${className}`}>
    {children}
  </div>
);

const FulfillmentDetail = ({ fulfillmentId, onBack }) => {
  const [fo, setFo] = useState(null);
  const [loading, setLoading] = useState(true);
  const [updatingLine, setUpdatingLine] = useState(null);

  const fetchFo = useCallback(async () => {
    try {
      setLoading(true);
      const { data } = await api.get(`/seller/fulfillment-orders/${fulfillmentId}`);
      setFo(data);
    } catch {
      toast.error("Failed to load fulfillment details");
    } finally {
      setLoading(false);
    }
  }, [fulfillmentId]);

  useEffect(() => { fetchFo(); }, [fetchFo]);

  const handleLineAction = async (line, action, qty) => {
    setUpdatingLine(line.orderLineNumber);
    try {
      await api.post("/order-line/update", {
        fulfillmentId: line.fulfillmentId ?? fulfillmentId,
        orderLineNumber: line.orderLineNumber,
        action,
        qty,
      });
      toast.success(`Line ${action.toLowerCase()}ed`);
      fetchFo();
    } catch (err) {
      toast.error(err?.response?.data?.message || `Failed to ${action.toLowerCase()} line`);
    } finally {
      setUpdatingLine(null);
    }
  };

  if (loading && !fo) return (
    <div className="space-y-4">
      <CardSkeleton />
      <CardSkeleton />
    </div>
  );

  if (!fo) return <div className="state-panel" role="alert"><h2>Fulfillment details couldn’t load.</h2><p>Check your connection and try again.</p><button className="button button-dark" onClick={fetchFo}>Try again</button><button className="text-link" onClick={onBack}>Back to fulfillments</button></div>;

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center gap-3">
        <button
          onClick={onBack}
          className="control flex items-center gap-1.5 text-xs font-semibold text-blue-500 hover:text-blue-700 bg-blue-50 hover:bg-blue-100 px-3 py-1.5 rounded-xl transition-colors cursor-pointer"
        >
          <MdArrowBack size={13} /> Back
        </button>
        <div>
          <p className="text-sm font-bold text-gray-900">{fo.fulfillmentId}</p>
          <p className="text-[11px] text-gray-400">Order #{fo.orderId} · {formatDate(fo.createdAt)}</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 items-start">
        {/* Order lines */}
        <div className="lg:col-span-2">
          <SectionCard>
            <div className="px-5 py-4 border-b border-gray-50">
              <h3 className="text-xs font-bold text-gray-400 uppercase tracking-widest">Order Lines</h3>
            </div>
            <div className="divide-y divide-gray-50">
              {fo.orderLines?.map((line, i) => {
                const { label, color, icon: StatusIcon } = getLineStatus(line.orderLineStatus);
                const isUpdating = updatingLine === line.orderLineNumber;
                const lineStatus = line.orderLineStatus?.toUpperCase();
                const canAccept = lineStatus === "CREATED" || lineStatus === "CONFIRMED";
                const canShip = lineStatus === "PROCESSING" || lineStatus === "PARTIALLY_PROCESSED";
                const isTerminal = lineStatus === "FULFILLED" || lineStatus === "PARTIALLY_FULFILLED" || lineStatus === "CANCELLED";

                return (
                  <div key={i} className="px-5 py-4">
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
                          <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full shrink-0 flex items-center gap-1 ${color}`}>
                            <StatusIcon size={9} /> {label}
                          </span>
                        </div>
                        <div className="flex items-center gap-3 mt-1 text-[11px] text-gray-400 flex-wrap">
                          <span>Ordered: <b className="text-gray-700">{line.orderedQty}</b></span>
                          <span>Accepted: <b className="text-gray-700">{line.acceptedQty}</b></span>
                          <span>Shipped: <b className="text-gray-700">{line.shippedQty}</b></span>
                          <span>Cancelled: <b className="text-gray-700">{line.cancelledQty}</b></span>
                          <span>Pending: <b className="text-blue-600">{line.pendingQty}</b></span>
                        </div>

                        {/* Actions */}
                        {!isTerminal && (
                          <div className="flex items-center gap-2 mt-2.5 flex-wrap">
                            {canAccept && (
                              <button
                                onClick={() => handleLineAction(line, "ACCEPT", line.pendingQty)}
                                disabled={isUpdating}
                                className="control text-[11px] font-bold px-3 py-1.5 bg-blue-50 text-blue-600 hover:bg-blue-100 rounded-lg transition-colors cursor-pointer disabled:opacity-40"
                              >
                                {isUpdating ? "..." : "Accept"}
                              </button>
                            )}
                            {canShip && (
                              <button
                                onClick={() => handleLineAction(line, "SHIP", line.pendingQty)}
                                disabled={isUpdating}
                                className="control text-[11px] font-bold px-3 py-1.5 bg-blue-50 text-blue-700 hover:bg-blue-100 rounded-lg transition-colors cursor-pointer disabled:opacity-40"
                              >
                                {isUpdating ? "..." : "Ship All"}
                              </button>
                            )}
                          </div>
                        )}
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
              <h3 className="text-xs font-bold text-gray-400 uppercase tracking-widest mb-3 flex items-center gap-1.5">
                <MdPerson size={13} /> Customer
              </h3>
              <p className="text-sm font-semibold text-gray-900">{fo.customer?.name}</p>
              <p className="text-xs text-gray-400 mt-0.5">{fo.customer?.email}</p>
            </div>
          </SectionCard>
          <SectionCard>
            <div className="p-5">
              <h3 className="text-xs font-bold text-gray-400 uppercase tracking-widest mb-3 flex items-center gap-1.5">
                <MdLocationOn size={13} /> Delivery Address
              </h3>
              {fo.deliveryAddress ? (
                <div className="space-y-1 text-xs text-gray-600">
                  <p className="font-bold text-gray-900">{fo.deliveryAddress.recipientName}</p>
                  <p>{fo.deliveryAddress.addressLine1}</p>
                  {fo.deliveryAddress.addressLine2 && <p>{fo.deliveryAddress.addressLine2}</p>}
                  <p>{fo.deliveryAddress.city}, {fo.deliveryAddress.state} — {fo.deliveryAddress.pincode}</p>
                  <p className="pt-1 text-gray-500">📞 {fo.deliveryAddress.recipientPhone}</p>
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
  const pendingLines = fo.orderLines?.filter(l => {
    const s = l.orderLineStatus?.toUpperCase();
    return s === "CREATED" || s === "CONFIRMED";
  }).length ?? 0;

  return (
    <div
      onClick={onClick}
      className="group surface  transition-[transform,opacity,background-color] duration-200 cursor-pointer p-5"
    >
      <div className="flex items-start justify-between gap-3 mb-3">
        <div>
          <p className="text-sm font-bold text-gray-900">{fo.fulfillmentId}</p>
          <p className="text-[11px] text-gray-400 mt-0.5">Order #{fo.orderId}</p>
        </div>
        <MdArrowForward size={16} className="text-gray-300 group-hover:text-blue-500 transition-colors shrink-0 mt-0.5" />
      </div>

      <div className="flex items-center gap-3 text-[11px] text-gray-500 mb-3">
        <span>{fo.customer?.name}</span>
        <span className="text-gray-200">·</span>
        <span>{formatDate(fo.createdAt)}</span>
      </div>

      <div className="flex items-center gap-2 flex-wrap">
        {fo.orderLines?.slice(0, 3).map((line, i) => {
          const { label, color } = getLineStatus(line.orderLineStatus);
          return (
            <span key={i} className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${color}`}>
              {label}
            </span>
          );
        })}
        {lineCount > 3 && (
          <span className="text-[10px] text-gray-400">+{lineCount - 3} more</span>
        )}
        {pendingLines > 0 && (
          <span className="ml-auto text-[10px] font-bold text-amber-600 bg-amber-50 px-2 py-0.5 rounded-full">
            {pendingLines} pending action
          </span>
        )}
      </div>
    </div>
  );
};



const FulfillmentTab = () => {
  const [subtab, setSubtab] = useState("active");
  const [activeFulfillments, setActiveFulfillments] = useState([]);
  const [closedFulfillments, setClosedFulfillments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedId, setSelectedId] = useState(null);
  const [search, setSearch] = useState("");

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([
      api.get("/seller/open-fulfillment-orders", { signal: controller.signal }),
      api.get("/seller/closed-fulfillment-orders", { signal: controller.signal })
    ]).then(([active, closed]) => {
      if (!controller.signal.aborted) { setActiveFulfillments(active.data ?? []); setClosedFulfillments(closed.data ?? []); }
    }).catch(() => { if (!controller.signal.aborted) toast.error("Failed to load fulfillment orders"); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, []);

  if (selectedId) return (
    <FulfillmentDetail
      fulfillmentId={selectedId}
      onBack={() => setSelectedId(null)}
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
      {/* Subtab switcher */}
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div className="flex gap-1 bg-white rounded-xl border border-gray-100 shadow-sm p-1">
          {SUBTABS.map(({ id, label }) => (
            <button
              key={id}
              onClick={() => { setSubtab(id); setSearch(""); }}
              className={`control px-4 py-2 rounded-lg text-xs font-semibold transition-[transform,opacity,background-color] cursor-pointer flex items-center gap-1.5 ${subtab === id
                ? "bg-gray-950 text-white"
                : "text-gray-500 hover:text-gray-800 hover:bg-gray-50"
                }`}
            >
              {label}
              {id === "active" && activeFulfillments.length > 0 && (
                <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded-full ${subtab === "active" ? "bg-white text-gray-900" : "bg-blue-500 text-white"
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
            placeholder="Search by ID or customer..."
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
      {loading ? (
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
