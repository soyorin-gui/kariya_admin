import { useEffect, useMemo, useState } from 'react';
import { App, Alert, Button, Card, Empty, Form, Input, InputNumber, List, Select, Segmented, Space, Spin, Statistic, Table, Tabs, Tag, Tooltip, Typography } from 'antd';
import { ClearOutlined, DatabaseOutlined, PlayCircleOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { CodeViewer } from '../../../components/CodeViewer';
import { getApiErrorMessage } from '../../../utils/apiError';
import { executeDataExplorerQuery, getDataExplorerEnvironments, getDataExplorerResources, getDataExplorerTemplates } from './api';
import { RecordDetailDrawer } from './RecordDetailDrawer';
import type { DataExplorerEnvironment, DataExplorerQueryResult, DataExplorerResource, DataSourceType, QueryRecord, QueryTemplate } from './types';
import './index.css';

const sourceLabels: Record<DataSourceType, string> = { ELASTICSEARCH: 'Elasticsearch', HBASE: 'HBase', REDIS: 'Redis' };

function hbaseQuery(mode: 'GET' | 'SCAN', rowKey: string, startRow: string, stopRow: string, limit: number) {
  return JSON.stringify(mode === 'GET' ? { operation: 'GET', rowKey } : { operation: 'SCAN', startRow, stopRow, limit }, null, 2);
}

export default function DataExplorerPage() {
  const { message } = App.useApp();
  const [sourceType, setSourceType] = useState<DataSourceType>('ELASTICSEARCH');
  const [environments, setEnvironments] = useState<DataExplorerEnvironment[]>([]);
  const [environmentId, setEnvironmentId] = useState<string>();
  const [resources, setResources] = useState<DataExplorerResource[]>([]);
  const [templates, setTemplates] = useState<QueryTemplate[]>([]);
  const [resourceId, setResourceId] = useState<string>();
  const [templateId, setTemplateId] = useState<string>();
  const [keyword, setKeyword] = useState('');
  const [queryText, setQueryText] = useState('');
  const [loading, setLoading] = useState(true);
  const [resourceLoading, setResourceLoading] = useState(false);
  const [querying, setQuerying] = useState(false);
  const [result, setResult] = useState<DataExplorerQueryResult>();
  const [error, setError] = useState<string>();
  const [record, setRecord] = useState<QueryRecord>();
  const [hbaseMode, setHbaseMode] = useState<'GET' | 'SCAN'>('GET');
  const [rowKey, setRowKey] = useState('CUST#10001');
  const [startRow, setStartRow] = useState('CUST#10001');
  const [stopRow, setStopRow] = useState('CUST#10010');
  const [limit, setLimit] = useState(10);
  const selectedEnvironment = environments.find((item) => item.id === environmentId);

  useEffect(() => {
    void getDataExplorerEnvironments()
      .then((items) => { setEnvironments(items); setEnvironmentId(items[0]?.id); })
      .catch((cause) => setError(getApiErrorMessage(cause, '无法加载运行环境')))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    let active = true;
    setResourceLoading(true);
    setResult(undefined);
    setError(undefined);
    setResourceId(undefined);
    setTemplateId(undefined);
    void Promise.all([getDataExplorerResources(sourceType, keyword), getDataExplorerTemplates(sourceType)])
      .then(([nextResources, nextTemplates]) => {
        if (!active) return;
        setResources(nextResources);
        setTemplates(nextTemplates);
        setResourceId(nextResources[0]?.id);
        const initialTemplate = nextTemplates[0];
        setTemplateId(initialTemplate?.id);
        setQueryText(sourceType === 'HBASE' ? hbaseQuery('GET', rowKey, startRow, stopRow, limit) : initialTemplate?.queryText ?? '');
      })
      .catch((cause) => active && setError(getApiErrorMessage(cause, '无法加载数据资源')))
      .finally(() => active && setResourceLoading(false));
    return () => { active = false; };
    // keyword is deliberately refreshed through the resource search action, not on every keystroke.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sourceType]);

  const refreshResources = async () => {
    setResourceLoading(true);
    try {
      const items = await getDataExplorerResources(sourceType, keyword);
      setResources(items);
      if (!items.some((item) => item.id === resourceId)) setResourceId(items[0]?.id);
    } catch (cause) { setError(getApiErrorMessage(cause, '无法刷新数据资源')); }
    finally { setResourceLoading(false); }
  };

  const applyTemplate = (id: string) => {
    const template = templates.find((item) => item.id === id);
    if (!template) return;
    setTemplateId(id);
    setQueryText(template.queryText);
    if (sourceType === 'HBASE') setHbaseMode(template.id === 'hbase-scan' ? 'SCAN' : 'GET');
    setResult(undefined);
    setError(undefined);
  };

  const updateHbaseQuery = (nextMode = hbaseMode, nextRowKey = rowKey, nextStartRow = startRow, nextStopRow = stopRow, nextLimit = limit) => {
    setQueryText(hbaseQuery(nextMode, nextRowKey, nextStartRow, nextStopRow, nextLimit));
    setResult(undefined);
  };

  const execute = async () => {
    if (!environmentId) return message.warning('请先选择运行环境');
    if (!queryText.trim()) return message.warning('请输入查询内容');
    setQuerying(true);
    setError(undefined);
    setResult(undefined);
    try {
      setResult(await executeDataExplorerQuery({ sourceType, environmentId, resourceId, templateId, queryText }));
    } catch (cause) {
      setError(getApiErrorMessage(cause, '执行查询失败'));
    } finally { setQuerying(false); }
  };

  const columns = useMemo<ColumnsType<QueryRecord>>(() => [
    ...(result?.columns ?? []).map((column) => ({
      title: column.title,
      dataIndex: ['values', column.key],
      width: column.width,
      ellipsis: true,
      render: (value: unknown) => value === null || value === undefined ? '-' : typeof value === 'object' ? JSON.stringify(value) : String(value),
    })),
    { title: '操作', fixed: 'right', width: 88, render: (_, item) => <Button type='link' size='small' onClick={(event) => { event.stopPropagation(); setRecord(item); }}>展开</Button> },
  ], [result]);

  const sourceHint = sourceType === 'ELASTICSEARCH'
    ? '选择索引后编写 JSON DSL；格式化会校验 JSON。'
    : sourceType === 'HBASE'
      ? '使用参数表单生成 Get 或 Scan 查询描述，也可直接编辑 JSON。'
      : '仅支持模板中的只读 GET、HGETALL、LRANGE、SMEMBERS 命令。';

  if (loading) return <Spin fullscreen tip='正在加载数据查询工作台' />;

  return <div className='page-fill dataexplorer-page'>
    <div className='page-head'>
      <div><div className='page-kicker'>DATA EXPLORER</div><h1 className='page-title'>数据查询工作台</h1></div>
      <Space wrap>
        <Select aria-label='运行环境' value={environmentId} style={{ width: 180 }} options={environments.map((item) => ({ value: item.id, label: item.name }))} onChange={(value) => { setEnvironmentId(value); setResult(undefined); }} />
        <Tag color='processing' icon={<DatabaseOutlined />}>{selectedEnvironment?.connectionLabel ?? '连接未选择'}</Tag>
      </Space>
    </div>
    <Card className='dataexplorer-source-card'>
      <Space wrap>
        <Typography.Text strong>数据源</Typography.Text>
        <Segmented<DataSourceType> value={sourceType} options={(Object.keys(sourceLabels) as DataSourceType[]).map((value) => ({ value, label: sourceLabels[value] }))} onChange={setSourceType} />
        <Typography.Text type='secondary'>{sourceHint}</Typography.Text>
      </Space>
    </Card>
    <div className='dataexplorer-layout'>
      <Card title='数据资源浏览器' className='dataexplorer-resource-card' extra={<Tooltip title='刷新资源'><Button type='text' icon={<ReloadOutlined />} loading={resourceLoading} onClick={() => void refreshResources()} /></Tooltip>}>
        <Input value={keyword} onChange={(event) => setKeyword(event.target.value)} onPressEnter={() => void refreshResources()} allowClear prefix={<SearchOutlined />} placeholder={sourceType === 'REDIS' ? '搜索 Key（有限列表）' : '搜索资源'} />
        <Spin spinning={resourceLoading}>
          <List className='dataexplorer-resource-list' size='small' locale={{ emptyText: '没有匹配的数据资源' }} dataSource={resources} renderItem={(item) => <List.Item className={`dataexplorer-resource-item${item.id === resourceId ? ' is-active' : ''}`} onClick={() => { setResourceId(item.id); setResult(undefined); }}>
            <List.Item.Meta title={<Space size={4}>{item.group && <Tag>{item.group}</Tag>}<span className='dataexplorer-resource-name'>{item.name}</span></Space>} description={<span>{item.description}{item.detail ? ` · ${item.detail}` : ''}</span>} />
          </List.Item>} />
        </Spin>
      </Card>
      <div>
        <Card title='查询编辑器' extra={<Button type='primary' icon={<PlayCircleOutlined />} loading={querying} onClick={() => void execute()}>执行查询</Button>}>
          <div className='dataexplorer-query-grid'>
            <div className='dataexplorer-editor'>
              <CodeViewer value={queryText} onChange={setQueryText} readOnly={false} language={sourceType === 'REDIS' ? 'text' : 'json'} filename={`${sourceType.toLowerCase()}-query.${sourceType === 'REDIS' ? 'txt' : 'json'}`} height={310} />
            </div>
            <div>
              <Typography.Title level={5}>查询模板</Typography.Title>
              <Select value={templateId} style={{ width: '100%' }} placeholder='选择查询模板' options={templates.map((item) => ({ value: item.id, label: item.name }))} onChange={applyTemplate} />
              {templates.find((item) => item.id === templateId) && <Typography.Paragraph type='secondary' style={{ marginTop: 8 }}>{templates.find((item) => item.id === templateId)?.description}</Typography.Paragraph>}
              <Button block icon={<ClearOutlined />} onClick={() => { setQueryText(''); setResult(undefined); setError(undefined); }}>清空查询</Button>
              {sourceType === 'HBASE' && <Form layout='vertical' className='dataexplorer-hbase-form'>
                <Form.Item label='查询方式'><Segmented<'GET' | 'SCAN'> block value={hbaseMode} options={[{ value: 'GET', label: 'RowKey Get' }, { value: 'SCAN', label: 'Scan' }]} onChange={(value) => { setHbaseMode(value); updateHbaseQuery(value); }} /></Form.Item>
                {hbaseMode === 'GET' ? <Form.Item label='RowKey'><Input value={rowKey} onChange={(event) => { setRowKey(event.target.value); updateHbaseQuery(hbaseMode, event.target.value); }} /></Form.Item> : <>
                  <Form.Item label='startRow'><Input value={startRow} onChange={(event) => { setStartRow(event.target.value); updateHbaseQuery(hbaseMode, rowKey, event.target.value); }} /></Form.Item>
                  <Form.Item label='stopRow'><Input value={stopRow} onChange={(event) => { setStopRow(event.target.value); updateHbaseQuery(hbaseMode, rowKey, startRow, event.target.value); }} /></Form.Item>
                  <Form.Item label='limit'><InputNumber min={1} max={1000} value={limit} style={{ width: '100%' }} onChange={(value) => { const next = value ?? 10; setLimit(next); updateHbaseQuery(hbaseMode, rowKey, startRow, stopRow, next); }} /></Form.Item>
                </>}
              </Form>}
            </div>
          </div>
          {querying && <Alert style={{ marginTop: 16 }} type='info' showIcon message='正在执行模拟查询' description='D1 仅从前端 API Mock 获取演示结果，未连接真实数据源。' />}
          {error && <Alert style={{ marginTop: 16 }} type='error' showIcon message='查询失败' description={error} closable onClose={() => setError(undefined)} />}
        </Card>
        <Card title='查询结果' style={{ marginTop: 16 }}>
          {!result && !querying && <Empty description='配置查询后执行，即可在这里查看表格和原始结果' />}
          {result && <>
            <div className='dataexplorer-result-meta'>
              <Tag color='success'>查询完成</Tag>
              <Statistic title='查询耗时' value={result.elapsedTimeMs} suffix='ms' valueStyle={{ fontSize: 16 }} />
              <Statistic title='返回数量' value={result.returnedCount} valueStyle={{ fontSize: 16 }} />
              {result.warnings.map((warning) => <Tag key={warning} color='warning'>{warning}</Tag>)}
            </div>
            <Tabs items={[
              { key: 'table', label: `表格结果 (${result.returnedCount})`, children: result.records.length ? <Table className='dataexplorer-result-table' rowKey='id' size='small' columns={columns} dataSource={result.records} scroll={{ x: 760 }} pagination={{ pageSize: 5, showSizeChanger: false, showTotal: (total) => `共 ${total} 条` }} onRow={(item) => ({ onClick: () => setRecord(item) })} /> : <Empty description='没有匹配的数据' /> },
              { key: 'raw', label: 'JSON / 原始结果', children: <CodeViewer value={result.rawResult} language='json' filename='query-result.json' height={360} /> },
            ]} />
          </>}
        </Card>
      </div>
    </div>
    <RecordDetailDrawer record={record} onClose={() => setRecord(undefined)} />
  </div>;
}
