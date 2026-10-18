import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { MdEmail, MdRefresh, MdCheckCircle, MdArrowForward } from "react-icons/md";
import toast from "react-hot-toast";
import api from "../../backend/api";

const RESEND_COOLDOWN = 30;

const EmailVerificationPending = () => {
    const navigate = useNavigate();
    const location = useLocation();

    const email = location.state?.email ?? "";

    const [cooldown, setCooldown] = useState(0);
    const [resending, setResending] = useState(false);
    const [resendCount, setResendCount] = useState(0);

    useEffect(() => {
        if (cooldown <= 0) return;
        const t = setInterval(() => setCooldown(c => c - 1), 1000);
        return () => clearInterval(t);
    }, [cooldown]);

    const handleResend = async () => {
        if (!email) { toast.error("Email address not found. Please register again."); return; }
        setResending(true);
        try {
            await api.post(`/auth/resend-verification?email=${encodeURIComponent(email)}`);
            toast.success("Verification email sent!");
            setCooldown(RESEND_COOLDOWN);
            setResendCount(c => c + 1);
        } catch (err) {
            toast.error(err?.response?.data?.message || "Failed to resend email");
        } finally {
            setResending(false);
        }
    };

    const canResend = cooldown <= 0 && !resending;

    return (
        <div className="bg-gray-50 min-h-screen flex items-center justify-center px-4">
            <div className="surface p-8 max-w-md w-full text-center">

                {/* Icon */}
                <div className="w-16 h-16 rounded-2xl bg-blue-50 flex items-center justify-center mx-auto mb-5">
                    <MdEmail size={30} className="text-blue-400" />
                </div>

                <h1 className="text-xl font-black text-gray-900 mb-2">Check your inbox</h1>
                <p className="text-sm text-gray-500 leading-relaxed mb-1">
                    We sent a verification link to
                </p>
                {email && (
                    <p className="text-sm font-bold text-gray-900 mb-5">{email}</p>
                )}
                <p className="text-xs text-gray-400 mb-8 leading-relaxed">
                    Click the link in the email to verify your account.
                    If you don't see it, check your spam folder.
                </p>

                {/* Resend button */}
                <button
                    onClick={handleResend}
                    disabled={!canResend}
                    className="w-full flex items-center justify-center gap-2 py-3 text-sm font-bold rounded-xl transition-[transform,opacity,background-color] active:scale-[0.98] disabled:cursor-not-allowed
                        bg-gray-950 hover:bg-gray-800 text-white disabled:bg-gray-100 disabled:text-gray-400"
                >
                    <MdRefresh size={16} className={resending ? "animate-spin" : ""} />
                    {resending ? "Sending..." : cooldown > 0 ? `Resend in ${cooldown}s` : "Resend Verification Email"}
                </button>

                {/* Cooldown hint */}
                {cooldown > 0 && (
                    <div className="mt-3 flex items-center justify-center gap-1.5">
                        <div className="h-1 bg-gray-100 rounded-full flex-1 overflow-hidden">
                            <div
                                className="h-full bg-blue-400 rounded-full transition-[transform,opacity,background-color] duration-1000"
                                style={{ width: `${((RESEND_COOLDOWN - cooldown) / RESEND_COOLDOWN) * 100}%` }}
                            />
                        </div>
                        <span className="text-[11px] text-gray-400 shrink-0 font-medium">{cooldown}s</span>
                    </div>
                )}

                {resendCount > 0 && (
                    <p className="text-[11px] text-blue-700 font-medium mt-3 flex items-center justify-center gap-1">
                        <MdCheckCircle size={13} /> Email sent {resendCount} time{resendCount > 1 ? "s" : ""}
                    </p>
                )}

                <div className="mt-8 pt-6 border-t border-gray-100">
                    <button
                        onClick={() => navigate("/login")}
                        className="flex items-center gap-1.5 text-sm text-blue-500 hover:text-blue-700 font-semibold cursor-pointer transition-colors mx-auto"
                    >
                        Go to Login <MdArrowForward size={15} />
                    </button>
                </div>
            </div>
        </div>
    );
};

export default EmailVerificationPending;