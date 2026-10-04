import { useLayoutEffect, useMemo, useRef, useState } from 'react';
import { App, AutoComplete, Form, Input, InputNumber, Modal, Radio, Select, Spin, TreeSelect } from 'antd';
import { createMenu, getMenu, updateMenu } from '../../../api/menu';
import { availableComponents } from '../../../router/componentRegistry';
import { MENU_ICON_OPTIONS } from '../../../router/iconRegistry';
import type { MenuRequest, SystemMenu } from '../../../types/menu';
import { getApiErrorMessage } from '../../../utils/apiError';
import { FieldLabel } from '../../../components/FieldLabel';
import { IconPicker } from '../../../components/IconPicker/IconPicker';

interface MenuDialogProps {
  open: boolean;
  menuId: number | null;
  menus: SystemMenu[];
  onClose: () => void;
  onSaved: () => void;
}

const typeOptions = [{ value: 'DIR', label: '目录' }, { value: 'MENU', label: '菜单' }, { value: 'BUTTON', label: '按钮' }];
interface MenuTreeNode { value: number; title: string; disabled?: boolean; children?: MenuTreeNode[] }
const treeData = (menus: SystemMenu[], parentId = 0, disabledIds: Set<number>): MenuTreeNode[] => menus.filter((menu) => menu.parentId === parentId).map((menu) => ({ value: menu.id, title: menu.menuName, disabled: disabledIds.has(menu.id) || menu.menuType === 'BUTTON', children: treeData(menus, menu.id, disabledIds) }));
function blockedIds(menus: SystemMenu[], id?: number) {
  const blocked = new Set<number>();
  if (!id) return blocked;
  const collect = (parentId: number) => menus.filter((menu) => menu.parentId === parentId).forEach((menu) => { blocked.add(menu.id); collect(menu.id); });
  blocked.add(id); collect(id);
  return blocked;
}

