import type { ReactNode } from 'react';
import { QuestionCircleOutlined } from '@ant-design/icons';
import { Tooltip } from 'antd';
import './fieldLabel.css';

export interface FieldLabelProps {
  text: ReactNode;
  hint?: ReactNode;
}

/** 为表单标签附加按需展示的规则说明，不额外占用表单纵向空间。 */
export function FieldLabel({ text, hint }: FieldLabelProps) {
  if (!hint) return <>{text}</>;
  return (
    <span className='field-label'>
      {text}
      <Tooltip title={hint}>
        <QuestionCircleOutlined className='field-label-hint' />
      </Tooltip>
    </span>
  );
}
