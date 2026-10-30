import { useEffect, useRef, useState } from 'react';
import api, { session } from '../../backend/api';
import useWorkflowResource, { useWorkflowEpoch } from '../../hooks/useWorkflowResource';
import { createSubmission } from '../../utils/fulfillment';
import { formatDate } from '../../utils/formatDate';
import ModalFrame from '../shared/ModalFrame';
import Accordion from '../shared/Accordion';
import InfoHint from '../shared/InfoHint';

const labels={ SHIPPED:'Shipped', OUT_FOR_DELIVERY:'Out for delivery', DELIVERED:'Delivered' };

export function ShipmentLineAction({ data, fulfillmentId, lineNumber, onOpen, disabled=false }) {
  const item=data?.handoffItems?.find(value=>value.fulfillmentId===fulfillmentId&&value.lineNumber===lineNumber&&value.maximum>0);
  if(!item)return null;
  return <span className="inline-flex items-center gap-1"><button type="button" className="button button-dark" disabled={disabled} onClick={()=>onOpen({fulfillmentId,lineNumber,requestId:crypto.randomUUID()})}>Mark shipped</button><InfoHint label="Mark shipped">After accepting, you can ship directly. Confirm that the selected items are packed and handed to the carrier; preparation and packing updates are optional.</InfoHint></span>;
}

export default function ShipmentPanel({ scope='customer', recordId, version, onChanged, onData, lineRequest }) {
  const epoch=useWorkflowEpoch();
  return <ShipmentContent key={`${epoch}:${scope}:${recordId}`} scope={scope} recordId={recordId} version={version} onChanged={onChanged} onData={onData} lineRequest={lineRequest} />;
}

