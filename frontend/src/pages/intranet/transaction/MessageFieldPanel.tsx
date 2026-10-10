import { useEffect, useMemo, useState } from 'react';
import { App, Button, Form, Input, Modal, Popconfirm, Radio, Select, Space, Table, Tabs, Tag } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { createTransactionMessageField, deleteTransactionMessageField, getTransactionMessageFields, updateTransactionMessageField } from './api';
import type { MessageSide, TransactionMessageField, TransactionMessageFieldRequest } from './types';
import { Permission } from '../../../permission/Permission';
import { getApiErrorMessage } from '../../../utils/apiError';

type TreeField = TransactionMessageField & { children?: TreeField[] };
const empty: TransactionMessageFieldRequest = { parentId: 0, nodeType: 'FIELD', fieldNameCn: '', fieldNameEn: '', requiredFlag: 0, sortOrder: 0 };
function toTree(rows: TransactionMessageField[]): TreeField[] {
  const map = new Map<number, TreeField>();
  rows.forEach((row) => map.set(row.id, { ...row }));
  const roots: TreeField[] = [];
  map.forEach((row) => {
    const parent = map.get(row.parentId);
    if (parent) parent.children = [...(parent.children ?? []), row];
    else roots.push(row);
  });
  return roots;
}
export function MessageFieldPanel({ transactionId }: { transactionId: number }) {
  const { message } = App.useApp();
  const [side, setSide] = useState<MessageSide>('REQUEST');
  const [rows, setRows] = useState<TransactionMessageField[]>([]);
  const [editing, setEditing] = useState<TransactionMessageField | null | undefined>();
  const [form] = Form.useForm<TransactionMessageFieldRequest>();
  const load = async () => {
    try {
      setRows(await getTransactionMessageFields(transactionId, side));
    } catch (error) {
      message.error(getApiErrorMessage(error, '无法加载报文字段'));
    }
  };
  useEffect(() => {
    void load();
  }, [side, transactionId]);
  useEffect(() => {
    if (editing !== undefined) form.setFieldsValue(editing ?? empty);
  }, [editing, form]);
  const save = async () => {
    try {
      const values = await form.validateFields();
      const result = editing ? await updateTransactionMessageField(transactionId, side, editing.id, values) : await createTransactionMessageField(transactionId, side, values);
      message.success(result.message || '保存成功');
      setEditing(undefined);
      void load();
    } catch (error) {
      if (!(error as { errorFields?: unknown }).errorFields) message.error(getApiErrorMessage(error, '保存失败'));
    }
  };
  const tree = useMemo(() => toTree(rows), [rows]);
  const columns: ColumnsType<TreeField> = [
    { title: '字段中文名', dataIndex: 'fieldNameCn', width: 140 },
    { title: '字段英文名', dataIndex: 'fieldNameEn', width: 145, render: (value) => <code>{value}</code> },
    { title: '节点', dataIndex: 'nodeType', width: 90, render: (value) => <Tag>{value}</Tag> },
    { title: '数据类型', dataIndex: 'dataType', width: 110, render: (value) => value || '-' },
    { title: '长度', dataIndex: 'dataLength', width: 90, render: (value) => value || '-' },
    { title: '必填', dataIndex: 'requiredFlag', width: 65, render: (value) => (value === 1 ? '是' : '否') },
    {
      title: '操作',
      width: 150,
      render: (_, row) => (
        <Permission code='intranet:transaction:update'>
          <Space>
            <a onClick={() => setEditing(row)}>
              <EditOutlined /> 编辑
            </a>
            <Popconfirm
              title='确定删除此字段？'
              onConfirm={() =>
                void deleteTransactionMessageField(transactionId, side, row.id)
                  .then(() => {
                    message.success('删除字段成功');
                    void load();
                  })
                  .catch((error) => message.error(getApiErrorMessage(error)))
              }
            >
              <a className='danger'>
                <DeleteOutlined /> 删除
              </a>
            </Popconfirm>
          </Space>
        </Permission>
      ),
    },
  ];
  return (
    <>
      <Tabs
        activeKey={side}
        onChange={(key) => setSide(key as MessageSide)}
        items={[
          { key: 'REQUEST', label: '请求字段' },
          { key: 'RESPONSE', label: '响应字段' },
        ]}
        tabBarExtraContent={
          <Permission code='intranet:transaction:update'>
            <Button type='primary' size='small' icon={<PlusOutlined />} onClick={() => setEditing(null)}>
              新增字段
            </Button>
          </Permission>
        }
      />
      <Table<TreeField> rowKey='id' columns={columns} dataSource={tree} pagination={false} size='small' scroll={{ x: 900 }} locale={{ emptyText: '暂无字段' }} />
      <Modal
        className='system-dialog transaction-field-dialog'
        width={620}
        open={editing !== undefined}
        title={editing ? '编辑报文字段' : '新增报文字段'}
        onCancel={() => setEditing(undefined)}
        onOk={() => void save()}
        destroyOnHidden
      >
        <Form form={form} layout='horizontal' labelCol={{ flex: '0 0 96px' }} colon={false} requiredMark={false}>
          <Form.Item name='parentId' label='父节点' rules={[{ required: true }]}>
            <Select options={[{ value: 0, label: '根节点' }, ...rows.filter((row) => row.id !== editing?.id).map((row) => ({ value: row.id, label: `${row.fieldNameCn}（${row.fieldNameEn}）` }))]} />
          </Form.Item>
          <Form.Item name='nodeType' label='节点类型' rules={[{ required: true }]}>
            <Select options={['FIELD', 'OBJECT', 'ARRAY'].map((value) => ({ value, label: value }))} />
          </Form.Item>
          <Form.Item name='fieldNameCn' label='字段中文名' rules={[{ required: true }]}>
            <Input maxLength={64} placeholder='请输入字段中文名' />
          </Form.Item>
          <Form.Item name='fieldNameEn' label='字段英文名' rules={[{ required: true }]}>
            <Input maxLength={64} placeholder='请输入字段英文名' />
          </Form.Item>
          <Form.Item name='dataType' label='数据类型'>
            <Input maxLength={64} placeholder='例如 String、Decimal' />
          </Form.Item>
          <Form.Item name='dataLength' label='长度/精度'>
            <Input maxLength={64} placeholder='例如 32、18,2' />
          </Form.Item>
          <Form.Item name='requiredFlag' label='是否必填'>
            <Radio.Group
              options={[
                { value: 0, label: '否' },
                { value: 1, label: '是' },
              ]}
            />
          </Form.Item>
          <Form.Item name='sortOrder' label='展示顺序'>
            <Input type='number' min={0} />
          </Form.Item>
          <Form.Item name='description' label='字段说明'>
            <Input.TextArea rows={2} maxLength={1000} placeholder='可选，说明字段含义或规则' />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
