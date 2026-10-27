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
