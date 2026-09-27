import { useLayoutEffect, useMemo, useRef, useState } from 'react';
import { App, Form, Input, InputNumber, Modal, Radio, Select, Spin, TreeSelect } from 'antd';
import { createDept, getDept, getDeptFormOptions, updateDept } from '../../../api/dept';
import type { Dept, DeptFormOptions, DeptRequest } from '../../../types/dept';
import { getApiErrorMessage } from '../../../utils/apiError';
import { FieldLabel } from '../../../components/FieldLabel';

interface DeptDialogProps {
  open: boolean;
  deptId: number | null;
  allDepts: Dept[];
  onClose: () => void;
  onSaved: () => void;
}
interface DeptTreeNode { value: number; title: string; disabled?: boolean; children?: DeptTreeNode[] }
function toTree(depts: Dept[], parentId = 0, blocked: Set<number>): DeptTreeNode[] {
  return depts.filter((dept) => dept.parentId === parentId).map((dept) => ({
    value: dept.id,
    title: dept.deptName,
    // 只读祖先部门保留显示以维持上下文，但不能被选为新的上级部门。
    disabled: blocked.has(dept.id) || !dept.canCreateChildren,
    children: toTree(depts, dept.id, blocked),
  }));
}
function blockedIds(depts: Dept[], id?: number) {
  const blocked = new Set<number>();
  if (!id) return blocked;
  const collect = (parentId: number) => depts.filter((dept) => dept.parentId === parentId).forEach((dept) => { blocked.add(dept.id); collect(dept.id); });
  blocked.add(id); collect(id);
  return blocked;
}

