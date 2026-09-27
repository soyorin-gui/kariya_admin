import { useCallback, useEffect, useState, type RefObject } from 'react';

export function useFullscreen<T extends HTMLElement>(targetRef: RefObject<T | null>) {
  const [fullscreen, setFullscreen] = useState(false);
  useEffect(() => {
    const sync = () => setFullscreen(document.fullscreenElement === targetRef.current);
    document.addEventListener('fullscreenchange', sync);
    return () => document.removeEventListener('fullscreenchange', sync);
  }, [targetRef]);
  const toggleFullscreen = useCallback(async () => {
    if (document.fullscreenElement === targetRef.current) await document.exitFullscreen();
    else await targetRef.current?.requestFullscreen();
  }, [targetRef]);
  return { fullscreen, toggleFullscreen };
}
