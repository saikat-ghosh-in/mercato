import MarketplaceFinance from "../shared/MarketplaceFinance";
import { ORDER_LINE_STATUS_MAP } from "../../utils/dashboardStatus";
import { MdShoppingBag, MdAccessTime, MdHourglassEmpty, MdTrendingUp } from "react-icons/md";
import { useManagementQuery } from "../../reduxStore/services/privateApi.js";
import { formatCurrency } from "../../utils/formatCurrency";
import {
    DashboardSkeleton, DashboardError,
    StatCard, SectionLabel,
    TopSellingProducts, InventoryAlerts,
    StatusBreakdownBars,
} from "../shared/DashboardWidgets";

const SellerOverviewTab = () => {
    const { data: stats, isLoading: loading, error: queryError, refetch: fetchStats } = useManagementQuery("seller");
    const error = queryError ? queryError.data?.message || "Failed to load stats" : "";

    if ((loading || !stats) && !error) return <DashboardSkeleton />;
    if (error) return <DashboardError message={error} onRetry={fetchStats} />;

    const { fulfillment, topSellingProducts, inventoryAlerts, orderLineStatusBreakdown } = stats;

    return (
        <div className="space-y-6">

            {/* Revenue */}
            <MarketplaceFinance />

            {/* Fulfillment stats */}
            <div>
                <SectionLabel>Fulfillments</SectionLabel>
                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatCard
                        label="Total Fulfillments"
                        value={fulfillment.total}
                        sub={`${fulfillment.todayCount} today`}
                        icon={MdShoppingBag}
                        iconBg="bg-gray-700"
                    />
                    <StatCard
                        label="Active"
                        value={fulfillment.activeOrders}
                        sub="Confirmed + Processing"
                        icon={MdAccessTime}
                        iconBg="bg-blue-400"
                    />
                    <StatCard
                        label="Pending Payment"
                        value={fulfillment.pendingPayment}
                        sub="Awaiting payment"
                        icon={MdHourglassEmpty}
                        iconBg="bg-amber-400"
                    />
                    <StatCard
                        label="Avg Order Value"
                        value={formatCurrency(fulfillment.averageOrderValue)}
                        sub="From line totals"
                        icon={MdTrendingUp}
                        iconBg="bg-purple-500"
                    />
                </div>
            </div>

            {/* Top products + inventory */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                <TopSellingProducts products={topSellingProducts} />
                <InventoryAlerts alerts={inventoryAlerts} />
            </div>

            {/* Order line status breakdown */}
            <StatusBreakdownBars
                title="Order Line Status Breakdown"
                icon={MdShoppingBag}
                entries={orderLineStatusBreakdown}
                statusMap={ORDER_LINE_STATUS_MAP}
            />
        </div>
    );
};

export default SellerOverviewTab;