export function DeptDialog({ open, deptId, allDepts, onClose, onSaved }: DeptDialogProps) {
  const [form] = Form.useForm<DeptRequest>();
  const { message } = App.useApp();
  const [options, setOptions] = useState<DeptFormOptions>();
  const [dept, setDept] = useState<Dept>();
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);
  const editing = deptId !== null;
  const parentTree = useMemo(() => toTree(allDepts, 0, blockedIds(allDepts, dept?.id)), [allDepts, dept?.id]);
  /**
   * 「上级部门」能不能清空（清空 = 建顶级部门）由后端决定，不能在这里推：
   * 一个数据范围是"本部门及下级"、本人又恰好在根部门的账号，"能管这个部门"和
   * "有可建的父部门"都是 true，但后端对 parentId<=0 一律拒绝 —— 自己推就会推出一个
   * 点了必然 403 的入口。candidates 加载完成前按"不可以"处理，避免先给出可点状态再收回。
   */
  const canCreateRoot = options?.canCreateRoot ?? false;
  const parentHint = canCreateRoot
    ? '留空即为顶级部门。已删除的部门不在候选里；不能选择自身或自身的下级。'
    : '必须选择一个你有权管理的上级部门：只有数据范围为"全部"的账号才能新建顶级部门。已删除的部门不在候选里；不能选择自身或自身的下级。';

  useLayoutEffect(() => {
    if (!open) return;
    let active = true;
    form.resetFields(); setDept(undefined); setOptions(undefined); setLoading(true);
    void Promise.all([deptId === null ? Promise.resolve(undefined) : getDept(deptId), getDeptFormOptions()])
      .then(([detail, loadedOptions]) => {
        if (!active) return;
        setDept(detail); setOptions(loadedOptions);
        form.setFieldsValue(detail
          ? { parentId: detail.parentId || undefined, deptName: detail.deptName, deptCode: detail.deptCode, leaderUserId: detail.leaderUserId, sortOrder: detail.sortOrder, status: detail.status }
          : { parentId: allDepts.find((value) => value.canCreateChildren)?.id, deptName: '', deptCode: '', leaderUserId: undefined, sortOrder: 0, status: 1 });
      })
      .catch((error) => { if (active) { message.error(getApiErrorMessage(error, editing ? '无法加载部门详情' : '无法加载部门表单数据')); onClose(); } })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [allDepts, deptId, editing, form, message, open]);

  /**
   * 防重复提交：ref 同步挡住同一轮渲染内的第二次调用，state 负责按钮加载效果。
   */
  const submit = async () => {
    if (submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);
    try {
      const values = await form.validateFields();
      const response = editing ? await updateDept(deptId!, values) : await createDept(values);
      message.success(response.message || (editing ? '修改部门成功' : '新增部门成功'));
      onSaved(); onClose();
    } catch (error) {
      if ((error as { errorFields?: unknown }).errorFields) return;
      message.error(getApiErrorMessage(error, '保存部门失败'));
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  };

  return (
    <Modal
      className='system-dialog'
      open={open}
      width={600}
      title={editing ? '编辑部门' : '新增部门'}
      okText='确定'
      cancelText='取消'
      onCancel={onClose}
      onOk={() => void submit()}
      confirmLoading={submitting}
      okButtonProps={{ disabled: loading }}
      cancelButtonProps={{ disabled: submitting || loading }}
      closable={!submitting && !loading}
      maskClosable={!submitting && !loading}
      keyboard={!submitting && !loading}
      destroyOnHidden
      forceRender
    >
      {loading ? (
        <div className='dialog-loading'>
          <Spin />
          <span>正在加载表单数据...</span>
        </div>
      ) : (
        <Form form={form} layout='horizontal' labelCol={{ flex: '0 0 96px' }} colon={false} labelWrap requiredMark={false}>
          <Form.Item name='deptName' label='部门名称' rules={[{ required: true, message: '请输入部门名称' }, { max: 80, message: '部门名称最长 80 个字符' }]}>
            <Input placeholder='例如：运营部' />
          </Form.Item>
          <Form.Item
            name='deptCode'
            label={<FieldLabel text='部门编码' hint='部门的唯一英文编码，以字母开头，可使用数字、下划线或短横线；删除后该编码仍会被历史记录占用。' />}
            rules={[{ required: true, message: '请输入部门编码' }, { max: 80, message: '部门编码最长 80 个字符' }, { pattern: /^[A-Za-z][A-Za-z0-9_-]*$/, message: '以字母开头，可使用数字、下划线或短横线' }]}
          >
            <Input placeholder='例如：OPERATIONS' />
          </Form.Item>
          <Form.Item name='parentId' label={<FieldLabel text='上级部门' hint={parentHint} />}>
            <TreeSelect showSearch treeNodeFilterProp='title' allowClear={canCreateRoot} treeDefaultExpandAll treeData={parentTree} placeholder={canCreateRoot ? '顶级部门' : '请选择或搜索可管理的上级部门'} />
          </Form.Item>
          <Form.Item name='leaderUserId' label={<FieldLabel text='负责人' hint='该部门审批部门变更申请的负责人，未配置时只能由超级管理员审批' />}>
            <Select allowClear showSearch optionFilterProp='label' options={options?.leaders} placeholder='请选择负责人（部门变更审批人）' />
          </Form.Item>
          <Form.Item name='sortOrder' label={<FieldLabel text='显示排序' hint='数字越小越靠前。只能填 0 及以上的整数。' />} rules={[{ required: true, message: '请输入排序值' }]}>
            <InputNumber min={0} step={1} precision={0} style={{ width: '100%' }} placeholder='例如：10' />
          </Form.Item>
          {/*
            「停用」的语义（产品决定，别再按"级联下线"去理解）：
            停用只影响"能不能再往里放人"，不影响已经在里面的人。
          */}
          <Form.Item
            name='status'
            label={<FieldLabel text='状态' hint='停用后：该部门不再出现在任何"选择部门"的候选里，也不能再往里新增或调整用户；但已经在该部门下的用户、以及他们现有的数据权限都不受影响 —— 数据权限由角色的数据范围决定，与部门状态无关。' />}
            rules={[{ required: true }]}
          >
            <Radio.Group disabled={dept?.builtin === 1}>
              <Radio value={1}>启用</Radio>
              <Radio value={0}>停用</Radio>
            </Radio.Group>
          </Form.Item>
        </Form>
      )}
    </Modal>
  );
}
