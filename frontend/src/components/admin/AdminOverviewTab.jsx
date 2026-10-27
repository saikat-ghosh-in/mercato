import MarketplaceFinance from "../shared/MarketplaceFinance";
import { ORDER_STATUS_MAP } from "../../utils/dashboardStatus";
import {
    MdPeople, MdShoppingBag, MdAccessTime, MdHourglassEmpty,
} from "react-icons/md";
import { useManagementQuery } from "../../reduxStore/services/privateApi.js";
import { formatCurrency } from "../../utils/formatCurrency";
import { formatDate } from "../../utils/formatDate";
import {
    DashboardSkeleton, DashboardError,
    StatCard, SectionCard, SectionLabel, RevenueCards,
    TopSellingProducts, StatusBreakdownBars,

} from "../shared/DashboardWidgets";

const RecentOrders = ({ orders, statusMap }) => {
    const getStatus = (raw) =>
        statusMap[raw?.toUpperCase()] ?? { label: raw ?? "Unknown", color: "bg-gray-100 text-gray-600", icon: MdAccessTime };

    return (
        <SectionCard title="Recent Orders" icon={MdShoppingBag}>
            {!orders?.length ? (
                <p className="text-sm text-gray-400 text-center py-4">No recent orders</p>
            ) : (
                <div className="overflow-x-auto">
                    <table className="w-full">
                        <thead>
                            <tr className="border-b border-gray-50">
                                {["Order ID", "Customer", "Status", "Amount", "Date"].map(h => (
                                    <th key={h} className="text-left text-xs font-bold text-gray-400  pb-3 pr-4">{h}</th>
                                ))}
                            </tr>
                        </thead>
                        <tbody>
                            {orders.map(o => {
                                const { label, color, icon: StatusIcon } = getStatus(o.orderStatus);
                                return (
                                    <tr key={o.orderId} className="border-b border-gray-50 last:border-0">
                                        <td className="py-3 pr-4 text-xs font-mono font-semibold text-gray-700 whitespace-nowrap">{o.orderId}</td>
                                        <td className="py-3 pr-4">
                                            <p className="text-sm font-semibold text-gray-800">{o.customerName}</p>
                                            <p className="text-xs text-gray-400">{o.customerEmail}</p>
                                        </td>
                                        <td className="py-3 pr-4">
                                            <span className={`text-xs font-bold px-2 py-0.5 rounded-full flex items-center gap-1 w-fit ${color}`}>
                                                <StatusIcon size={9} />{label}
                                            </span>
                                        </td>
                                        <td className="py-3 pr-4 text-sm font-semibold text-gray-950 whitespace-nowrap">{formatCurrency(o.totalAmount)}</td>
                                        <td className="py-3 text-xs text-gray-400 whitespace-nowrap">{formatDate(o.createdAt)}</td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>
            )}
        </SectionCard>
    );
};

const UserBreakdown = ({ users }) => (
    <SectionCard title="User Breakdown" icon={MdPeople}>
        <div className="space-y-3">
            {[
                { label: "Total users", value: users.total },
                { label: "Sellers", value: users.sellers },
                { label: "Administrators", value: users.admins },
                { label: "New this month", value: users.newThisMonth },
            ].map(({ label, value }) => (
                <div key={label} className="flex items-center gap-3 py-2 border-b border-gray-50 last:border-0">
                    <span className="text-sm text-gray-600 flex-1">{label}</span>
                    <span className="text-base font-semibold text-gray-950">{value}</span>
                </div>
            ))}
        </div>
    </SectionCard>
);

const AdminOverviewTab = () => {
    const { data: stats, isLoading: loading, error: queryError, refetch: fetchStats } = useManagementQuery("admin");
    const error = queryError ? queryError.data?.message || "Failed to load stats" : "";

    if ((loading || !stats) && !error) return <DashboardSkeleton />;
    if (error) return <DashboardError message={error} onRetry={fetchStats} />;

    const { users, order, topSellingProducts, orderStatusBreakdown, recentOrders } = stats;

    return (
        <div className="space-y-6">

            {/* Revenue */}
            <MarketplaceFinance admin />
            {stats.revenue && <RevenueCards revenue={stats.revenue} admin />}

            {/* Order + user stats */}
            <div>
                <SectionLabel>Orders & Users</SectionLabel>
                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatCard
                        label="Total Orders"
                        value={order.total}
                        sub={`${order.todayCount} today`}
                        icon={MdShoppingBag}
                        iconBg="bg-gray-700"
                    />
                    <StatCard
                        label="Active Orders"
                        value={order.activeOrders}
                        sub="Confirmed + Processing"
                        icon={MdAccessTime}
                        iconBg="bg-blue-400"
                    />
                    <StatCard
                        label="Pending Payment"
                        value={order.pendingPayment}
                        sub="Awaiting payment"
                        icon={MdHourglassEmpty}
                        iconBg="bg-amber-400"
                    />
                    <StatCard
                        label="Total Users"
                        value={users.total}
                        sub={`${users.newThisMonth} new this month`}
                        icon={MdPeople}
                        iconBg="bg-blue-500"
                    />
                </div>
            </div>

            {/* Top products + user breakdown */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                <TopSellingProducts products={topSellingProducts} />
                <UserBreakdown users={users} />
            </div>

            {/* Order status breakdown */}
            <StatusBreakdownBars
                title="Order Status Breakdown"
                icon={MdShoppingBag}
                entries={orderStatusBreakdown}
                statusMap={ORDER_STATUS_MAP}
            />

            {/* Recent orders */}
            <RecentOrders orders={recentOrders} statusMap={ORDER_STATUS_MAP} />
        </div>
    );
};

export default AdminOverviewTab;
