import test from 'node:test';
import assert from 'node:assert/strict';
const workflow = await import('../src/utils/fulfillment.js').catch(() => ({}));
test('release countdown never predates a refreshed receipt at the immutable boundary',()=>{
  assert.equal(workflow.remainingUntil('2026-10-26T01:00:01.000Z',1150,900,'2026-10-26T01:00:01.150Z'),0);
});
test('customer cancellation uses authoritative maxima after other units prepare', () => {
  assert.equal(workflow.actionMaximum?.({workflow:{version:3,allowedActions:['CANCEL'],actionMaxima:{CANCEL:2}}}, 'CANCEL'), 2);
  assert.equal(workflow.actionMaximum?.({workflow:{allowedActions:[],actionMaxima:{CANCEL:2}}}, 'CANCEL'), 0);
});
test('whole customer cancellation requires every active unit eligible', () => {
  const line = {orderedQty:5,cancelledQty:1,workflow:{version:2,allowedActions:['CANCEL'],actionMaxima:{CANCEL:3}}};
  assert.equal(workflow.canCancelWhole?.({workflow:{version:2},orderLines:[line]}), false);
  assert.equal(workflow.canCancelWhole?.({workflow:{version:2},orderLines:[{...line,workflow:{...line.workflow,actionMaxima:{CANCEL:4}}}]}), true);
});
test('uncertain retries preserve exact payload and version; edits or conflict issue new IDs', () => {
  assert.equal(typeof workflow.createSubmission, 'function');
  let id=0; const submission=workflow.createSubmission(() => `id-${++id}`);
  const a=submission.payload({qty:2,reason:'stock'}, 3);
  assert.deepEqual(submission.payload({qty:2,reason:'stock'}, 4), a);
  assert.notEqual(submission.payload({qty:1,reason:'stock'}, 4).operationId, a.operationId);
  submission.clear();
  assert.equal(submission.payload({qty:1,reason:'stock'}, 5).expectedVersion, 5);
});
test('new reads, record switches and account changes fence late results', () => {
  assert.equal(typeof workflow.createResponseFence, 'function');
  let epoch=1; const fence=workflow.createResponseFence(() => epoch);
  const first=fence.next(); const second=fence.next();
  assert.equal(first(),false); assert.equal(second(),true);
  epoch++; assert.equal(second(),false);
  const third=fence.next(); fence.invalidate(); assert.equal(third(),false);
});
test('server offset determines exact seller hold boundary without client clock assumptions', () => {
  assert.equal(workflow.remainingUntil?.('2026-10-26T01:00:00Z', 1000, 2000, '2026-10-26T00:59:59Z'), 0);
  assert.equal(workflow.remainingUntil?.('2026-10-26T01:00:00Z', 1000, 1000, '2026-10-26T00:59:59Z'), 1000);
});
test('legacy fulfilled is shipped, never invented delivery; workflow state takes precedence', () => {
  assert.equal(workflow.workflowLabel?.({orderLineStatus:'FULFILLED'}),'Shipped');
  assert.equal(workflow.workflowLabel?.({orderStatus:'FULFILLMENT_COMPLETE',workflow:{state:'PACKED'}}),'Packed');
});


test('whole cancellation fails closed for review, missing lines and invalid revisions', () => {
  const line={orderedQty:2,cancelledQty:0,workflow:{allowedActions:['CANCEL'],actionMaxima:{CANCEL:2}}};
  assert.equal(workflow.canCancelWhole({workflow:{version:-1},orderLines:[line]}),false);
  assert.equal(workflow.canCancelWhole({workflow:{version:2,reviewRequired:true},orderLines:[line]}),false);
  assert.equal(workflow.canCancelWhole({workflow:{version:2},orderLines:[]}),false);
});

test('compact summaries show one cancelled state without redundant quantity buckets', () => {
  assert.deepEqual(workflow.workflowSummary?.({workflow:{state:'CANCELLED',orderedQty:1,cancelledQty:1,shippedQty:0,deliveredQty:0}}, 'customer'),
    {quantity:1,label:'Cancelled',segments:[]});
});

test('mixed summaries partition cumulative quantities without counting preparation twice', () => {
  const record={workflow:{state:'CONFIRMED',orderedQty:10,cancelledQty:2,acceptedQty:8,preparingQty:7,packedQty:6,shippedQty:5,deliveredQty:2}};
  assert.deepEqual(workflow.workflowSummary?.(record, 'customer'), {quantity:10,label:'Processing',segments:[
    {label:'Processing',quantity:3},{label:'Shipped',quantity:3},{label:'Delivered',quantity:2},{label:'Cancelled',quantity:2},
  ]});
});

test('unconfirmed active quantities await payment and never imply processing', () => {
  assert.deepEqual(workflow.workflowSummary?.({workflow:{state:'AWAITING_FUNDING',orderedQty:4,cancelledQty:1,shippedQty:0,deliveredQty:0}}, 'customer'),
    {quantity:4,label:'Awaiting payment',segments:[{label:'Awaiting payment',quantity:3},{label:'Cancelled',quantity:1}]});
});

test('customer preparation stages collapse while operational labels stay specific', () => {
  for(const [state,label] of [['CONFIRMED','Confirmed'],['ACCEPTED','Accepted'],['PREPARING','Preparing'],['PACKED','Packed']]) {
    const record={workflow:{state,orderedQty:2,cancelledQty:0,shippedQty:0,deliveredQty:0}};
    assert.deepEqual(workflow.workflowSummary?.(record, 'customer'),{quantity:2,label:'Processing',segments:[]});
    assert.equal(workflow.workflowSummary?.(record).label,label);
  }
});

test('legacy missing counters retain known quantity and status without inventing history', () => {
  assert.deepEqual(workflow.workflowSummary?.({orderedQty:3,orderLineStatus:'FULFILLED'}, 'customer'),{quantity:3,label:'Shipped',segments:[]});
  assert.deepEqual(workflow.workflowSummary?.({orderStatus:'CREATED',orderLines:[{quantity:2},{orderedQty:3}]}, 'customer'),{quantity:5,label:'Awaiting payment',segments:[]});
  assert.deepEqual(workflow.workflowSummary?.({orderLineStatus:'PROCESSING'}, 'customer'),{quantity:null,label:'Processing (legacy)',segments:[]});
});

test('incomplete or inconsistent counts never become a fabricated mixed breakdown', () => {
  assert.deepEqual(workflow.workflowSummary?.({workflow:{state:'PACKED',orderedQty:5,cancelledQty:1,shippedQty:2}}, 'customer'),{quantity:5,label:'Processing',segments:[]});
  assert.deepEqual(workflow.workflowSummary?.({workflow:{state:'CONFIRMED',orderedQty:2,cancelledQty:1,shippedQty:3,deliveredQty:0}}, 'customer'),{quantity:2,label:'Processing',segments:[]});
});
