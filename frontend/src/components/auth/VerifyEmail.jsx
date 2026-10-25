import { Spinner } from "../shared/Spinner";
import ActionArrow from "../shared/ActionArrow";
import { useEffect, useState } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import { MdCheckCircle, MdCancel, MdHourglassEmpty, MdEmail, MdRefresh } from "react-icons/md";
import toast from "../../utils/toastUtils.js";
import api from "../../backend/api";

const VerifyEmail = () => {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const token = searchParams.get("token");

    const [status, setStatus] = useState("loading");
    const [message, setMessage] = useState("");
    const [email, setEmail] = useState("");
    const [cooldown, setCooldown] = useState(0);
    const [resending, setResending] = useState(false);

    useEffect(() => {
        if (!token) {
            setStatus("error");
            setMessage("No verification token found in the link.");
            return;
        }
        const verify = async () => {
            try {
                const { data } = await api.post(`/auth/verify-email?token=${token}`);
                setMessage(data?.message || "Email verified successfully!");
                setStatus("success");
            } catch (err) {
                setStatus("error");
                setMessage(err?.response?.data?.message || "Verification failed. The link may have expired.");
            }
        };
        verify();
    }, [token]);

    useEffect(() => {
        if (cooldown <= 0) return;
        const t = setInterval(() => setCooldown(c => c - 1), 1000);
        return () => clearInterval(t);
    }, [cooldown]);

    const handleResend = async () => {
        if (!email) { toast.error("Enter your email to resend"); return; }
        setResending(true);
        try {
            await api.post(`/auth/resend-verification?email=${encodeURIComponent(email)}`);
            toast.info("If your account needs verification, an email is queued. Delivery is not yet confirmed.");
            setCooldown(30);
        } catch (err) {
            toast.error(err?.response?.data?.message || "Failed to resend email");
        } finally {
            setResending(false);
        }
    };

    const config = {
        loading: {
            icon: MdHourglassEmpty,
            iconBg: "bg-blue-50",
            iconColor: "text-blue-600",
            title: "Verifying your email...",
            sub: "Please wait, this will only take a moment.",
        },
        success: {
            icon: MdCheckCircle,
            iconBg: "bg-blue-50",
            iconColor: "text-blue-500",
            title: "Email Verified!",
            sub: message,
        },
        error: {
            icon: MdCancel,
            iconBg: "bg-red-50",
            iconColor: "text-red-400",
            title: "Verification Failed",
            sub: message,
        },
    }[status];

    const Icon = config.icon;

    return (
        <div className="bg-gray-50 min-h-screen flex items-center justify-center px-4">
            <div className="surface p-8 max-w-sm w-full text-center">
                <div className={`w-16 h-16 rounded-2xl ${config.iconBg} flex items-center justify-center mx-auto mb-5`}>
                    <Icon size={30} className={config.iconColor} />
                </div>
                <h1 className="text-xl font-semibold text-gray-900 mb-2">{config.title}</h1>
                <p className="text-sm text-gray-500 leading-relaxed mb-8">{config.sub}</p>

                {status === "success" && (
                    <button
                        onClick={() => navigate("/login")}
                        className="w-full flex items-center justify-center gap-2 py-3 text-sm font-bold bg-gray-950 hover:bg-gray-800 text-white rounded-xl transition-[transform,opacity,background-color] cursor-pointer "
                    >
                        Go to Login <ActionArrow size={15} />
                    </button>
                )}

                {status === "error" && (
                    <div className="space-y-3">
                        <p className="text-xs text-gray-400">
                            Enter your email to resend the verification link
                        </p>
                        <div className="flex items-center gap-2.5 border border-gray-200 rounded-xl px-3 py-2.5 focus-within:ring-1 focus-within:ring-blue-300">
                            <MdEmail size={15} className="text-gray-400 shrink-0" />
                            <input
                                type="email"
                                value={email}
                                onChange={e => setEmail(e.target.value)}
                                placeholder="your@email.com"
                                className="w-full text-sm text-gray-800 focus:outline-none placeholder-gray-300"
                            />
                        </div>
                        <button
                            onClick={handleResend}
                            disabled={!email || cooldown > 0 || resending}
                            className="w-full flex items-center justify-center gap-2 py-3 text-sm font-bold bg-gray-950 hover:bg-gray-800 text-white rounded-xl transition-[transform,opacity,background-color] cursor-pointer  disabled:bg-gray-100 disabled:text-gray-400 disabled:cursor-not-allowed"
                        >
                            {resending ? <Spinner size={15} /> : <MdRefresh size={15} />}
                            {resending ? "Sending..." : cooldown > 0 ? `Resend in ${cooldown}s` : "Resend Verification Email"}
                        </button>
                        {cooldown > 0 && (
                            <div className="flex items-center gap-1.5">
                                <div className="h-1 bg-gray-100 rounded-full flex-1 overflow-hidden">
                                    <div
                                        className="h-full bg-blue-400 rounded-full "
                                        style={{ width: `${((30 - cooldown) / 30) * 100}%` }}
                                    />
                                </div>
                                <span className="text-xs text-gray-400 shrink-0">{cooldown}s</span>
                            </div>
                        )}
                        <button
                            onClick={() => navigate("/login")}
                            className="w-full py-2.5 text-sm font-semibold text-gray-400 hover:text-gray-600 transition-colors cursor-pointer"
                        >
                            Back to Login
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
};

export default VerifyEmail;
