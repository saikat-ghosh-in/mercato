import { useState } from "react";
import { SubHeaderContext } from "../../hooks/useSubHeader";

export const SubHeaderProvider = ({ children }) => {
  const [subHeader, setSubHeader] = useState(null);
  return <SubHeaderContext.Provider value={{ subHeader, setSubHeader }}>{children}</SubHeaderContext.Provider>;
};
