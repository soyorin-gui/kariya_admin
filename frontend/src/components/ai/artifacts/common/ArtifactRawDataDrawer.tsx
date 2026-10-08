import { Button, Drawer, Typography } from 'antd';
import { CodeOutlined } from '@ant-design/icons';
import { useState } from 'react';

interface ArtifactRawDataDrawerProps {
  data: unknown;
}

/** 原始 JSON 仅按需展示，避免技术数据默认占满聊天区域。 */
export function ArtifactRawDataDrawer({ data }: ArtifactRawDataDrawerProps) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button type='link' size='small' icon={<CodeOutlined />} onClick={() => setOpen(true)}>
        查看原始数据
      </Button>
      <Drawer title='结构化原始数据' width={520} open={open} onClose={() => setOpen(false)}>
        <Typography.Text code className='ai-artifact-raw-json'>
          {JSON.stringify(data, null, 2)}
        </Typography.Text>
      </Drawer>
    </>
  );
}
