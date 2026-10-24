import { createContext, useContext } from "react";
export const SubHeaderContext = createContext(null);
export const useSubHeader = () => useContext(SubHeaderContext);
