import { useLayoutEffect, useRef } from "react";

// Observe layout changes (including an expanded search), without React updates on scroll.
export function useMeasuredHeight(property) {
  const ref = useRef(null);
  useLayoutEffect(() => {
    const element = ref.current;
    if (!element) return;
    const measure = () => document.documentElement.style.setProperty(property, `${Math.ceil(element.getBoundingClientRect().height)}px`);
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => { observer.disconnect(); document.documentElement.style.removeProperty(property); };
  }, [property]);
  return ref;
}
