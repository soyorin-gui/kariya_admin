import { SearchOutlined } from '@ant-design/icons';
import { AutoComplete, Empty, Input } from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { menuBreadcrumb, pageMenus } from '../../router/menu';
import { useAppSelector } from '../../store/hooks';

/**
 * 顶栏功能搜索只检索当前账号已经获得授权的页面菜单。
 * 这样搜索结果和侧边栏使用同一份权限数据，不会把用户引向必然 403 的页面。
 */
export function GlobalSearch() {
  const navigate = useNavigate();
  const menus = useAppSelector((state) => state.auth.menus);
  const [keyword, setKeyword] = useState('');

  const options = useMemo(() => {
    const normalized = keyword.trim().toLocaleLowerCase();
    if (!normalized) return [];
    return pageMenus(menus)
      .map((menu) => {
        const path = menu.routePath!;
        const breadcrumb = menuBreadcrumb(path, menus).join(' / ');
        return { menu, path, breadcrumb, searchable: `${menu.menuName} ${breadcrumb} ${path}`.toLocaleLowerCase() };
      })
      .filter((item) => item.searchable.includes(normalized))
      .slice(0, 10)
      .map((item) => ({
        value: item.path,
        label: (
          <div className='global-search-option'>
            <span>{item.menu.menuName}</span>
            <small>{item.breadcrumb}</small>
          </div>
        ),
      }));
  }, [keyword, menus]);

  return (
    <AutoComplete
      className='quick-search'
      value={keyword}
      options={options}
      onSearch={setKeyword}
      onChange={setKeyword}
      onSelect={(path: string) => {
        setKeyword('');
        navigate(path);
      }}
      notFoundContent={keyword.trim() ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description='没有匹配的可用功能' /> : null}
      popupMatchSelectWidth={380}
    >
      <Input prefix={<SearchOutlined />} placeholder='搜索当前账号可用功能...' allowClear />
    </AutoComplete>
  );
}
