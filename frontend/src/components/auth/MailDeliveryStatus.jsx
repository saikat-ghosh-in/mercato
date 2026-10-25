const copy = {
  QUEUED: "Your verification email is queued. We’ll update this status when the email service responds.",
  SENDING: "We’re contacting the email service. Delivery has not been confirmed.",
  ACCEPTED: "The email service accepted your verification email. Check your inbox and spam folder; inbox delivery is not yet confirmed.",
  FAILED: "We couldn’t send your verification email. Our team can see the failure. Please try again later.",
  RETRYING: "We couldn’t send your email yet. We’ll retry automatically.",
  SKIPPED: "This verification link is no longer active. Use the latest email or request another link.",
  UNKNOWN: "Request a verification link below. Detailed delivery status is available in the browser used to register.",
};
export default function MailDeliveryStatus({ delivery }) {
  const state = delivery?.state || "QUEUED";
  return <p role="status" aria-live="polite" className={`mail-delivery-status ${state === "FAILED" ? "mail-delivery-error" : ""}`}>{copy[state] || copy.UNKNOWN}</p>;
}
