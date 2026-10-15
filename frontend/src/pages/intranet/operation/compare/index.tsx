import { useEffect, useMemo, useState } from 'react';
import { Alert, App, Button, Card, Empty, Form, Select, Space, Spin, Tag, Typography } from 'antd';
import { ApartmentOutlined, ReloadOutlined } from '@ant-design/icons';
import { CodeViewer } from '../../../../components/CodeViewer';
import { getApiErrorMessage } from '../../../../utils/apiError';
import type { Transaction, TransactionListItem, TransactionMessageField } from '../../transaction/types';
import { getDefinitionTargets, loadDefinition } from '../shared/definitions';
import type { DefinitionTarget } from '../shared/definitions';
import { buildSoapEnvelope, hydrateFormValues, normalizeFormValues, RequestField, toFieldTree } from '../shared/requestForm';
import { TestCaseControls } from '../shared/TestCaseControls';
import { getAllTransactions } from '../shared/transactions';
import { compareMockResponses, getComparisonEnvironments } from './api';
import type { ComparisonEnvironment, MockResponseComparison } from './types';
import { ComparisonResultDrawer } from './ComparisonResultDrawer';
import './index.css';

export default function OperationResponseComparePage() {
  const { message } = App.useApp();
  const [formA] = Form.useForm<Record<string, unknown>>();
  const [formB] = Form.useForm<Record<string, unknown>>();
  const [transactions, setTransactions] = useState<TransactionListItem[]>([]);
  const [environments, setEnvironments] = useState<ComparisonEnvironment[]>([]);
  const [transactionId, setTransactionId] = useState<number>();
  const [targets, setTargets] = useState<DefinitionTarget[]>([]);
  const [targetKeyA, setTargetKeyA] = useState<string>();
  const [targetKeyB, setTargetKeyB] = useState<string>();
  const [transactionA, setTransactionA] = useState<Transaction>();
  const [transactionB, setTransactionB] = useState<Transaction>();
  const [fieldsA, setFieldsA] = useState<TransactionMessageField[]>([]);
  const [fieldsB, setFieldsB] = useState<TransactionMessageField[]>([]);
  const [valuesA, setValuesA] = useState<Record<string, unknown>>({});
  const [valuesB, setValuesB] = useState<Record<string, unknown>>({});
  const [testCaseIdA, setTestCaseIdA] = useState<number>();
  const [testCaseIdB, setTestCaseIdB] = useState<number>();
  const [environmentAId, setEnvironmentAId] = useState<string>();
  const [environmentBId, setEnvironmentBId] = useState<string>();
  const [soapVersion, setSoapVersion] = useState<'1.1' | '1.2'>('1.1');
  const [result, setResult] = useState<MockResponseComparison>();
  const [onlyDifferences, setOnlyDifferences] = useState(true);
  const [resultDrawerOpen, setResultDrawerOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [fieldLoadingA, setFieldLoadingA] = useState(false);
  const [fieldLoadingB, setFieldLoadingB] = useState(false);
  const [comparing, setComparing] = useState(false);
  const treeA = useMemo(() => toFieldTree(fieldsA), [fieldsA]);
  const treeB = useMemo(() => toFieldTree(fieldsB), [fieldsB]);
  const requestXmlA = useMemo(() => buildSoapEnvelope(transactionA, treeA, valuesA, soapVersion), [soapVersion, transactionA, treeA, valuesA]);
  const requestXmlB = useMemo(() => buildSoapEnvelope(transactionB, treeB, valuesB, soapVersion), [soapVersion, transactionB, treeB, valuesB]);
  const targetA = targets.find((item) => item.key === targetKeyA);
  const targetB = targets.find((item) => item.key === targetKeyB);

  useEffect(() => {
    void Promise.all([getAllTransactions(), getComparisonEnvironments()])
      .then(([transactionList, environmentList]) => {
        setTransactions(transactionList);
        setEnvironments(environmentList);
        setEnvironmentAId(environmentList[0]?.id);
        setEnvironmentBId(environmentList[1]?.id);
      })
      .catch((error) => message.error(getApiErrorMessage(error, '无法初始化响应对比页面')))
      .finally(() => setLoading(false));
  }, [message]);

  const loadSide = async (side: 'A' | 'B', id: number, target: DefinitionTarget) => {
    const setFieldLoading = side === 'A' ? setFieldLoadingA : setFieldLoadingB;
    const form = side === 'A' ? formA : formB;
    setFieldLoading(true);
    form.resetFields();
    setResult(undefined);
    if (side === 'A') { setValuesA({}); setTestCaseIdA(undefined); }
    else { setValuesB({}); setTestCaseIdB(undefined); }
    try {
      const definition = await loadDefinition(id, target);
      if (side === 'A') { setTargetKeyA(target.key); setTransactionA(definition.transaction); setFieldsA(definition.fields); }
      else { setTargetKeyB(target.key); setTransactionB(definition.transaction); setFieldsB(definition.fields); }
    } catch (error) { message.error(getApiErrorMessage(error, `无法加载环境 ${side} 字段定义`)); }
    finally { setFieldLoading(false); }
  };

  const selectTransaction = async (id: number) => {
    setTransactionId(id);
    setTargets([]);
    setTargetKeyA(undefined);
    setTargetKeyB(undefined);
    try {
      const definitionTargets = await getDefinitionTargets(id);
      setTargets(definitionTargets);
      if (definitionTargets[0]) await Promise.all([loadSide('A', id, definitionTargets[0]), loadSide('B', id, definitionTargets[0])]);
    } catch (error) { message.error(getApiErrorMessage(error, '无法加载交易字段定义')); }
  };

  const compare = async () => {
    if (comparing) return;
    if (!transactionId) return message.warning('请选择交易');
    if (!environmentAId || !environmentBId) return message.warning('请选择两个对比环境');
    if (environmentAId === environmentBId) return message.warning('环境 A 和环境 B 不能相同');
    if (!targetA || !targetB) return message.warning('请选择两侧字段定义');
    try { await Promise.all([formA.validateFields(), formB.validateFields()]); }
    catch { return; }
    setComparing(true);
    setResult(undefined);
    setResultDrawerOpen(true);
    try {
      const [comparison] = await Promise.all([
        compareMockResponses({
          transactionId, environmentAId, environmentBId, soapVersion,
          bindingTypeA: targetA.bindingType, transactionVersionIdA: targetA.transactionVersionId, testCaseIdA,
          bindingTypeB: targetB.bindingType, transactionVersionIdB: targetB.transactionVersionId, testCaseIdB,
          requestXmlA, requestXmlB,
        }),
        new Promise((resolve) => window.setTimeout(resolve, 800)),
      ]);
      setResult(comparison);
    } catch (error) { message.error(getApiErrorMessage(error, '无法完成响应对比')); }
    finally { setComparing(false); }
  };

  if (loading) return <Spin fullscreen tip='正在加载响应对比配置' />;

  const renderSide = (side: 'A' | 'B') => {
    const isA = side === 'A';
    const environmentId = isA ? environmentAId : environmentBId;
    const target = isA ? targetA : targetB;
    const tree = isA ? treeA : treeB;
    const form = isA ? formA : formB;
    const fieldLoading = isA ? fieldLoadingA : fieldLoadingB;
    const requestXml = isA ? requestXmlA : requestXmlB;
    const setEnvironment = isA ? setEnvironmentAId : setEnvironmentBId;
    return <Card size='small' title={`环境 ${side} · 请求配置`} className='operation-compare-side-card'>
      <Space wrap className='operation-compare-side-toolbar'>
        <Select value={environmentId} placeholder={`环境 ${side}`} style={{ width: 170 }} options={environments.map((item) => ({ value: item.id, label: item.name, disabled: item.id === (isA ? environmentBId : environmentAId) }))} onChange={(value) => { setEnvironment(value); setResult(undefined); }} />
        <Select value={target?.key} placeholder='字段定义' style={{ width: 190 }} options={targets.map((item) => ({ value: item.key, label: item.label }))} onChange={(key) => { const next = targets.find((item) => item.key === key); if (transactionId && next) void loadSide(side, transactionId, next); }} />
        <Button icon={<ReloadOutlined />} disabled={!transactionId || !target || fieldLoading} onClick={() => transactionId && target && void loadSide(side, transactionId, target)}>重新加载</Button>
      </Space>
      <TestCaseControls transactionId={transactionId} target={target} targets={targets}
        getPayload={() => normalizeFormValues(form.getFieldsValue(true)) as Record<string, unknown>}
        onSelectionChange={isA ? setTestCaseIdA : setTestCaseIdB}
        onApply={(payload) => { const hydrated = hydrateFormValues(tree, payload); form.setFieldsValue(hydrated as Parameters<typeof form.setFieldsValue>[0]); if (isA) setValuesA(normalizeFormValues(hydrated) as Record<string, unknown>); else setValuesB(normalizeFormValues(hydrated) as Record<string, unknown>); setResult(undefined); }} />
      <Spin spinning={fieldLoading} tip={`正在加载环境 ${side} 请求字段`}>
        <Typography.Title level={5}>业务参数</Typography.Title>
        <Form form={form} layout='vertical' className='operation-compare-side-form' onValuesChange={() => { const normalized = normalizeFormValues(form.getFieldsValue(true)) as Record<string, unknown>; if (isA) setValuesA(normalized); else setValuesB(normalized); setResult(undefined); }}>
          {!transactionId ? <Empty description='请选择交易' /> : !tree.length ? <Empty description='该字段定义没有请求字段' /> : tree.map((field) => <RequestField key={field.id} field={field} />)}
        </Form>
      </Spin>
      <Typography.Title level={5}>请求报文</Typography.Title>
      <CodeViewer value={requestXml} language='xml' filename={`comparison-${side.toLowerCase()}-request.xml`} height={300} />
    </Card>;
  };

  return <div className='page-fill operation-compare-page'>
    <div className='page-head'><div><div className='page-kicker'>TRANSACTION OPERATION</div><h1 className='page-title'>多环境响应对比</h1></div></div>
    <Alert className='operation-compare-card' type='info' showIcon message='外网模拟模式' description='两侧可以选择不同字段版本和测试案例；当前响应仍为虚构演示数据，不会调用交易容器。' />
    <Card className='operation-compare-card' title='对比配置'>
      <Space wrap className='operation-compare-toolbar'>
        <Select value={transactionId} showSearch optionFilterProp='label' placeholder='请选择交易' style={{ width: 360 }} options={transactions.map((item) => ({ value: item.id, label: `${item.transactionCode} - ${item.transactionName}` }))} onChange={(value) => void selectTransaction(value)} />
        <Select value={soapVersion} style={{ width: 120 }} options={[{ value: '1.1', label: 'SOAP 1.1' }, { value: '1.2', label: 'SOAP 1.2' }]} onChange={(value) => { setSoapVersion(value); setResult(undefined); }} />
        <Button type='primary' icon={<ApartmentOutlined />} loading={comparing} disabled={!transactionId || fieldLoadingA || fieldLoadingB || comparing} onClick={() => void compare()}>开始对比</Button>
        {requestXmlA !== requestXmlB && <Tag color='warning'>两侧请求报文存在差异</Tag>}
      </Space>
      <div className='operation-compare-side-grid'>{renderSide('A')}{renderSide('B')}</div>
    </Card>
    <ComparisonResultDrawer open={resultDrawerOpen} loading={comparing} result={result} onlyDifferences={onlyDifferences} onOnlyDifferencesChange={setOnlyDifferences} onClose={() => setResultDrawerOpen(false)} />
  </div>;
}
