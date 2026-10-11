import { useEffect, useRef, type Key, type ReactNode } from 'react';
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

/**
 * intranet 行内编辑表格骨架：单行编辑 + 固定表头 + 表格内滚动 + 自动滚到正在编辑的行。
 *
 * 自带 `.card-scroll` 滚动容器（配合 global.css 的 page-fill 约定使用，见 D-031），
 * 所以调用方把它直接放在 `.table-card` 里即可，**不要再自己包一层滚动 div**。
 *
 * `sticky` 由本组件统一设置：antd 在设了 `sticky` 后会把表头拆成独立的
 * `.ant-table-sticky-holder`（`position: sticky`），这才是"表头不随表体滚动"的正确做法。
 * 只靠 CSS 给 `th` 加 `position: sticky` 是无效的——`scroll.x` 会让 antd 把表包进
 * `.ant-table-content`（`overflow-y: scroll`），`th` 会被困在那个不滚动的容器里。
 */
export function InlineEditableTable<Row extends object, Values extends object>({ form, editingKey, getRowKey, columns, dataSource, ...tableProps }: InlineEditableTableProps<Row, Values>) {
  const scrollRef = useRef<HTMLDivElement>(null);

  /**
   * 进入新增/编辑时把目标行滚进视野。
   * 新增行是追加在表格末尾的，不滚动的话用户点完"新增"看不到任何变化。
   * `block: 'nearest'` 表示"只滚到刚好可见"：新增行从底部滑入，编辑屏幕中间的行则几乎不跳动。
   */
  useEffect(() => {
    if (editingKey === undefined || editingKey === null) return;
    const row = scrollRef.current?.querySelector(`tr[data-row-key="${CSS.escape(String(editingKey))}"]`);
    row?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  }, [editingKey]);

  const resolvedColumns: TableProps<Row>['columns'] = columns.map(({ field, rules, editor, view, ...column }) => ({
    ...column,
    render: (value: unknown, row: Row, index: number) => {
      if (field && editor && getRowKey(row) === editingKey) {
        return <Form.Item name={field as never} rules={rules} style={{ margin: 0 }}>{editor(row)}</Form.Item>;
      }
      return view ? view(value, row, index) : (value as ReactNode);
    },
  }));
  return (
    <div className='card-scroll' ref={scrollRef}>
      <Form form={form} component={false}>
        <Table<Row>
          {...tableProps}
          rowKey={getRowKey}
          columns={resolvedColumns}
          dataSource={dataSource}
          sticky={{ getContainer: () => scrollRef.current ?? window }}
        />
      </Form>
    </div>
  );
}
