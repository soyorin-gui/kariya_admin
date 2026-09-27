import { useEffect, useRef } from 'react';

export function useResizeObserver<T extends Element>(callback: (entry: ResizeObserverEntry) => void) {
  const elementRef = useRef<T>(null);
  const callbackRef = useRef(callback);
  callbackRef.current = callback;

  useEffect(() => {
    const element = elementRef.current;
    if (!element || typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(([entry]) => {
      if (entry) callbackRef.current(entry);
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  return elementRef;
}
