import { useEffect, useState } from 'react';
import api, { session } from '../../backend/api';
import { createSubmission, remainingUntil, workflowLabel, cancellationPolicy } from '../../utils/fulfillment';
import ModalFrame from '../shared/ModalFrame';
import Accordion from '../shared/Accordion';

export function WorkflowProgress({record, showState=true}) {
  const workflow = record?.workflow;
  return <div className="space-y-2">
    {showState && <p className="text-sm font-semibold text-gray-900">{workflowLabel(record)}</p>}
    <dl className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-gray-600">
      {['ordered','accepted','preparing','packed','shipped','delivered','cancelled'].map(stage => {
        const qty = workflow?.[stage + 'Qty'] ?? record?.[stage + 'Qty'];
        return qty == null ? null : <div key={stage}>
          <dt className="inline capitalize">{stage}: </dt>
          <dd className="inline font-semibold tabular-nums">{qty}</dd>
        </div>;
      })}
    </dl>
    {workflow?.reviewRequired && <p role="status" className="text-sm text-amber-800">Workflow review required: {workflow.reviewReason || 'Actions await verification.'}</p>}
  </div>;
}

export function WorkflowDeadlines({workflow, receivedAt, onRelease}) {
  const [now, setNow] = useState(Date.now);
  const release = workflow?.effectiveSellerActionsAvailableAt || workflow?.sellerActionsAvailableAt;
  useEffect(() => {
    if (!workflow?.serverNow) return;
    const currentTime = Date.now();
    const future = [release, workflow.acceptDeadline, workflow.preparationReviewAt]
      .filter(Boolean)
      .map(deadline => remainingUntil(deadline, receivedAt, currentTime, workflow.serverNow))
      .filter(remaining => remaining > 0);
    if (!future.length) return;
    const interval = release && remainingUntil(release, receivedAt, currentTime, workflow.serverNow) > 0
      ? setInterval(() => setNow(Date.now()), 1000) : undefined;
    const timeout = setTimeout(onRelease, Math.min(...future) + 50);
    return () => { clearInterval(interval); clearTimeout(timeout); };
  }, [release, receivedAt, workflow?.serverNow, workflow?.acceptDeadline, workflow?.preparationReviewAt, onRelease]);
  if (!workflow) return null;
  const remaining = release && workflow.serverNow ? remainingUntil(release, receivedAt, now, workflow.serverNow) : 0;
  const dates = [['Confirmed',workflow.confirmedAt],['Seller actions available',release],['Accept by',workflow.acceptDeadline],['Preparation review',workflow.preparationReviewAt]];
  return <div className="text-xs text-gray-600 space-y-1">
    {remaining > 0 && <p>Read only · seller actions unlock in <span className="tabular-nums">{Math.ceil(remaining / 60000)} min</span>. Server verifies availability.</p>}
    {dates.map(([label,date]) => date && <p key={label}>
      {label}: <time dateTime={date}>{new Date(date).toLocaleString()}</time>
      {label === 'Accept by' && workflow.serverNow && Date.parse(date) <= Date.parse(workflow.serverNow)
        ? ' · Acceptance deadline passed; unaccepted units are subject to seller-delay cancellation.' : ''}
      {label === 'Preparation review' && workflow.serverNow && Date.parse(date) <= Date.parse(workflow.serverNow)
        ? ' · Review due for outstanding work; this does not automatically cancel prepared units.' : ''}
    </p>)}
  </div>;
}

export function CancellationPolicy() {
  return <Accordion><Accordion.Item value="cancellation"><Accordion.Trigger>Cancellation and refund policy</Accordion.Trigger><Accordion.Content><p className="text-sm text-gray-600">{cancellationPolicy}</p></Accordion.Content></Accordion.Item></Accordion>;
}

