import { useEffect, useState } from 'react';
import { Alert, App, Descriptions, Empty, Form, Modal, Select, Space, Spin, Tag, Typography } from 'antd';
import { getEsOptions, getHbaseOptions } from '../dataasset/api';
import type { EsIndex, HbaseTable } from '../dataasset/types';
import { getTransactionDataResources, updateTransactionDataResources } from './api';
import type { TransactionDataResources } from './types';
import { getApiErrorMessage } from '../../../utils/apiError';

interface DataResourceDialogProps {
  transaction?: { id: number; status: number; transactionName: string };
  editable: boolean;
  onClose: () => void;
}

export function DataResourceDialog({ transaction, editable, onClose }: DataResourceDialogProps) {
  const [form] = Form.useForm<TransactionDataResources>();
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [hbase, setHbase] = useState<HbaseTable[]>([]);
  const [es, setEs] = useState<EsIndex[]>([]);
  const [resources, setResources] = useState<TransactionDataResources>({ hbaseTableIds: [], esIndexIds: [] });
  useEffect(() => {
    if (!transaction) return;
    let active = true;
    setLoading(true);
    setResources({ hbaseTableIds: [], esIndexIds: [] });
    if (editable) form.resetFields();
    void Promise.all([getTransactionDataResources(transaction.id), getHbaseOptions(), getEsOptions()])
      .then(([resources, hbaseOptions, esOptions]) => {
        if (!active) return;
        if (editable) form.setFieldsValue(resources);
        setResources(resources);
        setHbase(hbaseOptions);
        setEs(esOptions);
      })
      .catch((e) => {
        message.error(getApiErrorMessage(e, '无法加载数据资源'));
        onClose();
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [editable, form, message, onClose, transaction]);
  const save = async () => {
    if (!transaction || !editable) return;
    try {
      setSaving(true);
      const result = await updateTransactionDataResources(transaction.id, await form.validateFields());
      message.success(result.message);
      onClose();
    } catch (e) {
      if (!(e as { errorFields?: unknown }).errorFields) message.error(getApiErrorMessage(e));
    } finally {
      setSaving(false);
    }
  };
  const selectedHbase = hbase.filter((item) => resources.hbaseTableIds?.includes(item.id));
  const selectedEs = es.filter((item) => resources.esIndexIds?.includes(item.id));
  return (
    <Modal
      className='system-dialog'
      width={720}
      title={`${editable ? '配置' : '查看'}交易数据资源${transaction ? ` · ${transaction.transactionName}` : ''}`}
      open={!!transaction}
      onCancel={onClose}
      onOk={editable ? () => void save() : onClose}
      okText={editable ? '保存' : '关闭'}
      cancelButtonProps={editable ? undefined : { style: { display: 'none' } }}
      confirmLoading={saving}
      destroyOnHidden
      forceRender
    >
      {loading ? (
        <div className='dialog-loading'>
          <Spin />
        </div>
      ) : editable ? (
        <Form form={form} layout='vertical' initialValues={{ hbaseTableIds: [], esIndexIds: [] }}>
          <Alert type='info' showIcon message='这里记录交易涉及的资源集合，不表达查询分支、先后顺序或多表整合逻辑。' />
          <Form.Item name='hbaseTableIds' label='直接查询的 HBase 表'>
            <Select
              mode='multiple'
              allowClear
              showSearch
              optionFilterProp='label'
              placeholder='可选；入参可直接定位 RowKey 时配置'
              options={hbase.map((x) => ({ value: x.id, label: `${x.marketName}:${x.tableName}` }))}
            />
          </Form.Item>
          <Form.Item name='esIndexIds' label='先查 ES 的索引'>
            <Select
              mode='multiple'
              allowClear
              showSearch
              optionFilterProp='label'
              placeholder='可选；所选索引必须包含固定 id 字段'
              options={es.map((x) => ({ value: x.id, label: `${x.indexName}（${x.indexPattern}） → ${x.hbaseMarketName}:${x.hbaseTableName}` }))}
            />
          </Form.Item>
        </Form>
      ) : (
        <Space direction='vertical' size='large' style={{ width: '100%' }}>
          <Alert
            type='info'
            showIcon
            message={transaction?.status === 2 || transaction?.status === 3 ? '当前交易的数据资源配置仅供查看；如需调整，请通过交易变更流程进入开发状态。' : '你没有维护交易数据资源的权限，当前仅供查看。'}
          />
          <section>
            <Typography.Title level={5}>先查 ES 的索引 <Tag>{selectedEs.length}</Tag></Typography.Title>
            {selectedEs.length ? selectedEs.map((item) => (
              <Descriptions key={item.id} bordered size='small' column={1} className='transaction-resource-descriptions'>
                <Descriptions.Item label='索引名称'>{item.indexName}</Descriptions.Item>
                <Descriptions.Item label='索引模式'>{item.indexPattern}</Descriptions.Item>
                <Descriptions.Item label='关联 HBase'>{item.hbaseMarketName}:{item.hbaseTableName}</Descriptions.Item>
                <Descriptions.Item label='分区规则'>{item.partitionRule || '-'}</Descriptions.Item>
                <Descriptions.Item label='说明'>{item.description || '-'}</Descriptions.Item>
              </Descriptions>
            )) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description='未配置 ES 索引' />}
          </section>
          <section>
            <Typography.Title level={5}>直接查询的 HBase 表 <Tag>{selectedHbase.length}</Tag></Typography.Title>
            {selectedHbase.length ? selectedHbase.map((item) => (
              <Descriptions key={item.id} bordered size='small' column={1} className='transaction-resource-descriptions'>
                <Descriptions.Item label='表名'>{item.marketName}:{item.tableName}</Descriptions.Item>
                <Descriptions.Item label='RowKey 规则'>{item.rowkeyRule || '-'}</Descriptions.Item>
                <Descriptions.Item label='锁定键'>{item.lockKey || '-'}</Descriptions.Item>
                <Descriptions.Item label='数据量'>{item.dataVolume || '-'}</Descriptions.Item>
                <Descriptions.Item label='数据深度'>{item.dataDepth || '-'}</Descriptions.Item>
                <Descriptions.Item label='说明'>{item.description || '-'}</Descriptions.Item>
              </Descriptions>
            )) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description='未配置直接查询的 HBase 表' />}
          </section>
        </Space>
      )}
    </Modal>
  );
}
