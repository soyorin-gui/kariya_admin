import { Tag, type TagProps } from 'antd';
import type { ReactNode } from 'react';

export type StatusTagStatus = 'success' | 'error' | 'warning' | 'processing' | 'default';

export interface StatusTagProps extends Omit<TagProps, 'color' | 'children'> {
  status?: StatusTagStatus;
  label?: ReactNode;
  children?: ReactNode;
}

const colors: Record<StatusTagStatus, TagProps['color']> = {
  success: 'success',
  error: 'error',
  warning: 'warning',
  processing: 'processing',
  default: 'default',
};

export function StatusTag({ status = 'default', label, children, ...tagProps }: StatusTagProps) {
  return (
    <Tag {...tagProps} color={colors[status]}>
      {label ?? children}
    </Tag>
  );
}
