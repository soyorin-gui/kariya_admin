import { Input, Popover } from 'antd';
import { ExpandOutlined } from '@ant-design/icons';
import './field-text.css';

export function ExpandableTextInput({ value, onChange, maxLength = 1000, placeholder }: { value?: string; onChange?: (value: string) => void; maxLength?: number; placeholder?: string }) {
  const current = value ?? '';
  return <Input size='small' value={current} onChange={(event) => onChange?.(event.target.value)} maxLength={maxLength} placeholder={placeholder} suffix={
    <Popover trigger='click' placement='topRight' content={<div className='intranet-field-text-editor'><Input.TextArea value={current} onChange={(event) => onChange?.(event.target.value)} autoSize={{ minRows: 5, maxRows: 10 }} maxLength={maxLength} showCount placeholder={placeholder} /></div>}>
      <button type='button' className='intranet-field-text-expand' aria-label='展开编辑说明' onMouseDown={(event) => event.preventDefault()}><ExpandOutlined /></button>
    </Popover>
  } />;
}
