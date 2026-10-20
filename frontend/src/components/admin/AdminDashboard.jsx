import DashboardFrame from "../shared/DashboardFrame";
import { activeDashboardTab, dashboardParams } from "../../utils/dashboardNavigation";
import { useSearchParams } from "react-router-dom";
import { useSelector } from "react-redux";
import {
  MdDashboard, MdPeople, MdShoppingBag,
  MdCategory, MdShoppingCart,
  MdInventory,
} from "react-icons/md";
import AdminOverviewTab from "./AdminOverviewTab";
import UsersTab from "./UserManagement";
import OrdersTab from "./OrdersTab";
import ProductsTab from "../shared/ProductsTab";
import CategoriesTab from "./CategoriesTab";
import CartsTab from "./CartsTab";
import SellerApplicationsTab from "./SellerApplicationsTab";
import CommissionTab from "./CommissionTab";

const NAV = [
  { id: "overview", label: "Overview", icon: MdDashboard },
  { id: "users", label: "Users", icon: MdPeople },
  { id: "applications", label: "Seller approvals", icon: MdPeople },
  { id: "commission", label: "Commission", icon: MdShoppingBag },
  { id: "orders", label: "Orders", icon: MdShoppingBag },
  { id: "categories", label: "Categories", icon: MdCategory },
  { id: "products", label: "Products", icon: MdInventory },
  { id: "carts", label: "Carts", icon: MdShoppingCart },
];

const AdminDashboard = () => {
  const [params, setParams] = useSearchParams();
  const activeTab = activeDashboardTab(NAV, params);
  const user = useSelector(s => s.auth?.userDetails);

  const renderTab = () => {
    switch (activeTab) {
      case "overview": return <AdminOverviewTab />;
      case "users": return <UsersTab />;
      case "applications": return <SellerApplicationsTab />;
      case "commission": return <CommissionTab />;
      case "orders": return <OrdersTab />;
      case "categories": return <CategoriesTab />;
      case "products": return <ProductsTab mode="admin" />;
      case "carts": return <CartsTab />;
      default: return <AdminOverviewTab />;
    }
  };

  return <DashboardFrame title="Administration" subtitle={user?.username} items={NAV} activeTab={activeTab} onSelect={id => setParams(previous => dashboardParams(previous, id))}>
    {renderTab()}
  </DashboardFrame>;
};

export default AdminDashboard;
