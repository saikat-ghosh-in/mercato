const profileKeys = ["firstName", "lastName", "phoneNumber", "profileImageUrl", "sellerDisplayName"];
export const validatedOrderId = value => typeof value === "string" && /^[A-Za-z0-9_-]{1,100}$/.test(value) ? value : null;
export function profileDraft(draft, user) {
  if (!user) return null;
  const userId = user.userId ?? user.id ?? user.username;
  if (draft?.userId === userId && draft.dirty) return draft;
  return { userId, dirty: false, form: Object.fromEntries(profileKeys.map(key => [key, user[key] ?? ""])) };
}

export function mfaAction(state, code, recovery = false, replace = false) {
  code = code.trim();
  if (!recovery && !(replace && !code) && !/^\d{6}$/.test(code)) throw Error("Enter the six-digit verification code.");
  if (recovery && !code) throw Error("Enter a recovery code.");
  const url = replace ? "/auth/mfa/replace" : state === "STEP_UP_REQUIRED" ? "/auth/step-up/verify" : recovery ? "/auth/mfa/recovery" : "/auth/mfa/verify";
  return { url, data: { code } };
}

export function recoveredPayment(data, orderId) {
  if (String(data?.orderId) !== String(orderId) || data.canResume !== true ||
      typeof data.paymentSessionId !== "string" || !data.paymentSessionId.trim() ||
      !Number.isFinite(data.totalAmount) || data.totalAmount < 0) {
    throw Error("This payment couldn’t be resumed. Check your order status before trying again.");
  }
  return data;
}
