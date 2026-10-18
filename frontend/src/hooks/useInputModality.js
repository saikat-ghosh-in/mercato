import { useEffect } from "react";

// Keep keyboard navigation instant without re-rendering the application on each key.
export function useInputModality() {
  useEffect(() => {
    const root = document.documentElement;
    const keyboard = event => {
      if (!event.metaKey && !event.ctrlKey && !event.altKey && root.dataset.input !== "keyboard") root.dataset.input = "keyboard";
    };
    const pointer = () => { if (root.dataset.input !== "pointer") root.dataset.input = "pointer"; };
    window.addEventListener("keydown", keyboard, true);
    window.addEventListener("pointerdown", pointer, true);
    return () => {
      window.removeEventListener("keydown", keyboard, true);
      window.removeEventListener("pointerdown", pointer, true);
      delete root.dataset.input;
    };
  }, []);
}
