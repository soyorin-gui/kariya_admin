import { Drawer, Empty } from 'antd';
import { CodeViewer } from '../../../components/CodeViewer';
import type { QueryRecord } from './types';

export function RecordDetailDrawer({ record, onClose }: { record?: QueryRecord; onClose: () => void }) {
  return <Drawer title='查询记录详情' width={720} open={Boolean(record)} onClose={onClose} destroyOnClose>
    {record ? <CodeViewer value={JSON.stringify(record.raw, null, 2)} language='json' filename={`record-${record.id}.json`} height='calc(100vh - 160px)' /> : <Empty description='请选择查询记录' />}
  </Drawer>;
}
