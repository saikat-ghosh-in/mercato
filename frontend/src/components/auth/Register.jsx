import ActionArrow from "../shared/ActionArrow";
import { useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { useDispatch } from "react-redux";
import { Link, useNavigate } from "react-router-dom";
import { FiArrowRight, FiCheck, FiEye, FiEyeOff, FiPackage, FiShoppingBag, FiTruck } from "react-icons/fi";
import Field from "../shared/AuthField";
import SelectControl from "../shared/SelectControl";
import { registerNewUser } from "../../reduxStore/actions/authActions";
import { usernameRules, emailRules, phoneRules, passwordRules } from "../../utils/validationRules";

const required = max => ({ required: "This field is required", maxLength: { value: max, message: `Use ${max} characters or fewer` }, validate: value => Boolean(value?.trim()) || "This field is required" });
const inputClass = "registration-input";
const businessFields = [["Store name", "storeName", 100], ["Legal business name", "legalBusinessName", 150], ["Business support email", "supportEmail", 100], ["Product categories", "productCategories", 300], ["Pickup address", "pickupAddress", 500], ["Pickup state / union territory", "pickupState", 80]];
const businessInfo = {
  storeName: "The name shoppers will see on your products and storefront. It can differ from your registered business name.",
  legalBusinessName: "Use the legal name shown on your business and tax records so the review team can match your application.",
  supportEmail: "A monitored business inbox for marketplace and customer support matters. It can differ from your sign-in email.",
  productCategories: "List the kinds of products you plan to sell, such as home accessories or electronics. The team reviews category eligibility.",
  pickupAddress: "The location where Mercato’s delivery partner will collect packed orders. Include building and street details.",
  pickupState: "The state or union territory of your pickup location. This is used when reviewing delivery and tax eligibility.",
};

export default function Register() {
  const dispatch = useDispatch(); const navigate = useNavigate();
  const [accountType, setAccountType] = useState("CUSTOMER");
  const [showPassword, setShowPassword] = useState(false);
  const { register, handleSubmit, reset, control, getValues, formState: { errors, isSubmitting }, setError } = useForm({ mode: "onTouched", shouldUnregister: true, defaultValues: { sellerApplication: { taxRegistrationType: "GST_REGISTERED", businessType: "SOLE_PROPRIETOR" } } });
  const taxType = useWatch({ control, name: "sellerApplication.taxRegistrationType", defaultValue: "GST_REGISTERED" });
  const seller = accountType === "SELLER";
  const onSubmit = async data => {
    const body = { ...data, accountType };
    if (!seller) delete body.sellerApplication;
    else body.sellerApplication.policyVersion = "2026-10-draft";
    try { await dispatch(registerNewUser(body, reset, navigate)); }
    catch (error) { setError("root", { message: error?.response?.data?.message || "We couldn’t create your account. Your details are retained; please try again." }); }
  };
  return <div className="site-container registration-page">
    <aside className="registration-story"><h1>{seller ? <>Sell on Mercato</> : <>Your Mercato account</>}</h1><p>{seller ? "Bring your products to Mercato. We help with discovery and arrange delivery, while you focus on what you do best." : "Save your favourites, keep track of deliveries and make room for your next discovery."}</p><ul>{(seller ? [[FiPackage, "Your products, your storefront"], [FiTruck, "Shipping arranged by Mercato"], [FiCheck, "Verified sellers. Transparent fees."]] : [[FiShoppingBag, "A marketplace of independent sellers"], [FiTruck, "Delivery choices that suit your day"], [FiCheck, "Track orders and keep your favourites"]]).map(([Icon, text]) => <li key={text}><Icon aria-hidden="true" />{text}</li>)}</ul><Link to="/seller-policies" className="text-link">{seller ? "Read seller policies & fees" : "Thinking about selling? Meet Mercato."} <ActionArrow /></Link></aside>
    <section className="registration-form-panel" aria-label="Create your account"><div className="account-type-switch" aria-label="Account type">{[["CUSTOMER", "I’m shopping"], ["SELLER", "I’m selling"]].map(([value, label]) => <button type="button" key={value} aria-pressed={accountType === value} disabled={isSubmitting} onClick={() => setAccountType(value)}>{label}</button>)}</div><h2>{seller ? "Apply to sell" : "Create your account"}</h2><p>{seller ? "Create your login and share your business details. Email verification and admin approval are required before selling." : "Your next good find starts here."}</p>
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="registration-form">
      <div className="registration-name-row"><Field label="First name" required error={errors.firstName?.message}><input {...register("firstName", required(50))} autoComplete="given-name" className={inputClass} /></Field><Field label="Last name" error={errors.lastName?.message}><input {...register("lastName", { maxLength: 50 })} autoComplete="family-name" className={inputClass} /></Field></div>
      <Field label="Username" required error={errors.username?.message}><input {...register("username", usernameRules)} autoComplete="username" autoCapitalize="none" className={inputClass} /></Field>
      <Field label="Email" required error={errors.email?.message}><input {...register("email", { ...emailRules, maxLength: 100 })} type="email" autoComplete="email" className={inputClass} /></Field>
      <Field label="Phone number" required error={errors.phoneNumber?.message}><span>+91</span><input {...register("phoneNumber", phoneRules)} type="tel" inputMode="numeric" maxLength={10} autoComplete="tel-national" className={inputClass} /></Field>
      <Field label="Password" required error={errors.password?.message} hint="At least 8 characters, with uppercase, lowercase and a number."><input {...register("password", { ...passwordRules, maxLength: 120, deps: ["confirmPassword"] })} type={showPassword ? "text" : "password"} autoComplete="new-password" className={inputClass} /><button type="button" className="icon-action" aria-label={showPassword ? "Hide passwords" : "Show passwords"} onClick={() => setShowPassword(v => !v)}>{showPassword ? <FiEyeOff /> : <FiEye />}</button></Field>
      <Field label="Confirm password" required error={errors.confirmPassword?.message}><input {...register("confirmPassword", { required: "Confirm your password", validate: value => value === getValues("password") || "Passwords do not match" })} type={showPassword ? "text" : "password"} autoComplete="new-password" className={inputClass} /></Field>
      {seller && <fieldset className="seller-registration-fields"><legend>Your business</legend><p>Use your registered details. The team will verify them before approving your store. Do not enter bank passwords, card details or Aadhaar numbers.</p>
        {businessFields.map(([label, name, max]) => <Field key={name} label={label} info={businessInfo[name]} required error={errors.sellerApplication?.[name]?.message}><input {...register(`sellerApplication.${name}`, name === "supportEmail" ? { ...emailRules, maxLength: max } : required(max))} type={name === "supportEmail" ? "email" : "text"} maxLength={max} className={inputClass} /></Field>)}
        <Controller name="sellerApplication.businessType" control={control} rules={{ required: "Choose a business type" }} render={({ field }) => <Field label="Business type" info="Choose the legal structure shown on your business records. Select sole proprietor if you operate the business in your own name." required error={errors.sellerApplication?.businessType?.message}><SelectControl {...field} onChange={e => field.onChange(e.target.value)} aria-label="Business type">{[["SOLE_PROPRIETOR", "Sole proprietor"], ["PARTNERSHIP", "Partnership"], ["COMPANY", "Company"], ["LLP", "Limited liability partnership"], ["OTHER", "Other registered entity"]].map(([value, label]) => <option key={value} value={value}>{label}</option>)}</SelectControl></Field>} />
        <Field label="PAN" required error={errors.sellerApplication?.pan?.message} info="Use the 10-character business PAN, or proprietor PAN for a sole proprietorship. It must match the PAN embedded in the GSTIN. These details are restricted to application review."><input {...register("sellerApplication.pan", { required: "PAN is required", pattern: { value: /^[A-Z]{5}[0-9]{4}[A-Z]$/, message: "Enter a valid PAN format" }, setValueAs: value => value.trim().toUpperCase() })} maxLength={10} autoCapitalize="characters" className={inputClass} /></Field>
        <Controller name="sellerApplication.taxRegistrationType" control={control} rules={{ required: "Choose a tax registration" }} render={({ field }) => <Field label="Tax registration" info="Select your current GST registration status. Enrolled, unregistered applications require a portal enrolment number and a separate eligibility review before selling." required error={errors.sellerApplication?.taxRegistrationType?.message}><SelectControl {...field} onChange={e => field.onChange(e.target.value)} aria-label="Tax registration"><option value="GST_REGISTERED">GST registered</option><option value="ENROLLED_UNREGISTERED">GST portal enrolled, unregistered</option></SelectControl></Field>} />
        {taxType === "ENROLLED_UNREGISTERED" ? <Field label="GST portal enrolment number" required info="Enter the enrolment number issued by the GST portal. The team must verify eligibility; approved unregistered stores are restricted to their pickup state." error={errors.sellerApplication?.enrolmentNumber?.message}><input {...register("sellerApplication.enrolmentNumber", { required: "Enrolment number is required", pattern: { value: /^[A-Za-z0-9/-]{6,30}$/, message: "Use 6–30 letters, digits, slash or hyphen" } })} className={inputClass} /></Field> : <Field label="GSTIN" info="Enter your 15-character GST identification number exactly as registered. It must contain the same PAN entered above; formatting checks do not verify the government record." required error={errors.sellerApplication?.gstin?.message}><input {...register("sellerApplication.gstin", { required: "GSTIN is required", setValueAs: value => value.trim().toUpperCase(), validate: value => /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/.test(value) && value.slice(2,12) === getValues("sellerApplication.pan") || "Enter a valid GSTIN matching your PAN" })} maxLength={15} autoCapitalize="characters" className={inputClass} /></Field>}
        <Field label="Pickup pincode" info="Use the six-digit pincode for the pickup address, so the team can review delivery serviceability." required error={errors.sellerApplication?.pickupPincode?.message}><input {...register("sellerApplication.pickupPincode", { required: "Pincode is required", pattern: { value: /^[1-9][0-9]{5}$/, message: "Enter a valid 6-digit pincode" } })} inputMode="numeric" maxLength={6} className={inputClass} /></Field>
        <label className="policy-acknowledgment"><input type="checkbox" {...register("sellerApplication.policiesAcknowledged", { required: "Please review the seller policies" })} /><span>I have reviewed the <Link to="/seller-policies" target="_blank" rel="noreferrer">seller policies, fees, shipping and returns</Link> (draft 2026-10). I understand approval is required and the launch agreement must be finalized before live selling.</span></label>{errors.sellerApplication?.policiesAcknowledged && <p className="inline-error" role="alert">{errors.sellerApplication.policiesAcknowledged.message}</p>}
      </fieldset>}
      {errors.root && <p className="inline-error" role="alert">{errors.root.message}</p>}
      <button className="button button-dark button-prominent w-full" disabled={isSubmitting} type="submit">{isSubmitting ? "Creating account…" : seller ? "Submit seller application" : "Create account"}<FiArrowRight /></button>
      <p className="registration-login">Already part of Mercato? <Link to="/login">Sign in</Link></p>
    </form></section>
  </div>;
}
