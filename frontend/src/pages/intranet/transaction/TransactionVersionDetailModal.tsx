import { useEffect, useState } from 'react';
import { App, Descriptions, Modal, Spin, Table, Tag, Typography } from 'antd';
import { getTransactionVersionDetail } from './api';
import type { TransactionVersionDetail } from './types';
import { getApiErrorMessage } from '../../../utils/apiError';

const text = (value?: string | number) => (value === undefined || value === null || value === '' ? '-' : String(value));

export function TransactionVersionDetailModal({ id, onClose }: { id?: number; onClose: () => void }) {
  const { message } = App.useApp();
  const [detail, setDetail] = useState<TransactionVersionDetail>();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!id) {
      setDetail(undefined);
      return;
    }
    let active = true;
    setLoading(true);
    void getTransactionVersionDetail(id)
      .then((value) => {
        if (active) setDetail(value);
      })
      .catch((error) => {
        message.error(getApiErrorMessage(error, '无法加载版本快照'));
        onClose();
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [id, message, onClose]);

  const version = detail?.version;
  return (
    <Modal width={920} open={Boolean(id)} title={version ? `交易版本快照 · V${version.versionNo}` : '交易版本快照'} footer={null} onCancel={onClose}>
      {loading || !detail || !version ? (
        <div className='dialog-loading'>
          <Spin />
        </div>
      ) : (
        <>
          <Descriptions bordered column={2} size='small'>
            <Descriptions.Item label='交易编码'>{version.transactionCode}</Descriptions.Item>
            <Descriptions.Item label='交易名称'>{version.transactionName}</Descriptions.Item>
            <Descriptions.Item label='来源需求'>#{version.requirementId}</Descriptions.Item>
            <Descriptions.Item label='审批实例'>#{version.approvalInstanceId}</Descriptions.Item>
            <Descriptions.Item label='ESF服务'>{version.esfServiceName}</Descriptions.Item>
            <Descriptions.Item label='ESF操作'>
              {version.esfServiceOperationName}（{version.esfServiceOperationId}）
            </Descriptions.Item>
            <Descriptions.Item label='ESF地址' span={2}>
              {version.esfServiceAddress}
            </Descriptions.Item>
            <Descriptions.Item label='业务对接人'>{text(version.businessContact)}</Descriptions.Item>
            <Descriptions.Item label='数据时效性'>{text(version.dataTimeliness)}</Descriptions.Item>
            <Descriptions.Item label='查询范围'>{text(version.queryScope)}</Descriptions.Item>
            <Descriptions.Item label='打印文件模式'>{version.printFileMode}</Descriptions.Item>
            <Descriptions.Item label='排序规则' span={2}>
              {text(version.sortRule)}
            </Descriptions.Item>
            <Descriptions.Item label='数据校验范围' span={2}>
              {text(version.dataValidationScope)}
            </Descriptions.Item>
            <Descriptions.Item label='交易说明' span={2}>
              {text(version.description)}
            </Descriptions.Item>
          </Descriptions>
          <Typography.Title level={5} style={{ marginTop: 20 }}>
            报文字段快照
          </Typography.Title>
          <Table
            rowKey='id'
            size='small'
            pagination={false}
            dataSource={detail.messageFields}
            locale={{ emptyText: '无报文字段' }}
            columns={[
              { title: '方向', dataIndex: 'messageSide', width: 90, render: (value) => (value === 'REQUEST' ? '请求' : '响应') },
              { title: '中文名', dataIndex: 'fieldNameCn' },
              { title: '英文名', dataIndex: 'fieldNameEn' },
              { title: '节点类型', dataIndex: 'nodeType', width: 100 },
              { title: '数据类型', dataIndex: 'dataType', width: 100, render: text },
              { title: '必填', dataIndex: 'requiredFlag', width: 70, render: (value) => (value === 1 ? '是' : '否') },
            ]}
          />
          <Typography.Title level={5} style={{ marginTop: 20 }}>
            数据资源快照
          </Typography.Title>
          <Descriptions bordered column={1} size='small'>
            <Descriptions.Item label='HBase表'>{detail.hbaseTableIds.length ? detail.hbaseTableIds.map((value) => <Tag key={value}>#{value}</Tag>) : '-'}</Descriptions.Item>
            <Descriptions.Item label='ES索引'>{detail.esIndexIds.length ? detail.esIndexIds.map((value) => <Tag key={value}>#{value}</Tag>) : '-'}</Descriptions.Item>
          </Descriptions>
        </>
      )}
    </Modal>
  );
}
