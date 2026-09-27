import { useCallback, useEffect, useRef, useState } from 'react';

async function writeWithFallback(value: string): Promise<void> {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(value);
    return;
  }
  const textarea = document.createElement('textarea');
  textarea.value = value;
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.select();
  const succeeded = document.execCommand('copy');
  textarea.remove();
  if (!succeeded) throw new Error('浏览器不支持复制操作');
}

export function useClipboard(resetDelay = 1600) {
  const [copied, setCopied] = useState(false);
  const [copying, setCopying] = useState(false);
  const [error, setError] = useState<unknown>();
  const timerRef = useRef<number>();

  useEffect(() => () => window.clearTimeout(timerRef.current), []);

  const copy = useCallback(
    async (value: string) => {
      setCopying(true);
      setError(undefined);
      try {
        await writeWithFallback(value);
        setCopied(true);
        window.clearTimeout(timerRef.current);
        timerRef.current = window.setTimeout(() => setCopied(false), resetDelay);
        return true;
      } catch (reason) {
        setCopied(false);
        setError(reason);
        return false;
      } finally {
        setCopying(false);
      }
    },
    [resetDelay],
  );

  return { copy, copied, copying, error };
}
