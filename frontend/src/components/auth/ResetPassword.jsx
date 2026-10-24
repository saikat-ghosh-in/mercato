import Field from "../shared/AuthField";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { FiLock, FiEye, FiEyeOff, FiArrowLeft } from "react-icons/fi";
import { useForm, useWatch } from "react-hook-form";
import { useState } from "react";
import toast from "react-hot-toast";
import api, { session } from "../../backend/api";
import { passwordRules } from "../../utils/validationRules";

const inputCls = "w-full text-sm text-gray-800 focus:outline-none bg-transparent placeholder-gray-300";

const ResetPassword = () => {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const token = searchParams.get("token");
    const [showPassword, setShowPassword] = useState(false);
    const [showConfirm, setShowConfirm] = useState(false);

    const {
        register,
        handleSubmit,
        control,
        trigger,
        formState: { errors, isSubmitting },
    } = useForm({ mode: "onTouched" });

    const passwordValue = useWatch({ control, name: "password", defaultValue: "" });
    const confirmValue = useWatch({ control, name: "confirmPassword", defaultValue: "" });
    const passwordErrors = errors.password
        ? Object.values(errors.password.types ?? {})
        : [];

    const onSubmit = async (data) => {
        if (!token) {
            toast.error("Reset link is invalid");
            return;
        }
        try {
            await api.post("/auth/reset-password", {
                token,
                password: data.password,
            });
            await session.refresh({ broadcast: true });
            toast.success("Password updated. Please sign in.");
            navigate("/login");
        } catch (err) {
            toast.error(err?.response?.data?.message || "Failed to reset password");
        }
    };

    if (!token) {
        return (
            <div className="bg-gray-50 min-h-screen flex items-center justify-center py-10 px-4">
                <div className="w-full max-w-md surface p-8 text-center">
                    <h1 className="text-lg font-bold text-gray-900 mb-2">Invalid reset link</h1>
                    <p className="text-sm text-gray-500 mb-6">Request a new password reset link to continue.</p>
                    <Link
                        to="/forgot-password"
                        className="control inline-block py-3 px-6 bg-blue-500 hover:bg-blue-600 text-white text-sm font-bold rounded-xl transition-colors"
                    >
                        Request new link
                    </Link>
                </div>
            </div>
        );
    }

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
                    <h1 className="text-xl font-bold text-gray-900 mb-1">Set a new password</h1>
                    <p className="text-sm text-gray-500 mb-6">Choose a strong password for your account.</p>

                    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
                        <Field
                            label="New Password"
                            errors={passwordErrors}
                            icon={FiLock}
                            required
                        >
                            <input
                                {...register("password", {
                                    ...passwordRules,
                                    onChange: () => trigger("password"),
                                })}
                                type={showPassword ? "text" : "password"}
                                placeholder="New password"
                                autoComplete="new-password"
                                className={inputCls}
                            />
                            {passwordValue?.length > 0 && (
                                <button
                                    type="button"
                                    onClick={() => setShowPassword((v) => !v)} aria-label={showPassword ? "Hide password" : "Show password"}
                                    className="icon-action text-gray-400 hover:text-gray-600 transition-colors cursor-pointer shrink-0"
                                >
                                    {showPassword ? <FiEyeOff size={15} /> : <FiEye size={15} />}
                                </button>
                            )}
                        </Field>

                        <Field
                            label="Confirm Password"
                            error={errors.confirmPassword?.message}
                            icon={FiLock}
                            required
                        >
                            <input
                                {...register("confirmPassword", {
                                    required: "Please confirm your password",
                                    validate: (value) =>
                                        value === passwordValue || "Passwords do not match",
                                })}
                                type={showConfirm ? "text" : "password"}
                                placeholder="Repeat password"
                                autoComplete="new-password"
                                className={inputCls}
                            />
                            {confirmValue.length > 0 && (
                                <button
                                    type="button"
                                    onClick={() => setShowConfirm((v) => !v)} aria-label={showConfirm ? "Hide confirmation password" : "Show confirmation password"}
                                    className="icon-action text-gray-400 hover:text-gray-600 transition-colors cursor-pointer shrink-0"
                                >
                                    {showConfirm ? <FiEyeOff size={15} /> : <FiEye size={15} />}
                                </button>
                            )}
                        </Field>

                        <button
                            type="submit"
                            disabled={isSubmitting}
                            className="control control-prominent w-full py-3 bg-gray-950 hover:bg-gray-800 disabled:opacity-50 text-white text-sm font-bold rounded-xl transition-[transform,opacity,background-color] active:scale-[0.98]"
                        >
                            {isSubmitting ? "Updating..." : "Update Password"}
                        </button>
                    </form>
                </div>
            </div>
        </div>
    );
};

export default ResetPassword;
