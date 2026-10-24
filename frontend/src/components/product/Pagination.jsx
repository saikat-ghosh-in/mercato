import { FiChevronLeft, FiChevronRight } from "react-icons/fi";

const Pagination = ({ pageInfo, loading, onPageChange }) => {
    const { totalPages, pageNumber, lastPage } = pageInfo;
    if (loading || totalPages <= 1) return null;

    return (
        <div className="flex items-center justify-center gap-2 mt-10">
            <button
                onClick={() => onPageChange(pageNumber - 1)}
                disabled={pageNumber === 0}
                className="control flex items-center gap-1.5 px-4 py-2 text-sm font-medium border border-gray-200 rounded-xl bg-white text-gray-600 hover:border-blue-300 hover:text-blue-500 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer"
            >
                <FiChevronLeft size={15} /> Prev
            </button>

            <div className="flex items-center gap-1.5">
                {Array.from({ length: totalPages }).map((_, i) => {
                    const nearCurrent = Math.abs(i - pageNumber) <= 1;
                    const isEdge      = i === 0 || i === totalPages - 1;
                    const isEllipsis  = (i === 1 && pageNumber > 3) || (i === totalPages - 2 && pageNumber < totalPages - 4);

                    if (isEllipsis) return <span key={i} className="text-gray-400 text-sm px-1">…</span>;
                    if (!nearCurrent && !isEdge) return null;

                    return (
                        <button
                            key={i}
                            onClick={() => onPageChange(i)}
                            className={`w-9 h-9 rounded-xl text-sm font-semibold transition-colors cursor-pointer ${
                                i === pageNumber
                                    ? "bg-blue-500 text-white "
                                    : "bg-white border border-gray-200 text-gray-600 hover:border-blue-300 hover:text-blue-500"
                            }`}
                        >
                            {i + 1}
                        </button>
                    );
                })}
            </div>

            <button
                onClick={() => onPageChange(pageNumber + 1)}
                disabled={lastPage}
                className="control flex items-center gap-1.5 px-4 py-2 text-sm font-medium border border-gray-200 rounded-xl bg-white text-gray-600 hover:border-blue-300 hover:text-blue-500 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer"
            >
                Next <FiChevronRight size={15} />
            </button>
        </div>
    );
};

export default Pagination;