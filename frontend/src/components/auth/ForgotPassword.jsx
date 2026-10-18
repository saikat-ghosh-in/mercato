import Field from "../shared/AuthField";
import { Link } from "react-router-dom";
import { FiMail, FiArrowLeft } from "react-icons/fi";
import { useForm } from "react-hook-form";
import { useState } from "react";
import toast from "react-hot-toast";
import api from "../../backend/api";

const inputCls = "w-full text-sm text-gray-800 focus:outline-none bg-transparent placeholder-gray-300";

const ForgotPassword = () => {
    const [submitted, setSubmitted] = useState(false);
    const {
        register,
        handleSubmit,
        formState: { errors, isSubmitting },
    } = useForm({ mode: "onTouched" });

    const onSubmit = async (data) => {
        try {
            await api.post("/auth/forgot-password", { email: data.email });
            setSubmitted(true);
            toast.success("Check your inbox for a reset link");
        } catch (err) {
            toast.error(err?.response?.data?.message || "Failed to send reset email");
        }
    };

    return (
        <div className="bg-gray-50 min-h-screen flex items-center justify-center py-10 px-4">
            <div className="w-full max-w-md">
                <Link
                    to="/login"
                    className="inline-flex items-center gap-1.5 text-xs font-semibold text-gray-400 hover:text-blue-500 mb-6 transition-colors"
                >
                    <FiArrowLeft size={14} /> Back to login
                </Link>

                <div className="surface p-8">
                    <h1 className="text-xl font-bold text-gray-900 mb-1">Forgot password?</h1>
                    <p className="text-sm text-gray-500 mb-6">
                        Enter your email and we&apos;ll send you a link to reset your password.
                    </p>

                    {submitted ? (
                        <div className="text-center py-4">
                            <p className="text-sm text-gray-600">
                                If that email is registered, you&apos;ll receive a reset link shortly.
                            </p>
                        </div>
                    ) : (
                        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
                            <Field label="Email" error={errors.email?.message} icon={FiMail} required>
                                <input
                                    {...register("email", {
                                        required: "Email is required",
                                        pattern: {
                                            value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
                                            message: "Enter a valid email",
                                        },
                                    })}
                                    type="email"
                                    placeholder="you@example.com"
                                    autoComplete="email"
                                    className={inputCls}
                                />
                            </Field>

                            <button
                                type="submit"
                                disabled={isSubmitting}
                                className="w-full py-3 bg-gray-950 hover:bg-gray-800 disabled:opacity-50 text-white text-sm font-bold rounded-xl transition-[transform,opacity,background-color] active:scale-[0.98]"
                            >
                                {isSubmitting ? "Sending..." : "Send Reset Link"}
                            </button>
                        </form>
                    )}
                </div>
            </div>
        </div>
    );
};

export default ForgotPassword;
