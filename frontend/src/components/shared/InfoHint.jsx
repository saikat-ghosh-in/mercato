import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { FiInfo } from 'react-icons/fi';

/** Information, not an action: hover or focus reveals the same short hint. */
export default function InfoHint({ label, children }) {
  const id=useId(),trigger=useRef(null),tooltip=useRef(null),closing=useRef(null);
  const [open,setOpen]=useState(false);
  const [portalRoot,setPortalRoot]=useState(null);
  const show=()=>{window.clearTimeout(closing.current);setPortalRoot(trigger.current?.closest('[role="dialog"]')||document.body);setOpen(true);};
  const leave=()=>{
    window.clearTimeout(closing.current);
    if(document.activeElement!==trigger.current)closing.current=window.setTimeout(()=>setOpen(false),120);
  };
  useLayoutEffect(()=>{
    if(!open||!trigger.current||!tooltip.current)return;
    const anchor=trigger.current.getBoundingClientRect(),panel=tooltip.current;
    const width=Math.min(280,window.innerWidth-24);
    panel.style.width=`${width}px`;
    const height=panel.getBoundingClientRect().height;
    const left=Math.max(12,Math.min(anchor.left-8,window.innerWidth-width-12));
    const top=anchor.bottom+8+height<=window.innerHeight-12?anchor.bottom+8:Math.max(12,anchor.top-height-8);
    panel.style.left=`${left}px`;panel.style.top=`${top}px`;
  },[open,label,children]);
  useEffect(()=>{
    if(!open)return;
    const dismiss=()=>setOpen(false),key=event=>{if(event.key==='Escape'){event.preventDefault();event.stopPropagation();dismiss();}};
    window.addEventListener('resize',dismiss);window.addEventListener('scroll',dismiss,true);document.addEventListener('keydown',key);
    return()=>{window.removeEventListener('resize',dismiss);window.removeEventListener('scroll',dismiss,true);document.removeEventListener('keydown',key);};
  },[open]);
  useEffect(()=>()=>window.clearTimeout(closing.current),[]);
  return <>
    <span ref={trigger} role="img" tabIndex={0} aria-label={`About ${label}`} aria-describedby={open?id:undefined} className="field-info-button info-hint-trigger" onPointerEnter={event=>{if(event.pointerType!=='touch')show();}} onPointerLeave={leave} onFocus={show} onBlur={()=>setOpen(false)}><FiInfo aria-hidden="true"/></span>
    {open&&portalRoot&&createPortal(<div ref={tooltip} id={id} role="tooltip" className="field-info-panel info-hint-tooltip" onPointerEnter={show} onPointerLeave={leave}><strong>{label}</strong><p>{children}</p></div>,portalRoot)}
  </>;
}
