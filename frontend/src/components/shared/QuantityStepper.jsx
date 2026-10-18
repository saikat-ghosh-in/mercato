import { FiMinus, FiPlus } from "react-icons/fi";
import { HiOutlineTrash } from "react-icons/hi";

const QuantityStepper = ({
    quantity,
    onIncrease,
    onDecrease,
    canAddMore = true,
    pendingSync = false
}) => {
    const showTrash = quantity === 1;

    return (
        <div className="flex items-center border border-gray-200 rounded-xl overflow-hidden h-8 w-fit">
            <button
                onClick={onDecrease}
                aria-label={showTrash ? "Remove item" : "Decrease quantity"}
                disabled={pendingSync}
                className={`w-12 h-8 flex items-center justify-center transition-colors shrink-0 text-gray-400
                    disabled:opacity-30 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-gray-400
                    ${showTrash ? "hover:bg-gray-100 hover:text-red-500" : "hover:bg-gray-100 hover:text-gray-900"}`}
            >
                {showTrash ? <HiOutlineTrash size={14} /> : <FiMinus size={14} />}
            </button>

            <span className="px-4 text-sm font-bold text-gray-600 min-w-12 text-center">
                {quantity}
            </span>

            <button
                onClick={onIncrease}
                aria-label="Increase quantity"
                disabled={!canAddMore || pendingSync}
                className="w-12 h-8 flex items-center justify-center text-gray-400 transition-colors shrink-0
                    hover:bg-gray-100 hover:text-gray-900
                    disabled:opacity-30 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-gray-400"
            >
                <FiPlus size={14} />
            </button>
        </div>
    );
};

export default QuantityStepper;
