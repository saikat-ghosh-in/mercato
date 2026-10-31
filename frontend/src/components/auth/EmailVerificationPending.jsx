import { Spinner } from "../shared/Spinner";
import { useEffect, useState } from "react";
import { useLocation, Link } from "react-router-dom";
import { MdEmail } from "react-icons/md";
import api from "../../backend/api";
import MailDeliveryStatus from "./MailDeliveryStatus";
import "./email-delivery.css";

export default function EmailVerificationPending() {
  const location = useLocation();
  const [email, setEmail] = useState(location.state?.email || "");
  const [delivery, setDelivery] = useState({ state: location.state?.email ? "QUEUED" : "UNKNOWN" });
  const [requestMessage, setRequestMessage] = useState("");
  const [error, setError] = useState("");
  const [resending, setResending] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setTimeout(() => setCooldown(value => Math.max(0, value - 1)), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);
  useEffect(() => {
    const controller = new AbortController(); let timer; let attempts = 0;
    const poll = async () => {
      try {
        const { data } = await api.get("/auth/verification-delivery", { signal: controller.signal });
        if (controller.signal.aborted) return;
        setDelivery(data); setError("");
        if (["QUEUED", "SENDING", "RETRYING"].includes(data.state) && ++attempts < 60) timer = setTimeout(poll, 5000);
      } catch { if (!controller.signal.aborted) setError("Delivery status couldn’t load. Please refresh the status."); }
    };
    poll();
    return () => { controller.abort(); clearTimeout(timer); };
  }, [revision]);
  const resend = async event => {
    event.preventDefault(); if (resending || cooldown > 0) return; setResending(true); setError(""); setRequestMessage("");
    try {
      const { data } = await api.post(`/auth/resend-verification?email=${encodeURIComponent(email.trim())}`);
      setRequestMessage(data.message || "If your account needs verification, an email is queued. Delivery is not yet confirmed.");
      setCooldown(30); setRevision(value => value + 1);
    } catch (err) {
      setError(err?.response?.data?.message || "Your request couldn’t be processed. Please try again.");
      const retryAfter = Number(err?.response?.headers?.["retry-after"]);
      if (Number.isFinite(retryAfter) && retryAfter > 0) setCooldown(Math.min(3600, Math.ceil(retryAfter)));
    } finally { setResending(false); }
  };
  return <div className="bg-gray-50 min-h-screen flex items-center justify-center px-4 py-10">
    <div className="surface p-6 sm:p-8 max-w-md w-full">
      <div className="w-12 h-12 rounded-lg bg-blue-50 flex items-center justify-center mb-5"><MdEmail size={22} aria-hidden="true" /></div>
      <h1 className="text-xl font-semibold mb-3">Verify your email</h1>
      <p className="text-sm text-gray-500 leading-relaxed mb-5">Open the latest verification link to finish creating your account.</p>
      <MailDeliveryStatus delivery={delivery} />
      {requestMessage && delivery.state === "UNKNOWN" && <p role="status" className="text-sm text-gray-600 mt-4">{requestMessage}</p>}
      {error && <p role="alert" className="text-sm text-red-700 mt-4">{error}</p>}
      <button className="text-sm underline mt-4" onClick={() => setRevision(value => value + 1)}>Refresh status</button>
      <form onSubmit={resend} className="mt-6 space-y-3">
        <label className="block text-sm font-medium">Email address<input className="w-full border rounded-md p-3 mt-2" type="email" autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} required maxLength={100} /></label>
        <button className="button button-dark w-full" disabled={resending || cooldown > 0 || !email.trim()}>{resending && <Spinner size={14} />}{resending ? "Requesting email…" : cooldown > 0 ? `Try again in ${cooldown}s` : "Request another link"}</button>
      </form>
      <p className="text-xs text-gray-500 leading-relaxed mt-4">Once accepted by the email service, delivery can take a few minutes. Check spam too. Repeated requests replace earlier links.</p>
      <Link className="text-link inline-block mt-6 text-sm" to="/login">Go to sign in</Link>
    </div>
  </div>;
}
