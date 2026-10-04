import { useCallback, useEffect, useState } from 'react';
import { App, Button, Empty, Input, Modal, Space, Steps, Table, Tabs, Tag, Typography } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { CheckOutlined, EyeOutlined, ReloadOutlined, StopOutlined } from '@ant-design/icons';
import { getApprovalTasks, type ApprovalScope, type ApprovalTask } from '../../../api/approvals';
import { approveDepartmentChange, getDepartmentChange, rejectDepartmentChange, type DepartmentChangeDetail } from '../../../api/departmentChange';
import { getApiErrorMessage } from '../../../utils/apiError';
import '../../system/shared.css';

const requestStatus: Record<string, { text: string; color: string }> = {
  PENDING_SOURCE: { text: '原部门审批中', color: 'processing' },
  PENDING_TARGET: { text: '目标部门审批中', color: 'processing' },
  APPROVED: { text: '已通过', color: 'success' },
  REJECTED: { text: '已拒绝', color: 'error' },
  CANCELLED: { text: '已撤销', color: 'default' },
};

export default function ApprovalCenterPage() {
  const { message } = App.useApp();
  const [scope, setScope] = useState<ApprovalScope>('pending');
  const [records, setRecords] = useState<ApprovalTask[]>([]);
  const [loading, setLoading] = useState(false);
  const [pageNum, setPageNum] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [detail, setDetail] = useState<DepartmentChangeDetail>();
  const [rejectOpen, setRejectOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      const page = await getApprovalTasks(scope, pageNum, pageSize);
      setRecords(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(getApiErrorMessage(error, '审批任务加载失败'));
    } finally {
      setLoading(false);
    }
  }, [message, pageNum, pageSize, scope]);

  useEffect(() => {
    void load();
  }, [load]);

  const openDetail = async (task: ApprovalTask) => {
    try {
      setDetail(await getDepartmentChange(task.requestId));
    } catch (error) {
      message.error(getApiErrorMessage(error, '审批详情加载失败'));
    }
  };
  const approve = async () => {
    if (!detail) return;
    try {
      setSubmitting(true);
      const result = await approveDepartmentChange(detail.id);
      setDetail(result.data);
      message.success(result.message);
      await load();
    } catch (error) {
      message.error(getApiErrorMessage(error, '审批失败'));
    } finally {
      setSubmitting(false);
    }
  };
  const reject = async () => {
    if (!detail || !reason.trim()) return message.warning('请填写拒绝原因');
    try {
      setSubmitting(true);
      const result = await rejectDepartmentChange(detail.id, reason.trim());
      setDetail(result.data);
      setRejectOpen(false);
      setReason('');
      message.success(result.message);
      await load();
    } catch (error) {
      message.error(getApiErrorMessage(error, '拒绝申请失败'));
    } finally {
      setSubmitting(false);
    }
  };

  const columns: ColumnsType<ApprovalTask> = [
    { title: '申请人', dataIndex: 'requesterName', width: 120 },
    {
      title: '部门变更',
      render: (_, row) => (
        <Space size={6}>
          <span>{row.fromDeptName}</span>
          <span>→</span>
          <strong>{row.targetDeptName}</strong>
        </Space>
      ),
    },
    { title: '申请说明', dataIndex: 'reason', ellipsis: true },
    {
      title: scope === 'processed' ? '处理结果' : '状态',
      width: 140,
      render: (_, row) => {
        if (scope === 'processed') return <Tag color={row.stepStatus === 'APPROVED' ? 'success' : 'error'}>{row.stepStatus === 'APPROVED' ? '已通过' : '已拒绝'}</Tag>;
        const status = requestStatus[row.requestStatus] ?? { text: row.requestStatus, color: 'default' };
        return <Tag color={status.color}>{status.text}</Tag>;
      },
    },
    { title: '当前处理人', dataIndex: 'assignedUserName', width: 140 },
    { title: '提交时间', dataIndex: 'createdTime', width: 180, render: (value: string) => value?.replace('T', ' ') },
    {
      title: '操作',
      width: 90,
      fixed: 'right',
      render: (_, row) => (
        <Button type='link' icon={<EyeOutlined />} onClick={() => void openDetail(row)}>
          详情
        </Button>
      ),
    },
  ];
  const pagination: TablePaginationConfig = {
    current: pageNum,
    pageSize,
    total,
    showSizeChanger: true,
    showTotal: (value) => `共 ${value} 条`,
    onChange: (page, size) => {
      setPageNum(page);
      setPageSize(size);
    },
  };

  return (
    <div>
      <div className='page-head'>
        <div>
          <div className='page-kicker'>APPROVALS</div>
          <Typography.Title level={2} className='page-title'>
            审批中心
          </Typography.Title>
        </div>
        <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void load()}>
          刷新
        </Button>
      </div>
      <div className='table-card'>
        <Tabs
          activeKey={scope}
          onChange={(key) => {
            setScope(key as ApprovalScope);
            setPageNum(1);
          }}
          items={[
            { key: 'pending', label: '待我审批' },
            { key: 'processed', label: '我已处理' },
            { key: 'mine', label: '我的申请' },
          ]}
        />
        <Table
          rowKey={(row) => `${row.requestId}-${row.stepType}-${row.decidedTime ?? ''}`}
          columns={columns}
          dataSource={records}
          loading={loading}
          pagination={pagination}
          locale={{ emptyText: <Empty description={scope === 'pending' ? '暂无待办审批' : '暂无记录'} /> }}
          scroll={{ x: 980 }}
        />
      </div>
      <Modal
        width={720}
        open={Boolean(detail)}
        title='部门变更审批详情'
        onCancel={() => setDetail(undefined)}
        footer={
          detail?.canApprove
            ? [
                <Button key='reject' danger icon={<StopOutlined />} onClick={() => setRejectOpen(true)}>
                  拒绝
                </Button>,
                <Button key='approve' type='primary' icon={<CheckOutlined />} loading={submitting} onClick={() => void approve()}>
                  通过当前步骤
                </Button>,
              ]
            : null
        }
      >
        {detail && (
          <>
            <Typography.Title level={4}>
              {detail.requesterName}：{detail.fromDeptName} → {detail.targetDeptName}
            </Typography.Title>
            <Typography.Paragraph type='secondary'>申请说明：{detail.reason}</Typography.Paragraph>
            <Steps
              direction='vertical'
              current={Math.max(0, detail.currentStep - 1)}
              items={detail.steps.map((step) => ({
                title: step.type === 'SOURCE' ? `原部门审批 · ${step.deptName || '无部门'}` : `目标部门审批 · ${step.deptName}`,
                description:
                  step.status === 'SKIPPED'
                    ? '无需审批'
                    : step.status === 'WAITING'
                      ? '等待上一阶段完成'
                      : step.status === 'PENDING'
                        ? `等待 ${step.assignedUserName || '超级管理员'} 审批`
                        : step.status === 'APPROVED'
                          ? `${step.decidedByName || '管理员'} 已通过`
                          : `${step.decidedByName || '管理员'} 已拒绝：${step.decisionReason || ''}`,
                status: step.status === 'APPROVED' || step.status === 'SKIPPED' ? 'finish' : step.status === 'REJECTED' ? 'error' : step.status === 'PENDING' ? 'process' : 'wait',
              }))}
            />
          </>
        )}
      </Modal>
      <Modal
        title='拒绝部门变更申请'
        open={rejectOpen}
        confirmLoading={submitting}
        okButtonProps={{ danger: true }}
        okText='确认拒绝'
        onOk={() => void reject()}
        onCancel={() => {
          setRejectOpen(false);
          setReason('');
        }}
      >
        <Input.TextArea value={reason} onChange={(event) => setReason(event.target.value)} rows={4} maxLength={500} showCount placeholder='请输入拒绝原因（必填）' />
      </Modal>
    </div>
  );
}
