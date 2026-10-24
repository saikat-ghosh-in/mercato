import { Dialog, DialogBackdrop, DialogPanel, DialogTitle } from "@headlessui/react";
export default function ModalFrame({ title, onClose, children }) {
  return <Dialog open onClose={onClose} className="relative z-[80]"><DialogBackdrop transition className="dialog-backdrop" /><div className="modal-viewport fixed inset-0 flex items-center justify-center p-4 overflow-y-auto"><DialogPanel transition className="modal-frame"><DialogTitle className="sr-only">{title}</DialogTitle>{children}</DialogPanel></div></Dialog>;
}
