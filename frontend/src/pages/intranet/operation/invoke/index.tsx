import { useEffect, useMemo, useState } from 'react';
import { App, Button, Card, DatePicker, Empty, Form, Input, InputNumber, Select, Space, Spin, Switch, Tabs, Tag, Typography } from 'antd';
import { MinusCircleOutlined, PlusOutlined, ReloadOutlined, SendOutlined } from '@ant-design/icons';
import { CodeViewer } from '../../../../components/CodeViewer';
import { getApiErrorMessage } from '../../../../utils/apiError';
import { getTransaction, getTransactionMessageFields, getTransactions } from '../../transaction/api';
import type { Transaction, TransactionListItem, TransactionMessageField } from '../../transaction/types';
import './index.css';

type FieldNode = TransactionMessageField & { children: FieldNode[] };
type FormPath = Array<string | number>;

function toTree(rows: TransactionMessageField[]): FieldNode[] {
  const nodes = new Map(rows.map((row) => [row.id, { ...row, children: [] as FieldNode[] }]));
  const roots: FieldNode[] = [];
  nodes.forEach((node) => {
    const parent = nodes.get(node.parentId);
    if (parent) parent.children.push(node);
    else roots.push(node);
  });
  return roots;
}

function isNumeric(dataType?: string): boolean { return /^(byte|short|int|integer|long|float|double|decimal|number|bigdecimal)$/i.test(dataType?.trim() ?? ''); }
function isBoolean(dataType?: string): boolean { return /^(boolean|bool)$/i.test(dataType?.trim() ?? ''); }
function isDate(dataType?: string): boolean { return /^(date|datetime|timestamp|localdate|localdatetime)$/i.test(dataType?.trim() ?? ''); }

function normalizePreview(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(normalizePreview);
  if (value && typeof value === 'object') {
    if ('format' in value && typeof value.format === 'function') return value.format('YYYY-MM-DD HH:mm:ss');
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, normalizePreview(item)]));
  }
  return value;
}

