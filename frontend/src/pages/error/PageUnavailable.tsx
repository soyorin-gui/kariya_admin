import './pageUnavailable.css';

/**
 * 页面组件缺失 —— 这是给**开发者**看的诊断页，不是给终端用户的设计页。
 *
 * 触发场景：菜单里配了 component = "system/xxx/index"，但当前前端产物里没有对应文件。
 * 与其静默显示 404（会让人以为是路由或权限问题），不如直接把"该建哪个文件"写清楚。
 */
export default function PageUnavailable({ menuName, component, file }: { menuName: string; component?: string | null; file: string }) {
  return (
    <section className='page-unavailable'>
      <h2>页面组件不存在</h2>
      <p>
        菜单「{menuName}」指向的前端组件没有被构建进当前前端产物，因此页面无法渲染。
      </p>
      <dl>
        <dt>菜单配置的 component</dt>
        <dd>
          <code>{component?.trim() || '(空)'}</code>
        </dd>
        <dt>期望创建的文件</dt>
        <dd>
          <code>{file}</code>
        </dd>
      </dl>
      <ol>
        <li>
          按上面的路径创建页面文件，并默认导出一个 React 组件（可参考 <code>src/pages/system/user/index.tsx</code>）；
        </li>
        <li>
          开发模式下保存即生效；<strong>生产环境需要重新执行 <code>npm run build</code> 并发布</strong>
          —— 浏览器无法执行没有被编译过的 tsx 文件；
        </li>
        <li>
          路径区分大小写，<code>system/user/index</code> 与 <code>System/User/index</code> 不是同一个组件；
        </li>
        <li>
          也可以在「菜单管理」里把该菜单的组件改成下拉中已存在的页面（下拉选项来自当前产物里真实存在的文件）。
        </li>
      </ol>
    </section>
  );
}
