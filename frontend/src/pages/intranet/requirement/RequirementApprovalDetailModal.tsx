import { useEffect, useState } from 'react';
import { App, Button, Descriptions, Input, Modal, Popconfirm, Spin, Tag, Typography } from 'antd';
import { CheckOutlined, StopOutlined } from '@ant-design/icons';
import { approveRequirement, getNewTransactionDetail, getRequirement, getTransactionCallDetail, getTransactionChangeDetail, rejectRequirement } from './api';
import type { NewTransactionDetail, Requirement, TransactionCallDetail, TransactionChangeDetail } from './types';
import { getApiErrorMessage } from '../../../utils/apiError';

const typeText: Record<number, string> = { 1: '新增交易', 2: '交易变更', 3: '交易调用' };
const statusText: Record<number, { label: string; color: string }> = {
  0: { label: '草稿', color: 'default' },
  1: { label: '审批中', color: 'processing' },
  2: { label: '已通过', color: 'success' },
  3: { label: '已拒绝', color: 'error' },
  4: { label: '已撤销', color: 'default' },
};

const text = (value?: string | number) => value === undefined || value === null || value === '' ? '-' : String(value);
const yesNo = (value?: number) => value === 1 ? '是' : value === 0 ? '否' : '-';
const queryBehavior = (value?: string) => value === 'INTERNAL' ? '对客' : value === 'EXTERNAL' ? '非对客' : text(value);

