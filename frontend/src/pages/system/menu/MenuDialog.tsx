import { useEffect, useMemo } from 'react';
import { App, Form, Input, InputNumber, Modal, Radio, Select, TreeSelect } from 'antd';
import { MenuOutlined } from '@ant-design/icons';
import { createMenu, updateMenu } from '../../../api/menu';
import type { MenuRequest, SystemMenu } from '../../../types/menu';
import { getApiErrorMessage } from '../../../utils/apiError';

interface MenuDialogProps {
  open: boolean;
  menu: SystemMenu | null;
  menus: SystemMenu[];
  onClose: () => void;
  onSaved: () => void;
}

const typeOptions = [{ value: 'DIR', label: '目录' }, { value: 'MENU', label: '菜单' }, { value: 'BUTTON', label: '按钮' }];
interface MenuTreeNode { value: number; title: string; disabled?: boolean; children?: MenuTreeNode[] }
const treeData = (menus: SystemMenu[], parentId = 0, disabledIds: Set<number>): MenuTreeNode[] => menus.filter((menu) => menu.parentId === parentId).map((menu) => ({ value: menu.id, title: menu.menuName, disabled: disabledIds.has(menu.id), children: treeData(menus, menu.id, disabledIds) }));
function blockedIds(menus: SystemMenu[], id?: number) {
  const blocked = new Set<number>();
  if (!id) return blocked;
  const collect = (parentId: number) => menus.filter((menu) => menu.parentId === parentId).forEach((menu) => { blocked.add(menu.id); collect(menu.id); });
  blocked.add(id); collect(id);
  return blocked;
}

export function MenuDialog({ open, menu, menus, onClose, onSaved }: MenuDialogProps) {
  const [form] = Form.useForm<MenuRequest>();
  const { message } = App.useApp();
  const editing = menu !== null;
  const menuType = Form.useWatch('menuType', form);
  const parents = useMemo(() => treeData(menus, 0, blockedIds(menus, menu?.id)), [menu?.id, menus]);

  useEffect(() => {
    if (!open) return;
    form.setFieldsValue(menu ? { parentId: menu.parentId || undefined, menuName: menu.menuName, menuType: menu.menuType, routeName: menu.routeName, routePath: menu.routePath, component: menu.component, permissionCode: menu.permissionCode, icon: menu.icon, sortOrder: menu.sortOrder, visible: menu.visible, status: menu.status, keepAlive: menu.keepAlive } : { parentId: undefined, menuName: '', menuType: 'MENU', routeName: '', routePath: '', component: '', permissionCode: '', icon: '', sortOrder: 0, visible: 1, status: 1, keepAlive: 0 });
  }, [form, menu, open]);

  const submit = async () => {
    try {
      const values = await form.validateFields();
      const response = editing ? await updateMenu(menu.id, values) : await createMenu(values);
      message.success(response.message || (editing ? '修改菜单成功' : '新增菜单成功'));
      onSaved(); onClose();
    } catch (error) {
      if ((error as { errorFields?: unknown }).errorFields) return;
      message.error(getApiErrorMessage(error, '保存菜单失败'));
    }
  };

  return <Modal className='system-dialog menu-dialog' open={open} width={760} title={<span className='system-dialog-title'><MenuOutlined />{editing ? '编辑菜单' : '新增菜单'}</span>} okText='确定' cancelText='取消' onCancel={onClose} onOk={() => void submit()} destroyOnHidden forceRender styles={{ body: { maxHeight: '62vh', overflowY: 'auto' } }}>
    <Form form={form} layout='vertical' requiredMark={false}>
      <div className='form-grid'>
        <Form.Item name='menuName' label='菜单名称' rules={[{ required: true, message: '请输入菜单名称' }]}><Input placeholder='例如：操作日志' /></Form.Item>
        <Form.Item name='menuType' label='菜单类型' rules={[{ required: true }]}><Select options={typeOptions} disabled={menu?.builtin === 1} /></Form.Item>
      </div>
      <div className='form-grid'>
        <Form.Item name='parentId' label='上级菜单'><TreeSelect allowClear treeDefaultExpandAll treeData={parents} placeholder='顶级菜单' /></Form.Item>
        <Form.Item name='sortOrder' label='显示排序' rules={[{ required: true, message: '请输入排序值' }]}><InputNumber min={0} precision={0} style={{ width: '100%' }} /></Form.Item>
      </div>
      {menuType !== 'BUTTON' && <div className='form-grid'>
        <Form.Item name='routeName' label='路由名称'><Input placeholder='例如：system-log' /></Form.Item>
        <Form.Item name='routePath' label='路由地址' rules={menuType === 'MENU' ? [{ required: true, message: '菜单类型必须填写路由地址' }] : []}><Input placeholder='例如：/system/log' /></Form.Item>
      </div>}
      {menuType === 'MENU' && <Form.Item name='component' label='前端组件'><Input placeholder='例如：system/log/index' /></Form.Item>}
      {menuType === 'BUTTON' && <Form.Item name='permissionCode' label='权限标识' rules={[{ required: true, message: '请输入权限标识' }]}><Input placeholder='例如：system:log:list' /></Form.Item>}
      <div className='form-grid'>
        <Form.Item name='icon' label='图标名称'><Input placeholder='例如：MenuOutlined' /></Form.Item>
        <Form.Item name='keepAlive' label='缓存页面'><Radio.Group disabled={menuType !== 'MENU'}><Radio value={1}>开启</Radio><Radio value={0}>关闭</Radio></Radio.Group></Form.Item>
      </div>
      <div className='form-grid'>
        <Form.Item name='visible' label='菜单可见'><Radio.Group><Radio value={1}>显示</Radio><Radio value={0}>隐藏</Radio></Radio.Group></Form.Item>
        <Form.Item name='status' label='状态'><Radio.Group><Radio value={1}>启用</Radio><Radio value={0}>停用</Radio></Radio.Group></Form.Item>
      </div>
    </Form>
  </Modal>;
}
