import { App, Button, Tooltip, type ButtonProps } from 'antd';
import { CheckOutlined, CopyOutlined } from '@ant-design/icons';
import { useClipboard } from '../../hooks/useClipboard';

export interface CopyButtonProps extends Omit<ButtonProps, 'icon' | 'loading' | 'onClick'> {
  value: string;
  tooltip?: string;
  successTooltip?: string;
  successMessage?: string;
  errorMessage?: string;
}

export function CopyButton({ value, tooltip = '复制', successTooltip = '已复制', successMessage, errorMessage = '复制失败，请检查浏览器权限', ...buttonProps }: CopyButtonProps) {
  const { message } = App.useApp();
  const { copy, copied, copying } = useClipboard();
  const handleCopy = async () => {
    const succeeded = await copy(value);
    if (succeeded && successMessage) message.success(successMessage);
    if (!succeeded) message.error(errorMessage);
  };
  return (
    <Tooltip title={copied ? successTooltip : tooltip}>
      <Button type='text' aria-label={copied ? successTooltip : tooltip} {...buttonProps} loading={copying} icon={copied ? <CheckOutlined /> : <CopyOutlined />} onClick={() => void handleCopy()} />
    </Tooltip>
  );
}
