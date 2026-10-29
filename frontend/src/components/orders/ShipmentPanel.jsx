import { useEffect, useRef, useState } from 'react';
import api, { session } from '../../backend/api';
import useWorkflowResource, { useWorkflowEpoch } from '../../hooks/useWorkflowResource';
import { createSubmission } from '../../utils/fulfillment';
import { formatDate } from '../../utils/formatDate';
import ModalFrame from '../shared/ModalFrame';
import Accordion from '../shared/Accordion';

const labels={ SHIPPED:'Shipped', OUT_FOR_DELIVERY:'Out for delivery', DELIVERED:'Delivered' };

export default function ShipmentPanel({ scope='customer', recordId, version, onChanged, onData }) {
  const epoch=useWorkflowEpoch();
  return <ShipmentContent key={`${epoch}:${scope}:${recordId}`} scope={scope} recordId={recordId} version={version} onChanged={onChanged} onData={onData} />;
}

function ShipmentContent({ scope, recordId, version, onChanged, onData }) {
  const prefix=scope==='seller'?`/seller/fulfillment-orders/${encodeURIComponent(recordId)}`:`/${scope==='admin'?'admin':'user'}/orders/${encodeURIComponent(recordId)}`;
  const {data,loading,error,refresh}=useWorkflowResource(`${prefix}/shipments`);
  const [draft,setDraft]=useState(null);
  const [busy,setBusy]=useState(false);
  const [message,setMessage]=useState('');
  const [submission]=useState(()=>createSubmission());
  const alive=useRef(true);
  const previousVersion=useRef(version);
  useEffect(()=>{if(data)onData?.(data);},[data,onData]);
  useEffect(()=>{alive.current=true;return()=>{alive.current=false;};},[]);
  useEffect(()=>{if(previousVersion.current!==version){previousVersion.current=version;refresh();}},[version,refresh]);
  const open=value=>{submission.clear();setMessage('');setDraft(value);};
  const edit=values=>{submission.clear();setMessage('');setDraft(current=>({...current,...values}));};
  const groups=scope==='customer'?[]:[...new Set((data?.handoffItems||[]).map(item=>item.fulfillmentId))];
  const eligible=(data?.handoffItems||[]).filter(item=>item.fulfillmentId===draft?.fulfillmentId);
  const entries=eligible.map(item=>({lineNumber:item.lineNumber,qty:Number(draft?.quantities?.[item.lineNumber]||0)}));
  const validQuantities=entries.length>0&&entries.some(item=>item.qty>0)&&entries.every((item,index)=>Number.isSafeInteger(item.qty)&&item.qty>=0&&item.qty<=eligible[index].maximum);
  const valid=draft?.type==='handoff'?Boolean(draft.confirmed&&/^[A-Za-z0-9-]{1,80}$/.test(draft.waybill)&&validQuantities)
    :Boolean(draft?.reason?.trim()&&(draft.type==='delivered'||/^[A-Za-z0-9-]{1,80}$/.test(draft.waybill)));
  const run=async(action,success)=>{
    if(busy)return;
    const epoch=session.getSnapshot().epoch;
    const current=()=>alive.current&&epoch===session.getSnapshot().epoch;
    setBusy(true);setMessage('');
    try{
      await action();if(!current())return;
      if(success){submission.clear();setDraft(null);}
      await refresh();if(current())await onChanged?.();
    }catch(failure){
      if(!current())return;
      if(failure.response?.status===409){submission.clear();setMessage('This order changed. Your draft is retained. Review the updated quantities and try again.');await refresh();await onChanged?.();}
      else setMessage(failure.response?.data?.message||'The result is uncertain. Retry the same submission safely, or edit your draft.');
    }finally{if(current())setBusy(false);}
  };
  const submit=event=>{
    event.preventDefault();if(!valid||!data)return;
    if(draft.type==='handoff'){
      const body=submission.payload({waybill:draft.waybill,actualHandoff:true,items:entries.filter(item=>item.qty>0)},data.version);
      run(()=>api.post(`/${scope}/fulfillment-orders/${encodeURIComponent(draft.fulfillmentId)}/shipments`,body),true);
    }else{
      const body=submission.payload({reason:draft.reason.trim(),...(draft.type==='tracking'?{waybill:draft.waybill}:{})},data.version);
      run(()=>api.request({method:draft.type==='tracking'?'patch':'post',url:`/admin/shipments/${encodeURIComponent(draft.parcelId)}/${draft.type==='tracking'?'tracking':'delivered'}`,data:body}),true);
    }
  };
  if(!data&&loading)return <section aria-label="Packages" aria-busy="true" className="surface p-4 space-y-3"><h2 className="text-sm font-semibold">Packages</h2><div className="h-12 rounded-lg bg-gray-100" /></section>;
  return <section aria-label="Packages" className="surface p-4 space-y-4 min-w-0">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <h2 className="text-sm font-semibold">Packages</h2>
      <div className="flex flex-wrap gap-2">
        {groups.map(id=><button type="button" key={id} className="button button-dark" disabled={busy||loading} aria-label={groups.length>1?`Mark shipped ${id}`:'Mark shipped'} onClick={()=>open({type:'handoff',fulfillmentId:id,waybill:'',quantities:{},confirmed:false})}>Mark shipped{groups.length>1&&<span className="ml-1 text-xs">{id}</span>}</button>)}
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
      {scope==='admin'&&parcel.status!=='DELIVERED'&&<div className="flex flex-wrap gap-2"><button type="button" className="button button-outline" disabled={busy} onClick={()=>open({type:'tracking',parcelId:parcel.id,waybill:parcel.waybill,reason:''})}>Correct tracking</button><button type="button" className="button button-outline" disabled={busy} onClick={()=>open({type:'delivered',parcelId:parcel.id,reason:''})}>Mark delivered</button></div>}
    </article>)}
    {draft&&<ModalFrame title={draft.type==='handoff'?'Record carrier handoff':draft.type==='tracking'?'Correct tracking':'Record delivery'} onClose={()=>{if(!busy)setDraft(null);}}>
      <form onSubmit={submit} className="surface p-5 space-y-4 w-full min-w-0">
        <h2 className="text-base font-semibold">{draft.type==='handoff'?'Record carrier handoff':draft.type==='tracking'?'Correct tracking':'Record delivery'}</h2>
        {draft.type!=='delivered'&&<label className="block text-sm">Tracking number<input aria-label="Tracking number" className="mt-2 w-full border rounded-lg px-3 py-2 text-base" value={draft.waybill} maxLength={80} required disabled={busy} onChange={event=>edit({waybill:event.target.value.trim()})} /></label>}
        {draft.type==='handoff'?<>
          <p className="text-xs text-gray-500">Choose the packed quantities handed to the carrier. Leave other items at zero.</p>
          {eligible.map(item=><label key={item.lineNumber} className="block text-sm break-words">{item.productName}<span className="block text-xs text-gray-500">Maximum {item.maximum}</span><input aria-label={`Ship quantity for ${item.productName}`} className="mt-1 w-full border rounded-lg px-3 py-2 text-base" type="number" inputMode="numeric" min="0" max={item.maximum} step="1" value={draft.quantities[item.lineNumber]??'0'} disabled={busy} onChange={event=>edit({quantities:{...draft.quantities,[item.lineNumber]:event.target.value}})} /></label>)}
          <label className="flex items-start gap-2 text-sm"><input type="checkbox" checked={draft.confirmed} disabled={busy} onChange={event=>edit({confirmed:event.target.checked})} />I confirm these items were handed to the carrier.</label>
        </>:<label className="block text-sm">Reason<textarea aria-label="Audit reason" className="mt-2 w-full border rounded-lg px-3 py-2 text-base" rows={3} maxLength={500} value={draft.reason} disabled={busy} required onChange={event=>edit({reason:event.target.value})} /></label>}
        {message&&<p role="alert" className="text-sm text-red-700">{message}</p>}
        <div className="flex flex-wrap gap-2"><button type="button" className="button button-outline" disabled={busy} onClick={()=>setDraft(null)}>Close</button><button type="submit" className="button button-dark" disabled={busy||loading||!valid}>{busy?'Saving…':draft.type==='handoff'?'Confirm handoff':draft.type==='tracking'?'Save tracking':'Confirm delivered'}</button></div>
      </form>
    </ModalFrame>}
  </section>;
}
