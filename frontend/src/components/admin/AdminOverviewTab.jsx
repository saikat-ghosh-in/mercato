import { ORDER_STATUS_MAP } from "../../utils/dashboardStatus";
import { useEffect, useState } from "react";
import {
    MdPeople, MdShoppingBag, MdAccessTime, MdHourglassEmpty,
    MdStorefront, MdAdminPanelSettings, MdPerson,
} from "react-icons/md";
import api from "../../backend/api";
import { formatCurrency } from "../../utils/formatCurrency";
import { formatDate } from "../../utils/formatDate";
import {
    DashboardSkeleton, DashboardError,
    RevenueCards, StatCard, SectionCard, SectionLabel,
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
                                    <th key={h} className="text-left text-[10px] font-bold text-gray-400 uppercase tracking-widest pb-3 pr-4">{h}</th>
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
                                            <p className="text-[10px] text-gray-400">{o.customerEmail}</p>
                                        </td>
                                        <td className="py-3 pr-4">
                                            <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full flex items-center gap-1 w-fit ${color}`}>
                                                <StatusIcon size={9} />{label}
                                            </span>
                                        </td>
                                        <td className="py-3 pr-4 text-sm font-black text-gray-950 whitespace-nowrap">{formatCurrency(o.totalAmount)}</td>
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
                { label: "Total Users", value: users.total, icon: MdPerson, color: "bg-blue-50 text-blue-600" },
                { label: "Sellers", value: users.sellers, icon: MdStorefront, color: "bg-amber-50 text-amber-600" },
                { label: "Admins", value: users.admins, icon: MdAdminPanelSettings, color: "bg-purple-50 text-purple-600" },
                { label: "New This Month", value: users.newThisMonth, icon: MdPeople, color: "bg-blue-50 text-blue-700" },
            ].map(({ label, value, icon: Icon, color }) => (
                <div key={label} className="flex items-center gap-3 py-2 border-b border-gray-50 last:border-0">
                    <div className={`p-2 rounded-lg shrink-0 ${color}`}>
                        <Icon size={14} />
                    </div>
                    <span className="text-sm text-gray-600 flex-1">{label}</span>
                    <span className="text-base font-black text-gray-950">{value}</span>
                </div>
            ))}
        </div>
    </SectionCard>
);

const AdminOverviewTab = () => {
    const [stats, setStats] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    const fetchStats = async () => {
        try {
            setLoading(true);
            setError("");
            const { data } = await api.get("/admin/dashboard/stats");
            setStats(data);
        } catch (err) {
            setError(err?.response?.data?.message || "Failed to load stats");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => { fetchStats(); }, []);

    if (loading && !stats) return <DashboardSkeleton />;
    if (error) return <DashboardError message={error} onRetry={fetchStats} />;

    const { users, order, revenue, topSellingProducts, orderStatusBreakdown, recentOrders } = stats;

    return (
        <div className="space-y-6">

            {/* Revenue */}
            <RevenueCards revenue={revenue} />

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
                        iconBg="bg-purple-500"
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
