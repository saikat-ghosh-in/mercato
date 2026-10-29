export const actionMaximum = (line, action) => {
  const workflow = line?.workflow;
  const maximum = workflow?.actionMaxima?.[action];
  return workflow?.allowedActions?.includes(action) && Number.isSafeInteger(maximum) && maximum > 0 ? maximum : 0;
};

export const canCancelWhole = order => Number.isSafeInteger(order?.workflow?.version)
  && order.workflow.version >= 0
  && !order.workflow.reviewRequired && Boolean(order.orderLines?.some(line => line.orderedQty > line.cancelledQty))
  && order.orderLines.every(line => {
    const active = line.orderedQty - line.cancelledQty;
    return active === 0 || actionMaximum(line, 'CANCEL') === active;
  });

export function createSubmission(makeId = () => crypto.randomUUID()) {
  let saved;
  return {
    payload(body, version) {
      const signature = JSON.stringify(body);
      if (saved?.signature !== signature) saved = { signature, value: { ...body, operationId: makeId(), expectedVersion: version } };
      return saved.value;
    },
    clear() { saved = undefined; },
  };
}

export function createResponseFence(getEpoch) {
  let generation = 0;
  return {
    next() { const request = ++generation; const epoch = getEpoch(); return () => request === generation && epoch === getEpoch(); },
    invalidate() { generation++; },
  };
}

export const remainingUntil = (deadline, receivedAt, now, serverNow) => Math.max(0, Date.parse(deadline) - Date.parse(serverNow) - Math.max(0, now - receivedAt));
const labels = { AWAITING_FUNDING:'Awaiting funding', CONFIRMED:'Confirmed', ACCEPTED:'Accepted', PREPARING:'Preparing', PACKED:'Packed', SHIPPED:'Shipped', DELIVERED:'Delivered', CANCELLED:'Cancelled', CREATED:'Awaiting payment', FULFILLED:'Shipped', FULFILLMENT_COMPLETE:'Shipped', PARTIALLY_FULFILLED:'Partly shipped', PROCESSING:'Processing (legacy)', PARTIALLY_PROCESSED:'Partly processing (legacy)', FULFILLMENT_PROCESSING:'Processing (legacy)' };
export const workflowLabel = (record, audience = 'operations') => {
  const state = record?.workflow?.state || record?.orderStatus || record?.orderLineStatus;
  if (audience === 'customer') {
    if (['AWAITING_FUNDING', 'CREATED'].includes(state)) return 'Awaiting payment';
    if (['CONFIRMED', 'ACCEPTED', 'PREPARING', 'PACKED'].includes(state)) return 'Processing';
  }
  return labels[state] || 'Awaiting review';
};

export function workflowSummary(record, audience = 'operations') {
  const count = stage => record?.workflow?.[stage + 'Qty'] ?? record?.[stage + 'Qty'];
  const valid = value => Number.isSafeInteger(value) && value >= 0;
  const lineQuantities = record?.orderLines?.map(line => line.orderedQty ?? line.quantity);
  const ordered = count('ordered') ?? record?.quantity
    ?? (lineQuantities?.length && lineQuantities.every(valid) ? lineQuantities.reduce((sum, qty) => sum + qty, 0) : null);
  const quantity = valid(ordered) ? ordered : null;
  const label = workflowLabel(record, audience);
  const summary = {quantity, label, segments:[]};
  const cancelled = count('cancelled'), shipped = count('shipped'), delivered = count('delivered');
  if (![quantity, cancelled, shipped, delivered].every(valid)
    || delivered > shipped || shipped + cancelled > quantity) return summary;
  const state = record?.workflow?.state || record?.orderStatus || record?.orderLineStatus;
  const awaitingPayment = ['AWAITING_FUNDING', 'CREATED'].includes(state);
  const confirmed = !awaitingPayment && (Boolean(record?.workflow?.confirmedAt)
    || ['CONFIRMED', 'ACCEPTED', 'PREPARING', 'PACKED', 'SHIPPED', 'DELIVERED'].includes(state));
  if (!awaitingPayment && !confirmed && state !== 'CANCELLED') return summary;
  const segments = [
    {label:awaitingPayment ? 'Awaiting payment' : 'Processing', quantity:quantity - cancelled - shipped},
    {label:'Shipped', quantity:shipped - delivered},
    {label:'Delivered', quantity:delivered},
    {label:'Cancelled', quantity:cancelled},
  ].filter(segment => segment.quantity > 0);
  return {...summary, segments:segments.length > 1 ? segments : []};
}

export const cancellationPolicy = 'You can cancel units until preparation starts. A partial customer cancellation retains fixed platform and handling fees; merchandise and unused delivery shares are refunded. Seller or seller-delay cancellation refunds every allocated charge for affected units. Full cancellation returns residual buyer charges. Remaining items are not repriced. Refund completion follows the payment provider’s actual status; bank processing times vary. In-app returns and reverse pickup are deferred in this sandbox.';
export const paymentPolicy = 'This sandbox uses Cashfree immediate payment collection. Delayed capture is off. If payment is interrupted, continue the same checkout or check Your orders before starting again. Refunds return through the original payment method; bank processing times vary.';