export function QuantityCommand({label, maximum, version, endpoint, makeBody, onComplete, cancellation=false, whole=false, disabled=false}) {
  const [open, setOpen] = useState(false);
  const [quantity, setQuantity] = useState('1');
  const [reason, setReason] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [submission] = useState(createSubmission);
  const [lifetime] = useState(() => ({active:true}));
  useEffect(() => { lifetime.active=true; return () => {lifetime.active=false;}; }, [lifetime]);
  if (!maximum && !open) return null;
  const qty = Number(quantity);
  const versionKnown = Number.isSafeInteger(version) && version >= 0;
  const valid = Number.isSafeInteger(qty) && qty >= 1 && qty <= maximum && versionKnown && (!cancellation || (reason.trim().length > 0 && reason.trim().length <= 500 && confirmed));
  const edit = update => { submission.clear(); setError(''); update(); };
  const submit = async event => {
    event.preventDefault();
    if (!valid || busy || disabled) return;
    const epoch=session.getSnapshot().epoch;
    const current=() => lifetime.active && epoch===session.getSnapshot().epoch;
    setBusy(true); setError('');
    try {
      await api.post(endpoint, submission.payload(makeBody(qty, reason.trim()), version));
      if (!current()) return;
      submission.clear(); setOpen(false); setConfirmed(false); await onComplete();
    } catch (failure) {
      if (!current()) return;
      if (failure.response?.status===409) {
        submission.clear();
        setError('This order changed. Your quantity and reason are retained. Review the refreshed facts and current maximum before submitting again.');
        await onComplete();
      } else setError(failure.response?.data?.message || 'The result is uncertain. Retry the same submission to safely check it, or edit your request.');
    } finally { if (current()) setBusy(false); }
  };
  return <>
    <button type="button" className={`button button-outline ${cancellation ? 'text-red-600' : ''}`}
      disabled={disabled || busy || !maximum || !versionKnown} onClick={() => setOpen(true)}>{label}</button>
    {open && <ModalFrame title={label} onClose={() => {if (!busy) setOpen(false);}}>
      <form className="workflow-command-form surface p-5 w-full space-y-4" onSubmit={submit}>
        <h2 className="text-base font-semibold">{label}</h2>
        {!whole && <label className="block text-sm">
          Quantity <span className="text-gray-500">(maximum {maximum})</span>
          <input className="mt-2 w-full min-h-11 rounded-lg border border-gray-200 px-3 py-2 text-base" aria-label="Quantity" type="number" inputMode="numeric"
            min="1" max={maximum} step="1" required value={quantity} disabled={busy}
            onChange={event => edit(() => setQuantity(event.target.value))} />
        </label>}
        {cancellation && <>
          <label className="block text-sm">Reason
            <textarea aria-label="Reason" className="mt-2 w-full rounded-lg border border-gray-200 px-3 py-2 text-base" rows={3} maxLength={500} required
              value={reason} disabled={busy} onChange={event => edit(() => setReason(event.target.value))} />
          </label>
          <p className="text-sm text-gray-600">{whole ? 'Cancel all remaining eligible units.' : `Cancel ${Number.isSafeInteger(qty) ? qty : 'selected'} unit(s). Other units stay in the order.`}</p>
          <label className="flex items-start gap-2 text-sm">
            <input type="checkbox" checked={confirmed} disabled={busy} onChange={event => setConfirmed(event.target.checked)} />
            I confirm this cancellation.
          </label>
        </>}
        {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
        {!maximum && <p role="status" className="text-sm text-amber-800">No units are currently eligible. Your draft is retained.</p>}
        <div className="flex flex-wrap gap-2">
          <button type="button" className="button button-outline" disabled={busy} onClick={() => setOpen(false)}>Keep editing later</button>
          <button type="submit" className="button button-dark" disabled={busy || !valid || disabled}>{busy ? 'Saving…' : 'Confirm ' + label.toLowerCase()}</button>
        </div>
      </form>
    </ModalFrame>}
  </>;
}
