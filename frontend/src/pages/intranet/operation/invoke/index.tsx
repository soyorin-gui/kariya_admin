import { useEffect, useMemo, useState } from 'react';
import { App, Button, Empty, Form, Select, Space, Spin, Tabs, Tag, Typography } from 'antd';
import { ReloadOutlined, SendOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { CodeViewer } from '../../../../components/CodeViewer';
import { getApiErrorMessage } from '../../../../utils/apiError';
import type { Transaction, TransactionListItem, TransactionMessageField } from '../../transaction/types';
import { getDefinitionTargets, loadDefinition } from '../shared/definitions';
import type { DefinitionTarget } from '../shared/definitions';
import { buildSoapEnvelope, hydrateFormValues, normalizeFormValues, RequestField, toFieldTree } from '../shared/requestForm';
import { TestCaseControls } from '../shared/TestCaseControls';
import { getAllTransactions } from '../shared/transactions';
import { BatchInvokeDrawer } from './BatchInvokeDrawer';
import './index.css';

function xmlName(value: string): string { return value.replace(/[^A-Za-z0-9_.:-]/g, '_') || 'operation'; }

function buildMockSoapResponse(transaction: Transaction | undefined, soapVersion: '1.1' | '1.2'): string {
  const namespace = soapVersion === '1.1' ? 'http://schemas.xmlsoap.org/soap/envelope/' : 'http://www.w3.org/2003/05/soap-envelope';
  const operation = xmlName(`${transaction?.esfServiceOperationName || transaction?.transactionCode || 'operation'}Response`);
  return `<?xml version="1.0" encoding="UTF-8"?>
<soapenv:Envelope xmlns:soapenv="${namespace}" xmlns:mock="urn:lbl:operation:mock">
  <soapenv:Header/>
  <soapenv:Body>
    <${operation}>
      <mock:responseStatus>Y</mock:responseStatus>
      <mock:responseCode>MOCK_SUCCESS</mock:responseCode>
      <mock:responseMessage>静态模拟调用成功，未向交易容器发送请求</mock:responseMessage>
      <mock:globalFlowId>MOCK202610150000000001</mock:globalFlowId>
      <mock:elapsedTime>128</mock:elapsedTime>
    </${operation}>
  </soapenv:Body>
</soapenv:Envelope>`;
}

export default function TransactionInvokePage() {
  const { message } = App.useApp();
  const [form] = Form.useForm<Record<string, unknown>>();
  const [transactions, setTransactions] = useState<TransactionListItem[]>([]);
  const [transaction, setTransaction] = useState<Transaction>();
  const [selectedTransactionId, setSelectedTransactionId] = useState<number>();
  const [definitionTargets, setDefinitionTargets] = useState<DefinitionTarget[]>([]);
  const [definitionTargetKey, setDefinitionTargetKey] = useState<string>();
  const [fields, setFields] = useState<TransactionMessageField[]>([]);
  const [values, setValues] = useState<Record<string, unknown>>({});
  const [targetEndpoint, setTargetEndpoint] = useState<string>();
  const [soapVersion, setSoapVersion] = useState<'1.1' | '1.2'>('1.1');
  const [response, setResponse] = useState('');
  const [previewTab, setPreviewTab] = useState('request');
  const [sending, setSending] = useState(false);
  const [loading, setLoading] = useState(true);
  const [fieldLoading, setFieldLoading] = useState(false);
  const [batchOpen, setBatchOpen] = useState(false);
  const [preview, setPreview] = useState('{}');
  const tree = useMemo(() => toFieldTree(fields), [fields]);
  const soapRequest = useMemo(() => buildSoapEnvelope(transaction, tree, values, soapVersion), [soapVersion, transaction, tree, values]);

  useEffect(() => {
    void getAllTransactions()
      .then(setTransactions)
      .catch((error) => message.error(getApiErrorMessage(error, '无法加载交易列表')))
      .finally(() => setLoading(false));
  }, [message]);

  const loadFields = async (transactionId: number, target: DefinitionTarget) => {
    setFieldLoading(true);
    form.resetFields();
    setValues({});
    setResponse('');
    setPreviewTab('request');
    try {
      const definition = await loadDefinition(transactionId, target);
      setDefinitionTargetKey(target.key);
      setTransaction(definition.transaction);
      setTargetEndpoint(definition.transaction.esfServiceAddress || undefined);
      setFields(definition.fields);
    }
    catch (error) { setTransaction(undefined); setTargetEndpoint(undefined); setFields([]); message.error(getApiErrorMessage(error, '无法加载交易请求字段')); }
    finally { setFieldLoading(false); }
  };

  const selectTransaction = async (transactionId: number) => {
    setSelectedTransactionId(transactionId);
    setDefinitionTargets([]);
    setDefinitionTargetKey(undefined);
    try {
      const targets = await getDefinitionTargets(transactionId);
      setDefinitionTargets(targets);
      if (targets[0]) await loadFields(transactionId, targets[0]);
    } catch (error) { message.error(getApiErrorMessage(error, '无法加载交易字段定义')); }
  };

  const selectDefinition = (key: string) => {
    const target = definitionTargets.find((item) => item.key === key);
    if (selectedTransactionId && target) void loadFields(selectedTransactionId, target);
  };

  const sendRequest = async () => {
    if (sending) return;
    try {
      await form.validateFields();
    } catch {
      return;
    }
    setSending(true);
    setResponse('');
    setPreviewTab('response');
    try {
      await new Promise((resolve) => window.setTimeout(resolve, 900));
      setResponse(buildMockSoapResponse(transaction, soapVersion));
    } finally {
      setSending(false);
    }
  };

  if (loading) return <Spin fullscreen tip='正在加载交易列表' />;
  return (
    <div className='page-fill operation-invoke-page'>
      <div className='page-head'><div><div className='page-kicker'>TRANSACTION OPERATION</div><h1 className='page-title'>交易在线调试</h1></div></div>
      <div className='table-card operation-invoke-card'>
        <div className='operation-invoke-workspace'>
          <div className='operation-invoke-request'>
            <Space wrap className='operation-invoke-toolbar'>
              <Select value={selectedTransactionId} showSearch optionFilterProp='label' placeholder='请选择交易' style={{ width: 330 }} options={transactions.map((transaction) => ({ value: transaction.id, label: `${transaction.transactionCode} - ${transaction.transactionName}` }))} onChange={(value) => void selectTransaction(value)} />
              <Select value={definitionTargetKey} placeholder='请选择字段定义' style={{ width: 190 }} options={definitionTargets.map((item) => ({ value: item.key, label: item.label }))} onChange={selectDefinition} disabled={!selectedTransactionId} />
              <Button icon={<ReloadOutlined />} disabled={!selectedTransactionId || !definitionTargetKey} onClick={() => { const target = definitionTargets.find((item) => item.key === definitionTargetKey); if (selectedTransactionId && target) void loadFields(selectedTransactionId, target); }}>重新加载字段</Button>
              <Select mode='tags' maxCount={1} value={targetEndpoint ? [targetEndpoint] : []} style={{ width: 260 }} placeholder='选择或输入目标 IP/地址' options={transaction?.esfServiceAddress ? [{ value: transaction.esfServiceAddress, label: transaction.esfServiceAddress }] : []} onChange={(items) => setTargetEndpoint(items.at(-1))} />
              <Select value={soapVersion} style={{ width: 110 }} options={[{ value: '1.1', label: 'SOAP 1.1' }, { value: '1.2', label: 'SOAP 1.2' }]} onChange={setSoapVersion} />
              <Button type='primary' icon={<SendOutlined />} loading={sending} disabled={!selectedTransactionId || fieldLoading || sending} onClick={() => void sendRequest()}>发送请求</Button>
              <Button icon={<ThunderboltOutlined />} disabled={!transactions.length} onClick={() => setBatchOpen(true)}>一键调用</Button>
              <Tag color='warning'>静态响应模式</Tag>
              <Tag color='processing'>{definitionTargets.find((item) => item.key === definitionTargetKey)?.label ?? '未选择字段定义'}</Tag>
            </Space>
            <div style={{ margin: '12px 0' }}>
              <TestCaseControls
                transactionId={selectedTransactionId}
                target={definitionTargets.find((item) => item.key === definitionTargetKey)}
                targets={definitionTargets}
                getPayload={() => normalizeFormValues(form.getFieldsValue(true)) as Record<string, unknown>}
                onApply={(payload) => { const hydrated = hydrateFormValues(tree, payload); form.setFieldsValue(hydrated as Parameters<typeof form.setFieldsValue>[0]); setValues(normalizeFormValues(hydrated) as Record<string, unknown>); setResponse(''); setPreviewTab('request'); }}
              />
            </div>
            <Spin spinning={fieldLoading} tip='正在生成请求表单'>
              <Form form={form} layout='vertical' className='operation-invoke-form' onValuesChange={() => setValues(normalizeFormValues(form.getFieldsValue(true)) as Record<string, unknown>)}>
                {!selectedTransactionId ? <Empty description='请选择交易后生成请求表单' /> : !tree.length ? <Empty description='该交易尚未配置请求字段' /> : tree.map((field) => <RequestField key={field.id} field={field} />)}
              </Form>
            </Spin>
          </div>
          <div className='operation-invoke-preview'>
            <Typography.Title level={5}>调用报文</Typography.Title>
            <Typography.Paragraph type='secondary'>报文由当前交易字段自动生成；当前点击发送只展示固定模拟响应，不会调用交易容器。</Typography.Paragraph>
            <Tabs activeKey={previewTab} onChange={setPreviewTab} items={[
              { key: 'request', label: `请求报文（SOAP ${soapVersion}）`, children: <CodeViewer className='operation-invoke-code-viewer' value={soapRequest} language='xml' filename='soap-request.xml' height='auto' /> },
              {
                key: 'response',
                label: '应用响应',
                children: (
                  <div className='operation-invoke-response-pane'>
                    <Spin spinning={sending} tip='正在等待应用响应' wrapperClassName='operation-invoke-response-spinner'>
                      {response
                        ? <CodeViewer className='operation-invoke-code-viewer' value={response} language='xml' filename='soap-response.xml' height='auto' />
                        : <Empty description={sending ? '请求处理中，请稍候' : '发送请求后显示响应报文'} />}
                    </Spin>
                  </div>
                ),
              },
            ]} />
          </div>
        </div>
      </div>
      <BatchInvokeDrawer open={batchOpen} transactions={transactions} soapVersion={soapVersion} onClose={() => setBatchOpen(false)} />
    </div>
  );
}
