import { Popover, PopoverButton, PopoverPanel } from '@headlessui/react';
import { FiInfo } from 'react-icons/fi';

export default function InfoButton({ label, children }) {
  return <Popover className="inline-flex shrink-0">
    <PopoverButton type="button" className="field-info-button" aria-label={`About ${label}`}><FiInfo aria-hidden="true" /></PopoverButton>
    <PopoverPanel anchor="bottom start" transition className="field-info-panel z-[110]" role="note"><strong>{label}</strong><p>{children}</p></PopoverPanel>
  </Popover>;
}
