import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { MdLock, MdRefresh, MdLogin } from "react-icons/md";
import api from "../../backend/api";

const AccountDeactivated = () => {
  const navigate = useNavigate();
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetch = async () => {
      try {
        const { data } = await api.get("/deletion-status");
        setStatus(data);
      } catch {
        console.warn("Failed to fetch account status");
      } finally {
        setLoading(false);
      }
    };
    fetch();
  }, []);

  return (
    <main className="min-h-screen bg-gray-50 flex items-center justify-center px-4 py-10">
      <div className="w-full max-w-md">

        {/* Brand */}
        <div className="text-center mb-8">
          <div className="w-12 h-12 rounded-2xl overflow-hidden mx-auto mb-4 shadow-sm">
            <img src="/logo.jpg" alt="Mercato" className="w-full h-full object-cover" />
          </div>
        </div>

        <div className="surface p-8 text-center">

          {/* Icon */}
          <div className="w-16 h-16 rounded-2xl bg-amber-50 flex items-center justify-center mx-auto mb-5">
            <MdLock size={30} className="text-amber-500" />
          </div>

          <h1 className="text-xl font-black text-gray-950 mb-2">Account Deactivated</h1>
          <p className="text-sm text-gray-500 leading-relaxed mb-6">
            Your account has been deactivated and is scheduled for permanent deletion.
          </p>

          {/* Days remaining card */}
          {!loading && status && (
            <div className={`rounded-2xl border p-4 mb-6 ${status.daysLeft <= 2
              ? "bg-red-50 border-red-100"
              : "bg-amber-50 border-amber-100"
              }`}>
              <p className={`text-3xl font-black mb-1 ${status.daysLeft <= 2 ? "text-red-600" : "text-amber-600"
                }`}>
                {status.daysLeft} day{status.daysLeft !== 1 ? "s" : ""}
              </p>
              <p className={`text-xs font-semibold ${status.daysLeft <= 2 ? "text-red-500" : "text-amber-600"
                }`}>
                until permanent deletion
              </p>
              {status.daysLeft <= 2 && (
                <p className="text-[11px] text-red-400 mt-1.5">
                  ⚠️ Act quickly — your account and all data will be permanently deleted soon
                </p>
              )}
            </div>
          )}

          {loading && (
            <div className="rounded-2xl bg-gray-50 border border-gray-100 p-4 mb-6 animate-pulse">
              <div className="h-8 bg-gray-200 rounded w-1/4 mx-auto mb-2" />
              <div className="h-3 bg-gray-200 rounded w-1/2 mx-auto" />
            </div>
          )}

          {/* What happens info */}
          <div className="text-left space-y-3 mb-6 bg-gray-50 rounded-xl p-4">
            <p className="text-xs font-bold text-gray-400 uppercase tracking-widest">What happens next</p>
            {[
              ["🔒", "Your account is disabled and you cannot log in"],
              ["⏳", "After 7 days, your account and all data are permanently deleted"],
              ["↩️", "Log in within this window to automatically reactivate your account"],
            ].map(([icon, text]) => (
              <div key={text} className="flex items-start gap-2.5">
                <span className="text-base shrink-0">{icon}</span>
                <p className="text-xs text-gray-600 leading-relaxed">{text}</p>
              </div>
            ))}
          </div>

          {/* Reactivation CTA */}
          <div className="bg-blue-50 border border-blue-100 rounded-xl p-4 mb-6 text-left">
            <p className="text-xs font-bold text-blue-700 mb-1 flex items-center gap-1.5">
              <MdRefresh size={13} /> Want to reactivate?
            </p>
            <p className="text-xs text-blue-600 leading-relaxed">
              Simply log in with your credentials and your account will be automatically reactivated.
            </p>
          </div>

          <div className="space-y-2.5">
            <button
              onClick={() => navigate("/login")}
              className="w-full flex items-center justify-center gap-2 py-3 text-sm font-bold bg-gray-950 hover:bg-gray-800 text-white rounded-xl transition-[transform,opacity,background-color] cursor-pointer active:scale-[0.98]"
            >
              <MdLogin size={15} /> Log In to Reactivate
            </button>
          </div>
        </div>

        <p className="text-xs text-gray-400 text-center mt-6">
          Need help?{" "}
          <a href="mailto:support@mercato.in" className="text-blue-500 font-semibold hover:text-blue-700 transition-colors">
            Contact support
          </a>
        </p>
      </div>
    </main>
  );
};

export default AccountDeactivated;