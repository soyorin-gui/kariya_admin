import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { App, Button, Form, Input, Popconfirm, Select, Space, Tag } from 'antd';
import { ArrowLeftOutlined, CloseOutlined, DeleteOutlined, EditOutlined, PlusOutlined, SaveOutlined, SearchOutlined } from '@ant-design/icons';
import { createEsField, createHbaseField, deleteEsField, deleteHbaseField, getEsFields, getEsIndex, getFieldImpact, getHbaseFields, getHbaseTable, updateEsField, updateHbaseField } from '../api';
import type { EsFieldRequest, EsIndex, HbaseFieldRequest, HbaseTable } from '../types';
import { Permission } from '../../../../permission/Permission';
import { getApiErrorMessage } from '../../../../utils/apiError';
import { InlineEditableTable, type InlineEditableColumn } from '../../components/InlineEditableTable';
import { ExpandableTextInput } from '../../components/FieldText';
import { EllipsisText } from '../../../../components/EllipsisText';
import './index.css';

type AssetType = 'HBASE' | 'ES';
type FieldFormValues = {
  columnFamily?: string;
  fieldName: string;
  dataType?: string;
  searchableFlag?: number;
  sortableFlag?: number;
  description?: string;
  sortOrder: number;
};
type FieldRow = FieldFormValues & { id: number };
const DRAFT_ID = -1;

