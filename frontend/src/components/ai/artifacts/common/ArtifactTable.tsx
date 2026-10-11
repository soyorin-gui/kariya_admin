import { Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { AgentTableColumn } from '../../../../types/agent';
import { EllipsisText } from '../../../EllipsisText';

interface ArtifactTableProps {
  columns: AgentTableColumn[];
  rows: Record<string, unknown>[];
}

function displayValue(value: unknown, format?: string) {
  if (value === null || value === undefined || value === '') return '-';
  if (format === 'status') return <Tag>{String(value)}</Tag>;
  if (format === 'duration') return `${String(value)} ms`;
  return String(value);
}

export function ArtifactTable({ columns, rows }: ArtifactTableProps) {
  const tableColumns: ColumnsType<Record<string, unknown>> = columns.map((column) => ({
    key: column.key,
    dataIndex: column.key,
    title: column.title,
    ellipsis: { showTitle: false },
    render: (value: unknown) => {
      const display = displayValue(value, column.format);
      // status 列渲染的是 Tag，不能塞进截断容器；只有纯文本才交给 EllipsisText。
      return typeof display === 'string' ? <EllipsisText value={display} /> : display;
    },
  }));
  const data = rows.map((row, index) => ({ ...row, __rowKey: index }));
  return (
    <Table
      className='ai-report-table'
      size='small'
      columns={tableColumns}
      dataSource={data}
      rowKey='__rowKey'
      pagination={false}
      scroll={{ x: 'max-content' }}
    />
  );
}
