import type { Key, ReactNode } from 'react';
import { Form, Table } from 'antd';
import type { FormInstance, FormItemProps, TableProps } from 'antd';
import type { ColumnType } from 'antd/es/table';

export interface InlineEditableColumn<Row extends object, Values extends object> extends Omit<ColumnType<Row>, 'render'> {
  field?: Extract<keyof Values, string>;
  rules?: FormItemProps['rules'];
  editor?: (row: Row) => ReactNode;
  view?: (value: unknown, row: Row, index: number) => ReactNode;
}

export interface InlineEditableTableProps<Row extends object, Values extends object> extends Omit<TableProps<Row>, 'columns' | 'dataSource' | 'rowKey'> {
  form: FormInstance<Values>;
  editingKey?: Key;
  getRowKey: (row: Row) => Key;
  columns: Array<InlineEditableColumn<Row, Values>>;
  dataSource: Row[];
}

export function InlineEditableTable<Row extends object, Values extends object>({ form, editingKey, getRowKey, columns, dataSource, ...tableProps }: InlineEditableTableProps<Row, Values>) {
  const resolvedColumns: TableProps<Row>['columns'] = columns.map(({ field, rules, editor, view, ...column }) => ({
    ...column,
    render: (value: unknown, row: Row, index: number) => {
      if (field && editor && getRowKey(row) === editingKey) {
        return <Form.Item name={field as never} rules={rules} style={{ margin: 0 }}>{editor(row)}</Form.Item>;
      }
      return view ? view(value, row, index) : (value as ReactNode);
    },
  }));
  return <Form form={form} component={false}><Table<Row> {...tableProps} rowKey={getRowKey} columns={resolvedColumns} dataSource={dataSource} /></Form>;
}
