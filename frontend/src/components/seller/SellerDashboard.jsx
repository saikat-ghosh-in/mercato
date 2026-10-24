import DashboardFrame from "../shared/DashboardFrame";
import { activeDashboardTab, dashboardParams } from "../../utils/dashboardNavigation";
import PromotionsTab from "./PromotionsTab";
import { useSelector } from "react-redux";
import {
  MdDashboard, MdInventory, MdLocalShipping,
  MdStorefront,
} from "react-icons/md";
import SellerOverviewTab from "./SellerOverviewTab";
import ProductsTab from "../shared/ProductsTab";
import FulfillmentTab from "./FulfillmentTab";
import SellerNotices from "./SellerNotices";
import SellerApplicationStatus from "./SellerApplicationStatus";
import useSellerNotices from "../../hooks/useSellerNotices";
import { useSearchParams } from "react-router-dom";

const NAV = [
  { id: "overview", label: "Overview", icon: MdDashboard },
  { id: "products", label: "Products", icon: MdInventory },
  { id: "fulfillment", label: "Fulfillment", icon: MdLocalShipping },
  { id: "promotions", label: "Promotions", icon: MdStorefront },
  { id: "notices", label: "Seller notices", icon: MdStorefront },
];

const SellerDashboard = () => {
  const [params, setParams] = useSearchParams();
  const activeTab = activeDashboardTab(NAV, params);
  const user = useSelector(s => s.auth?.userDetails);
  const inbox = useSellerNotices(Boolean(user?.roles?.some(role => ["ROLE_SELLER", "ROLE_ADMIN"].includes(role))));
  if (["PENDING", "REJECTED"].includes(user?.sellerStatus) && !user?.roles?.includes("ROLE_ADMIN")) return <SellerApplicationStatus user={user} />;

  const renderTab = () => {
    switch (activeTab) {
      case "overview": return <SellerOverviewTab />;
      case "products": return <ProductsTab />;
      case "fulfillment": return <FulfillmentTab />;
      case "promotions": return <PromotionsTab />;
      case "notices": return <SellerNotices {...inbox} />;
      default: return <SellerOverviewTab />;
    }
  };

  return <DashboardFrame title="Seller dashboard" subtitle={user?.sellerDisplayName || user?.username} items={NAV.map(item => ({ ...item, count: item.id === "notices" ? inbox.unread : 0 }))} activeTab={activeTab} onSelect={id => setParams(previous => dashboardParams(previous, id))}>
    {user?.sellerSuspended && <section className="surface p-4 mb-5" role="status"><h2 className="text-base font-semibold">Selling is suspended</h2><p className="text-sm text-gray-600 mt-2">Your listings are hidden. New listings and promotions are unavailable. You can still manage existing orders, view earnings and use your shopper account. Contact support through Help for assistance.</p></section>}
    {renderTab()}
  </DashboardFrame>;
};

export default SellerDashboard;
