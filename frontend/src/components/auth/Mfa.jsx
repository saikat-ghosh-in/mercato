import { useState } from "react";
import { useSelector } from "react-redux";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom";
import api, { session } from "../../backend/api.js";
import { loginDestination } from "../../utils/accountRouting.js";
import { mfaAction } from "../../utils/accountState.js";
import Field from "../shared/AuthField";

export default function Mfa() {
  const { mfa, user } = useSelector(state => state.auth);
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [setup, setSetup] = useState(null);
  const [code, setCode] = useState("");
  const [recovery, setRecovery] = useState(false);
  const [codes, setCodes] = useState(null);
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [retryAt, setRetryAt] = useState(0);
  const replacement = params.get("replace") === "1" && !setup;
  const state = mfa?.state || "NONE";
  const enrollment = state === "ENROLL_REQUIRED" || Boolean(setup);
  const stepUp = state === "STEP_UP_REQUIRED";
  const redirect = params.get("redirect") || "/";

  if (["NONE", "VERIFIED"].includes(state) && !user) return <Navigate to="/login" replace />;
  if (["NONE", "VERIFIED"].includes(state) && user && !params.has("replace") && !codes) return <Navigate to={loginDestination(user, redirect)} replace />;
  const complete = async () => {
    const identity = await session.refresh({ broadcast: true });
    navigate(loginDestination(identity.user, redirect), { replace: true });
  };
  const run = async operation => {
    if (busy) return;
    if (Date.now() < retryAt) { setError("Please wait before trying again."); return; }
    setBusy(true); setError(""); setMessage("");
    try { await operation(); }
    catch (err) {
      const seconds = Number(err.response?.headers?.["retry-after"]);
      if (Number.isFinite(seconds) && seconds > 0) setRetryAt(Date.now() + seconds * 1000);
      setError(err.response?.data?.message || err.message || "Verification couldn’t complete. Please try again.");
    } finally { setBusy(false); }
  };
  const beginEnrollment = () => run(async () => {
    const { data } = await api.post("/auth/mfa/enroll", {});
    setSetup(data);
  });
  const sendEmail = () => run(async () => {
    const { data } = await api.post("/auth/step-up/send", {});
    setMessage(data.message || "Check your email for a verification code.");
  });
  const submit = event => {
    event.preventDefault();
    run(async () => {
      const request = mfaAction(state, code, recovery, replacement);
      const { data } = await api.post(request.url, request.data);
      setCode("");
      if (replacement) { setSetup(data); setRecovery(false); return; }
      if (data.recoveryCodes?.length) { setCodes(data.recoveryCodes); setSetup(null); return; }
      await complete();
    });
  };
  const title = codes ? "Save your recovery codes" : replacement ? "Replace your authenticator" : enrollment ? "Set up your authenticator" : stepUp ? "Verify your sign-in" : recovery ? "Use a recovery code" : "Two-step verification";
  return <div className="bg-gray-50 flex items-start justify-center px-4 py-10"><div className="w-full max-w-md"><h1 className="text-2xl font-semibold text-gray-950 tracking-tight mb-3">{title}</h1><div className="surface p-7 space-y-4">
    {codes ? <><p>Keep these codes somewhere safe. Each code works once. They won’t be shown again.</p><ul className="grid gap-2" aria-label="Recovery codes">{codes.map(value => <li className="font-mono text-sm break-all" key={value}>{value}</li>)}</ul><label className="flex gap-2"><input type="checkbox" checked={saved} onChange={event => setSaved(event.target.checked)} />I saved my recovery codes</label><button className="button button-dark w-full" disabled={!saved || busy} onClick={() => run(complete)}>Continue</button></> : <>
      {replacement ? <p>Enter a current authenticator code, or leave it blank if you verified with an authenticator or recovery code in this session within the last five minutes. Then add and confirm your replacement authenticator.</p> : stepUp ? <><p>Enter the short verification code sent to your account email.</p><button className="button button-outline" disabled={busy} onClick={sendEmail}>Send a verification code</button></> : enrollment ? <p>Administrator access requires an authenticator. Add the setup key to your authenticator app, then enter the six-digit code.</p> : <p>{recovery ? "Enter one unused recovery code. It can only be used once." : "Enter the current six-digit code from your authenticator."}</p>}
      {enrollment && !setup && <button className="button button-dark w-full" disabled={busy} onClick={beginEnrollment}>Start setup</button>}
      {setup && <div className="space-y-3"><p className="text-sm">Authenticator setup key</p><code className="block break-all select-all">{setup.secret}</code><a className="text-link" href={setup.otpauthUri}>Open authenticator app</a></div>}
      {(!enrollment || setup) && <form onSubmit={submit} className="space-y-4"><Field label={recovery ? "Recovery code" : "Verification code"} required={!replacement}><input className="w-full text-sm text-gray-800 focus:outline-none bg-transparent" autoComplete="one-time-code" inputMode={recovery ? "text" : "numeric"} value={code} onChange={event => setCode(event.target.value)} maxLength={recovery ? 64 : 6} autoFocus required={!replacement} /></Field><button className="button button-dark w-full" disabled={busy || (!replacement && !code.trim())}>{busy ? "Verifying…" : replacement ? "Verify and replace" : "Verify"}</button></form>}
      {!enrollment && !stepUp && !replacement && <button className="text-link" disabled={busy} onClick={() => { setRecovery(value => !value); setCode(""); setError(""); }}>{recovery ? "Use my authenticator" : "Use a recovery code"}</button>}
    </>}
    {message && <p role="status">{message}</p>}{error && <p role="alert" className="inline-error">{error}</p>}<div className="flex flex-col items-start gap-3">{!codes && <button className="text-link" disabled={busy} onClick={() => run(async () => { await api.post("/auth/signout"); await session.refresh({ broadcast: true }); navigate("/login", { replace: true }); })}>Restart sign-in</button>}<Link className="text-link" to="/help">Get help with account access</Link></div>
  </div></div></div>;
}
