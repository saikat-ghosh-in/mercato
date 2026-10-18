import { useSubHeader } from "../../hooks/useSubHeader";

const SubHeaderSlot = () => {
    const { subHeader } = useSubHeader();
    if (!subHeader) return null;

    return (
        <div className="sticky top-[136px] md:top-[140px] z-30 bg-white border-b border-slate-200 shadow-sm">
            {subHeader}
        </div>
    );
};

export default SubHeaderSlot;
