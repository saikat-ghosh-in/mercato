import { useDispatch } from "react-redux";
import { useForm, Controller } from "react-hook-form";
import { MdLocationOn } from "react-icons/md";
import { addUpdateUserAddress } from "../../reduxStore/actions/addressActions";

const Label = ({ children, required }) => (
  <label className="block text-xs font-semibold text-gray-600 mb-1.5">
    {children}
    {required && <span className="text-red-400 ml-0.5">*</span>}
  </label>
);

const InputField = ({ field, placeholder, error, type = "text", prefix, maxLength, inputMode }) => (
  <div className={`flex items-center border rounded-xl overflow-hidden transition-[transform,opacity,background-color]
        ${error ? "border-red-300 bg-red-50 focus-within:ring-1 focus-within:ring-red-400" : "border-gray-200 bg-white focus-within:border-blue-400 focus-within:ring-1 focus-within:ring-blue-400"}`}
  >
    {prefix && (
      <span className="px-3 text-xs font-semibold text-gray-400 border-r border-gray-200 bg-gray-50 py-2.5 shrink-0">
        {prefix}
      </span>
    )}
    <input
      {...field}
      aria-label={{ recipientName: "Recipient name", recipientPhone: "Phone number", addressLine1: "Address line 1", addressLine2: "Address line 2", city: "City", state: "State", pincode: "Pincode", country: "Country" }[field.name]}
      aria-invalid={Boolean(error)}
      type={type}
      placeholder={placeholder}
      maxLength={maxLength}
      inputMode={inputMode}
      className="w-full px-3 py-2.5 text-sm text-gray-900 bg-transparent focus:outline-none placeholder:text-gray-300"
    />
  </div>
);

const FieldError = ({ message }) =>
  message ? <p className="mt-1 text-[11px] text-red-500">{message}</p> : null;


const AddAddressForm = ({ address, setOpenAddressModal, setLoadingAddresses }) => {
  const dispatch = useDispatch();

  const { control, handleSubmit, formState: { errors, isSubmitting: isLoading } } = useForm({
    mode: "onTouched",
    defaultValues: {
      recipientName: address?.recipientName || "",
      recipientPhone: address?.recipientPhone || "",
      addressLine1: address?.addressLine1 || "",
      addressLine2: address?.addressLine2 || "",
      city: address?.city || "",
      state: address?.state || "",
      pincode: address?.pincode || "",
      country: address?.country || "India",
    },
  });

  const onSubmit = (data) => {
    return dispatch(addUpdateUserAddress(data, address?.addressId, setOpenAddressModal, setLoadingAddresses));
  };

  const isEditing = !!address?.addressId;

  return (
    <div>
      {/* Header */}
      <div className="flex items-center gap-2.5 mb-6">
        <div className="p-2 bg-blue-50 rounded-xl">
          <MdLocationOn size={18} className="text-blue-500" />
        </div>
        <h2 className="text-base font-bold text-gray-900">
          {isEditing ? "Edit Address" : "Add New Address"}
        </h2>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">

          {/* Recipient Name */}
          <div>
            <Label required>Recipient Name</Label>
            <Controller
              name="recipientName"
              control={control}
              rules={{ required: "Recipient name is required" }}
              render={({ field }) => (
                <InputField field={field} placeholder="Full name" error={errors.recipientName} />
              )}
            />
            <FieldError message={errors.recipientName?.message} />
          </div>

          {/* Phone */}
          <div>
            <Label required>Phone Number</Label>
            <Controller
              name="recipientPhone"
              control={control}
              rules={{
                required: "Phone number is required",
                pattern: { value: /^[0-9]{10}$/, message: "Enter a valid 10-digit number" },
              }}
              render={({ field }) => (
                <InputField
                  field={field}
                  placeholder="10-digit number"
                  error={errors.recipientPhone}
                  prefix="+91"
                  maxLength={10}
                  inputMode="numeric"
                />
              )}
            />
            <FieldError message={errors.recipientPhone?.message} />
          </div>

          {/* Address Line 1 */}
          <div className="sm:col-span-2">
            <Label required>Address Line 1</Label>
            <Controller
              name="addressLine1"
              control={control}
              rules={{ required: "Address is required" }}
              render={({ field }) => (
                <InputField field={field} placeholder="Street address" error={errors.addressLine1} />
              )}
            />
            <FieldError message={errors.addressLine1?.message} />
          </div>

          {/* Address Line 2 */}
          <div className="sm:col-span-2">
            <Label>Address Line 2 <span className="text-gray-300 font-normal">(optional)</span></Label>
            <Controller
              name="addressLine2"
              control={control}
              render={({ field }) => (
                <InputField field={field} placeholder="Apt, suite, floor" />
              )}
            />
          </div>

          {/* City */}
          <div>
            <Label required>City</Label>
            <Controller
              name="city"
              control={control}
              rules={{ required: "City is required" }}
              render={({ field }) => (
                <InputField field={field} placeholder="City" error={errors.city} />
              )}
            />
            <FieldError message={errors.city?.message} />
          </div>

          {/* State */}
          <div>
            <Label required>State</Label>
            <Controller
              name="state"
              control={control}
              rules={{ required: "State is required" }}
              render={({ field }) => (
                <InputField field={field} placeholder="State / Province" error={errors.state} />
              )}
            />
            <FieldError message={errors.state?.message} />
          </div>

          {/* Pincode */}
          <div>
            <Label required>Pincode</Label>
            <Controller
              name="pincode"
              control={control}
              rules={{ required: "Pincode is required", pattern: { value: /^[1-9][0-9]{5}$/, message: "Enter a valid 6-digit Indian pincode" } }}
              render={({ field }) => (
                <InputField field={field} placeholder="Postal code" error={errors.pincode} inputMode="numeric" />
              )}
            />
            <FieldError message={errors.pincode?.message} />
          </div>

          {/* Country */}
          <div>
            <Label required>Country</Label>
            <Controller
              name="country"
              control={control}
              rules={{ required: "Country is required" }}
              render={({ field }) => (
                <InputField field={field} placeholder="Country" error={errors.country} />
              )}
            />
            <FieldError message={errors.country?.message} />
          </div>
        </div>

        {/* Actions */}
        <div className="flex justify-end gap-3 mt-6 pt-4 border-t border-gray-100">
          <button
            type="button"
            onClick={() => setOpenAddressModal(false)}
            className="px-5 py-2.5 text-sm font-semibold text-gray-600 bg-white border border-gray-200 hover:border-gray-300 rounded-xl transition-colors"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={isLoading}
            className="flex items-center gap-2 px-6 py-2.5 text-sm font-bold text-white bg-blue-500 hover:bg-blue-600 active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed rounded-xl transition-[transform,opacity,background-color] shadow-sm"
          >
            {isLoading ? (
              <>
                <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
                </svg>
                Saving...
              </>
            ) : isEditing ? "Update Address" : "Save Address"}
          </button>
        </div>
      </form>
    </div>
  );
};

export default AddAddressForm;
