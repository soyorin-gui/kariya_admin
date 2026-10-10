import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { App, Button, Form, Input, Popconfirm, Select, Space, Table, Tabs, Tag } from 'antd';
import { ArrowLeftOutlined, DeleteOutlined, EditOutlined, PlusOutlined, SaveOutlined, CloseOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { createTransactionMessageField, deleteTransactionMessageField, getTransaction, getTransactionMessageFields, updateTransactionMessageField } from '../api';
import type { MessageSide, Transaction, TransactionMessageField, TransactionMessageFieldRequest } from '../types';
import { Permission } from '../../../../permission/Permission';
import { getApiErrorMessage } from '../../../../utils/apiError';
import '../index.css';
import './index.css';

type TreeField = TransactionMessageField & { children?: TreeField[] };
const DRAFT_ID = -1;
const empty = (parentId = 0): TransactionMessageFieldRequest => ({ parentId, nodeType: 'FIELD', fieldNameCn: '', fieldNameEn: '', requiredFlag: 0, sortOrder: 0 });
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

export default function TransactionFieldDesignPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const transactionId = Number(params.get('transactionId'));
  const [transaction, setTransaction] = useState<Transaction>();
  const [side, setSide] = useState<MessageSide>('REQUEST');
  const [rows, setRows] = useState<TransactionMessageField[]>([]);
  const [editingId, setEditingId] = useState<number>();
  const [draftParentId, setDraftParentId] = useState<number>();
  const [form] = Form.useForm<TransactionMessageFieldRequest>();
  const load = async () => {
    if (!Number.isInteger(transactionId) || transactionId <= 0) return;
    try {
      const [detail, fields] = await Promise.all([getTransaction(transactionId), getTransactionMessageFields(transactionId, side)]);
      setTransaction(detail);
      setRows(fields);
    } catch (error) {
      message.error(getApiErrorMessage(error, '无法加载交易字段设计'));
    }
  };
  useEffect(() => {
    void load();
  }, [side, transactionId]);
  const editing = editingId !== undefined;
  const startCreate = (parentId = 0) => {
    setDraftParentId(parentId);
    setEditingId(DRAFT_ID);
    form.setFieldsValue(empty(parentId));
  };
  const startEdit = (row: TransactionMessageField) => {
    setDraftParentId(undefined);
    setEditingId(row.id);
    form.setFieldsValue(row);
  };
  const cancel = () => {
    setEditingId(undefined);
    setDraftParentId(undefined);
    form.resetFields();
  };
  const save = async () => {
    try {
      const values = await form.validateFields();
      const original = rows.find((row) => row.id === editingId);
      const payload: TransactionMessageFieldRequest = { ...values, parentId: editingId === DRAFT_ID ? draftParentId ?? 0 : original!.parentId, sortOrder: Number(values.sortOrder ?? 0) };
      const result = editingId === DRAFT_ID ? await createTransactionMessageField(transactionId, side, payload) : await updateTransactionMessageField(transactionId, side, editingId!, payload);
      message.success(result.message || '保存成功');
      cancel();
      void load();
    } catch (error) {
      if (!(error as { errorFields?: unknown }).errorFields) message.error(getApiErrorMessage(error, '保存失败'));
    }
  };
  const tableRows = useMemo(() => {
    if (editingId !== DRAFT_ID) return rows;
    return [...rows, { id: DRAFT_ID, transactionId, messageSide: side, ...empty(draftParentId ?? 0) }];
  }, [draftParentId, editingId, rows, side, transactionId]);
  const tree = useMemo(() => toTree(tableRows), [tableRows]);
  const fieldRules = (name: keyof TransactionMessageFieldRequest) => {
    if (name === 'fieldNameCn') return [{ required: true, message: '请输入字段中文名' }];
    if (name === 'fieldNameEn') return [{ required: true, message: '请输入字段英文名' }];
    return [];
  };
  const cell = (row: TreeField, name: keyof TransactionMessageFieldRequest, normal: ReactNode, editor: ReactNode) =>
    row.id === editingId ? (
      <Form.Item name={name} style={{ margin: 0 }} rules={fieldRules(name)}>
        {editor}
      </Form.Item>
    ) : (
      normal
    );
  const columns: ColumnsType<TreeField> = [
    { title: '字段中文名', dataIndex: 'fieldNameCn', width: 170, render: (v, row) => cell(row, 'fieldNameCn', v, <Input size='small' maxLength={64} placeholder='字段中文名' />) },
    { title: '字段英文名', dataIndex: 'fieldNameEn', width: 180, render: (v, row) => cell(row, 'fieldNameEn', <code>{v}</code>, <Input size='small' maxLength={64} placeholder='字段英文名' />) },
    {
      title: '节点类型',
      dataIndex: 'nodeType',
      width: 110,
      render: (v, row) => cell(row, 'nodeType', <Tag>{v}</Tag>, <Select size='small' options={['FIELD', 'OBJECT', 'ARRAY'].map((value) => ({ value, label: value }))} />),
    },
    { title: '数据类型', dataIndex: 'dataType', width: 120, render: (v, row) => cell(row, 'dataType', v || '-', <Input size='small' maxLength={64} placeholder='String' />) },
    { title: '长度/精度', dataIndex: 'dataLength', width: 105, render: (v, row) => cell(row, 'dataLength', v || '-', <Input size='small' maxLength={64} placeholder='32 / 18,2' />) },
    {
      title: '必填',
      dataIndex: 'requiredFlag',
      width: 85,
      render: (v, row) =>
        cell(
          row,
          'requiredFlag',
          v === 1 ? '是' : '否',
          <Select
            size='small'
            options={[
              { value: 0, label: '否' },
              { value: 1, label: '是' },
            ]}
          />,
        ),
    },
    { title: '顺序', dataIndex: 'sortOrder', width: 75, render: (v, row) => cell(row, 'sortOrder', v, <Input size='small' type='number' min={0} />) },
    {
      title: '说明',
      dataIndex: 'description',
      width: 300,
      render: (v, row) => cell(row, 'description', v || '-', <Input.TextArea autoSize={{ minRows: 1, maxRows: 4 }} maxLength={1000} placeholder='字段说明' />),
    },
    {
      title: '操作',
      fixed: 'right',
      width: 210,
      render: (_, row) =>
        row.id === editingId ? (
          <Space>
            <a onClick={() => void save()}>
              <SaveOutlined /> 保存
            </a>
            <a onClick={cancel}>
              <CloseOutlined /> 取消
            </a>
          </Space>
        ) : (
          <Permission code='intranet:transaction:update'>
            <Space>
              <a onClick={() => startCreate(row.id)}>
                <PlusOutlined /> 子字段
              </a>
              <a onClick={() => startEdit(row)}>
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
  if (!Number.isInteger(transactionId) || transactionId <= 0)
    return (
      <div className='transaction-field-page'>
        <p>缺少有效交易编号。</p>
      </div>
    );
  return (
    <div className='transaction-field-page'>
      <div className='page-head'>
        <div>
          <div className='page-kicker'>INTRANET ASSETS</div>
          <h1 className='page-title'>字段设计 · {transaction?.transactionName || '加载中'}</h1>
          <div className='transaction-field-subtitle'>{transaction?.transactionCode}</div>
        </div>
        <Button icon={<ArrowLeftOutlined />} onClick={() => void navigate('/intranet/transaction')}>
          返回交易列表
        </Button>
      </div>
      <div className='table-card'>
        <Tabs
          activeKey={side}
          onChange={(key) => {
            cancel();
            setSide(key as MessageSide);
          }}
          items={[
            { key: 'REQUEST', label: '请求字段' },
            { key: 'RESPONSE', label: '响应字段' },
          ]}
          tabBarExtraContent={
            <Permission code='intranet:transaction:update'>
              <Button type='primary' icon={<PlusOutlined />} disabled={editing} onClick={() => startCreate()}>
                新增根字段
              </Button>
            </Permission>
          }
        />
        <Form form={form} component={false}>
          <Table<TreeField> rowKey='id' columns={columns} dataSource={tree} pagination={false} scroll={{ x: 1400 }} locale={{ emptyText: '暂无字段，点击“新增根字段”开始维护。' }} />
        </Form>
      </div>
    </div>
  );
}
