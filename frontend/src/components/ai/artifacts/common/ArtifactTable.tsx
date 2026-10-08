import { Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { AgentTableColumn } from '../../../../types/agent';

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
    ellipsis: true,
    render: (value: unknown) => displayValue(value, column.format),
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
