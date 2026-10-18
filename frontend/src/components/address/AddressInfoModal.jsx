import { Dialog, DialogPanel, DialogTitle } from "@headlessui/react";
import { MdClose } from "react-icons/md";

export default function AddressInfoModal({ open, setOpen, children }) {
  return <Dialog open={open} onClose={setOpen} className="relative z-60">
    <div className="fixed inset-0 bg-black/40" aria-hidden="true" />
    <div className="fixed inset-0 overflow-y-auto p-4 flex items-start sm:items-center justify-center">
      <DialogPanel className="relative w-full max-w-lg bg-white rounded-xl shadow-xl border border-gray-100 my-auto">
        <DialogTitle className="sr-only">Delivery address</DialogTitle>
        <button onClick={() => setOpen(false)} aria-label="Close address form" className="absolute top-4 right-4 p-2 text-gray-400 hover:text-gray-600"><MdClose size={20} /></button>
        <div className="p-6">{children}</div>
      </DialogPanel>
    </div>
  </Dialog>;
}
