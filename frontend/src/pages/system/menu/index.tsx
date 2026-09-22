import { useEffect, useMemo, useState } from 'react';
import { App, Button, Input, Popconfirm, Space, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DeleteOutlined, EditOutlined, MenuOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { deleteMenu, getMenus } from '../../../api/menu';
import { Permission } from '../../../permission/Permission';
import type { SystemMenu } from '../../../types/menu';
import { getApiErrorMessage } from '../../../utils/apiError';
import { MenuDialog } from './MenuDialog';
import './index.css';

interface MenuRow extends SystemMenu {
  children?: MenuRow[];
}
const typeLabel: Record<SystemMenu['menuType'], { text: string; color: string }> = {
  DIR: { text: '目录', color: 'blue' },
  MENU: { text: '菜单', color: 'cyan' },
  BUTTON: { text: '按钮', color: 'purple' },
};
function toTree(menus: SystemMenu[], parentId = 0): MenuRow[] {
  return menus
    .filter((menu) => menu.parentId === parentId)
    .map((menu) => {
      const children = toTree(menus, menu.id);
      return children.length ? { ...menu, children } : { ...menu };
    });
}

export default function MenuPage() {
  const { message } = App.useApp();
  const [menus, setMenus] = useState<SystemMenu[]>([]);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<SystemMenu | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const load = async () => {
    try {
      setLoading(true);
      setMenus(await getMenus());
    } catch (error) {
      message.error(getApiErrorMessage(error, '无法获取菜单列表'));
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    void load();
  }, []);
  const rows = useMemo(() => {
    const matches = (menu: SystemMenu) => !keyword.trim() || [menu.menuName, menu.routePath, menu.permissionCode].some((value) => value?.toLowerCase().includes(keyword.toLowerCase()));
    const prune = (items: MenuRow[]): MenuRow[] =>
      items.flatMap((item) => {
        const children = prune(item.children ?? []);
        return matches(item) || children.length ? [{ ...item, children }] : [];
      });
    return prune(toTree(menus));
  }, [keyword, menus]);
  const openDialog = (menu: SystemMenu | null = null) => {
    setEditing(menu);
    setDialogOpen(true);
  };
  const columns: ColumnsType<MenuRow> = [
    {
      title: '菜单名称',
      dataIndex: 'menuName',
      width: 210,
      render: (value, row) => (
        <Space>
          {value}
          {row.builtin === 1 && <Tag color='blue'>内置</Tag>}
        </Space>
      ),
    },
    { title: '类型', dataIndex: 'menuType', width: 92, render: (value: SystemMenu['menuType']) => <Tag color={typeLabel[value].color}>{typeLabel[value].text}</Tag> },
    { title: '路由 / 权限标识', width: 255, render: (_, row) => <code>{row.permissionCode || row.routePath || '-'}</code> },
    { title: '组件', dataIndex: 'component', width: 180, render: (value) => value || '-' },
    { title: '排序', dataIndex: 'sortOrder', width: 70 },
    { title: '显示', dataIndex: 'visible', width: 75, render: (value) => <Tag color={value === 1 ? 'success' : 'default'}>{value === 1 ? '显示' : '隐藏'}</Tag> },
    { title: '状态', dataIndex: 'status', width: 75, render: (value) => <Tag color={value === 1 ? 'success' : 'default'}>{value === 1 ? '启用' : '停用'}</Tag> },
    {
      title: '操作',
      width: 165,
      fixed: 'right',
      render: (_, row) => (
        <Space size='middle'>
          <Permission code='system:menu:update'>
            <a onClick={() => openDialog(row)}>
              <EditOutlined /> 编辑
            </a>
          </Permission>
          <Permission code='system:menu:delete'>
            <Popconfirm
              title='确定删除此菜单？'
              description='存在子菜单时不能删除。'
              okText='删除'
              cancelText='取消'
              onConfirm={() =>
                void deleteMenu(row.id)
                  .then(() => {
                    message.success('删除菜单成功');
                    void load();
                  })
                  .catch((error) => message.error(getApiErrorMessage(error)))
              }
            >
              <a className='danger'>
                <DeleteOutlined /> 删除
              </a>
            </Popconfirm>
          </Permission>
        </Space>
      ),
    },
  ];
  return (
    <div className='menu-page'>
      <div className='page-head'>
        <div>
          <div className='page-kicker'>SYSTEM MANAGEMENT</div>
          <h1 className='page-title'>菜单管理</h1>
        </div>
      </div>
      <div className='table-card'>
        <div className='table-toolbar'>
          <Input value={keyword} onChange={(event) => setKeyword(event.target.value)} prefix={<SearchOutlined />} placeholder='搜索菜单名称、路由或权限标识' />
          <Space wrap>
            <Button icon={<ReloadOutlined />} onClick={() => void load()}>
              刷新
            </Button>
            <Permission code='system:menu:add'>
              <Button type='primary' icon={<PlusOutlined />} onClick={() => openDialog()}>
                新增菜单
              </Button>
            </Permission>
          </Space>
        </div>
        <Table
          rowKey='id'
          columns={columns}
          dataSource={rows}
          loading={loading}
          pagination={false}
          scroll={{ x: 1150 }}
          expandable={{
            defaultExpandAllRows: true,
            indentSize: 18,
            expandIcon: ({ expanded, onExpand, record }) =>
              record.children?.length ? (
                <button type='button' className={`tree-expand-arrow${expanded ? ' is-expanded' : ''}`} aria-label={expanded ? '收起子菜单' : '展开子菜单'} onClick={(event) => onExpand(record, event)} />
              ) : null,
          }}
        />
      </div>
      <MenuDialog open={dialogOpen} menu={editing} menus={menus} onClose={() => setDialogOpen(false)} onSaved={() => void load()} />
    </div>
  );
}
