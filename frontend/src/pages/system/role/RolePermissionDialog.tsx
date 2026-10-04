import { useLayoutEffect, useMemo, useState } from 'react';
import type { Key } from 'react';
import { App, Modal, Spin, Tree } from 'antd';
import type { DataNode } from 'antd/es/tree';
import { getRoleMenuIds, getGrantableMenus, grantRoleMenus } from '../../../api/role';
import type { SystemMenu } from '../../../types/menu';
import type { Role } from '../../../types/role';
import { getApiErrorMessage } from '../../../utils/apiError';

interface RolePermissionDialogProps {
  open: boolean;
  role: Role | null;
  onClose: () => void;
  onSaved: () => void;
}

const menuTypeLabel: Record<SystemMenu['menuType'], string> = { DIR: '目录', MENU: '菜单', BUTTON: '按钮' };
function toTree(menus: SystemMenu[], parentId = 0): DataNode[] {
  return menus.filter((menu) => menu.parentId === parentId).map((menu) => ({
    key: menu.id,
    title: <span className='permission-tree-title'><span>{menu.menuName}</span><small>{menuTypeLabel[menu.menuType]}{menu.permissionCode ? ` · ${menu.permissionCode}` : ''}</small></span>,
    children: toTree(menus, menu.id),
  }));
}

export function RolePermissionDialog({ open, role, onClose, onSaved }: RolePermissionDialogProps) {
  const { message } = App.useApp();
  const [menus, setMenus] = useState<SystemMenu[]>([]);
  const [checkedKeys, setCheckedKeys] = useState<Key[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const treeData = useMemo(() => toTree(menus), [menus]);

  useLayoutEffect(() => {
    if (!open || !role) return;
    let active = true;
    setMenus([]); setCheckedKeys([]);
    setLoading(true);
    void Promise.all([getGrantableMenus(), getRoleMenuIds(role.id)])
      .then(([allMenus, granted]) => { if (active) { setMenus(allMenus); setCheckedKeys(granted); } })
      .catch((error) => { if (active) { message.error(getApiErrorMessage(error, '无法加载菜单权限')); onClose(); } })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [message, open, role]);

  const save = async () => {
    if (!role || role.scopeEditable === false) return;
    try {
      setSaving(true);
      await grantRoleMenus(role.id, checkedKeys.map(Number));
      message.success('菜单权限保存成功');
      onSaved();
      onClose();
    } catch (error) {
      message.error(getApiErrorMessage(error, '菜单权限保存失败'));
    } finally {
      setSaving(false);
    }
  };

  return <Modal className='system-dialog role-grant-dialog' open={open} width={720} title={role ? `菜单权限授权 · ${role.roleName}` : '菜单权限授权'} okText='保存授权' cancelText='取消' onCancel={onClose} onOk={() => void save()} okButtonProps={{ disabled: loading || role?.scopeEditable === false }} cancelButtonProps={{ disabled: loading || saving }} closable={!loading && !saving} maskClosable={!loading && !saving} keyboard={!loading && !saving} confirmLoading={saving} destroyOnHidden>
    <p className='dialog-description'>勾选该角色可以访问的目录、菜单和操作按钮；取消勾选后权限会立即收回。</p>
    {role?.scopeEditable === false && <p className='dialog-description'>已分配角色的菜单授权仅超级管理员可修改。</p>}
    <div className='permission-tree'>
      {loading ? <Spin /> : <Tree checkable disabled={role?.scopeEditable === false} defaultExpandAll checkedKeys={checkedKeys} onCheck={(keys) => setCheckedKeys(Array.isArray(keys) ? keys : keys.checked)} treeData={treeData} />}
    </div>
  </Modal>;
}
