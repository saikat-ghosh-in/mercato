import { LoadingLayout } from "./LoadingStates";
import {
    MdCheckCircle, MdCancel, MdHourglassEmpty,
    MdAccessTime, MdLocalShipping, MdWarning,
    MdTrendingUp,
} from "react-icons/md";
import { formatCurrency } from "../../utils/formatCurrency";

export const StatSkeleton = () => <LoadingLayout variant="form" label="Loading content" />;

export const StatCard = ({ label, value, sub }) => (
    <div className="surface dashboard-stat">
        <p className="dashboard-stat-label">{label}</p>
        <p className="dashboard-stat-value">{value}</p>
        {sub && <p className="dashboard-stat-detail">{sub}</p>}
    </div>
);

export const SectionCard = ({ title, icon: Icon, children }) => (
    <div className="surface p-5">
        {title && (
            <h3 className="dashboard-section-heading flex items-center gap-2">
                {Icon && <Icon size={13} />}{title}
            </h3>
        )}
        {children}
    </div>
);

export const SectionLabel = ({ children }) => (
    <h2 className="dashboard-section-heading">{children}</h2>
);

export const RevenueCards = ({ revenue }) => (
    <div>
        <SectionLabel>Revenue</SectionLabel>
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard label="All-Time" value={formatCurrency(revenue.allTime)} icon={MdTrendingUp} iconBg="bg-blue-500" />
            <StatCard label="This Month" value={formatCurrency(revenue.thisMonth)} icon={MdTrendingUp} iconBg="bg-blue-500" />
            <StatCard label="Today" value={formatCurrency(revenue.today)} icon={MdTrendingUp} iconBg="bg-blue-500" />
            <StatCard
                label="Avg Revenue / Order"
                value={formatCurrency(revenue.averagePerOrder)}
                icon={MdTrendingUp}
                iconBg="bg-gray-700"
            />
        </div>
    </div>
);

export const TopSellingProducts = ({ products }) => (
    <SectionCard title="Top Selling Products" icon={MdTrendingUp}>
        {products?.length > 0 ? (
            <div className="space-y-2">
                {products.map((p, i) => (
                    <div key={p.productId} className="flex items-center gap-3 py-2 border-b border-gray-50 last:border-0">
                        <span className="text-xs font-semibold text-gray-300 w-5 shrink-0">#{i + 1}</span>
                        <div className="flex-1 min-w-0">
                            <p className="text-sm font-semibold text-gray-800 truncate">{p.productName}</p>
                            <p className="text-xs text-gray-400">{p.totalShippedQty} units shipped</p>
                        </div>
                        <span className="text-sm font-semibold text-gray-950 shrink-0">
                            {formatCurrency(p.totalRevenue)}
                        </span>
                    </div>
                ))}
            </div>
        ) : (
            <p className="text-sm text-gray-400 text-center py-4">No sales data yet</p>
        )}
    </SectionCard>
);

export const InventoryAlerts = ({ alerts }) => (
    <SectionCard title="Low Inventory Alerts" icon={MdWarning}>
        {alerts?.length > 0 ? (
            <div className="space-y-2">
                {alerts.map(p => (
                    <div key={p.productId} className="flex items-center gap-3 py-2 border-b border-gray-50 last:border-0">
                        <div className={`w-2 h-2 rounded-full shrink-0 ${p.availableQty === 0 ? "bg-red-500" : "bg-amber-400"}`} />
                        <div className="flex-1 min-w-0">
                            <p className="text-sm font-semibold text-gray-800 truncate">{p.productName}</p>
                            <p className="text-xs text-gray-400">
                                {p.availableQty} available · {p.reservedQty} reserved · {p.physicalQty} physical
                            </p>
                        </div>
                        <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${p.availableQty === 0
                                ? "bg-red-100 text-red-600"
                                : "bg-amber-100 text-amber-700"
                            }`}>
                            {p.availableQty === 0 ? "Out of Stock" : "Low Stock"}
                        </span>
                    </div>
                ))}
            </div>
        ) : (
            <div className="flex items-center gap-2 text-blue-700 py-4 justify-center">
                <MdCheckCircle size={16} />
                <p className="text-sm font-semibold">All products well stocked</p>
            </div>
        )}
    </SectionCard>
);

export const StatusBreakdownBars = ({ title, entries, statusMap, icon }) => {
    const total = Object.values(entries ?? {}).reduce((a, b) => a + b, 0);

    if (!entries || total === 0) return (
        <SectionCard title={title} icon={icon}>
            <p className="text-sm text-gray-400 text-center py-4">No data yet</p>
        </SectionCard>
    );

    const getConfig = (raw) =>
        statusMap[raw?.toUpperCase()] ?? { label: raw, color: "bg-gray-100 text-gray-600", icon: MdAccessTime };

    return (
        <SectionCard title={title} icon={icon}>
            <div className="space-y-2.5">
                {Object.entries(entries).map(([status, count]) => {
                    const { label, color, icon: StatusIcon } = getConfig(status);
                    const pct = Math.round((count / total) * 100);
                    return (
                        <div key={status} className="dashboard-status-row">
                            <span className={`text-xs font-bold px-2 py-0.5 rounded-full dashboard-status-label flex items-center justify-center gap-1 ${color}`}>
                                <StatusIcon size={9} />{label}
                            </span>
                            <div className="flex-1 bg-gray-100 rounded-full h-1.5 overflow-hidden">
                                <div
                                    className="h-full bg-gray-800 rounded-full "
                                    style={{ width: `${pct}%` }}
                                />
                            </div>
                            <span className="text-xs font-bold text-gray-700 w-6 text-right shrink-0">{count}</span>
                        </div>
                    );
                })}
            </div>
        </SectionCard>
    );
};

export const DashboardSkeleton = () => <LoadingLayout variant="dashboard" label="Loading content" />;

export const DashboardError = ({ message, onRetry }) => (
    <div className="surface p-10 text-center">
        <MdWarning size={28} className="text-red-300 mx-auto mb-3" />
        <p className="text-sm font-semibold text-gray-700 mb-1">Failed to load stats</p>
        <p className="text-xs text-gray-400 mb-5">{message}</p>
        {onRetry && (
            <button
                onClick={onRetry}
                className="control inline-flex items-center gap-2 bg-gray-950 hover:bg-gray-800 text-white text-xs font-bold px-4 py-2 rounded-xl transition-[transform,opacity,background-color] cursor-pointer "
            >
                Retry
            </button>
        )}
    </div>
);