export function RequirementApprovalDetailModal({
  id,
  canApprove,
  onClose,
  onCompleted,
}: {
  id?: number;
  canApprove: boolean;
  onClose: () => void;
  onCompleted: () => void;
}) {
  const { message } = App.useApp();
  const [requirement, setRequirement] = useState<Requirement>();
  const [newTransactionDetail, setNewTransactionDetail] = useState<NewTransactionDetail>();
  const [transactionCallDetail, setTransactionCallDetail] = useState<TransactionCallDetail>();
  const [transactionChangeDetail, setTransactionChangeDetail] = useState<TransactionChangeDetail>();
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [reason, setReason] = useState('');

  useEffect(() => {
    if (!id) {
      setRequirement(undefined);
      setNewTransactionDetail(undefined);
      setTransactionCallDetail(undefined);
      setTransactionChangeDetail(undefined);
      return;
    }
    let active = true;
    setLoading(true);
    void (async () => {
      try {
        const value = await getRequirement(id);
        const newDetail = value.requirementType === 1 ? await getNewTransactionDetail(id) : undefined;
        const changeDetail = value.requirementType === 2 ? await getTransactionChangeDetail(id) : undefined;
        const callDetail = value.requirementType === 3 ? await getTransactionCallDetail(id) : undefined;
        if (active) {
          setRequirement(value);
          setNewTransactionDetail(newDetail);
          setTransactionChangeDetail(changeDetail);
          setTransactionCallDetail(callDetail);
        }
      } catch (error) {
        message.error(getApiErrorMessage(error, '无法加载需求审批详情'));
        onClose();
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => { active = false; };
  }, [id, message, onClose]);

  const approve = async () => {
    if (!id) return;
    try {
      setSubmitting(true);
      await approveRequirement(id);
      message.success('需求审批已通过');
      onCompleted();
      onClose();
    } catch (error) {
      message.error(getApiErrorMessage(error, '审批失败'));
    } finally {
      setSubmitting(false);
    }
  };

  const reject = async () => {
    if (!id || !reason.trim()) {
      message.warning('请填写拒绝原因');
      return;
    }
    try {
      setSubmitting(true);
      await rejectRequirement(id, reason.trim());
      message.success('需求已拒绝');
      setRejectOpen(false);
      setReason('');
      onCompleted();
      onClose();
    } catch (error) {
      message.error(getApiErrorMessage(error, '拒绝需求失败'));
    } finally {
      setSubmitting(false);
    }
  };

  const status = requirement ? statusText[requirement.status] : undefined;
  return (
    <>
      <Modal
        width={820}
        open={Boolean(id)}
        title={requirement ? `需求审批详情 · ${requirement.title}` : '需求审批详情'}
        onCancel={onClose}
        footer={canApprove && requirement?.status === 1 ? [
          <Button key='reject' danger icon={<StopOutlined />} onClick={() => setRejectOpen(true)}>拒绝</Button>,
          <Popconfirm key='approve' title='确定通过该需求？' onConfirm={() => void approve()}>
            <Button type='primary' icon={<CheckOutlined />} loading={submitting}>通过</Button>
          </Popconfirm>,
        ] : null}
      >
        {loading || !requirement ? <div className='dialog-loading'><Spin /></div> : (
          <>
            <Typography.Title level={5}>需求概要</Typography.Title>
            <Descriptions bordered column={2} size='small'>
              <Descriptions.Item label='OA编号'>{requirement.oaNo}</Descriptions.Item>
              <Descriptions.Item label='需求类型'><Tag color='blue'>{typeText[requirement.requirementType]}</Tag></Descriptions.Item>
              <Descriptions.Item label='需求名称' span={2}>{requirement.title}</Descriptions.Item>
              <Descriptions.Item label='审批状态'><Tag color={status?.color}>{status?.label}</Tag></Descriptions.Item>
              <Descriptions.Item label='期望上线时间'>{text(requirement.expectedOnlineDate)}</Descriptions.Item>
              <Descriptions.Item label='需求描述' span={2}>{text(requirement.description)}</Descriptions.Item>
            </Descriptions>

            {requirement.requirementType === 1 && (
              <>
                <Typography.Title level={5} style={{ marginTop: 20 }}>新增交易明细</Typography.Title>
                <Descriptions bordered column={2} size='small'>
                  <Descriptions.Item label='成本业务联系人'>{text(newTransactionDetail?.costBusinessContact)}</Descriptions.Item>
                  <Descriptions.Item label='业务部门'>{text(newTransactionDetail?.businessDepartment)}</Descriptions.Item>
                  <Descriptions.Item label='OA联系单号'>{text(newTransactionDetail?.oaContactNo)}</Descriptions.Item>
                  <Descriptions.Item label='是否投产数据'>{yesNo(newTransactionDetail?.dataProjectedFlag)}</Descriptions.Item>
                  <Descriptions.Item label='数据来源系统'>{text(newTransactionDetail?.dataSourceSystem)}</Descriptions.Item>
                  <Descriptions.Item label='调用方系统'>{text(newTransactionDetail?.callingSystem)}</Descriptions.Item>
                  <Descriptions.Item label='调用方负责人'>{text(newTransactionDetail?.callingSystemOwner)}</Descriptions.Item>
                  <Descriptions.Item label='用户查询行为'>{queryBehavior(newTransactionDetail?.userQueryBehavior)}</Descriptions.Item>
                  <Descriptions.Item label='接口数据字段' span={2}>{text(newTransactionDetail?.interfaceDataFields)}</Descriptions.Item>
                  <Descriptions.Item label='三超详细描述' span={2}>{text(newTransactionDetail?.thirdCategoryDetail)}</Descriptions.Item>
                  <Descriptions.Item label='五敏详细描述' span={2}>{text(newTransactionDetail?.fiveCategoryDetail)}</Descriptions.Item>
                  <Descriptions.Item label='企架详细描述' span={2}>{text(newTransactionDetail?.architectureDetail)}</Descriptions.Item>
                  <Descriptions.Item label='交易服务时间'>{text(newTransactionDetail?.serviceWindow)}</Descriptions.Item>
                  <Descriptions.Item label='响应时间要求'>{text(newTransactionDetail?.responseTimeRequirement)}</Descriptions.Item>
                  <Descriptions.Item label='并发用户数'>{text(newTransactionDetail?.concurrentUserCount)}</Descriptions.Item>
                  <Descriptions.Item label='TPS峰值'>{text(newTransactionDetail?.tpsPeak)}</Descriptions.Item>
                  <Descriptions.Item label='TPS估算依据' span={2}>{text(newTransactionDetail?.tpsEstimationBasis)}</Descriptions.Item>
                  <Descriptions.Item label='历史查询范围'>{text(newTransactionDetail?.historyQueryRange)}</Descriptions.Item>
                  <Descriptions.Item label='单次查询跨度'>{text(newTransactionDetail?.singleQueryRange)}</Descriptions.Item>
                  <Descriptions.Item label='分页记录数'>{text(newTransactionDetail?.pageSize)}</Descriptions.Item>
                  <Descriptions.Item label='日交易量'>{text(newTransactionDetail?.dailyTransactionVolume)}</Descriptions.Item>
                  <Descriptions.Item label='是否生成文件'>{yesNo(newTransactionDetail?.generateFileFlag)}</Descriptions.Item>
                  <Descriptions.Item label='文件生成范围'>{text(newTransactionDetail?.fileGenerationScope)}</Descriptions.Item>
                  <Descriptions.Item label='是否涉及计算'>{yesNo(newTransactionDetail?.calculationFlag)}</Descriptions.Item>
                  <Descriptions.Item label='计算场景'>{text(newTransactionDetail?.calculationScenario)}</Descriptions.Item>
                  <Descriptions.Item label='备注' span={2}>{text(newTransactionDetail?.remarks)}</Descriptions.Item>
                </Descriptions>
              </>
            )}

            {requirement.requirementType === 2 && (
              <>
                <Typography.Title level={5} style={{ marginTop: 20 }}>交易变更明细</Typography.Title>
                <Descriptions bordered column={2} size='small'>
                  <Descriptions.Item label='交易ID' span={2}>{text(transactionChangeDetail?.transactionId)}</Descriptions.Item>
                  <Descriptions.Item label='ESF变更' span={2}>{text(transactionChangeDetail?.esfChange)}</Descriptions.Item>
                  <Descriptions.Item label='数据逻辑变更' span={2}>{text(transactionChangeDetail?.dataLogicChange)}</Descriptions.Item>
                  <Descriptions.Item label='交易逻辑变更' span={2}>{text(transactionChangeDetail?.transactionLogicChange)}</Descriptions.Item>
                  <Descriptions.Item label='备注' span={2}>{text(transactionChangeDetail?.remarks)}</Descriptions.Item>
                </Descriptions>
              </>
            )}

            {requirement.requirementType === 3 && (
              <>
                <Typography.Title level={5} style={{ marginTop: 20 }}>交易调用明细</Typography.Title>
                <Descriptions bordered column={2} size='small'>
                  <Descriptions.Item label='成本业务联系人'>{text(transactionCallDetail?.costBusinessContact)}</Descriptions.Item>
                  <Descriptions.Item label='业务部门'>{text(transactionCallDetail?.businessDepartment)}</Descriptions.Item>
                  <Descriptions.Item label='OA联系单号'>{text(transactionCallDetail?.oaContactNo)}</Descriptions.Item>
                  <Descriptions.Item label='交易码'>{text(transactionCallDetail?.transactionCode)}</Descriptions.Item>
                  <Descriptions.Item label='调用方系统'>{text(transactionCallDetail?.callingSystem)}</Descriptions.Item>
                  <Descriptions.Item label='调用方负责人'>{text(transactionCallDetail?.callingSystemOwner)}</Descriptions.Item>
                  <Descriptions.Item label='期望上线时间'>{text(transactionCallDetail?.expectedOnlineDate)}</Descriptions.Item>
                  <Descriptions.Item label='用户查询行为'>{queryBehavior(transactionCallDetail?.userQueryBehavior)}</Descriptions.Item>
                  <Descriptions.Item label='现有能力是否满足' span={2}>{yesNo(transactionCallDetail?.currentCapabilityMetFlag)}</Descriptions.Item>
                  <Descriptions.Item label='接口数据字段' span={2}>{text(transactionCallDetail?.interfaceDataFields)}</Descriptions.Item>
                  <Descriptions.Item label='三超详细描述' span={2}>{text(transactionCallDetail?.thirdCategoryDetail)}</Descriptions.Item>
                  <Descriptions.Item label='五敏详细描述' span={2}>{text(transactionCallDetail?.fiveCategoryDetail)}</Descriptions.Item>
                  <Descriptions.Item label='企架详细描述' span={2}>{text(transactionCallDetail?.architectureDetail)}</Descriptions.Item>
                  <Descriptions.Item label='备注' span={2}>{text(transactionCallDetail?.remarks)}</Descriptions.Item>
                </Descriptions>
              </>
            )}
          </>
        )}
      </Modal>
      <Modal
        title='拒绝内网需求'
        open={rejectOpen}
        confirmLoading={submitting}
        okButtonProps={{ danger: true }}
        okText='确认拒绝'
        onOk={() => void reject()}
        onCancel={() => { setRejectOpen(false); setReason(''); }}
      >
        <Input.TextArea value={reason} onChange={(event) => setReason(event.target.value)} rows={4} maxLength={500} showCount placeholder='请输入拒绝原因（必填）' />
      </Modal>
    </>
  );
}
