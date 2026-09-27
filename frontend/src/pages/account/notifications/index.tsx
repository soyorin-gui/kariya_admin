import { useEffect, useState } from 'react';
import { App, Button, Card, Empty, Input, List, Modal, Space, Steps, Tag, Typography } from 'antd';
import { BellOutlined, CheckOutlined } from '@ant-design/icons';
import { useSearchParams } from 'react-router-dom';
import type { SystemNotification } from '../../../api/notifications';
import { approveDepartmentChange, getDepartmentChange, rejectDepartmentChange, type DepartmentChangeDetail } from '../../../api/departmentChange';
import { useAppDispatch, useAppSelector } from '../../../store/hooks';
import { loadNotifications, readAllNotifications, readNotification } from '../../../store/notificationSlice';
import { getApiErrorMessage } from '../../../utils/apiError';
import '../../system/shared.css';

export default function NotificationsPage() {
  const { message } = App.useApp();
  const dispatch = useAppDispatch();
  /**
   * 列表与加载态都来自全局 store（见 store/notificationSlice），与顶栏铃铛共用同一份数据：
   * 在这里"标记已读/全部已读"之后，顶栏的红点与下拉列表会立刻同步，不需要手动通知它。
   * 改动前这里是页面自己的 useState，于是消息中心读完了、顶栏红点还在。
   */
  const items = useAppSelector((state) => state.notifications.items);
  const loading = useAppSelector((state) => state.notifications.loading);
  const [detail, setDetail] = useState<DepartmentChangeDetail>();
  const [rejectOpen, setRejectOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [searchParams] = useSearchParams();
  const load = () => dispatch(loadNotifications());
  useEffect(() => {
    void load();
  }, [dispatch]);
  useEffect(() => {
    const businessId = Number(searchParams.get('businessId'));
    if (Number.isFinite(businessId) && businessId > 0)
      void getDepartmentChange(businessId)
        .then(setDetail)
        .catch(() => undefined);
  }, [searchParams]);
  const open = async (item: SystemNotification) => {
    try {
      // unwrap()：标记已读失败要走进 catch 给出提示（改动前也是这个行为），
      // 而 thunk 本身不会抛异常，不 unwrap 就会静默吞掉。
      if (!item.readTime) await dispatch(readNotification(item.id)).unwrap();
      if (item.businessType === 'DEPARTMENT_CHANGE' && item.businessId) setDetail(await getDepartmentChange(item.businessId));
    } catch (error) {
      message.error(getApiErrorMessage(error, '无法打开消息'));
    }
  };
  const readAll = async () => {
    await dispatch(readAllNotifications());
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
    if (!detail || !reason.trim()) {
      message.warning('请填写拒绝原因');
      return;
    }
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
  return (
    <div>
      <div className='page-head'>
        <div>
          <div className='page-kicker'>NOTIFICATIONS</div>
          <Typography.Title level={2} className='page-title'>
            消息中心
          </Typography.Title>
        </div>
        <Button icon={<CheckOutlined />} onClick={() => void readAll()}>
          全部标为已读
        </Button>
      </div>
      <Card className='table-card'>
        <List
          loading={loading}
          dataSource={items}
          locale={{ emptyText: <Empty description='暂无消息' /> }}
          renderItem={(item) => (
            <List.Item onClick={() => void open(item)} style={{ cursor: 'pointer', background: item.readTime ? undefined : 'rgba(22,119,255,.035)', paddingInline: 16 }}>
              <List.Item.Meta
                avatar={<BellOutlined style={{ color: item.readTime ? '#aeb8c8' : 'var(--brand)', fontSize: 20 }} />}
                title={
                  <Space>
                    {item.title}
                    {!item.readTime && <Tag color='blue'>未读</Tag>}
                  </Space>
                }
                description={
                  <>
                    <div>{item.content}</div>
                    <small>{item.createdTime?.replace('T', ' ')}</small>
                  </>
                }
              />
            </List.Item>
          )}
        />
      </Card>
      <Modal
        width={720}
        open={Boolean(detail)}
        title='部门变更审批进度'
        footer={
          detail?.canApprove
            ? [
                <Button key='reject' danger onClick={() => setRejectOpen(true)}>
                  拒绝
                </Button>,
                <Button key='approve' type='primary' loading={submitting} onClick={() => void approve()}>
                  通过当前步骤
                </Button>,
              ]
            : null
        }
        onCancel={() => setDetail(undefined)}
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
