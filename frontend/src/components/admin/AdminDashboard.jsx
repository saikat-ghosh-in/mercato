import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useSelector } from "react-redux";
import {
  MdDashboard, MdPeople, MdShoppingBag,
  MdCategory, MdShoppingCart,
  MdInventory, MdMenu, MdClose,
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
  const activeTab = NAV.some(item => item.id === params.get("tab")) ? params.get("tab") : "overview";
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
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

  return (
    <div className="admin-workspace bg-gray-50 min-h-screen">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">

        {/* Header */}
        <div className="flex items-center gap-3 mb-6">
          <div>
            <h1 className="text-xl font-bold text-gray-900">Administration</h1>
            <p className="text-xs text-gray-500 mt-0.5">{user?.username}</p>
          </div>
          <button
            className="dashboard-nav-toggle icon-action ml-auto lg:hidden p-2 rounded-xl bg-white border border-gray-100 text-gray-500"
            aria-label="Toggle dashboard navigation" aria-expanded={mobileNavOpen} onClick={() => setMobileNavOpen(v => !v)}
          >
            {mobileNavOpen ? <MdClose size={18} /> : <MdMenu size={18} />}
          </button>
        </div>

        {/* Tab nav */}
        <div className={`admin-tabs ${mobileNavOpen ? "flex" : "hidden"} lg:flex flex-col lg:flex-row flex-wrap gap-1 surface p-1.5 mb-6`}>
          {NAV.map(({ id, label, icon: Icon }) => (
            <button
              key={id}
              aria-pressed={activeTab === id}
              onClick={() => { setParams(previous => { const next = new URLSearchParams(previous); next.set("tab", id); return next; }); setMobileNavOpen(false); }}
              className={`control flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-semibold transition-[transform,opacity,background-color] cursor-pointer flex-1 justify-center lg:justify-start ${activeTab === id
                ? "bg-gray-950 text-white"
                : "text-gray-500 hover:bg-gray-50 hover:text-gray-800"
                }`}
            >
              <Icon size={16} />
              {label}
            </button>
          ))}
        </div>

        {/* Content */}
        {renderTab()}
      </div>
    </div>
  );
};

export default AdminDashboard;
