import { useEffect, useMemo, useState, type Key } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { App, Button, Form, Input, Popconfirm, Select, Space, Tabs, Tag } from 'antd';
import { ArrowLeftOutlined, DeleteOutlined, EditOutlined, PlusOutlined, SaveOutlined, CloseOutlined, SearchOutlined } from '@ant-design/icons';
import { createTransactionMessageField, deleteTransactionMessageField, getTransaction, getTransactionMessageFields, updateTransactionMessageField } from '../api';
import type { MessageSide, Transaction, TransactionMessageField, TransactionMessageFieldRequest } from '../types';
import { Permission } from '../../../../permission/Permission';
import { getApiErrorMessage } from '../../../../utils/apiError';
import { InlineEditableTable, type InlineEditableColumn } from '../../components/InlineEditableTable';
import { ExpandableTextInput } from '../../components/FieldText';
import { EllipsisText } from '../../../../components/EllipsisText';
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
/** 默认全展开：收集所有还有子节点的节点 id（与菜单管理页的默认展开语义一致）。 */
function collectParentIds(nodes: TreeField[]): number[] {
  return nodes.flatMap((node) => (node.children?.length ? [node.id, ...collectParentIds(node.children)] : []));
}
function filterTree(nodes: TreeField[], keyword: string, editingId?: number): TreeField[] {
  const normalized = keyword.trim().toLowerCase();
  const prune = (items: TreeField[]): TreeField[] =>
    items.flatMap((item) => {
      const children = prune(item.children ?? []);
      const matchesKeyword = !normalized || [item.fieldNameCn, item.fieldNameEn].some((value) => value.toLowerCase().includes(normalized));
      return item.id === editingId || matchesKeyword || children.length ? [{ ...item, children: children.length ? children : undefined }] : [];
    });
  return prune(nodes);
}
function countNodes(nodes: TreeField[]): number {
  return nodes.reduce((total, node) => total + 1 + countNodes(node.children ?? []), 0);
}
export default function TransactionFieldDesignPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const transactionId = Number(params.get('transactionId'));
  const [transaction, setTransaction] = useState<Transaction>();
  const [side, setSide] = useState<MessageSide>('REQUEST');
  const [rows, setRows] = useState<TransactionMessageField[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [editingId, setEditingId] = useState<number>();
  const [draftParentId, setDraftParentId] = useState<number>();
  const [expandedRowKeys, setExpandedRowKeys] = useState<readonly Key[]>();
  const [form] = Form.useForm<TransactionMessageFieldRequest>();
  const load = async () => {
    if (!Number.isInteger(transactionId) || transactionId <= 0) return;
    setLoading(true);
    try {
      const [detail, fields] = await Promise.all([getTransaction(transactionId), getTransactionMessageFields(transactionId, side)]);
      setTransaction(detail);
      setRows(fields);
    } catch (error) {
      message.error(getApiErrorMessage(error, '无法加载交易字段设计'));
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    void load();
  }, [side, transactionId]);
  const editing = editingId !== undefined;
  const tableRows = useMemo(() => {
    if (editingId !== DRAFT_ID) return rows;
    return [...rows, { id: DRAFT_ID, transactionId, messageSide: side, ...empty(draftParentId ?? 0) }];
  }, [draftParentId, editingId, rows, side, transactionId]);
  const tree = useMemo(() => toTree(tableRows), [tableRows]);
  const defaultExpanded = useMemo(() => collectParentIds(tree), [tree]);
  const filteredTree = useMemo(() => filterTree(tree, keyword, editingId), [editingId, keyword, tree]);
  const filtering = keyword.trim().length > 0;
  const filteredExpanded = useMemo(() => collectParentIds(filteredTree), [filteredTree]);
  const visibleCount = useMemo(() => countNodes(filteredTree), [filteredTree]);
  const startCreate = (parentId = 0) => {
    setDraftParentId(parentId);
    setEditingId(DRAFT_ID);
    // 草稿行会挂在 parentId 下面。父节点若正收起着，编辑器就藏在里面，用户点了"子字段"看不到任何反应。
    // 所以在开始新增子字段时把该父节点并入展开集合；用户没手动点过箭头时，先把默认全展开物化出来。
    if (parentId !== 0) {
      setExpandedRowKeys((keys) => {
        const current = keys ?? defaultExpanded;
        return current.includes(parentId) ? current : [...current, parentId];
      });
    }
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
  const fieldRules = (name: keyof TransactionMessageFieldRequest) => {
    if (name === 'fieldNameCn') return [{ required: true, message: '请输入字段中文名' }];
    if (name === 'fieldNameEn') return [{ required: true, message: '请输入字段英文名' }];
    return [];
  };
  const columns: Array<InlineEditableColumn<TreeField, TransactionMessageFieldRequest>> = [
    { title: '字段中文名', dataIndex: 'fieldNameCn', field: 'fieldNameCn', rules: fieldRules('fieldNameCn'), width: 170, editor: () => <Input size='small' maxLength={64} placeholder='字段中文名' /> },
    { title: '字段英文名', dataIndex: 'fieldNameEn', field: 'fieldNameEn', rules: fieldRules('fieldNameEn'), width: 180, view: (v) => <code>{String(v)}</code>, editor: () => <Input size='small' maxLength={64} placeholder='字段英文名' /> },
    {
      title: '节点类型',
      dataIndex: 'nodeType',
      width: 110,
      field: 'nodeType', view: (v) => <Tag>{String(v)}</Tag>, editor: () => <Select size='small' options={['FIELD', 'OBJECT', 'ARRAY'].map((value) => ({ value, label: value }))} />,
    },
    { title: '数据类型', dataIndex: 'dataType', field: 'dataType', width: 120, view: (v) => String(v || '-'), editor: () => <Input size='small' maxLength={64} placeholder='String' /> },
    { title: '长度/精度', dataIndex: 'dataLength', field: 'dataLength', width: 105, view: (v) => String(v || '-'), editor: () => <Input size='small' maxLength={64} placeholder='32 / 18,2' /> },
    {
      title: '必填',
      dataIndex: 'requiredFlag',
      width: 85,
      field: 'requiredFlag', view: (v) => v === 1 ? '是' : '否', editor: () => <Select
            size='small'
            options={[
              { value: 0, label: '否' },
              { value: 1, label: '是' },
            ]}
          />,
    },
    { title: '顺序', dataIndex: 'sortOrder', field: 'sortOrder', width: 75, editor: () => <Input size='small' type='number' min={0} /> },
    {
      title: '说明',
      dataIndex: 'description',
      width: 300,
      field: 'description', view: (v) => <EllipsisText value={v as string | undefined} />, editor: () => <ExpandableTextInput maxLength={1000} placeholder='字段说明' />,
    },
    {
      title: '操作',
      fixed: 'right',
      width: 210,
      view: (_, row) =>
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
    <div className='page-fill transaction-field-page'>
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
            setLoading(true);
            // 换方向等于换了一棵全新的树，清掉旧展开集合，让新方向回到"默认全展开"。
            setExpandedRowKeys(undefined);
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
        <div className='transaction-field-toolbar'>
          <Space wrap>
            <Input
              className='transaction-field-search'
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              prefix={<SearchOutlined />}
              allowClear
              disabled={editing}
              placeholder='搜索字段中文名或英文名'
            />
            {filtering && <span className='transaction-field-filter-count'>显示 {visibleCount} / {tableRows.length} 个节点</span>}
          </Space>
        </div>
          <InlineEditableTable<TreeField, TransactionMessageFieldRequest>
            form={form}
            editingKey={editingId}
            getRowKey={(row) => row.id}
            columns={columns}
            dataSource={filteredTree}
            loading={{ spinning: loading, tip: '正在加载字段数据…' }}
            pagination={false}
            scroll={{ x: 1400 }}
            locale={{ emptyText: '暂无字段，点击“新增根字段”开始维护。' }}
            expandable={{
              expandedRowKeys: filtering ? filteredExpanded : expandedRowKeys ?? defaultExpanded,
              onExpandedRowsChange: filtering ? undefined : setExpandedRowKeys,
              indentSize: 18,
              // 与菜单管理、部门管理、侧边栏子菜单同一个 chevron：样式类来自基线 system/shared.css，
              // 本页的 index.css 已经 @import 了它，因此不需要新增任何样式。
              expandIcon: ({ expanded, onExpand, record }) =>
                record.children?.length ? (
                  <button type='button' className={`tree-expand-arrow${expanded ? ' is-expanded' : ''}`} aria-label={expanded ? '收起子字段' : '展开子字段'} onClick={(event) => onExpand(record, event)} />
                ) : null,
            }}
          />
      </div>
    </div>
  );
}
