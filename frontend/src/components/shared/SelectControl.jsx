import { Children, isValidElement } from "react";
import { Listbox, ListboxButton, ListboxOption, ListboxOptions } from "@headlessui/react";
import { FiCheck, FiChevronDown } from "react-icons/fi";

// Keep the existing option/event API while rendering the same accessible menu on every platform.
export default function SelectControl({ className = "", wrapperClassName = "", children, value = "", onChange, disabled = false, name, required, ref, ...props }) {
  const options = Children.toArray(children).filter(isValidElement);
  const selected = options.find(option => String(option.props.value) === String(value));
  return <Listbox value={String(value)} disabled={disabled} name={name} onChange={next => onChange?.({ target: { name, value: next }, type: "change" })}>
    <span className={"select-control " + wrapperClassName} data-disabled={disabled || undefined}>
      <ListboxButton {...props} ref={ref} type="button" className={"select-input " + className} aria-required={required || undefined}>
        <span className="select-value">{selected?.props.children || "Choose an option"}</span><FiChevronDown className="select-chevron" aria-hidden="true" />
      </ListboxButton>
    </span>
    <ListboxOptions anchor={{ to: "bottom start", gap: 6, padding: 12 }} transition modal={false} className="select-options">
      {options.map(option => <ListboxOption key={String(option.props.value)} value={String(option.props.value)} disabled={option.props.disabled} className="select-option">
        {({ selected }) => <><span>{option.props.children}</span><FiCheck aria-hidden="true" className={selected ? "" : "invisible"} /></>}
      </ListboxOption>)}
    </ListboxOptions>
  </Listbox>;
}
