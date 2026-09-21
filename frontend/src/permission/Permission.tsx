import type { ReactNode } from 'react';
import { useAppSelector } from '../store/hooks';
export function usePermission() {
  const permissions = useAppSelector((s) => s.auth.permissions);
  return (code: string) => permissions.includes(code);
}
export function Permission({ code, children }: { code: string; children: ReactNode }) {
  return usePermission()(code) ? <>{children}</> : null;
}
