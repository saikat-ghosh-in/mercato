import { useState } from "react";
import { useSelector } from "react-redux";
import {
  MdDashboard, MdPeople, MdShoppingBag,
  MdCategory, MdShoppingCart, MdAdminPanelSettings,
  MdInventory, MdMenu, MdClose,
} from "react-icons/md";
import AdminOverviewTab from "./AdminOverviewTab";
import UsersTab from "./UsersTab";
import OrdersTab from "./OrdersTab";
import ProductsTab from "../shared/ProductsTab";
import CategoriesTab from "./CategoriesTab";
import CartsTab from "./CartsTab";

const NAV = [
  { id: "overview", label: "Overview", icon: MdDashboard },
  { id: "users", label: "Users", icon: MdPeople },
  { id: "orders", label: "Orders", icon: MdShoppingBag },
  { id: "categories", label: "Categories", icon: MdCategory },
  { id: "products", label: "Products", icon: MdInventory },
  { id: "carts", label: "Carts", icon: MdShoppingCart },
];

const AdminDashboard = () => {
  const [activeTab, setActiveTab] = useState("overview");
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const user = useSelector(s => s.auth?.userDetails);

  const renderTab = () => {
    switch (activeTab) {
      case "overview": return <AdminOverviewTab />;
      case "users": return <UsersTab />;
      case "orders": return <OrdersTab />;
      case "categories": return <CategoriesTab />;
      case "products": return <ProductsTab mode="admin" />;
      case "carts": return <CartsTab />;
      default: return <AdminOverviewTab />;
    }
  };

  return (
    <div className="bg-gray-50 min-h-screen">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">

        {/* Header */}
        <div className="flex items-center gap-3 mb-6">
          <div className="p-2 bg-purple-50 rounded-xl">
            <MdAdminPanelSettings size={20} className="text-purple-500" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-gray-900">Admin Panel</h1>
            <p className="text-xs text-gray-500 mt-0.5">{user?.username}</p>
          </div>
          <button
            className="ml-auto lg:hidden p-2 rounded-xl bg-white border border-gray-100 text-gray-500"
            aria-label="Toggle dashboard navigation" aria-expanded={mobileNavOpen} onClick={() => setMobileNavOpen(v => !v)}
          >
            {mobileNavOpen ? <MdClose size={18} /> : <MdMenu size={18} />}
          </button>
        </div>

        {/* Tab nav */}
        <div className={`${mobileNavOpen ? "flex" : "hidden"} lg:flex flex-col lg:flex-row gap-1 surface p-1.5 mb-6`}>
          {NAV.map(({ id, label, icon: Icon }) => (
            <button
              key={id}
              aria-pressed={activeTab === id}
              onClick={() => { setActiveTab(id); setMobileNavOpen(false); }}
              className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-semibold transition-[transform,opacity,background-color] cursor-pointer flex-1 justify-center lg:justify-start ${activeTab === id
                ? "bg-gray-950 text-white shadow-sm"
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