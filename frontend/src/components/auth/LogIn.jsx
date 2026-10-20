import Field from "../shared/AuthField";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { FiLogIn, FiUser, FiLock, FiEye, FiEyeOff } from "react-icons/fi";
import { useForm, useWatch } from "react-hook-form";
import { useState } from "react";
import { useDispatch } from "react-redux";
import { authenticateUser } from "../../reduxStore/actions/authActions";

const inputCls = "w-full text-sm text-gray-800 focus:outline-none bg-transparent placeholder-gray-300";


const Login = () => {
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const location = useLocation();

    const redirect = new URLSearchParams(location.search).get("redirect") || "/";
    const [showPassword, setShowPassword] = useState(false);

    const {
        register,
        handleSubmit,
        reset,
        control,
        formState: { errors, isSubmitting, isValid },
    } = useForm({ mode: "onTouched" });

    const passwordValue = useWatch({ control, name: "password", defaultValue: "" });

    const onSubmit = (data) => {
        return dispatch(authenticateUser(data, navigate, reset, redirect));
    };

    return (
        <div className="min-h-screen bg-gray-50 flex items-center justify-center px-4 py-10">
            <div className="w-full max-w-md">

                {/* Brand */}
                <div className="text-center mb-8">
                    <h1 className="text-2xl font-black text-gray-950 tracking-tight">Welcome back</h1>
                    <p className="text-sm text-gray-400 mt-1">Sign in to your Mercato account</p>
                </div>

                <div className="surface p-7">
                    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">

                        {/* Username */}
                        <Field label="Username" error={errors.username?.message} icon={FiUser} required>
                            <input
                                {...register("username", { required: "Username is required" })}
                                placeholder="Your username"
                                autoComplete="username"
                                className={inputCls}
                            />
                        </Field>

                        {/* Password */}
                        <Field label="Password" error={errors.password?.message} icon={FiLock} required>
                            <input
                                {...register("password", { required: "Password is required" })}
                                type={showPassword ? "text" : "password"}
                                placeholder="••••••••"
                                autoComplete="current-password"
                                className={inputCls}
                            />
                            {passwordValue?.length > 0 && (
                                <button
                                    type="button"
                                    onClick={() => setShowPassword(v => !v)} aria-label={showPassword ? "Hide password" : "Show password"}
                                    className="icon-action text-gray-400 hover:text-gray-600 transition-colors cursor-pointer shrink-0"
                                >
                                    {showPassword ? <FiEyeOff size={15} /> : <FiEye size={15} />}
                                </button>
                            )}
                        </Field>

                        {/* Forgot password */}
                        <div className="flex justify-end -mt-2">
                            <Link
                                to="/forgot-password"
                                className="text-xs text-blue-500 hover:text-blue-700 font-semibold transition-colors"
                            >
                                Forgot password?
                            </Link>
                        </div>

                        {/* Submit */}
                        <button
                            type="submit"
                            disabled={!isValid || isSubmitting}
                            className="control control-prominent w-full flex items-center justify-center gap-2 bg-gray-950 hover:bg-gray-800 active:scale-[0.98] disabled:bg-gray-200 disabled:text-gray-400 disabled:cursor-not-allowed text-white text-sm font-bold py-3 rounded-xl transition-[transform,opacity,background-color] cursor-pointer"
                        >
                            <FiLogIn size={15} />
                            {isSubmitting ? "Signing in..." : "Sign In"}
                        </button>
                    </form>

                    <div className="mt-6 pt-5 border-t border-gray-100 text-center">
                        <p className="text-sm text-gray-500">
                            Don't have an account?{" "}
                            <Link to="/register" className="text-blue-500 font-semibold hover:text-blue-700 transition-colors">
                                Create one
                            </Link>
                        </p>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default Login;
