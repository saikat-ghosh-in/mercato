import { Children, cloneElement, isValidElement, useId } from "react";
import SelectControl from "./SelectControl";
import { Popover, PopoverButton, PopoverPanel } from "@headlessui/react";
import { FiInfo } from "react-icons/fi";
export default function AuthField({ label, error, errors = [], icon: Icon, required = false, hint, info, children }) {
  const id = useId();
  const messages = [...new Set([error, ...errors].filter(Boolean))];
  const describedBy = messages.length ? id + "-error" : hint ? id + "-hint" : undefined;
  return <div className="auth-field"><div className="auth-field-label"><label htmlFor={id}>{label}{required && <span className="text-red-500 ml-0.5" aria-hidden="true">*</span>}</label>{info && <Popover><PopoverButton type="button" className="field-info-button" aria-label={`About ${label}`}><FiInfo aria-hidden="true" /></PopoverButton><PopoverPanel anchor="bottom start" transition className="field-info-panel" role="note"><strong>{label}</strong><p>{info}</p></PopoverPanel></Popover>}</div>
    <div className={"auth-field-control flex items-center gap-2.5 border rounded-lg transition-colors focus-within:ring-1 " + (messages.length ? "border-red-300 focus-within:ring-red-300 bg-red-50/30" : "border-gray-200 focus-within:ring-blue-300")}>
      {Icon && <Icon size={15} className="text-gray-400" aria-hidden="true" />}
      {Children.map(children, child => isValidElement(child) && (["input","select","textarea"].includes(child.type) || child.type === SelectControl) ? cloneElement(child, { id, required, "aria-invalid": Boolean(messages.length), "aria-describedby": describedBy }) : child)}
    </div>
    {messages.length > 0 ? <div id={id + "-error"} role="alert" className="text-xs text-red-600 mt-1">{messages.map(message => <p key={message}>{message}</p>)}</div> : hint && <p id={id + "-hint"} className="text-xs text-gray-500 mt-2">{hint}</p>}
  </div>;
}