export default function DataAssetFieldDesignPage() {
  const { message, modal } = App.useApp();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [form] = Form.useForm<FieldFormValues>();
  const assetType = params.get('assetType')?.toUpperCase() as AssetType;
  const assetId = Number(params.get('assetId'));
  const returnTab = assetType === 'ES' ? 'es' : 'hbase';
  const valid = (assetType === 'HBASE' || assetType === 'ES') && Number.isInteger(assetId) && assetId > 0;
  const [asset, setAsset] = useState<HbaseTable | EsIndex>();
  const [rows, setRows] = useState<FieldRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [editingId, setEditingId] = useState<number>();
  const editing = editingId !== undefined;
  const title = asset ? (assetType === 'HBASE' ? `${(asset as HbaseTable).marketName}:${(asset as HbaseTable).tableName}` : (asset as EsIndex).indexName) : '加载中';

  const load = async () => {
    if (!valid) return;
    setLoading(true);
    try {
      const [detail, fields] = assetType === 'HBASE' ? await Promise.all([getHbaseTable(assetId), getHbaseFields(assetId)]) : await Promise.all([getEsIndex(assetId), getEsFields(assetId)]);
      setAsset(detail);
      setRows(fields.map((field) => ({ ...field })));
    } catch (error) {
      message.error(getApiErrorMessage(error, '无法加载字段设计'));
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    void load();
  }, [assetId, assetType]);

  const tableRows = useMemo(
    () =>
      editingId === DRAFT_ID
        ? [
            ...rows,
            assetType === 'HBASE'
              ? { id: DRAFT_ID, columnFamily: 'info', fieldName: '', sortOrder: 0 }
              : { id: DRAFT_ID, fieldName: '', dataType: 'keyword', searchableFlag: 0, sortableFlag: 0, sortOrder: 0 },
          ]
        : rows,
    [assetType, editingId, rows],
  );
  const filteredRows = useMemo(() => {
    const value = keyword.trim().toLowerCase();
    return value ? tableRows.filter((row) => row.id === editingId || row.fieldName.toLowerCase().includes(value)) : tableRows;
  }, [editingId, keyword, tableRows]);
  const startCreate = () => {
    const empty = assetType === 'HBASE' ? { columnFamily: 'info', fieldName: '', sortOrder: 0 } : { fieldName: '', dataType: 'keyword', searchableFlag: 0, sortableFlag: 0, sortOrder: 0 };
    form.setFieldsValue(empty);
    setEditingId(DRAFT_ID);
  };
  const startEdit = (row: FieldRow) => {
    form.setFieldsValue(row);
    setEditingId(row.id);
  };
  const cancel = () => {
    setEditingId(undefined);
    form.resetFields();
  };
  const save = async () => {
    try {
      const values = await form.validateFields();
      let result;
      if (assetType === 'HBASE') {
        const payload: HbaseFieldRequest = {
          columnFamily: values.columnFamily!,
          fieldName: values.fieldName,
          dataType: values.dataType,
          description: values.description,
          sortOrder: Number(values.sortOrder ?? 0),
        };
        result = editingId === DRAFT_ID ? await createHbaseField(assetId, payload) : await updateHbaseField(assetId, editingId!, payload);
      } else {
        const payload: EsFieldRequest = {
          fieldName: values.fieldName,
          dataType: values.dataType!,
          searchableFlag: values.searchableFlag!,
          sortableFlag: values.sortableFlag!,
          description: values.description,
          sortOrder: Number(values.sortOrder ?? 0),
        };
        result = editingId === DRAFT_ID ? await createEsField(assetId, payload) : await updateEsField(assetId, editingId!, payload);
      }
      message.success(result.message || '保存成功');
      cancel();
      void load();
    } catch (error) {
      if (!(error as { errorFields?: unknown }).errorFields) message.error(getApiErrorMessage(error, '保存失败'));
    }
  };
  const remove = async (row: FieldRow) => {
    try {
      if (assetType === 'HBASE') await deleteHbaseField(assetId, row.id);
      else await deleteEsField(assetId, row.id);
      message.success('删除字段成功');
      void load();
    } catch (error) {
      message.error(getApiErrorMessage(error));
    }
  };
  const operation: InlineEditableColumn<FieldRow, FieldFormValues> = {
    title: '操作',
    fixed: 'right',
    width: 170,
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
        <Space>
          <a onClick={() => void getFieldImpact(assetType === 'HBASE' ? 'HBASE_FIELD' : 'ES_FIELD', row.id).then(items => modal.info({ width: 720, title: `字段影响分析 · ${row.fieldName}`, content: items.length ? <ul>{items.map(item => <li key={`${item.transactionId}-${item.targetFieldId}`}>{item.transactionCode} · {item.transactionName} → {item.messageSide === 'REQUEST' ? '请求' : '响应'}.{item.fieldNameEn}（{item.relationType}）</li>)}</ul> : '未登记下游交易字段依赖。' }))}>
            影响分析
          </a>
          <Permission code='intranet:dataasset:update'>
            <a onClick={() => startEdit(row)}>
              <EditOutlined /> 编辑
            </a>
          </Permission>
          <Permission code='intranet:dataasset:delete'>
            {!(assetType === 'ES' && row.fieldName === 'id') && (
              <Popconfirm title='确定删除此字段？' onConfirm={() => void remove(row)}>
                <a className='danger'>
                  <DeleteOutlined /> 删除
                </a>
              </Popconfirm>
            )}
          </Permission>
        </Space>
      ),
  };
  const common: Array<InlineEditableColumn<FieldRow, FieldFormValues>> = [
    {
      title: '字段名',
      dataIndex: 'fieldName',
      field: 'fieldName',
      rules: [{ required: true, message: '请输入字段名' }],
      width: 210,
      view: (value) => <code>{String(value)}</code>,
      editor: (row) => <Input size='small' maxLength={128} placeholder='字段名' disabled={assetType === 'ES' && row.fieldName === 'id'} />,
    },
    {
      title: '数据类型',
      dataIndex: 'dataType',
      field: 'dataType',
      rules: assetType === 'ES' ? [{ required: true, message: '请选择数据类型' }] : undefined,
      width: 160,
      view: (value) => String(value || '-'),
      editor: () =>
        assetType === 'ES' ? (
          <Select size='small' options={['keyword', 'numeric', 'date', 'text'].map((value) => ({ value, label: value }))} />
        ) : (
          <Input size='small' maxLength={128} placeholder='例如 varchar(32)' />
        ),
    },
  ];
  const columns: Array<InlineEditableColumn<FieldRow, FieldFormValues>> =
    assetType === 'HBASE'
      ? [
          { title: '列族', dataIndex: 'columnFamily', field: 'columnFamily', rules: [{ required: true, message: '请输入列族' }], width: 140, editor: () => <Input size='small' maxLength={64} /> },
          ...common,
          { title: '顺序', dataIndex: 'sortOrder', field: 'sortOrder', rules: [{ required: true, message: '请输入顺序' }], width: 90, editor: () => <Input size='small' type='number' /> },
          {
            title: '说明',
            dataIndex: 'description',
            field: 'description',
            width: 360,
            view: (value) => <EllipsisText value={value as string | undefined} />,
            editor: () => <ExpandableTextInput maxLength={1000} placeholder='字段说明' />,
          },
          operation,
        ]
      : [
          ...common,
          {
            title: '可搜索',
            dataIndex: 'searchableFlag',
            field: 'searchableFlag',
            rules: [{ required: true, message: '请选择是否可搜索' }],
            width: 110,
            view: (value) => <Tag color={value ? 'success' : 'default'}>{value ? '是' : '否'}</Tag>,
            editor: () => (
              <Select
                size='small'
                options={[
                  { value: 1, label: '是' },
                  { value: 0, label: '否' },
                ]}
              />
            ),
          },
          {
            title: '可排序',
            dataIndex: 'sortableFlag',
            field: 'sortableFlag',
            rules: [{ required: true, message: '请选择是否可排序' }],
            width: 110,
            view: (value) => <Tag color={value ? 'success' : 'default'}>{value ? '是' : '否'}</Tag>,
            editor: () => (
              <Select
                size='small'
                options={[
                  { value: 1, label: '是' },
                  { value: 0, label: '否' },
                ]}
              />
            ),
          },
          { title: '顺序', dataIndex: 'sortOrder', field: 'sortOrder', rules: [{ required: true, message: '请输入顺序' }], width: 90, editor: () => <Input size='small' type='number' /> },
          {
            title: '说明',
            dataIndex: 'description',
            field: 'description',
            width: 360,
            view: (value) => <EllipsisText value={value as string | undefined} />,
            editor: () => <ExpandableTextInput maxLength={1000} placeholder='字段说明' />,
          },
          operation,
        ];

  if (!valid)
    return (
      <div className='dataasset-field-page'>
        <p>缺少有效的数据资产类型或编号。</p>
      </div>
    );
  return (
    <div className='page-fill dataasset-field-page'>
      <div className='page-head'>
        <div>
          <div className='page-kicker'>INTRANET DATA ASSETS</div>
          <h1 className='page-title'>
            {assetType === 'HBASE' ? 'HBase 字段设计' : 'ES 字段设计'} · {title}
          </h1>
        </div>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(`/intranet/dataasset?tab=${returnTab}`)}>
          返回数据资产
        </Button>
      </div>
      <div className='table-card'>
        <div className='dataasset-field-toolbar'>
          <Input value={keyword} onChange={(event) => setKeyword(event.target.value)} prefix={<SearchOutlined />} allowClear disabled={editing} placeholder='搜索字段名' />
          <Permission code='intranet:dataasset:add'>
            <Button type='primary' icon={<PlusOutlined />} disabled={editing} onClick={startCreate}>
              新增字段
            </Button>
          </Permission>
        </div>
        <InlineEditableTable<FieldRow, FieldFormValues>
          form={form}
          editingKey={editingId}
          getRowKey={(row) => row.id}
          columns={columns}
          dataSource={filteredRows}
          loading={{ spinning: loading, tip: '正在加载字段数据…' }}
          pagination={false}
          scroll={{ x: assetType === 'HBASE' ? 1100 : 1250 }}
          locale={{ emptyText: '暂无字段，点击“新增字段”开始维护。' }}
        />
      </div>
    </div>
  );
}
