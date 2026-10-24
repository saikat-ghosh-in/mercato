import ModalFrame from "../shared/ModalFrame";
export const DeleteModal = ({ open, setOpen, title, onDeleteHandler, loader }) => {
  if (!open) return null;
  return <ModalFrame title={title} onClose={() => { if (!loader) setOpen(false); }}><div className="p-8"><h2 className="text-2xl font-medium mb-6">{title}</h2><p className="text-sm text-gray-500 mb-8">Remove this saved address? You can add it again later.</p><div className="flex gap-4"><button className="button button-outline flex-1" disabled={loader} onClick={() => setOpen(false)}>Cancel</button><button className="button button-dark flex-1" disabled={loader} onClick={onDeleteHandler}>{loader ? "Deleting…" : "Delete"}</button></div></div></ModalFrame>;
};
