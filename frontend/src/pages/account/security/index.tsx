import { useEffect, useMemo, useState } from 'react';
import { App, Button, Card, Divider, Form, Input, List, Popconfirm, Result, Select, Space, Spin, Steps, Tag, Typography } from 'antd';
import { GithubOutlined, GoogleOutlined, LinkOutlined, SafetyCertificateOutlined, WechatOutlined } from '@ant-design/icons';
import { getExternalProviders } from '../../../api/auth';
import {
  getContactProfile,
  getExternalIdentities,
  getMySessions,
  removeMyOtherSessions,
  removeMySession,
  startExternalBinding,
  unbindExternalIdentity,
  updateContactProfile,
  type ExternalIdentity,
  type LoginSessionView,
} from '../../../api/account';
import type { ExternalProvider } from '../../../types/auth';
import { getApiErrorMessage } from '../../../utils/apiError';
import { cancelDepartmentChange, getDepartmentOptions, getDepartmentProfile, submitDepartmentChange, type DepartmentProfile } from '../../../api/departmentChange';
import '../../system/shared.css';
import './security.css';

const icons = { github: <GithubOutlined />, google: <GoogleOutlined />, wechat: <WechatOutlined />, enterprise: <SafetyCertificateOutlined /> };

export default function AccountSecurityPage() {
  const { message } = App.useApp();
  const [providers, setProviders] = useState<ExternalProvider[]>([]);
  const [identities, setIdentities] = useState<ExternalIdentity[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string>();
  const [sessions, setSessions] = useState<LoginSessionView[]>([]);
  const [savingContact, setSavingContact] = useState(false);
  const [contactForm] = Form.useForm<{ username: string; realName: string; phone?: string; email?: string }>();
  const [departmentForm] = Form.useForm<{ targetDeptId: number; reason: string }>();
  const [departmentProfile, setDepartmentProfile] = useState<DepartmentProfile>();
  const [departmentOptions, setDepartmentOptions] = useState<Array<{ id: number; name: string }>>([]);
  const [submittingDepartment, setSubmittingDepartment] = useState(false);
  const bindings = useMemo(() => new Map(identities.map((item) => [item.providerKey, item])), [identities]);
  const load = async () => {
    try {
      setLoading(true);
      setLoadError(undefined);
      const [available, current, contact, activeSessions, deptProfile, deptOptions] = await Promise.all([
        getExternalProviders(),
        getExternalIdentities(),
        getContactProfile(),
        getMySessions(),
        getDepartmentProfile(),
        getDepartmentOptions(),
      ]);
      setProviders(available);
      setIdentities(current);
      setSessions(activeSessions);
      setDepartmentProfile(deptProfile);
      setDepartmentOptions(deptOptions);
      contactForm.setFieldsValue({ username: contact.username, realName: contact.realName, phone: contact.phone, email: contact.email });
    } catch (error) {
      const errorMessage = getApiErrorMessage(error, '无法加载账户安全信息');
      setLoadError(errorMessage);
      message.error(errorMessage);
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    void load();
  }, []);
  const saveContact = async () => {
    try {
      const values = await contactForm.validateFields();
      setSavingContact(true);
      const response = await updateContactProfile({ phone: values.phone, email: values.email });
      message.success(response.message || '联系方式已更新');
    } catch (error) {
      if (!(error as { errorFields?: unknown }).errorFields) message.error(getApiErrorMessage(error, '联系方式保存失败'));
    } finally {
      setSavingContact(false);
    }
  };
  const removeSession = async (id: string) => {
    try {
      await removeMySession(id);
      message.success('该设备已退出登录');
      setSessions(await getMySessions());
    } catch (error) {
      message.error(getApiErrorMessage(error, '退出设备失败'));
    }
  };
  const submitDepartment = async () => {
    try {
      const values = await departmentForm.validateFields();
      setSubmittingDepartment(true);
      const result = await submitDepartmentChange(values);
      message.success(result.message || '部门变更申请已提交');
      setDepartmentProfile(await getDepartmentProfile());
      departmentForm.resetFields();
    } catch (error) {
      if (!(error as { errorFields?: unknown }).errorFields) message.error(getApiErrorMessage(error, '提交部门申请失败'));
    } finally {
      setSubmittingDepartment(false);
    }
  };
  const cancelDepartment = async () => {
    if (!departmentProfile?.activeRequest) return;
    try {
      await cancelDepartmentChange(departmentProfile.activeRequest.id);
      message.success('申请已撤销');
      setDepartmentProfile(await getDepartmentProfile());
    } catch (error) {
      message.error(getApiErrorMessage(error, '撤销申请失败'));
    }
  };
  const removeOthers = async () => {
    try {
      await removeMyOtherSessions();
      message.success('其他设备已退出登录');
      setSessions(await getMySessions());
    } catch (error) {
      message.error(getApiErrorMessage(error, '退出其他设备失败'));
    }
  };
  const bind = async (provider: string) => {
    try {
      window.location.assign(await startExternalBinding(provider));
    } catch (error) {
      message.error(getApiErrorMessage(error, '无法发起账号绑定'));
    }
  };
  const unbind = async (provider: string) => {
    try {
      await unbindExternalIdentity(provider);
      message.success('已解除绑定');
      await load();
    } catch (error) {
      message.error(getApiErrorMessage(error, '解除绑定失败'));
    }
  };
  return (
    <div className='account-security-page'>
      <div className='page-head'>
        <div>
          <div className='page-kicker'>ACCOUNT SECURITY</div>
          <Typography.Title level={2} className='page-title'>
            登录与安全
          </Typography.Title>
        </div>
      </div>
      <Card className='table-card identity-card account-security-card'>
        {loading ? (
          <div className='account-security-loading'>
            <Spin size='large' />
          </div>
        ) : loadError ? (
          <Result
            status='error'
            title='账户安全信息加载失败'
            subTitle={loadError}
            extra={
              <Button type='primary' onClick={() => void load()}>
                重新加载
              </Button>
            }
          />
        ) : (
          <>
            <Divider className='account-section-divider' orientation='left' orientationMargin={4}>
              联系方式
            </Divider>
            <section className='account-section'>
              <Typography.Paragraph type='secondary'>姓名和登录账号不可在此修改；手机号、邮箱仅用于联系信息。</Typography.Paragraph>
              <Form form={contactForm} layout='vertical' requiredMark={false}>
                <Form.Item name='username' label='登录账号'>
                  <Input disabled />
                </Form.Item>
                <Form.Item name='realName' label='姓名'>
                  <Input disabled />
                </Form.Item>
                <Form.Item name='phone' label='手机号' rules={[{ pattern: /^\+?[0-9 ()-]{5,32}$/, message: '手机号格式不正确' }]}>
                  <Input placeholder='可选' />
                </Form.Item>
                <Form.Item name='email' label='邮箱' rules={[{ type: 'email', message: '邮箱格式不正确' }, { max: 128 }]}>
                  <Input placeholder='可选' />
                </Form.Item>
                <Button type='primary' loading={savingContact} onClick={() => void saveContact()}>
                  保存联系方式
                </Button>
              </Form>
            </section>

            <Divider className='account-section-divider' orientation='left' orientationMargin={0}>
              所属部门
            </Divider>
            <section className='account-section'>
              <Typography.Paragraph type='secondary'>当前部门：{departmentProfile?.currentDeptName || '尚未分配部门'}。变更需按部门负责人审批流程生效。</Typography.Paragraph>
              {departmentProfile?.activeRequest ? (
                <>
                  <Steps
                    size='small'
                    current={Math.max(0, departmentProfile.activeRequest.currentStep - 1)}
                    status={departmentProfile.activeRequest.status === 'REJECTED' ? 'error' : 'process'}
                    items={departmentProfile.activeRequest.steps.map((step) => ({
                      title: step.type === 'SOURCE' ? '原部门审批' : '目标部门审批',
                      description:
                        step.status === 'SKIPPED'
                          ? '无需审批'
                          : step.status === 'APPROVED'
                            ? `${step.decidedByName || '管理员'}已通过`
                            : step.status === 'REJECTED'
                              ? `已拒绝：${step.decisionReason || ''}`
                              : step.status === 'WAITING'
                                ? '等待上一阶段'
                                : `等待${step.assignedUserName || '超级管理员'}处理`,
                      status: step.status === 'APPROVED' || step.status === 'SKIPPED' ? 'finish' : step.status === 'REJECTED' ? 'error' : step.status === 'PENDING' ? 'process' : 'wait',
                    }))}
                  />
                  <Space style={{ marginTop: 18 }}>
                    <Tag color='processing'>审批中</Tag>
                    <span>
                      {departmentProfile.activeRequest.fromDeptName} → {departmentProfile.activeRequest.targetDeptName}
                    </span>
                    <Popconfirm title='确认撤销本次申请？' onConfirm={() => void cancelDepartment()}>
                      <Button danger type='link'>
                        撤销申请
                      </Button>
                    </Popconfirm>
                  </Space>
                </>
              ) : (
                <Form form={departmentForm} layout='vertical' requiredMark={false}>
                  <Form.Item name='targetDeptId' label='申请加入部门' rules={[{ required: true, message: '请选择目标部门' }]}>
                    <Select placeholder='请选择部门' options={departmentOptions.filter((item) => item.id !== departmentProfile?.currentDeptId).map((item) => ({ value: item.id, label: item.name }))} />
                  </Form.Item>
                  <Form.Item
                    name='reason'
                    label='申请说明'
                    rules={[
                      { required: true, message: '请填写申请说明' },
                      { max: 500, message: '最多 500 个字符' },
                    ]}
                  >
                    <Input.TextArea rows={3} maxLength={500} showCount />
                  </Form.Item>
                  <Button type='primary' loading={submittingDepartment} onClick={() => void submitDepartment()}>
                    提交部门变更申请
                  </Button>
                </Form>
              )}
            </section>

            <Divider className='account-section-divider' orientation='left' orientationMargin={0}>
              已验证的登录方式
            </Divider>
            <section className='account-section'>
              <Typography.Paragraph type='secondary'>联系方式和登录身份彼此独立。只有完成平台认证并显示“已绑定”的身份，才能用于登录。</Typography.Paragraph>
              <List
                dataSource={providers}
                renderItem={(provider) => {
                  const bound = bindings.get(provider.key);
                  return (
                    <List.Item
                      actions={
                        bound
                          ? [
                              <Popconfirm key='unbind' title='确认解除绑定？' description='系统会检查解绑后是否还保留其他可用登录方式。' onConfirm={() => void unbind(provider.key)}>
                                <Button danger type='link'>
                                  解除绑定
                                </Button>
                              </Popconfirm>,
                            ]
                          : [
                              <Button key='bind' type='primary' ghost icon={<LinkOutlined />} disabled={!provider.enabled} onClick={() => void bind(provider.key)}>
                                {provider.enabled ? '立即绑定' : '暂未启用'}
                              </Button>,
                            ]
                      }
                    >
                      <List.Item.Meta
                        avatar={<div className={`identity-icon identity-${provider.icon}`}>{icons[provider.icon as keyof typeof icons] ?? <LinkOutlined />}</div>}
                        title={
                          <Space>
                            {provider.displayName}
                            {bound ? <Tag color='success'>已绑定</Tag> : <Tag>未绑定</Tag>}
                          </Space>
                        }
                        description={bound ? [bound.displayName, bound.email].filter(Boolean).join(' · ') || '身份已验证' : `绑定后可使用 ${provider.displayName} 直接登录`}
                      />
                    </List.Item>
                  );
                }}
              />
            </section>

            <Divider className='account-section-divider' orientation='left' orientationMargin={0}>
              登录设备
            </Divider>
            <section className='account-section'>
              <Space className='account-section-head'>
                <Typography.Paragraph type='secondary'>可以让不再使用的浏览器或设备立即退出登录。</Typography.Paragraph>
                <Popconfirm title='退出其他全部设备？' onConfirm={() => void removeOthers()}>
                  <Button danger>退出其他设备</Button>
                </Popconfirm>
              </Space>
              <List
                dataSource={sessions}
                locale={{ emptyText: '暂无登录会话' }}
                renderItem={(session) => (
                  <List.Item
                    actions={
                      session.current
                        ? [
                            <Tag key='current' color='blue'>
                              当前会话
                            </Tag>,
                          ]
                        : [
                            <Popconfirm key='remove' title='让该设备退出登录？' onConfirm={() => void removeSession(session.id)}>
                              <Button danger type='link'>
                                退出
                              </Button>
                            </Popconfirm>,
                          ]
                    }
                  >
                    <List.Item.Meta
                      title={
                        <Space>
                          {session.authMethod === 'PASSWORD' ? '账号密码' : session.providerKey || '第三方登录'}
                          {session.rememberMe && <Tag>记住我</Tag>}
                        </Space>
                      }
                      description={[session.loginIp || '未知地址', session.lastActiveTime?.replace('T', ' ') || '未知时间', session.userAgent || '未知设备'].join(' · ')}
                    />
                  </List.Item>
                )}
              />
            </section>
          </>
        )}
      </Card>
    </div>
  );
}
