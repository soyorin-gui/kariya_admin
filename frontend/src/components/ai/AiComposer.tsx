import { Button, Input, Tooltip } from 'antd';
import { SendOutlined, StopOutlined } from '@ant-design/icons';

interface AiComposerProps {
  value: string;
  loading: boolean;
  pageTitle?: string;
  onChange: (value: string) => void;
  onSend: () => void;
  onStop: () => void;
}

/** 单行起步、最多四行的统一消息编辑器。生成过程中发送按钮切换为停止按钮。 */
export function AiComposer({ value, loading, pageTitle, onChange, onSend, onStop }: AiComposerProps) {
  const action = loading ? onStop : onSend;
  return (
    <div className='ai-input'>
      <Input.TextArea
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onPressEnter={(event) => {
          if (!event.shiftKey) {
            event.preventDefault();
            if (!loading && value.trim()) onSend();
          }
        }}
        placeholder={pageTitle ? `在“${pageTitle}”页面提问…` : '输入你想了解的问题…'}
        autoSize={{ minRows: 1, maxRows: 4 }}
        variant='borderless'
      />
      <Tooltip title={loading ? '停止生成' : '发送'}>
        <Button
          className='ai-composer-action'
          type='primary'
          shape='circle'
          aria-label={loading ? '停止生成' : '发送'}
          icon={loading ? <StopOutlined /> : <SendOutlined />}
          disabled={!loading && !value.trim()}
          onClick={action}
        />
      </Tooltip>
    </div>
  );
}
