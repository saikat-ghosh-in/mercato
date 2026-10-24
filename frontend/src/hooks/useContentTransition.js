import { useLayoutEffect, useRef } from "react";

// Bridge a change of view without delaying navigation or moving keyboard focus.
export function useContentTransition(ref, view) {
  const previous = useRef(view);
  useLayoutEffect(() => {
    if (previous.current === view) return;
    previous.current = view;
    const element = ref.current;
    if (!element?.animate || document.documentElement.dataset.input === "keyboard") return;
    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)");
    const animation = element.animate(
      reduced.matches
        ? [{ opacity: .7 }, { opacity: 1 }]
        : [{ opacity: .7, transform: "translateY(4px)" }, { opacity: 1, transform: "translateY(0)" }],
      { duration: reduced.matches ? 100 : 180, easing: "cubic-bezier(0.23, 1, 0.32, 1)" },
    );
    const finish = () => animation.cancel();
    reduced.addEventListener("change", finish);
    return () => { animation.cancel(); reduced.removeEventListener("change", finish); };
  }, [ref, view]);
}
