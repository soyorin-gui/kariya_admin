import { Button, Card, DatePicker, Form, Input, InputNumber, Space, Switch, Tag, Typography } from 'antd';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { Transaction, TransactionMessageField } from '../../transaction/types';
import './requestForm.css';

export type FieldNode = TransactionMessageField & { children: FieldNode[] };
type FormPath = Array<string | number>;

export function toFieldTree(rows: TransactionMessageField[]): FieldNode[] {
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

export function hydrateFormValues(tree: FieldNode[], payload: Record<string, unknown>): Record<string, unknown> {
  const hydrate = (field: FieldNode, value: unknown): unknown => {
    if (value === undefined || value === null) return value;
    if (field.nodeType === 'ARRAY' && Array.isArray(value)) {
      return value.map((item) => field.children.length
        ? Object.fromEntries(field.children.map((child) => [child.fieldNameEn, hydrate(child, (item as Record<string, unknown>)?.[child.fieldNameEn])]))
        : hydrate({ ...field, nodeType: 'FIELD' }, item));
    }
    if (field.nodeType === 'OBJECT' && typeof value === 'object') {
      return Object.fromEntries(field.children.map((child) => [child.fieldNameEn, hydrate(child, (value as Record<string, unknown>)[child.fieldNameEn])]));
    }
    return isDate(field.dataType) && typeof value === 'string' ? dayjs(value) : value;
  };
  return Object.fromEntries(tree.map((field) => [field.fieldNameEn, hydrate(field, payload[field.fieldNameEn])]));
}

export function normalizeFormValues(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(normalizeFormValues);
  if (value && typeof value === 'object') {
    if ('format' in value && typeof value.format === 'function') return value.format('YYYY-MM-DD HH:mm:ss');
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, normalizeFormValues(item)]));
  }
  return value;
}

function xmlEscape(value: unknown): string {
  return String(normalizeFormValues(value) ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&apos;');
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

export function buildSoapEnvelope(transaction: Transaction | undefined, tree: FieldNode[], values: Record<string, unknown>, soapVersion: '1.1' | '1.2'): string {
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

export function RequestField({ field, path = [] }: { field: FieldNode; path?: FormPath }) {
  const nextPath = [...path, field.fieldNameEn];
  if (field.nodeType === 'OBJECT') return <Card size='small' title={fieldLabel(field)} className='operation-request-field-group'>{field.children.map((child) => <RequestField key={child.id} field={child} path={nextPath} />)}</Card>;
  if (field.nodeType === 'ARRAY') return (
    <Card size='small' title={fieldLabel(field)} className='operation-request-field-group'>
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
