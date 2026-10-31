import { createContext, useContext, useEffect, useId, useLayoutEffect, useRef, useState, useSyncExternalStore } from "react";
import { FiChevronRight } from "react-icons/fi";

const AccordionContext = createContext(null);
const ItemContext = createContext(null);
const motionQuery = "(prefers-reduced-motion: reduce)";
const subscribeMotion = notify => {
  const media = window.matchMedia(motionQuery);
  media.addEventListener("change", notify);
  return () => media.removeEventListener("change", notify);
};
const readMotion = () => window.matchMedia(motionQuery).matches;
const serverMotion = () => false;
const valuesOf = value => value == null ? [] : Array.isArray(value) ? value : [value];

function Accordion({ children, multiple = false, defaultValue, value, onValueChange, className = "", ...props }) {
  const [localValue, setLocalValue] = useState(defaultValue);
  const selected = valuesOf(value === undefined ? localValue : value);
  const toggle = item => {
    const next = multiple ? selected.includes(item) ? selected.filter(value => value !== item) : [...selected, item] : selected.includes(item) ? "" : item;
    if (value === undefined) setLocalValue(next);
    onValueChange?.(next);
  };
  return <AccordionContext.Provider value={{ selected, toggle }}><div {...props} className={`accordion ${className}`} data-accordion-root="">{children}</div></AccordionContext.Provider>;
}

function AccordionItem({ value, children, className = "", ...props }) {
  const accordion = useContext(AccordionContext);
  const id = useId();
  const triggerRef = useRef(null);
  const open = accordion.selected.includes(value);
  return <ItemContext.Provider value={{ value, open, triggerRef, triggerId: `${id}-trigger`, contentId: `${id}-content` }}><section {...props} className={`accordion-item ${className}`} data-state={open ? "open" : "closed"}>{children}</section></ItemContext.Provider>;
}

function AccordionTrigger({ children, className = "", onClick, onKeyDown, ...props }) {
  const { toggle } = useContext(AccordionContext);
  const { value, open, triggerRef, triggerId, contentId } = useContext(ItemContext);
  const navigate = event => {
    onKeyDown?.(event);
    if (event.defaultPrevented || !["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) return;
    const root = event.currentTarget.closest("[data-accordion-root]");
    const triggers = [...root.querySelectorAll("[data-accordion-trigger]")].filter(trigger => !trigger.disabled && trigger.closest("[data-accordion-root]") === root);
    const index = triggers.indexOf(event.currentTarget);
    const next = event.key === "Home" ? 0 : event.key === "End" ? triggers.length - 1 : (index + (event.key === "ArrowDown" ? 1 : -1) + triggers.length) % triggers.length;
    event.preventDefault();
    triggers[next]?.focus();
  };
  return <button {...props} ref={triggerRef} type="button" id={triggerId} aria-expanded={open} aria-controls={contentId} data-accordion-trigger="" className={`accordion-trigger ${className}`} onKeyDown={navigate} onClick={event => { onClick?.(event); if (!event.defaultPrevented) toggle(value); }}><span>{children}</span><FiChevronRight className="accordion-chevron" aria-hidden="true" /></button>;
}

function AccordionContent({ children, className = "", ...props }) {
  const { open, triggerRef, triggerId, contentId } = useContext(ItemContext);
  const contentRef = useRef(null);
  const reducedMotion = useSyncExternalStore(subscribeMotion, readMotion, serverMotion);
  const [present, setPresent] = useState(open);
  // Reopening renders the mounted content immediately and cancels the pending exit.
  if (open && !present) setPresent(true);
  useLayoutEffect(() => {
    if (!open && contentRef.current?.contains(document.activeElement)) triggerRef.current?.focus();
  }, [open, triggerRef]);
  useEffect(() => {
    if (open || !present) return;
    const timeout = window.setTimeout(() => setPresent(false), reducedMotion ? 0 : 180);
    return () => window.clearTimeout(timeout);
  }, [open, present, reducedMotion]);
  return <div {...props} ref={contentRef} id={contentId} role="region" aria-labelledby={triggerId} hidden={!open && (reducedMotion || !present)} inert={!open} aria-hidden={!open} data-state={open ? "open" : "closed"} className={`accordion-content ${className}`}><div className="accordion-content-inner">{children}</div></div>;
}

Accordion.Item = AccordionItem;
Accordion.Trigger = AccordionTrigger;
Accordion.Content = AccordionContent;
export default Accordion;
