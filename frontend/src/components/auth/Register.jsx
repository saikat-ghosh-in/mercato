import Field from "../shared/AuthField";
import { Link, useNavigate } from "react-router-dom";
import { FiUserPlus, FiUser, FiMail, FiLock, FiEye, FiEyeOff, FiPhone } from "react-icons/fi";
import { useForm, useWatch } from "react-hook-form";
import { useState } from "react";
import { useDispatch } from "react-redux";
import { registerNewUser } from "../../reduxStore/actions/authActions";
import {
    usernameRules,
    emailRules,
    passwordRules,
    phoneRules,
} from "../../utils/validationRules";

const inputCls = "w-full text-sm text-gray-800 focus:outline-none bg-transparent placeholder-gray-300";


const Register = () => {
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const [showPassword, setShowPassword] = useState(false);

    const {
        register,
        handleSubmit,
        reset,
        control,
        trigger,
        formState: { errors, isSubmitting, isValid },
    } = useForm({ mode: "onTouched", criteriaMode: "all" });

    const passwordValue = useWatch({ control, name: "password", defaultValue: "" });
    const passwordErrors = errors.password
        ? Object.values(errors.password.types ?? {})
            .filter(Boolean)
            .filter(v => v !== true)
        : [];

    const onSubmit = (data) => {
        return dispatch(registerNewUser(data, reset, navigate));
    };

    return (
        <div className="min-h-screen bg-gray-50 flex items-center justify-center px-4 py-10">
            <div className="w-full max-w-md">

                {/* Brand */}
                <div className="text-center mb-8">
                    <h1 className="text-2xl font-black text-gray-950 tracking-tight">Create an account</h1>
                    <p className="text-sm text-gray-400 mt-1">Join Mercato and start shopping</p>
                </div>

                <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-7">
                    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">

                        {/* Name row */}
                        <div className="grid grid-cols-2 gap-3">
                            <Field label="First Name" error={errors.firstName?.message} icon={FiUser}>
                                <input
                                    {...register("firstName", {
                                        maxLength: { value: 50, message: "Max 50 characters" }
                                    })}
                                    placeholder="John"
                                    className={inputCls}
                                />
                            </Field>
                            <Field label="Last Name" error={errors.lastName?.message}>
                                <input
                                    {...register("lastName", {
                                        maxLength: { value: 50, message: "Max 50 characters" }
                                    })}
                                    placeholder="Doe"
                                    className={inputCls}
                                />
                            </Field>
                        </div>

                        {/* Username */}
                        <Field label="Username" required error={errors.username?.message} icon={FiUser}>
                            <input
                                {...register("username", usernameRules)}
                                placeholder="johndoe"
                                autoComplete="username"
                                className={inputCls}
                            />
                        </Field>

                        {/* Email */}
                        <Field label="Email" required error={errors.email?.message} icon={FiMail}>
                            <input
                                {...register("email", emailRules)}
                                type="email"
                                placeholder="john@example.com"
                                autoComplete="email"
                                className={inputCls}
                            />
                        </Field>

                        {/* Phone */}
                        <Field label="Phone Number" error={errors.phoneNumber?.message} icon={FiPhone} required>
                            <span className="text-sm font-semibold text-gray-500 shrink-0 select-none">+91</span>
                            <div className="w-px h-4 bg-gray-200 shrink-0" />
                            <input
                                {...register("phoneNumber", phoneRules)}
                                type="tel"
                                placeholder="9876543210"
                                autoComplete="tel"
                                maxLength={10}
                                onInput={(e) => {
                                    e.target.value = e.target.value.replace(/[^0-9]/g, "").slice(0, 10);
                                }}
                                className={inputCls}
                            />
                        </Field>

                        {/* Password */}
                        <Field
                            label="Password"
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
                                placeholder="Enter a password"
                                autoComplete="new-password"
                                className={inputCls}
                            />
                            {passwordValue?.length > 0 && (
                                <button
                                    type="button"
                                    onClick={() => setShowPassword(v => !v)} aria-label={showPassword ? "Hide password" : "Show password"}
                                    className="text-gray-400 hover:text-gray-600 transition-colors cursor-pointer shrink-0"
                                >
                                    {showPassword ? <FiEyeOff size={15} /> : <FiEye size={15} />}
                                </button>
                            )}
                        </Field>

                        {/* Submit */}
                        <button
                            type="submit"
                            disabled={!isValid || isSubmitting}
                            className="w-full mt-2 flex items-center justify-center gap-2 bg-gray-950 hover:bg-gray-800 active:scale-[0.98]
                            disabled:bg-gray-200 disabled:text-gray-400 disabled:cursor-not-allowed text-white text-sm font-bold py-3 rounded-xl transition-all cursor-pointer"
                        >
                            <FiUserPlus size={15} />
                            {isSubmitting ? "Creating account..." : "Create Account"}
                        </button>
                    </form>

                    <div className="mt-6 pt-5 border-t border-gray-100 text-center">
                        <p className="text-sm text-gray-500">
                            Already have an account?{" "}
                            <Link to="/login" className="text-blue-500 font-semibold hover:text-blue-700 transition-colors">
                                Sign in
                            </Link>
                        </p>
                    </div>

                    <div className="mt-6 pt-5 border-t border-gray-100">
                        <p className="text-xs font-semibold text-gray-400 uppercase tracking-widest text-center mb-3">Want to sell?</p>
                        <button
                            type="button"
                            disabled
                            className="w-full px-4 py-2.5 border-2 border-dashed border-amber-300 bg-amber-50 rounded-xl text-sm font-bold text-amber-700 hover:bg-amber-100 transition-colors disabled:opacity-75 cursor-not-allowed"
                        >
                            🏪 Become a Seller - Coming Soon!
                        </button>
                        <p className="text-xs text-gray-400 text-center mt-2">Join us as a seller to reach millions of customers. Seller registration coming soon!</p>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default Register;