function ShipmentContent({ scope, recordId, version, onChanged, onData, lineRequest }) {
  const prefix=scope==='seller'?`/seller/fulfillment-orders/${encodeURIComponent(recordId)}`:`/${scope==='admin'?'admin':'user'}/orders/${encodeURIComponent(recordId)}`;
  const {data,loading,error,refresh}=useWorkflowResource(`${prefix}/shipments`);
  const [draft,setDraft]=useState(null);
  const [busy,setBusy]=useState(false);
  const [message,setMessage]=useState('');
  const [submission]=useState(()=>createSubmission());
  const [generations]=useState(()=>new Map());
  const [generating,setGenerating]=useState(false);
  const [generationAttempted,setGenerationAttempted]=useState(false);
  const [generationRetryAt,setGenerationRetryAt]=useState(0);
  const [generationWait,setGenerationWait]=useState(0);
  const draftGeneration=useRef(0);
  const generateRef=useRef(null);
  const alive=useRef(true);
  const previousVersion=useRef(version);
  const reportedVersion=useRef(version);
  const handledLine=useRef(null);
  useEffect(()=>{
    if(!lineRequest||handledLine.current===lineRequest.requestId||!data)return;
    handledLine.current=lineRequest.requestId;
    const item=data.handoffItems?.find(value=>value.fulfillmentId===lineRequest.fulfillmentId&&value.lineNumber===lineRequest.lineNumber&&value.maximum>0);
    if(!item){setMessage('No accepted units are available to ship for this item.');return;}
    const saved=generations.get(`${item.fulfillmentId}:${item.lineNumber}`);
    draftGeneration.current++;setGenerating(false);setGenerationAttempted(Boolean(saved?.attempted));setGenerationRetryAt(saved?.retryAt||0);submission.clear();setMessage(saved?.result?.message||'');setDraft({type:'handoff',fulfillmentId:item.fulfillmentId,lineNumber:item.lineNumber,waybill:saved?.result?.waybill||'',easyshipShipmentId:saved?.result?.easyshipShipmentId||'',quantities:{[item.lineNumber]:String(item.maximum)},confirmed:false});
  },[lineRequest,data,submission,generations]);
  useEffect(()=>{if(data)onData?.(data);},[data,onData]);
  useEffect(()=>{
    if(Number.isSafeInteger(version)&&Number.isSafeInteger(data?.version)&&data.version!==version&&reportedVersion.current!==data.version){
      reportedVersion.current=data.version;onChanged?.();
    }
  },[data,version,onChanged]);
  useEffect(()=>{alive.current=true;return()=>{alive.current=false;};},[]);
  useEffect(()=>{
    if(!generationRetryAt){setGenerationWait(0);return;}
    const tick=()=>setGenerationWait(Math.max(0,Math.ceil((generationRetryAt-Date.now())/1000)));
    tick();const timer=window.setInterval(tick,1000);return()=>window.clearInterval(timer);
  },[generationRetryAt]);
  useEffect(()=>{
    const revalidate=()=>{if(document.visibilityState==='visible')refresh();};
    const interval=window.setInterval(revalidate,30000);
    window.addEventListener('focus',revalidate);document.addEventListener('visibilitychange',revalidate);
    return()=>{window.clearInterval(interval);window.removeEventListener('focus',revalidate);document.removeEventListener('visibilitychange',revalidate);};
  },[refresh]);
  useEffect(()=>{if(previousVersion.current!==version){previousVersion.current=version;refresh();}},[version,refresh]);
  const open=value=>{draftGeneration.current++;setGenerating(false);setGenerationAttempted(false);setGenerationRetryAt(0);submission.clear();setMessage('');setDraft(value);};
  const close=()=>{draftGeneration.current++;setDraft(null);};
  const edit=values=>{submission.clear();setMessage('');if('waybill' in values||'easyshipShipmentId' in values)setGenerationRetryAt(0);setDraft(current=>({...current,...values}));};
  const eligible=(data?.handoffItems||[]).filter(item=>item.fulfillmentId===draft?.fulfillmentId&&(draft?.lineNumber==null||item.lineNumber===draft.lineNumber));
  const entries=eligible.map(item=>({lineNumber:item.lineNumber,qty:Number(draft?.quantities?.[item.lineNumber]||0)}));
  const validQuantities=entries.length>0&&entries.some(item=>item.qty>0)&&entries.every((item,index)=>Number.isSafeInteger(item.qty)&&item.qty>=0&&item.qty<=eligible[index].maximum);
  const needsEasyshipId=draft?.type==='handoff'?data?.trackingProvider==='EASYSHIP':draft?.provider==='EASYSHIP';
  const validEasyshipId=!needsEasyshipId||/^ES[A-Za-z0-9]{2}[0-9]{7,70}$/.test(draft?.easyshipShipmentId||'');
  const valid=draft?.type==='handoff'?Boolean(draft.confirmed&&/^[A-Za-z0-9-]{1,80}$/.test(draft.waybill)&&validQuantities)
    :Boolean(draft?.reason?.trim()&&(draft.type==='delivered'||/^[A-Za-z0-9-]{1,80}$/.test(draft.waybill)));
  const run=async(action,success)=>{
    if(busy)return;
    const epoch=session.getSnapshot().epoch;
    const current=()=>alive.current&&epoch===session.getSnapshot().epoch;
    setBusy(true);setMessage('');
    try{
      await action();if(!current())return;
      if(success){if(draft?.type==='handoff')generations.delete(`${draft.fulfillmentId}:${draft.lineNumber}`);submission.clear();setDraft(null);}
      await refresh();
    }catch(failure){
      if(!current())return;
      if(failure.response?.status===409){submission.clear();setMessage('This order changed. Your draft is retained. Review the updated quantities and try again.');await refresh();}
      else setMessage(failure.response?.data?.message||'The result is uncertain. Retry the same submission safely, or edit your draft.');
    }finally{if(current())setBusy(false);}
  };
  const submit=event=>{
    event.preventDefault();if(generating||!valid||!validEasyshipId||!data)return;
    if(draft.type==='handoff'){
      const body=submission.payload({waybill:draft.waybill,actualHandoff:true,items:entries.filter(item=>item.qty>0),...(needsEasyshipId?{easyshipShipmentId:draft.easyshipShipmentId}:{})},data.version);
      run(()=>api.post(`/${scope}/fulfillment-orders/${encodeURIComponent(draft.fulfillmentId)}/shipments`,body),true);
    }else{
      const body=submission.payload({reason:draft.reason.trim(),...(draft.type==='tracking'?{waybill:draft.waybill,...(needsEasyshipId?{easyshipShipmentId:draft.easyshipShipmentId}:{})}:{})},data.version);
      run(()=>api.request({method:draft.type==='tracking'?'patch':'post',url:`/admin/shipments/${encodeURIComponent(draft.parcelId)}/${draft.type==='tracking'?'tracking':'delivered'}`,data:body}),true);
    }
  };
  const generate=async(manualRetry=false)=>{
    if(generating||Date.now()<generationRetryAt||!draft||!data)return;
    const epoch=session.getSnapshot().epoch,instance=draftGeneration.current;
    const current=()=>alive.current&&epoch===session.getSnapshot().epoch&&instance===draftGeneration.current;
    const key=`${draft.fulfillmentId}:${draft.lineNumber}`;
    if(!generations.has(key))generations.set(key,{submission:createSubmission(),attempted:false,retryAt:0});
    const generation=generations.get(key);generation.attempted=true;setGenerationAttempted(true);
    if(manualRetry)generation.attempts=0;
    generation.attempts=(generation.attempts||0)+1;
    setGenerating(true);setMessage('');
    try{
      const response=await api.post(`/${scope}/fulfillment-orders/${encodeURIComponent(draft.fulfillmentId)}/shipments/prepare`,generation.submission.payload({lineNumber:draft.lineNumber},data.version));
      generation.result=response.data;
      if(!current())return;
      const result=response.data;
      if(result.state==='READY'){
        submission.clear();setDraft(value=>({...value,waybill:result.waybill,easyshipShipmentId:result.easyshipShipmentId}));generation.retryAt=0;
      }else generation.retryAt=result.state==='FAILED'?0:Date.now()+Math.max(0,result.retryAfterSeconds||0)*1000;
      setGenerationRetryAt(generation.retryAt);
      setMessage(result.state==='READY'?'Tracking is ready. Confirm handoff when this item leaves with the carrier.':generation.attempts<4?(result.message||'Confirming tracking automatically…'):'Tracking is not confirmed yet. Retry tracking, or enter existing references.');
    }catch(failure){
      if(!current())return;
      if(failure.response?.status===409){generation.submission.clear();generation.attempted=false;setGenerationAttempted(false);await refresh();setMessage('This item changed. Review the available units, then generate tracking again.');}
      else if(failure.response?.status>=400&&failure.response?.status<500){generation.result={state:'FAILED'};setGenerationRetryAt(0);setMessage(failure.response?.data?.message||'Tracking could not be generated. Check the delivery details.');}
      else{setMessage(generation.attempts<4?'Confirming tracking automatically…':'Tracking could not be confirmed. Retry tracking to recover the same request.');generation.retryAt=Date.now()+10000;setGenerationRetryAt(generation.retryAt);}
    }finally{if(current())setGenerating(false);}
  };
  const generationEntry=generations.get(`${draft?.fulfillmentId}:${draft?.lineNumber}`);
  const generationReady=generationEntry?.result?.state==='READY';
  const automaticRecovery=!generationReady&&generationRetryAt>0&&(generationEntry?.attempts||0)<4;
  useEffect(()=>{generateRef.current=generate;});
  useEffect(()=>{
    if(draft?.type!=='handoff'||!automaticRecovery||generating||busy)return;
    const timer=window.setTimeout(()=>generateRef.current?.(),Math.max(0,generationRetryAt-Date.now())+30);
    return()=>window.clearTimeout(timer);
  },[draft?.type,draft?.fulfillmentId,draft?.lineNumber,automaticRecovery,generating,busy,generationRetryAt]);
  if(!data&&loading)return <section aria-label="Packages" aria-busy="true" className="surface p-4 space-y-3"><h2 className="text-sm font-semibold">Packages</h2><div className="h-12 rounded-lg bg-gray-100" /></section>;
  return <section aria-label="Packages" className="surface p-4 space-y-4 min-w-0">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <h2 className="text-sm font-semibold">Packages</h2>
      <div className="flex flex-wrap gap-2">
        {data?.canRefresh&&<button type="button" className="button button-outline" disabled={busy||loading} onClick={()=>run(()=>api.post(`${prefix}/shipments/refresh`))}>{busy?'Checking…':'Refresh tracking'}</button>}
      </div>
    </div>
    {error&&<p role="alert" className="text-sm text-red-700">{error} <button type="button" className="text-link" onClick={refresh}>Retry packages</button></p>}
    {message&&!draft&&<p role="alert" className="text-sm text-red-700">{message}</p>}
    {data?.message&&<p className="text-xs text-gray-500">{data.message}</p>}
    {data&&data.shipments?.length===0&&<p className="text-sm text-gray-600">Package details will appear after handoff to the carrier.</p>}
    {(data?.shipments||[]).map(parcel=><article key={parcel.id} className="border border-gray-200 rounded-lg p-4 space-y-3 min-w-0">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0"><h3 className="text-sm font-semibold">{labels[parcel.status]||'Tracking updates pending'}</h3><p className="text-xs text-gray-500 mt-1 break-all">Tracking {parcel.waybill}</p></div>
        <button type="button" className="text-link text-xs" onClick={()=>{if(navigator.clipboard)navigator.clipboard.writeText(parcel.waybill).catch(()=>setMessage('Copy the tracking number shown above.'));else setMessage('Copy the tracking number shown above.');}}>Copy tracking number</button>
      </div>
      <ul className="space-y-1 text-sm">{parcel.items?.map(item=><li className="break-words" key={item.lineNumber}>{item.productName} <span className="text-gray-500">· Qty {item.qty}</span></li>)}</ul>
      {parcel.deliveredAt&&<p className="text-xs text-gray-600">Delivered {formatDate(parcel.deliveredAt)}</p>}
      {parcel.eta&&parcel.status!=='DELIVERED'&&<p className="text-xs text-gray-600">Estimated delivery {formatDate(parcel.eta)}</p>}
      {parcel.lastCheckedAt&&<p className="text-xs text-gray-500">Last checked {formatDate(parcel.lastCheckedAt)}</p>}
      {parcel.message&&<p className="text-xs text-gray-600">{parcel.message}</p>}
      {parcel.history?.length>0&&<Accordion><Accordion.Item value={parcel.id}><Accordion.Trigger>Tracking updates</Accordion.Trigger><Accordion.Content><ol className="space-y-2 text-sm py-2">{parcel.history.map((event,index)=><li key={`${event.code}:${index}`} className="break-words">{event.description}{event.providerAt&&<span className="block text-xs text-gray-500">{formatDate(event.providerAt)}</span>}</li>)}</ol></Accordion.Content></Accordion.Item></Accordion>}
      {scope==='admin'&&parcel.status!=='DELIVERED'&&<div className="flex flex-wrap gap-2"><button type="button" className="button button-outline" disabled={busy} onClick={()=>open({type:'tracking',parcelId:parcel.id,waybill:parcel.waybill,provider:parcel.provider,easyshipShipmentId:parcel.providerReference||'',reason:''})}>Correct tracking</button><button type="button" className="button button-outline" disabled={busy} onClick={()=>open({type:'delivered',parcelId:parcel.id,reason:''})}>Mark delivered</button></div>}
    </article>)}
    {draft&&<ModalFrame title={draft.type==='handoff'?'Record carrier handoff':draft.type==='tracking'?'Correct tracking':'Record delivery'} onClose={()=>{if(!busy&&!generating)close();}}>
      <form onSubmit={submit} className="surface p-5 space-y-4 w-full min-w-0">
        <div className="flex items-center gap-2"><h2 className="text-base font-semibold">{draft.type==='handoff'?'Mark item shipped':draft.type==='tracking'?'Correct tracking':'Record delivery'}</h2><InfoHint label="Shipping and tracking">Tracking identifies the package. Confirming handoff marks only the selected units shipped. Sandbox tracking does not purchase a label or arrange a real pickup.</InfoHint></div>
        {draft.type==='handoff'&&needsEasyshipId&&data?.canGenerateTracking&&<div className="space-y-2"><div className="flex items-center gap-2">{generationReady?<p role="status" className="text-sm font-medium">Tracking ready</p>:<button type="button" className="button button-outline" disabled={busy||generating||automaticRecovery||generationWait>0||generationEntry?.result?.state==='FAILED'} onClick={()=>generate(true)}>{generating?'Generating…':automaticRecovery?'Confirming tracking…':generationWait>0?`Retry in ${generationWait}s`:generationAttempted?'Retry tracking':'Generate sandbox tracking'}</button>}<InfoHint label="Sandbox tracking">Uses the delivery details saved with this order to create test tracking. This does not buy postage, request a pickup or send a real parcel.</InfoHint></div>{!generationReady&&<p className="text-xs text-gray-500">Generate both references here, or enter existing sandbox tracking below.</p>}</div>}
        {draft.type!=='delivered'&&<label className="block text-sm">Tracking number<input aria-label="Tracking number" className="mt-2 w-full border rounded-lg px-3 py-2 text-base" value={draft.waybill} maxLength={80} required disabled={busy||generating} onChange={event=>edit({waybill:event.target.value.trim()})} /></label>}
        {needsEasyshipId&&draft.type!=='delivered'&&<label className="block text-sm">Easyship shipment ID<input aria-label="Easyship shipment ID" className="mt-2 w-full border rounded-lg px-3 py-2 text-base" value={draft.easyshipShipmentId||''} maxLength={80} placeholder="ESIN10000001" required disabled={busy||generating} onChange={event=>edit({easyshipShipmentId:event.target.value.trim()})} /><span className="block mt-1 text-xs text-gray-500">{data?.canGenerateTracking?'Filled automatically when you generate sandbox tracking.':'Use the shipment ID from your Easyship sandbox account.'}</span></label>}
        {draft.type==='handoff'?<>
          <p className="text-xs text-gray-500">Choose how many accepted units are packed and handed to the carrier.</p>
          {eligible.map(item=><label key={item.lineNumber} className="block text-sm break-words">{item.productName}<span className="block text-xs text-gray-500">Maximum {item.maximum}</span><input aria-label={`Ship quantity for ${item.productName}`} className="mt-1 w-full border rounded-lg px-3 py-2 text-base" type="number" inputMode="numeric" min="0" max={item.maximum} step="1" value={draft.quantities[item.lineNumber]??'0'} disabled={busy||generating} onChange={event=>edit({quantities:{...draft.quantities,[item.lineNumber]:event.target.value}})} /></label>)}
          <label className="flex items-start gap-2 text-sm"><input type="checkbox" checked={draft.confirmed} disabled={busy||generating} onChange={event=>edit({confirmed:event.target.checked})} />I confirm these items were handed to the carrier.</label>
        </>:<label className="block text-sm">Reason<textarea aria-label="Audit reason" className="mt-2 w-full border rounded-lg px-3 py-2 text-base" rows={3} maxLength={500} value={draft.reason} disabled={busy} required onChange={event=>edit({reason:event.target.value})} /></label>}
        {message&&<p role="alert" className="text-sm text-red-700">{message}</p>}
        {draft.type!=='delivered'&&draft.waybill&&!/^[A-Za-z0-9-]{1,80}$/.test(draft.waybill)&&<p role="status" className="text-sm text-amber-800">Tracking numbers can contain letters, numbers and hyphens.</p>}
        {needsEasyshipId&&draft.easyshipShipmentId&&!validEasyshipId&&<p role="status" className="text-sm text-amber-800">Enter a valid Easyship shipment ID, starting with ES and its two-letter country code.</p>}
        {draft.type==='handoff'&&!draft.confirmed&&<p className="text-xs text-gray-500">Confirm carrier handoff above to enable shipping.</p>}
        <div className="flex flex-wrap gap-2"><button type="button" className="button button-outline" disabled={busy||generating} onClick={close}>Close</button><button type="submit" className="button button-dark" disabled={busy||generating||loading||!valid||!validEasyshipId}>{busy?'Saving…':draft.type==='handoff'?'Confirm handoff':draft.type==='tracking'?'Save tracking':'Confirm delivered'}</button></div>
      </form>
    </ModalFrame>}
  </section>;
}
