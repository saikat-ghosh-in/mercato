import { useEffect, useState } from 'react';
import api, { session } from '../../backend/api';
import { createSubmission, remainingUntil, workflowSummary, cancellationPolicy } from '../../utils/fulfillment';
import ModalFrame from '../shared/ModalFrame';
import Accordion from '../shared/Accordion';
import SelectControl from '../shared/SelectControl';
import InfoHint from '../shared/InfoHint';

const cancellationReasons={
  seller:['Out of stock','Unable to fulfil this item','Item damaged or unavailable','Listing or pricing error'],
  customer:['Changed my mind','Ordered by mistake','Found a better price','Delivery is taking too long','Need to change delivery address'],
  admin:['Customer requested cancellation','Seller unable to fulfil','Item unavailable'],
};
const actionHelp={
  Accept:'Confirm the units you can fulfil. You can start preparing them next.',
  'Start preparing':'Use this when work starts on these units. Customers can no longer cancel prepared units themselves.',
  'Mark as packed':'Confirm these units are packed. Mark them shipped separately when the carrier handoff is complete.',
};
const statusTone=label=>/cancel/i.test(label)?'cancelled':/delivered/i.test(label)?'delivered':/shipped/i.test(label)?'shipped':/packed/i.test(label)?'packed':/prepar/i.test(label)?'preparing':/payment|funding|review/i.test(label)?'waiting':'processing';
export function WorkflowBadge({record,audience='operations'}){
  const {label}=workflowSummary(record,audience);
  return <span className="workflow-state" data-status={statusTone(label)}>{label}</span>;
}

export function WorkflowProgress({record, showState=true, showQuantity=true, audience='operations'}) {
  const workflow = record?.workflow;
  const {quantity, label, segments} = workflowSummary(record, audience);
  return <div className="workflow-progress space-y-2">
    {(showState || (showQuantity && quantity !== null)) && <p className="workflow-progress-title text-sm font-semibold text-gray-900">
      {showQuantity && quantity !== null && <span className="tabular-nums">Qty {quantity}</span>}
      {showState && showQuantity && quantity !== null && ' · '}
      {showState && <span className="workflow-state" data-status={statusTone(label)}>{label}</span>}
    </p>}
    {segments.length > 0 && <><div className="quantity-distribution" aria-hidden="true">{segments.map(segment=><span key={segment.label} data-status={statusTone(segment.label)} style={{flex:segment.quantity}} />)}</div><dl aria-label="Quantity breakdown" className="quantity-legend">
      {segments.map(segment => <div key={segment.label} className="quantity-chip" data-status={statusTone(segment.label)}>
        <dt className="inline">{segment.label}: </dt><dd className="inline font-semibold tabular-nums">{segment.quantity}</dd>
      </div>)}
    </dl></>}
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
  const [quantity, setQuantity] = useState(String(maximum || 1));
  const [quantityEdited, setQuantityEdited] = useState(false);
  const [reason, setReason] = useState('');
  const [reasonChoice, setReasonChoice] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [submission] = useState(createSubmission);
  const [lifetime] = useState(() => ({active:true}));
  useEffect(() => { lifetime.active=true; return () => {lifetime.active=false;}; }, [lifetime]);
  if (!maximum && !open) return null;
  const qty = Number(quantity);
  const selectedReason=reasonChoice==='OTHER'?reason.trim():reasonChoice;
  const audience=endpoint.startsWith('/seller/')?'seller':endpoint.startsWith('/user/')?'customer':'admin';
  const versionKnown = Number.isSafeInteger(version) && version >= 0;
  const valid = Number.isSafeInteger(qty) && qty >= 1 && qty <= maximum && versionKnown && (!cancellation || (selectedReason.length > 0 && selectedReason.length <= 500 && confirmed));
  const edit = update => { submission.clear(); setError(''); update(); };
  const submit = async event => {
    event.preventDefault();
    if (!valid || busy || disabled) return;
    const epoch=session.getSnapshot().epoch;
    const current=() => lifetime.active && epoch===session.getSnapshot().epoch;
    setBusy(true); setError('');
    try {
      await api.post(endpoint, submission.payload(makeBody(qty, selectedReason), version));
      if (!current()) return;
      submission.clear(); setOpen(false); setConfirmed(false); setQuantityEdited(false); setReasonChoice(''); setReason(''); await onComplete();
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
    <button type="button" className={`button ${cancellation ? 'button-danger' : 'button-outline'}`}
      disabled={disabled || busy || !maximum || !versionKnown} onClick={() => {if(!quantityEdited)setQuantity(String(maximum));setOpen(true);}}>{label}</button>
    {open && <ModalFrame title={label} onClose={() => {if (!busy) setOpen(false);}}>
      <form className="workflow-command-form surface p-5 w-full space-y-4" onSubmit={submit}>
        <div className="flex items-center gap-2"><h2 className="text-base font-semibold">{label}</h2><InfoHint label={label}>{cancellation?'Choose eligible units and a reason. Other units stay in the order; refunds follow the cancellation policy.':actionHelp[label]||'Choose how many eligible units to update. The available maximum is selected initially.'}</InfoHint></div>
        {!whole && <label className="block text-sm">
          Quantity <span className="text-gray-500">(maximum {maximum})</span>
          <input className="mt-2 w-full min-h-11 rounded-lg border border-gray-200 px-3 py-2 text-base" aria-label="Quantity" type="number" inputMode="numeric"
            min="1" max={maximum} step="1" required value={quantity} disabled={busy}
            onChange={event => edit(() => {setQuantityEdited(true);setQuantity(event.target.value);})} />
        </label>}
        {cancellation && <>
          <div className="space-y-2"><p className="text-sm">Reason</p><SelectControl aria-label="Cancellation reason" value={reasonChoice} disabled={busy} required onChange={event=>edit(()=>setReasonChoice(event.target.value))}>
            <option value="" disabled>Select a reason</option>{cancellationReasons[audience].map(value=><option key={value} value={value}>{value}</option>)}<option value="OTHER">Other</option>
          </SelectControl></div>
          {reasonChoice==='OTHER'&&<label className="block text-sm">Tell us the reason
            <textarea aria-label="Reason" className="mt-2 w-full rounded-lg border border-gray-200 px-3 py-2 text-base" rows={3} maxLength={500} required
              value={reason} disabled={busy} onChange={event => edit(() => setReason(event.target.value))} />
          </label>}
          <p className="text-sm text-gray-600">{whole ? 'Cancel all remaining eligible units.' : `Cancel ${Number.isSafeInteger(qty) ? qty : 'selected'} unit(s) from this order.`}</p>
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