function xmlEscape(value: unknown): string {
  return String(normalizePreview(value) ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&apos;');
}

function xmlName(value: string): string { return value.replace(/[^A-Za-z0-9_.:-]/g, '_') || 'operation'; }

function renderXmlField(field: FieldNode, value: unknown): string {
  const name = xmlName(field.fieldNameEn);
  if (value === undefined || value === null || value === '') return '';
  if (field.nodeType === 'ARRAY') {
    if (!Array.isArray(value)) return '';
    return value.map((item) => field.children.length ? `<${name}>${field.children.map((child) => renderXmlField(child, (item as Record<string, unknown>)?.[child.fieldNameEn])).join('')}</${name}>` : `<${name}>${xmlEscape(item)}</${name}>`).join('');
  }
  if (field.nodeType === 'OBJECT') return `<${name}>${field.children.map((child) => renderXmlField(child, (value as Record<string, unknown>)?.[child.fieldNameEn])).join('')}</${name}>`;
  return `<${name}>${xmlEscape(value)}</${name}>`;
}

function buildSoapEnvelope(transaction: Transaction | undefined, tree: FieldNode[], values: Record<string, unknown>, soapVersion: '1.1' | '1.2'): string {
  const namespace = soapVersion === '1.1' ? 'http://schemas.xmlsoap.org/soap/envelope/' : 'http://www.w3.org/2003/05/soap-envelope';
  const operation = xmlName(transaction?.esfServiceOperationName || transaction?.transactionCode || 'operation');
  const content = tree.map((field) => renderXmlField(field, values[field.fieldNameEn])).join('');
  return `<?xml version="1.0" encoding="UTF-8"?>\n<soapenv:Envelope xmlns:soapenv="${namespace}">\n  <soapenv:Header/>\n  <soapenv:Body>\n    <${operation}>${content}</${operation}>\n  </soapenv:Body>\n</soapenv:Envelope>`;
}

function fieldLabel(field: TransactionMessageField) {
  return <Space size={4}><span>{field.fieldNameCn}</span><Typography.Text type='secondary' code>{field.fieldNameEn}</Typography.Text>{field.requiredFlag === 1 && <Tag color='error'>必填</Tag>}</Space>;
}

function ScalarField({ field, path }: { field: FieldNode; path: FormPath }) {
  const rules = field.requiredFlag === 1 && !isBoolean(field.dataType) ? [{ required: true, message: `请输入${field.fieldNameCn}` }] : [];
  const maxLength = /^\d+$/.test(field.dataLength ?? '') ? Number(field.dataLength) : undefined;
  if (isBoolean(field.dataType)) return <Form.Item label={fieldLabel(field)} name={path} valuePropName='checked'><Switch checkedChildren='是' unCheckedChildren='否' /></Form.Item>;
  if (isNumeric(field.dataType)) return <Form.Item label={fieldLabel(field)} name={path} rules={rules}><InputNumber style={{ width: '100%' }} placeholder={field.description || `请输入${field.fieldNameCn}`} /></Form.Item>;
  if (isDate(field.dataType)) return <Form.Item label={fieldLabel(field)} name={path} rules={rules}><DatePicker showTime={/time/i.test(field.dataType ?? '')} style={{ width: '100%' }} /></Form.Item>;
  return <Form.Item label={fieldLabel(field)} name={path} rules={rules}><Input maxLength={maxLength} placeholder={field.description || `请输入${field.fieldNameCn}`} /></Form.Item>;
}

function RequestField({ field, path }: { field: FieldNode; path: FormPath }) {
  const nextPath = [...path, field.fieldNameEn];
  if (field.nodeType === 'OBJECT') return <Card size='small' title={fieldLabel(field)} className='operation-invoke-group'>{field.children.map((child) => <RequestField key={child.id} field={child} path={nextPath} />)}</Card>;
  if (field.nodeType === 'ARRAY') return (
    <Card size='small' title={fieldLabel(field)} className='operation-invoke-group'>
      <Form.List name={nextPath}>{(items, { add, remove }) => <Space direction='vertical' style={{ width: '100%' }}>
        {items.map((item) => <Card key={item.key} size='small' extra={<Button type='text' danger icon={<MinusCircleOutlined />} onClick={() => remove(item.name)} />}>
          {field.children.length ? field.children.map((child) => <RequestField key={child.id} field={child} path={[...nextPath, item.name]} />) : <ScalarField field={{ ...field, nodeType: 'FIELD' }} path={[...nextPath, item.name]} />}
        </Card>)}
        <Button type='dashed' icon={<PlusOutlined />} onClick={() => add()} block>新增一项</Button>
      </Space>}</Form.List>
    </Card>
  );
  return <ScalarField field={field} path={nextPath} />;
}

export default function TransactionInvokePage() {
  const { message } = App.useApp();
  const [form] = Form.useForm<Record<string, unknown>>();
  const [transactions, setTransactions] = useState<TransactionListItem[]>([]);
  const [transaction, setTransaction] = useState<Transaction>();
  const [selectedTransactionId, setSelectedTransactionId] = useState<number>();
  const [fields, setFields] = useState<TransactionMessageField[]>([]);
  const [values, setValues] = useState<Record<string, unknown>>({});
  const [targetEndpoint, setTargetEndpoint] = useState<string>();
  const [soapVersion, setSoapVersion] = useState<'1.1' | '1.2'>('1.1');
  const [response, setResponse] = useState('');
  const [responseLabel, setResponseLabel] = useState('尚未发送请求');
  const [previewTab, setPreviewTab] = useState('request');
  const [sending, setSending] = useState(false);
  const [loading, setLoading] = useState(true);
  const [fieldLoading, setFieldLoading] = useState(false);
  const [preview, setPreview] = useState('{}');
  const tree = useMemo(() => toTree(fields), [fields]);
  const soapRequest = useMemo(() => buildSoapEnvelope(transaction, tree, values, soapVersion), [soapVersion, transaction, tree, values]);

  useEffect(() => {
    void getTransactions({ pageNum: 1, pageSize: 100 })
      .then((result) => setTransactions(result.records))
      .catch((error) => message.error(getApiErrorMessage(error, '无法加载交易列表')))
      .finally(() => setLoading(false));
  }, [message]);

  const loadFields = async (transactionId: number) => {
    setSelectedTransactionId(transactionId);
    setFieldLoading(true);
    form.resetFields();
    setValues({});
    setResponse('');
    setResponseLabel('尚未发送请求');
    setPreviewTab('request');
    try {
      const [detail, requestFields] = await Promise.all([getTransaction(transactionId), getTransactionMessageFields(transactionId, 'REQUEST')]);
      setTransaction(detail);
      setTargetEndpoint(detail.esfServiceAddress || undefined);
      setFields(requestFields);
    }
    catch (error) { setTransaction(undefined); setTargetEndpoint(undefined); setFields([]); message.error(getApiErrorMessage(error, '无法加载交易请求字段')); }
    finally { setFieldLoading(false); }
  };

  const sendRequest = async () => {
    if (!targetEndpoint) return message.error('请选择或输入目标环境地址');
    try {
      await form.validateFields();
    } catch {
      return;
    }
    setSending(true);
    setResponse('');
    setResponseLabel('正在等待应用响应');
    try {
      const endpoint = /^https?:\/\//i.test(targetEndpoint) ? targetEndpoint : `http://${targetEndpoint}`;
      const result = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': soapVersion === '1.1' ? 'text/xml;charset=UTF-8' : 'application/soap+xml;charset=UTF-8' },
        body: soapRequest,
      });
      const content = await result.text();
      setResponse(content);
      setResponseLabel(`HTTP ${result.status} ${result.statusText}`);
      setPreviewTab('response');
    } catch (error) {
      setResponse(String(error));
      setResponseLabel('请求失败（请确认目标地址与 CORS 配置）');
      setPreviewTab('response');
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
              <Select value={selectedTransactionId} showSearch optionFilterProp='label' placeholder='请选择交易' style={{ width: 360 }} options={transactions.map((transaction) => ({ value: transaction.id, label: `${transaction.transactionCode} - ${transaction.transactionName}` }))} onChange={(value) => void loadFields(value)} />
              <Button icon={<ReloadOutlined />} disabled={!selectedTransactionId} onClick={() => selectedTransactionId && void loadFields(selectedTransactionId)}>重新加载字段</Button>
              <Select mode='tags' maxCount={1} value={targetEndpoint ? [targetEndpoint] : []} style={{ width: 260 }} placeholder='选择或输入目标 IP/地址' options={transaction?.esfServiceAddress ? [{ value: transaction.esfServiceAddress, label: transaction.esfServiceAddress }] : []} onChange={(items) => setTargetEndpoint(items.at(-1))} />
              <Select value={soapVersion} style={{ width: 110 }} options={[{ value: '1.1', label: 'SOAP 1.1' }, { value: '1.2', label: 'SOAP 1.2' }]} onChange={setSoapVersion} />
              <Button type='primary' icon={<SendOutlined />} loading={sending} disabled={!selectedTransactionId || fieldLoading} onClick={() => void sendRequest()}>发送请求</Button>
              <Tag color='processing'>当前交易字段</Tag>
            </Space>
            <Spin spinning={fieldLoading} tip='正在生成请求表单'>
              <Form form={form} layout='vertical' className='operation-invoke-form' onValuesChange={() => setValues(normalizePreview(form.getFieldsValue(true)) as Record<string, unknown>)}>
                {!selectedTransactionId ? <Empty description='请选择交易后生成请求表单' /> : !tree.length ? <Empty description='该交易尚未配置请求字段' /> : tree.map((field) => <RequestField key={field.id} field={field} path={[]} />)}
              </Form>
            </Spin>
          </div>
          <div className='operation-invoke-preview'>
            <Typography.Title level={5}>调用报文</Typography.Title>
            <Typography.Paragraph type='secondary'>报文由当前交易字段自动生成；目标应用直接返回的原始文本会显示在响应页签。</Typography.Paragraph>
            <Tabs activeKey={previewTab} onChange={setPreviewTab} items={[
              { key: 'request', label: `SOAP ${soapVersion} 请求`, children: <CodeViewer className='operation-invoke-code-viewer' value={soapRequest} language='xml' filename='soap-request.xml' height='auto' /> },
              { key: 'response', label: '应用响应', children: <div className='operation-invoke-response-pane'><Typography.Text type={response ? undefined : 'secondary'}>{responseLabel}</Typography.Text><CodeViewer className='operation-invoke-code-viewer' value={response} language='xml' filename='soap-response.xml' height='auto' /></div> },
            ]} />
          </div>
        </div>
      </div>
    </div>
  );
}