export function MenuDialog({ open, menuId, menus, onClose, onSaved }: MenuDialogProps) {
  const [form] = Form.useForm<MenuRequest>();
  const { message } = App.useApp();
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(false);
  const [menu, setMenu] = useState<SystemMenu>();
  const [expandedParentKeys, setExpandedParentKeys] = useState<(string | number)[]>();
  const submittingRef = useRef(false);
  const editing = menuId !== null;
  const menuType = Form.useWatch('menuType', form);
  const parents = useMemo(() => treeData(menus, 0, blockedIds(menus, menu?.id)), [menu?.id, menus]);
  // 展开到可选的目录/菜单层；仅包含按钮的分支保持收起。
  const defaultParentKeys = useMemo(() => menus
    .filter((parent) => menus.some((child) => child.parentId === parent.id && child.menuType !== 'BUTTON'))
    .map((parent) => parent.id), [menus]);

  /**
   * 组件候选来自「当前前端产物里真实存在的页面文件」（构建期扫描 src/pages）。
   * 用 AutoComplete 而不是 Select：既能从候选里挑，也允许手写——手写一个不存在的值不会静默失败，
   * 页面会渲染出「组件不存在」诊断页并告诉开发者该建哪个文件。
   * 这里不缓存：availableComponents() 只是对构建期映射做一次 keys + sort，成本可忽略。
   */
  const componentOptions = availableComponents().map((value) => ({ value, label: value }));

  useLayoutEffect(() => {
    if (!open) return;
    let active = true;
    form.resetFields(); setMenu(undefined); setLoading(false);
    setExpandedParentKeys(undefined);
    if (menuId === null) {
      form.setFieldsValue({ parentId: undefined, menuName: '', menuType: 'MENU', routeName: '', routePath: '', component: '', permissionCode: '', icon: '', sortOrder: 0, visible: 1, status: 1, keepAlive: 0 });
      return;
    }
    setLoading(true);
    void getMenu(menuId).then((detail) => {
      if (!active) return;
      setMenu(detail);
      form.setFieldsValue({ parentId: detail.parentId || undefined, menuName: detail.menuName, menuType: detail.menuType, routeName: detail.routeName, routePath: detail.routePath, component: detail.component, permissionCode: detail.permissionCode, icon: detail.icon, sortOrder: detail.sortOrder, visible: detail.visible, status: detail.status, keepAlive: detail.keepAlive });
    }).catch((error) => {
      if (!active) return;
      message.error(getApiErrorMessage(error, '无法加载菜单详情')); onClose();
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [form, menuId, message, open]);

  /**
   * 防重复提交：ref 同步挡住同一轮渲染内的第二次调用，state 负责按钮加载效果。
   */
  const submit = async () => {
    if (submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);
    try {
      const values = await form.validateFields();
      const payload: MenuRequest = { ...values, keepAlive: values.keepAlive ?? menu?.keepAlive ?? 0 };
      const response = editing ? await updateMenu(menuId!, payload) : await createMenu(payload);
      message.success(response.message || (editing ? '修改菜单成功' : '新增菜单成功'));
      onSaved(); onClose();
    } catch (error) {
      if ((error as { errorFields?: unknown }).errorFields) return;
      message.error(getApiErrorMessage(error, '保存菜单失败'));
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  };

  return (
    <Modal
      className='system-dialog menu-dialog'
      open={open}
      width={640}
      title={editing ? '编辑菜单' : '新增菜单'}
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
      {loading ? <div className='dialog-loading'><Spin /><span>正在加载菜单详情...</span></div> : <Form form={form} layout='horizontal' labelCol={{ flex: '0 0 96px' }} colon={false} labelWrap requiredMark={false}>
        <Form.Item name='menuName' label='菜单名称' rules={[{ required: true, message: '请输入菜单名称' }, { max: 80, message: '菜单名称最长 80 个字符' }]}>
          <Input placeholder='例如：操作日志' />
        </Form.Item>
        <Form.Item name='menuType' label='菜单类型' rules={[{ required: true }]}>
          <Select options={typeOptions} disabled={menu?.builtin === 1} />
        </Form.Item>
        <Form.Item name='parentId' label='上级菜单'>
          <TreeSelect showSearch treeNodeFilterProp='title' allowClear treeExpandedKeys={expandedParentKeys ?? defaultParentKeys} onTreeExpand={setExpandedParentKeys} treeData={parents} placeholder='请选择或搜索上级菜单' />
        </Form.Item>
        <Form.Item name='sortOrder' label={<FieldLabel text='显示排序' hint='数字越小越靠前。只能填 0 及以上的整数。' />} rules={[{ required: true, message: '请输入排序值' }]}>
          <InputNumber min={0} step={1} precision={0} style={{ width: '100%' }} placeholder='例如：10' />
        </Form.Item>
        {menuType !== 'BUTTON' && (
          <Form.Item name='routeName' label='路由名称'>
            <Input placeholder='例如：system-log' />
          </Form.Item>
        )}
        {menuType !== 'BUTTON' && (
          <Form.Item
            name='routePath'
            label={<FieldLabel text='路由地址' hint='前端路由路径，需与菜单组件所在页面的地址一致，以 / 开头，例如 /system/log。' />}
            rules={menuType === 'MENU'
              ? [{ required: true, message: '菜单类型必须填写路由地址' }, { max: 160, message: '路由地址最长 160 个字符' }]
              : [{ max: 160, message: '路由地址最长 160 个字符' }]}
          >
            <Input placeholder='例如：/system/log' />
          </Form.Item>
        )}
        {menuType === 'MENU' && (
          <Form.Item
            name='component'
            label={<FieldLabel text='前端组件' hint='相对 src/pages 的路径、不带 .tsx 后缀，例如 system/user/index。候选来自当前前端产物里真实存在的页面文件；新增页面后需要重新构建前端才会出现在候选里。' />}
            rules={[{ required: true, message: '菜单类型必须填写前端组件' }, { max: 160, message: '前端组件最长 160 个字符' }]}
          >
            <AutoComplete options={componentOptions} filterOption={(input, option) => String(option?.value ?? '').toLowerCase().includes(input.toLowerCase())} placeholder='例如：system/user/index' />
          </Form.Item>
        )}
        {menuType === 'BUTTON' && (
          <Form.Item
            name='permissionCode'
            label={<FieldLabel text='权限标识' hint='与后端 @PreAuthorize 中使用的权限码保持一致，例如 system:user:list。' />}
            rules={[{ required: true, message: '请输入权限标识' }, { max: 120, message: '权限标识最长 120 个字符' }, { pattern: /^[a-z0-9_:.-]*$/, message: '只能包含小写字母、数字、冒号、下划线、点和短横线' }]}
          >
            <Input placeholder='例如：system:loginlog:list' />
          </Form.Item>
        )}
        <Form.Item name='icon' label={<FieldLabel text='图标' hint='只能从已登记的图标中选择（清单见 src/router/iconRegistry.tsx），避免填了名字却渲染不出来。' />}>
          <IconPicker options={MENU_ICON_OPTIONS} placeholder='请选择图标' />
        </Form.Item>
        <Form.Item name='visible' label={<FieldLabel text='菜单可见' hint='隐藏后不出现在侧边栏，但仍可通过地址直接访问，适合详情页这类不需要入口的页面。' />}>
          <Radio.Group>
            <Radio value={1}>显示</Radio>
            <Radio value={0}>隐藏</Radio>
          </Radio.Group>
        </Form.Item>
        <Form.Item name='status' label='状态'>
          <Radio.Group>
            <Radio value={1}>启用</Radio>
            <Radio value={0}>停用</Radio>
          </Radio.Group>
        </Form.Item>
        {menuType === 'MENU' && <Form.Item name='keepAlive' label={<FieldLabel text='页面缓存' hint='开启后，在后台菜单之间切换时保留该页面的组件状态和查询条件。刷新浏览器后缓存会清空。' />}>
          <Radio.Group><Radio value={1}>开启</Radio><Radio value={0}>关闭</Radio></Radio.Group>
        </Form.Item>}
      </Form>}
    </Modal>
  );
}
