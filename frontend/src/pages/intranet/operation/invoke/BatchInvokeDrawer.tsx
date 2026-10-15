import { useMemo, useState } from 'react';
import { Alert, App, Button, Drawer, Empty, Input, Progress, Space, Table, Tabs, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlayCircleOutlined } from '@ant-design/icons';
import { CodeViewer } from '../../../../components/CodeViewer';
import { getApiErrorMessage } from '../../../../utils/apiError';
import type { TransactionListItem } from '../../transaction/types';
import { invokeMockTransaction } from './api';
import type { MockTransactionInvocationResult } from './types';

const statusColor: Record<string, string> = { Y: 'success', N: 'warning', E: 'error' };

function demoDelay(total: number): Promise<void> {
  const milliseconds = Math.max(30, Math.min(220, Math.floor(3000 / Math.max(total, 1))));
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
}

export function BatchInvokeDrawer({
  open,
  transactions,
  soapVersion,
  onClose,
}: {
  open: boolean;
  transactions: TransactionListItem[];
  soapVersion: '1.1' | '1.2';
  onClose: () => void;
}) {
  const { message } = App.useApp();
  const [targetAddress, setTargetAddress] = useState('');
  const [running, setRunning] = useState(false);
  const [completed, setCompleted] = useState(0);
  const [results, setResults] = useState<MockTransactionInvocationResult[]>([]);
  const percent = transactions.length ? Math.round((completed / transactions.length) * 100) : 0;
  const successCount = useMemo(() => results.filter((item) => item.success).length, [results]);

  const columns: ColumnsType<MockTransactionInvocationResult> = [
    { title: '交易码', dataIndex: 'transactionCode', width: 150 },
    { title: '交易名称', dataIndex: 'transactionName', width: 220, ellipsis: true },
    { title: '状态', dataIndex: 'responseStatus', width: 80, align: 'center', render: (value) => <Tag color={statusColor[value]}>{value}</Tag> },
    { title: '响应码', dataIndex: 'responseCode', width: 150 },
    { title: '耗时', dataIndex: 'elapsedTime', width: 100, align: 'right', render: (value) => `${value} ms` },
    { title: '全局流水号', dataIndex: 'globalFlowId', width: 260 },
    { title: '结果', dataIndex: 'responseMessage', ellipsis: true },
  ];

  const start = async () => {
    const address = targetAddress.trim();
    if (!address) {
      message.warning('请输入目标容器 IP 或地址');
      return;
    }
    if (!transactions.length) {
      message.warning('当前没有可调用的交易资产');
      return;
    }
    setRunning(true);
    setCompleted(0);
    setResults([]);
    for (let index = 0; index < transactions.length; index += 1) {
      const transaction = transactions[index];
      try {
        const result = await invokeMockTransaction({ transactionId: transaction.id, targetAddress: address, soapVersion });
        setResults((current) => [...current, result]);
      } catch (error) {
        setResults((current) => [...current, {
          transactionId: transaction.id,
          transactionCode: transaction.transactionCode,
          transactionName: transaction.transactionName,
          targetAddress: address,
          success: false,
          responseStatus: 'E',
          responseCode: 'MOCK_REQUEST_FAILED',
          responseMessage: getApiErrorMessage(error, '模拟调用失败'),
          globalFlowId: '-',
          elapsedTime: 0,
          requestXml: '',
          responseXml: '',
        }]);
      }
      setCompleted(index + 1);
      if (index < transactions.length - 1) await demoDelay(transactions.length);
    }
    setRunning(false);
  };

  return (
    <Drawer
      title='一键调用全部交易'
      width='88vw'
      open={open}
      closable={!running}
      maskClosable={!running}
      onClose={onClose}
      destroyOnClose={false}
    >
      <Space direction='vertical' size='large' style={{ width: '100%' }}>
        <Alert
          type='info'
          showIcon
          message='演示模式'
          description='前端会逐笔调用本系统的模拟接口并展示进度；模拟接口不会访问下面填写的容器地址。'
        />
        <Space.Compact style={{ width: '100%' }}>
          <Input
            value={targetAddress}
            disabled={running}
            placeholder='请输入容器 IP 或完整地址，例如 10.0.0.8:8080'
            onChange={(event) => setTargetAddress(event.target.value)}
            onPressEnter={() => !running && void start()}
          />
          <Button type='primary' icon={<PlayCircleOutlined />} loading={running} onClick={() => void start()}>
            {results.length ? '重新模拟调用' : '开始模拟调用'}
          </Button>
        </Space.Compact>
        <div>
          <Progress percent={percent} status={running ? 'active' : completed && completed === transactions.length ? 'success' : 'normal'} />
          <Space wrap>
            <Typography.Text>总交易：{transactions.length}</Typography.Text>
            <Typography.Text>已完成：{completed}</Typography.Text>
            <Typography.Text type='success'>成功：{successCount}</Typography.Text>
            <Typography.Text type={results.length - successCount ? 'danger' : 'secondary'}>失败：{results.length - successCount}</Typography.Text>
            <Tag color='warning'>SOAP {soapVersion} · 静态模拟</Tag>
          </Space>
        </div>
        {!results.length ? (
          <Empty description='开始后将在这里逐笔显示调用结果' />
        ) : (
          <Table
            rowKey='transactionId'
            size='small'
            columns={columns}
            dataSource={results}
            pagination={false}
            scroll={{ x: 1200, y: 440 }}
            expandable={{
              expandedRowRender: (record) => (
                <Tabs items={[
                  { key: 'request', label: '模拟请求报文', children: <CodeViewer value={record.requestXml || record.responseMessage} language='xml' filename={`${record.transactionCode}-request.xml`} height={240} /> },
                  { key: 'response', label: '模拟响应报文', children: <CodeViewer value={record.responseXml || record.responseMessage} language='xml' filename={`${record.transactionCode}-response.xml`} height={240} /> },
                ]} />
              ),
            }}
          />
        )}
      </Space>
    </Drawer>
  );
}
