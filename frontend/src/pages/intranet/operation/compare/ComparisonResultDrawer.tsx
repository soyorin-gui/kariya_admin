import { useMemo } from 'react';
import { Card, Descriptions, Drawer, Empty, Space, Spin, Switch, Table, Tabs, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { CodeViewer } from '../../../../components/CodeViewer';
import type { DifferenceType, EnvironmentInvocationResult, MockResponseComparison, ResponseFieldComparison } from './types';

const differenceLabels: Record<DifferenceType, { label: string; color: string }> = {
  SAME: { label: '一致', color: 'success' },
  VALUE_DIFFERENT: { label: '值不同', color: 'warning' },
  ONLY_A: { label: '仅环境 A', color: 'error' },
  ONLY_B: { label: '仅环境 B', color: 'processing' },
};

function InvocationSummary({ result }: { result: EnvironmentInvocationResult }) {
  return (
    <Card size='small' title={result.environmentName} extra={<Tag color={result.success ? 'success' : 'error'}>{result.success ? '调用成功' : '调用失败'}</Tag>}>
      <Descriptions size='small' column={2}>
        <Descriptions.Item label='响应状态'>{result.responseStatus}</Descriptions.Item>
        <Descriptions.Item label='响应码'>{result.responseCode}</Descriptions.Item>
        <Descriptions.Item label='耗时'>{result.elapsedTime} ms</Descriptions.Item>
        <Descriptions.Item label='全局流水号'>{result.globalFlowId}</Descriptions.Item>
      </Descriptions>
    </Card>
  );
}

export function ComparisonResultDrawer({
  open,
  loading,
  result,
  onlyDifferences,
  onOnlyDifferencesChange,
  onClose,
}: {
  open: boolean;
  loading: boolean;
  result?: MockResponseComparison;
  onlyDifferences: boolean;
  onOnlyDifferencesChange: (value: boolean) => void;
  onClose: () => void;
}) {
  const tableRows = useMemo(
    () => (result?.fieldComparisons ?? []).filter((item) => !onlyDifferences || item.differenceType !== 'SAME'),
    [onlyDifferences, result],
  );
  const columns: ColumnsType<ResponseFieldComparison> = [
    { title: '字段路径', dataIndex: 'fieldPath', width: 380, ellipsis: true },
    { title: '对比结果', dataIndex: 'differenceType', width: 120, render: (value: DifferenceType) => <Tag color={differenceLabels[value].color}>{differenceLabels[value].label}</Tag> },
    { title: result?.environmentA.environmentName ?? '环境 A', dataIndex: 'valueA', render: (value) => value ?? <Typography.Text type='secondary'>不存在</Typography.Text> },
    { title: result?.environmentB.environmentName ?? '环境 B', dataIndex: 'valueB', render: (value) => value ?? <Typography.Text type='secondary'>不存在</Typography.Text> },
  ];

  return (
    <Drawer
      title={result ? `响应对比 · ${result.transactionCode} - ${result.transactionName}` : '多环境响应对比'}
      width='92vw'
      open={open}
      closable={!loading}
      maskClosable={!loading}
      keyboard={!loading}
      onClose={onClose}
      destroyOnClose={false}
    >
      <Spin spinning={loading} tip='正在调用两个模拟环境并比较响应'>
        {!result ? <Empty description={loading ? '响应对比处理中，请稍候' : '暂无对比结果'} /> : (
          <>
            <div className='operation-compare-summary-grid'>
              <InvocationSummary result={result.environmentA} />
              <InvocationSummary result={result.environmentB} />
            </div>
            <div className='operation-compare-response-grid'>
              {[result.environmentA, result.environmentB].map((environment) => <Card key={environment.environmentId} size='small' className='operation-compare-response-card' title={environment.environmentName}>
                <Tabs items={[
                  { key: 'request', label: '实际请求', children: <CodeViewer value={environment.requestXml} language='xml' filename={`${environment.environmentId}-request.xml`} height={330} /> },
                  { key: 'response', label: '应用响应', children: <CodeViewer value={environment.responseXml} language='xml' filename={`${environment.environmentId}-response.xml`} height={330} /> },
                ]} />
              </Card>)}
            </div>
            <Space style={{ marginBottom: 12 }}>
              <Typography.Title level={5} style={{ margin: 0 }}>字段对比</Typography.Title>
              <Switch checked={onlyDifferences} onChange={onOnlyDifferencesChange} />
              <span>仅查看差异</span>
              <Tag color='warning'>差异 {result.fieldComparisons.filter((item) => item.differenceType !== 'SAME').length} 项</Tag>
            </Space>
            <Table
              rowKey='fieldPath'
              size='small'
              columns={columns}
              dataSource={tableRows}
              pagination={false}
              scroll={{ x: 1000 }}
              rowClassName={(record) => record.differenceType === 'ONLY_A' ? 'operation-compare-only-a-row' : record.differenceType === 'ONLY_B' ? 'operation-compare-only-b-row' : record.differenceType === 'VALUE_DIFFERENT' ? 'operation-compare-difference-row' : ''}
            />
          </>
        )}
      </Spin>
    </Drawer>
  );
}
