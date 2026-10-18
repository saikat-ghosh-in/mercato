import { useState } from "react";
import { useSelector } from "react-redux";
import {
  MdDashboard, MdInventory, MdLocalShipping,
  MdStorefront, MdMenu, MdClose,
} from "react-icons/md";
import SellerOverviewTab from "./SellerOverviewTab";
import ProductsTab from "../shared/ProductsTab";
import FulfillmentTab from "./FulfillmentTab";

const NAV = [
  { id: "overview", label: "Overview", icon: MdDashboard },
  { id: "products", label: "Products", icon: MdInventory },
  { id: "fulfillment", label: "Fulfillment", icon: MdLocalShipping },
];

const SellerDashboard = () => {
  const [activeTab, setActiveTab] = useState("overview");
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const user = useSelector(s => s.auth?.userDetails);

  const renderTab = () => {
    switch (activeTab) {
      case "overview": return <SellerOverviewTab />;
      case "products": return <ProductsTab />;
      case "fulfillment": return <FulfillmentTab />;
      default: return <SellerOverviewTab />;
    }
  };

  return (
    <div className="bg-gray-50 min-h-screen">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">

        {/* Header */}
        <div className="flex items-center gap-3 mb-6">
          <div className="p-2 bg-blue-50 rounded-xl">
            <MdStorefront size={20} className="text-blue-500" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-gray-900">Seller Dashboard</h1>
            <p className="text-xs text-gray-500 mt-0.5">
              {user?.sellerDisplayName ?? user?.username}
            </p>
          </div>
          {/* Mobile nav toggle */}
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

        {/* Tab content */}
        {renderTab()}
      </div>
    </div>
  );
};

export default SellerDashboard;