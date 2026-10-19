import { FiChevronDown } from "react-icons/fi";

// A native select keeps platform keyboard and option behavior; only the arrow is styled.
export default function SelectControl({ className = "", wrapperClassName = "", children, ...props }) {
  return <span className={"select-control " + wrapperClassName} data-disabled={props.disabled || undefined}><select {...props} className={"select-input " + className}>{children}</select><FiChevronDown className="select-chevron" aria-hidden="true" /></span>;
}
