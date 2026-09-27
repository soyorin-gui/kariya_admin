# 架构与代码逻辑

> 这一篇回答：**代码是怎么跑起来的**。读完你应该能在脑子里完整跑一遍"打开页面 → 登录 → 看到菜单 → 点开用户管理 → 增删改查"的全过程，并知道每一跳落在哪个文件。

---

## 第一部分：后端

### 1.1 分层约定

```
Controller  ── 只做三件事：接参数、调 Service、包 Result
    │          权限用 @PreAuthorize("hasAuthority('xxx')") 声明
    │          写操作加 @OperationLog(module, action) 记审计
    ▼
Service     ── 业务规则、事务边界、越权校验（调 AccessPolicy）
    │          @Transactional 只加在 Service 的写方法上
    ▼
Mapper      ── MyBatis-Plus BaseMapper + @Select 注解里的手写 SQL
              （复杂查询：权限树递归 CTE、批量装配、含逻辑删除的查重）
```

**例外与刻意设计**：
- `AccessPolicy` 是一个 `@Service`，但它是**纯判定逻辑**（不写库），被各 Service 注入后调用。它是"这个人能不能干这件事"的**唯一真相来源**。
- `AuthController` 里有一部分查询逻辑（`/auth/me` 组装菜单树、`routeCatalog()`），这是因为它们就是"登录态自身的数据组装"，硬拆成 Service 反而更绕。
- `ExternalLoginService`（协议+网络）与 `ExternalLoginPersistence`（落库）**刻意分成两个类**，为了让"事务里不许有网络 IO"变成结构上的事实（`ExternalLoginPersistence.java:18-36` 有详细解释）。

### 1.2 一次 API 请求的完整生命周期

以 `GET /api/system/users?pageNum=1&pageSize=10`（用户列表）为例：

```
① Tomcat 接收请求
      │
② RequestLoggingFilter                    common/filter/RequestLoggingFilter.java
      │  生成 requestId 放进 MDC（日志里那个 [%X{requestId}]），记一条请求日志
      ▼
③ JwtAuthenticationFilter                 security/filter/JwtAuthenticationFilter.java
      │  1) shouldNotFilter()？/api/auth/**（除 onboarding）直接跳过，不解析令牌
      │  2) 读 Authorization: Bearer xxx
      │  3) jwt.parse() 验签 + 验过期  → 拿到 sid / authVersion / subject
      │  4) sessions.find(sid) 从 Redis 取会话；不存在 → 视为未认证
      │  5) 校验：用户存在 && status=1 && 用户.authVersion == 会话.authVersion
      │            && 会话.username == token.subject && 会话.authVersion == token.authVersion
      │  6) 查 menus.selectByUserId() 得到权限码集合 → 变成 Spring Security 的 authorities
      │  7) 把 CurrentUser(id, username, deptId) 放进 SecurityContext
      │     把 sid 放进 authentication.setDetails(sid)（供会话管理接口用）
      ▼
④ Spring Security 过滤器链
      │  .requestMatchers("/api/auth/**").permitAll() 等规则 → 本请求需要认证 ✅
      ▼
⑤ @PreAuthorize("hasAuthority('system:user:list')")   UserController.java:39
      │  authorities 里没有这个权限码 → 抛 AccessDeniedException
      │  → GlobalExceptionHandler#forbidden → 403 {"code":403,"message":"没有访问该资源的权限"}
      ▼
⑥ UserController#list → UserService#page
      │  access.actor()  ← 【关键】重新查库，构造"当前操作者"的完整画像
      │  access.applyUserScope(query, actor)  ← 把数据范围翻译成 SQL 条件
      │  mapper.selectPage(...)   ← MyBatis-Plus 分页插件自动加 LIMIT
      │  toListViews()  ← 批量装配角色名/部门名/权限集合，**避免 N+1**
      ▼
⑦ 包成统一响应 Result.ok(PageResult{records,total,page,size})
      │  出参格式：{"code":200,"message":"...","data":{...}}
      ▼
⑧ 前端 axios 拦截器原样放行（code 200）
```

**注意第 ③ 步和第 ⑥ 步的区别**：过滤器构造的是"轻量身份"（够用就行），`access.actor()` 构造的是"完整授权画像"（会查角色、数据范围、菜单）。两者刻意分开——过滤器在每个请求上跑，不能太重。

### 1.3 `AccessPolicy` —— 全项目权限判定的唯一真相来源

`security/context/AccessPolicy.java`（387 行）是整个系统最该读懂的文件。

```java
Actor actor()   // ← 一切判定的起点，返回一个"操作者画像"
record Actor(UserEntity user, boolean superAdmin, Set<String> permissions,
             boolean all, boolean self, Set<Long> departments, int maxScopeRank)
```

`actor()` 做的事（`AccessPolicy.java:45-85`）：

1. 从 `SecurityContext` 拿当前用户名 → 查 `sys_user`，不存在或停用 → 401。
2. 查该用户的**已分配角色**（含停用），用 `containsSuperAdmin()` 判断是不是超管（**必须 `role.status=1`**）。
3. 查该用户的**权限码集合**（递归 CTE：role → role_menu → menu）。
4. **把数据范围翻译成"可见部门集合"**：

| 角色的 `data_scope` | 含义 | 实现 |
| --- | --- | --- |
| `ALL` | 全部 | `all = true`（后续 SQL 不加任何条件） |
| `DEPT_AND_CHILDREN` | 本人部门及所有下级 | `departments` += 本人部门 + 用 `DeptPaths.subtreeQuery` 展开整棵子树 |
| `DEPT` | 仅本人部门 | `departments` += 本人部门 |
| `SELF` | 仅本人 | `self = true` |

5. 多个角色**取并集**，`maxScopeRank` 取最大值（SELF=1 < DEPT=2 < DEPT_AND_CHILDREN=3 < ALL=4）。

**这里有一个刻意的产品决定**（`AccessPolicy.java:56-62`）：`actor()` **不看部门的 `status`**。"停用部门"只表示"不能再往这个部门里放人"，**已经在里面的人完全不受影响**。想改成"停用即整条分支下线"，那是另一套业务规则，改之前先确认需求。

`AccessPolicy` 提供的判定能力：

| 方法 | 用途 |
| --- | --- |
| `applyUserScope(query, actor)` | 把数据范围变成 `WHERE dept_id IN (...) OR id = ?` 拼进查询 |
| `canManageUser(actor, target)` | 不能管理**同级或更高权限**的人；不能管理自己；不能管理内置账号 |
| `canAssignRole` / `requireAssignableRoles` | 不能分配比自己权限高、或**权限组合与自己完全相同**的角色 |
| `requireScope(actor, dataScope)` | 不能设置超过自身的数据范围 |
| `requireGrantableMenus(actor, ids)` | 不能授予自己没有的菜单 |
| `canManageDept` / `requireCreateDept` / `requireMoveDept` | 部门树上的操作边界 |
| `requireSuperAdmin(actor)` | 菜单定义、重置密码等平台级操作 |
| `PLATFORM_ONLY_PERMISSION_CODES` | 平台级权限码白名单（静态常量，全项目唯一定义处） |

**为什么"越权判定"这么啰嗦？** 因为这是后台系统里唯一一类"出错就等于被入侵"的逻辑。所以这里的风格是：**宁可拒绝，宁可多查一次库，也不做乐观假设。**

### 1.4 数据范围是怎么变成 SQL 的

`AccessPolicy.applyUserScope()`（`AccessPolicy.java:87-94`）:

```java
public void applyUserScope(LambdaQueryWrapper<UserEntity> query, Actor actor) {
    if (actor.all()) return;                    // ALL：不加任何条件
    query.and(condition -> {
        if (!actor.departments().isEmpty())
            condition.in(UserEntity::getDeptId, actor.departments());
        else
            condition.eq(UserEntity::getId, actor.user().getId());
        if (actor.self() && !actor.departments().isEmpty())
            condition.or().eq(UserEntity::getId, actor.user().getId());
    });
}
```

`UserService.page()` 和 `UserService.export()` 都调它，所以**列表和导出用的是同一套范围**——这点很重要，否则会出现"列表里看不到，导出的 Excel 里却有"的越权泄露。

### 1.5 部门树：物化路径（materialized path）

`sys_dept.ancestors` 存的是 `"0,1,2"` 这样的祖先路径（不含自身）。

**唯一口径在 `system/dept/support/DeptPaths.java`**，三处都在用它：
1. 数据范围展开子树（`AccessPolicy`）；
2. 移动部门时重写整棵子树的路径（`DeptService.update`）；
3. 移动前的环检测。

它存在的理由（`DeptPaths.java:14-18`）非常值得记住：

> 路径形如 `"0,1,2"`，若只用 `LIKE '0,1,2%'` 匹配，部门 20（`ancestors = "0,1,20"`）也会被算作部门 2 的后代——因为 `"0,1,20"` 的字符串前缀确实是 `"0,1,2"`。必须加逗号边界变成 `LIKE '0,1,2,%'`。

这是一个**静默算错、不报错**的经典 bug，历史上真的踩过。

### 1.6 权限树：递归 CTE

`MenuMapper.selectByUserId()`（`MenuMapper.java:14-32`）：

```sql
WITH RECURSIVE granted AS (
    SELECT DISTINCT m.* FROM sys_menu m
    INNER JOIN sys_role_menu rm ON rm.menu_id = m.id
    INNER JOIN sys_user_role ur ON ur.role_id = rm.role_id
    INNER JOIN sys_role r ON r.id = ur.role_id
    WHERE ur.user_id = ? AND m.deleted = 0 AND m.status = 1
      AND r.deleted = 0 AND r.status = 1      -- ★ 停用的角色不授予任何权限
), menu_tree AS (
    SELECT * FROM granted
    UNION
    SELECT parent.* FROM sys_menu parent       -- ★ 向上补齐祖先：授权的按钮，其父菜单也要出现
    INNER JOIN menu_tree child ON child.parent_id = parent.id
    WHERE parent.deleted = 0 AND parent.status = 1
)
SELECT DISTINCT * FROM menu_tree ORDER BY parent_id, sort_order, id
```

**它一次查询同时算出了两件事**：这个人能访问哪些菜单（= 前端路由表）+ 这个人有哪些权限码（= 后端 `@PreAuthorize` 的依据）。这是"菜单树本身就是授权结果"这一设计的核心——前端不需要再维护任何"路径 → 权限码"映射表。

还有一个批量版 `selectPermissionCodesByUserIds()`，用于用户列表里给每一行算"我能不能管理他"，**避免 N+1 查询**。

### 1.7 写入侧的一致性口径（一个反复出现的坑）

项目里到处能看到这样的注释：

> 逻辑删除只把 `deleted` 置 1，行和唯一索引项都还在表里。所以"删掉一个用户再建同名用户"必然撞唯一键。而 MyBatis-Plus 的 `selectCount` 会自动追加 `deleted = 0`，用它做预检会出现"校验通过、插入失败"。

**结论**：凡是带唯一索引 + 逻辑删除的字段，查重必须用"含已删除"的口径。为此有 5 个专用 Mapper 方法：

| 方法 | 表 |
| --- | --- |
| `UserMapper.countIncludingDeletedByUsername` | `sys_user.username` |
| `RoleMapper.countIncludingDeletedByRoleCode` | `sys_role.role_code` |
| `DeptMapper.countIncludingDeletedByDeptCode` | `sys_dept.dept_code` |
| `MenuMapper.countIncludingDeletedByRoutePath` | `sys_menu.route_path` |
| `MenuMapper.countIncludingDeletedByPermissionCode` | `sys_menu.permission_code` |

**新增任何带唯一索引的业务表时，请照抄这个约定。**

### 1.8 事务边界与"连接池纪律"

代码里有三处关于事务的刻意设计，都很值得学：

1. **审计日志用 `REQUIRES_NEW`**（`LoginLogService.java:46`、`OperationLogService.java:45`）
   业务失败会回滚，但"谁尝试做了什么"必须留下。所以审计写入开新事务，并且**内部吞掉所有异常**——审计失败不能让用户的操作跟着失败。

2. **外部登录的事务里绝不放网络 IO**（`ExternalLoginPersistence` 的类注释）
   换令牌 + 取用户资料各有 15 秒超时，最坏 30 秒。包在事务里会占住 Hikari 连接（`maximum-pool-size: 10`），几个并发就能打满连接池，**全站请求集体失败**。所以拆成"网络阶段"和"落库阶段"两个类。

3. **成功日志写在事务提交之后**（`ExternalLoginService.java:180-184`）
   既避免"一个请求同时占两条连接"，也避免成功日志被 catch 误判成失败。

### 1.9 统一响应与全局异常

出参统一 `Result<T>`：`{"code":200,"message":"...","data":...}`（`common/result/Result.java`）。
分页统一 `PageResult<T>`：`{records,total,page,size}`。

`GlobalExceptionHandler`（271 行）把异常映射成前端能理解的状态码，**这套映射是前端逻辑的基础**：

| 异常 | HTTP | 前端行为 |
| --- | --- | --- |
| `UnauthorizedException` | **401** | 触发静默续期；续期失败才跳登录页 |
| 过滤器层的未认证 | **401** | 同上（走 `authenticationEntryPoint`） |
| `AccessDeniedException`（`@PreAuthorize` 拒绝） | **403** | **不**续期，直接把消息展示出来 |
| `BusinessException` | 400 | 展示 `message` |
| `MethodArgumentNotValidException` / `ConstraintViolationException` | 400 | 展示"第一个"校验失败的具体文案 |
| `TooManyRequestsException` | **429** | 提示"稍后再试"（与 400 语义严格区分） |
| `DuplicateKeyException` | 400 | "数据已存在：…唯一字段重复" |
| `CannotCreateTransactionException` | 503 | "数据库连接不可用" |
| 其它 `Exception` | 500 | "系统繁忙，请稍后重试"（细节只进服务端日志） |

**为什么 `@PreAuthorize` 的拒绝必须单独处理**（`GlobalExceptionHandler.java:94-103`）：方法级权限校验是在 Controller 调用过程中抛异常的，此时请求已经进入 `DispatcherServlet`，Spring Security 的 `ExceptionTranslationFilter` 在它外层、永远看不到这个异常。不单独声明的话会被 `Exception.class` 兜住变成 500"系统繁忙"——把"没有权限"错报成"服务端故障"。

### 1.10 内置权限自动补建

`config/SystemPermissionInitializer.java` —— 一个 `ApplicationRunner`（`@Order(100)`），**每次启动幂等执行**：

- 按 `route_path` / `permission_code` 查缺失的内置菜单/按钮，缺则插入；
- 插入后自动授予 `super_admin`；
- 对两种脏数据做降级而不让应用起不来：①同一路由有多条记录 → 取 id 最小的一条 + WARN；②多实例同时启动撞唯一键 → 捕获 `DuplicateKeyException` 后重新读回对方插入的那条。

**为什么需要它**：`init_data.sql` 只在建空库时跑一次，老库升级不会重新执行，结果就是"代码里有这个页面、菜单树里却没有入口"，只能靠人工去菜单管理里点出来。

⚠️ **维护提醒**：新增系统级页面/权限时，要**同时**更新三处：
1. `db/init_data.sql`（新建库用）
2. `config/SystemPermissionInitializer.java` 的 `PAGES` / `PERMISSIONS` 列表（老库升级用）
3. 前端 `pages/` 下真实的页面文件 + 后端 `@PreAuthorize` 里的权限码

这三处漂移过一次（注释里明确提到 `system:user:export` 的补建曾经在三份种子逻辑之间漂移），所以 `AccessPolicy.PLATFORM_ONLY_PERMISSION_CODES` 被刻意做成了"单一真相来源"。

---

## 第二部分：前端

### 2.1 Provider 树

`main.tsx`（仅 21 行）：

```tsx
<React.StrictMode>
  <Provider store={store}>          {/* Redux Toolkit */}
    <ThemeProvider>                 {/* 明暗/主色/紧凑，写入 CSS 变量 + localStorage */}
      <AntApp>                      {/* AntD 的 App：提供 message / modal / notification 上下文 */}
        <RouterProvider router={router} />
      </AntApp>
    </ThemeProvider>
  </Provider>
</React.StrictMode>
```

**注意 `React.StrictMode`**：开发模式下每个 effect 会跑两遍。表单弹窗里的"防重复提交"（`submittingRef`）、`SmartTable` 的请求序号（`requestSequence`）都是为了让两遍执行不产生副作用。

**统一 UI 风格是怎么做到的**：`ThemeProvider` 把主题写进 CSS 自定义属性（`--brand`、`--surface`、`--line`、`--ink`、`--muted` 等），所有页面 CSS 只引用变量、不写死颜色。同时 AntD 的 `ConfigProvider` 拿到同一份 token。所以改主题色一处生效全站。

### 2.2 路由：静态骨架 + 一个动态出口

`router/index.tsx` 只有这些**不随业务变化**的路由：

| 路径 | 组件 |
| --- | --- |
| `/login` | 登录页 |
| `/register` | 注册页 |
| `/auth/callback` | 三方登录回跳落点 |
| `/account/setup` | 三方开户确认（受 `AuthGuard` 保护） |
| `/404` | 404（**刻意不套 `AuthGuard`**，它是"地址不存在"，与登录状态无关） |
| `/` | `AuthGuard` + `AppLayout` |
| ├ `index` | `MenuHomeRedirect` — 落到"我的第一项可见页面" |
| ├ `403` | 403 预览页 |
| ├ `account/security`、`account/notifications` | 个人中心（非菜单驱动，静态路由） |
| └ `*` | **`DynamicPage`** ← 所有业务页面都从这里出 |

**所有业务页面都不在路由表里登记。** 它们由后端菜单数据驱动。

### 2.3 动态路由的三个关键文件

#### ① `router/componentRegistry.tsx` —— 构建期扫描

```ts
const modules = import.meta.glob([
  '../pages/**/*.tsx',
  '!../pages/error/**',    // 错误页不是业务页面，不进菜单组件候选
  '!../pages/login/**',
  '!../pages/register/**',
  '!../pages/auth/**',
  '!../pages/account/**',
]);
```

Vite 在**构建期**扫描磁盘，生成 `{ '../pages/system/user/index.tsx': () => import(...) }` 映射。菜单里的 `component` 字符串（例如 `system/user/index`）经 `resolveMenuComponent()` 变成真实的懒加载组件。

**三个重要设计点**：
- **新增页面不需要改任何 map** —— 建文件 + 建菜单即可。（前提：文件必须进入本次构建产物，所以生产环境要重新 `npm run build`。）
- **解析结果必须缓存**（`resolved` Map）：`React.lazy()` 每次调用返回一个**全新**组件类型，每次渲染都重新 lazy 会让 React 认为换了组件从而卸载重建 —— 表现为页面反复刷新、接口被重复请求。
- 解析不到时返回 `null`，由 `DynamicPage` 渲染 `PageUnavailable` 诊断页（明确告诉你该建哪个文件），而不是静默显示 404。

`availableComponents()` 输出"当前产物里真实存在的页面清单"，菜单管理的"前端组件"下拉直接读它，所以**配不出一个渲染不出来的菜单**。

#### ② `router/menu.ts` —— 菜单树工具

| 函数 | 作用 |
| --- | --- |
| `pageMenus(menus)` | 过滤出 `menuType === 'MENU' && routePath` 的"真正页面"（DIR 只是分组、BUTTON 只是权限点） |
| `findMenuByPath(menus, pathname)` | 按地址找菜单 |
| `firstAvailablePath(menus)` | 登录后落到"我的第一项可见页面"，**交付不了时返回 `null` 而不是回退 `/home`** |
| `resolveRedirect(raw, origin)` | 防开放重定向：只接受同源站内绝对路径 |
| `ancestorMenuKeys(menus, pathname)` | 算当前地址的祖先链，用于自动展开侧边栏目录 |
| `menuBreadcrumb(pathname, menus)` | 面包屑 |

**`firstAvailablePath` 为什么不能回退 `/home`**（`menu.ts:16-28`）：菜单是按角色授权的，用户完全可能没被授予首页。回退 `/home` 会变成"一登录就撞 403"，而 403 页上的"返回首页"按钮又用同一个函数算出 `/home`，于是又 403 —— **用户被永久困在错误页里**。返回 `null` 让调用方渲染一个"当前账号还没有可访问的页面"的空状态（`DynamicPage.tsx:90-114`），用户始终有"退出登录"这条路。

**`resolveRedirect` 的威胁模型**（`menu.ts:38-54`）：攻击者发一个 `https://你的系统/login?redirect=https://钓鱼站`，受害者看到的是自己的域名和登录框、登录成功后还会弹"登录成功"，然后被整页跳到钓鱼站。所以只接受"以单个 `/` 开头"的站内路径，且用 `URL` 解析而不是正则（让浏览器按 RFC 3986 处理 `//evil.com`、`/\evil.com` 这类畸形写法）。

#### ③ `router/DynamicPage.tsx` —— 三级判定

```
1. 地址在我的菜单树里            → 渲染 menu.component 指向的页面
2. 不在我的菜单树、但全站配置里有 → 403（页面存在但我没权限）
3. 全站都没配过这个地址          → 重定向到 /404
```

第 2 步需要"全站路由目录"，由 `/auth/me` 的 `routes` 字段提供（`AuthController.routeCatalog()`，只含 `routePath` + 菜单名，不含任何权限信息）。这不是新增泄露——传统静态路由方案里整张路由表本来就随 JS 包公开。

**为什么"在我的菜单树里"就等于"有权限访问"**：`MenuMapper.selectByUserId()` 已经用递归 CTE 算出"这个人能访问的菜单集合"。这张树本身就是授权结果，前端不需要再维护映射表。

**`visible=0`（隐藏）的菜单仍然可以访问**，只是不出现在侧边栏 —— 这是"隐藏"应有的语义（详情页、带内部参数的页面常用），所以 `DynamicPage` 不过滤 `visible`，只有 `AppLayout.buildMenu()` 过滤。

**KeepAlive 的实现**：`DynamicPage` 维护一个 `cachedPaths` 数组，`keepAlive === 1` 的页面被挂载后用 `style={{display:'none'}}` 保留在 DOM 里。所以菜单管理里的"页面缓存"开关生效。

### 2.4 前端鉴权：`AuthGuard` + 拦截器

#### 令牌存在哪？

- **access token（15 分钟）存在 Redux 内存里**（`store/authSlice.ts`），**不落 localStorage** —— 降低 XSS 窃取风险，代价是刷新页面就没了。
- **刷新凭据（sid）存在 httpOnly Cookie 里**，名字 `lbl_refresh`，`Path=/api/auth`，`SameSite=Lax`，`Secure` 由配置决定。JS 读不到它。

#### 启动 / 刷新的流程（`AuthGuard.tsx`）

```
挂载
 ├─ token 为空 → 调 /auth/refresh（靠 Cookie 换新的 accessToken）
 ├─ 调 /auth/me → 拿到 { principalType, user, permissions, menus, routes }
 ├─ dispatch setSession + setProfile     ← 写入 Redux
 ├─ 若 principalType === 'ONBOARDING' → 强制跳 /account/setup
 └─ 失败 → clearSession + 跳 /login?redirect=当前路径
```

**为什么开户确认态要在跳转生效前保持加载态**（`AuthGuard.tsx:42-52`）：effect 里已经会把人送到 `/account/setup`，但"检查完成 → children 可见"这一帧仍会先渲染一次工作台，于是工作台会立刻发起一批对开户态**必然 403** 的请求（未读消息数、实时连接票据，还会因此建一次注定失败的 WebSocket 重连退避）。除了控制台噪音，也让"这个临时身份能不能用系统"变得难以判断。

#### 401 静默续期（`utils/request.ts`）

```
请求返回 401
 ├─ 是 _retry（已经重试过）？ → 直接失败
 ├─ 是"会话引导类接口"（AUTH_BOOTSTRAP 正则）？ → 直接失败（再 refresh 无意义）
 └─ 否则：
      refreshPromise ??= axios.post('/auth/refresh')   ← ★ 并发请求共享同一个 Promise
      拿到新 token → 写回 Redux → 用新 Authorization 重放原请求
      失败 → clearSession + 跳 /login?redirect=当前路径（用 authExpiredHandling 防重复跳）
```

**`AUTH_BOOTSTRAP` 必须是精确列举**（`request.ts:8-19`）：
```ts
const AUTH_BOOTSTRAP = /\/auth\/(refresh|me|touch|login|logout|register|captcha|external)\b/;
```
这些接口本身不依赖 access token（靠 Cookie 或压根不需要），返回 401 就代表"会话真的不存在了"。**不能用 `/\/auth\//` 这种前缀匹配**，因为它会命中 `/auth/onboarding/account/*` —— 这两个开户确认接口**真正需要访问令牌与静默续期**（它们的令牌同样只有 15 分钟）。

**403 与 401 必须严格区分**（`request.ts:21-27`）：403 = 已认证但权限不足，**不续期**，直接把后端消息展示出来；400 = 业务规则拒绝（"用户名已存在"），**绝不能**当成登录失效处理，否则会把用户莫名踢到登录页。

#### 退出登录

`AppLayout.exit()` → `logout()` API → `dispatch(clearSession())` → 跳 `/login`。
`clearSession` 是"会话结束"的**唯一收口**（`notificationSlice.ts:103-113` 有说明），所以通知状态也跟着它清空——否则退出后换另一个账号登录，在新账号首次拉取返回前，顶栏会显示**上一个账号**的通知与未读数（串号泄露）。

### 2.5 按钮级权限

`permission/Permission.tsx` 只有 9 行：

```tsx
export function usePermission() {
  const permissions = useAppSelector((s) => s.auth.permissions);
  return (code: string) => permissions.includes(code);
}
export function Permission({ code, children }: { code: string; children: ReactNode }) {
  return usePermission()(code) ? <>{children}</> : null;
}
```

用法（`pages/system/user/index.tsx:160-169`）：

```tsx
<Permission code='system:user:add'>
  <Button type='primary' icon={<PlusOutlined />} onClick={() => openDialog()}>新增用户</Button>
</Permission>
```

**它只负责"看不见"，不负责"拦得住"。** 真正的边界在后端 `@PreAuthorize`。前端隐藏按钮只是体验优化；直接调接口一样会被 403 挡住。

⚠️ 目前 `Permission` 只包裹了按钮，**没有**对表格列、行内操作之外的区域做细粒度控制，也没有"任一权限码命中"的变体（需要多个时得嵌套或自己用 `usePermission`）。

### 2.6 `SmartTable` —— 最值得复用的组件

`components/SmartTable/SmartTable.tsx`（238 行）。它把列表页的模板代码收敛成一个泛型组件：

```tsx
<SmartTable<UserListItem, { keyword: string }>
  ref={tableRef}                          // 拿到 reload / reset / getQueryParams
  rowKey='id'
  columns={columns}
  initialSearch={{ keyword: '' }}
  request={async ({ page, pageSize, search }) => {
    const result = await getUsers({ pageNum: page, pageSize, keyword: search.keyword });
    return { list: result.records, total: result.total };   // ★ 只需要返回 {list,total}
  }}
  onRequestError={(e) => message.error(getApiErrorMessage(e))}
  searchRender={({ search, setSearch, submit }) => ( ...自定义搜索区... )}
  toolbarRender={({ search, submit, reload }) => ( ...自定义工具栏... )}
  pagination={{ pageSize: 10, showTotal: (v) => `共 ${v} 条记录` }}
/>
```

两种用法：
- **`searchConfig`**：声明式配置搜索字段（`input` / `select` / `date` / `dateRange`），组件自动渲染查询/重置按钮。
- **`searchRender`**：给 `actions` 对象，完全自定义搜索区（系统管理页都用这个，因为要放 Icon 和自定义布局）。

**它内部解决掉的麻烦事**：
- **请求竞态**：`requestSequence` 计数器 + `active` 标志，**只有最后一次请求的结果会被写入 state**。快速切换页码时不会被慢响应覆盖。
- **查询与输入分离**：`search`（输入中）和 `submittedSearch`（已提交）是两个 state。**输入时不发请求**，只有 `submit()` 才提交并回到第 1 页。
- **`reload()` 的语义**：如果目标页码和当前页码不同就改页码（触发 effect），否则 `reloadToken++` 强制刷新。所以"删掉当前页最后一条"可以 `reload({ page: currentPage - 1 })`（`pages/system/user/index.tsx:112-113` 就是这么做的）。
- **排序/筛选归一化**：`normalizeSorter` 把 AntD 的 `sorter` 转成 `{field, order: 'asc'|'desc'}`。

⚠️ **已知短板**：`pageSize` 只从 `pagination.pageSize` 初始化，**不改 `${pageSize}` 变化**；`reload()` 无法改变 pageSize。当前所有页面都写死 `showSizeChanger: false`，所以还没暴露。

### 2.7 实时通知链路

```
登录后 AppLayout 挂载
 ├─ RealtimeClient.start()
 │    → POST /api/account/realtime/ticket  （用当前会话换一次性票据，60 秒有效）
 │    → new WebSocket(`ws(s)://<host>/ws/realtime?ticket=xxx`)
 │    → 收到 notification.created / department.request.updated → dispatch(loadNotifications())
 │    → 断线后指数退避重连（1s→2s→…→30s，带随机抖动）
 └─ UserActivityManager.start()
      → 监听 click/keydown/input/pointerdown/scroll
      → 距上次超过 5 分钟才 POST /auth/touch（续服务端会话空闲计时）
```

**为什么 WebSocket 不用 JWT 握手**：浏览器原生 `WebSocket` 不能自定义请求头，没法带 `Authorization`。所以设计成"先用已认证的 HTTP 请求换一个一次性票据，再用票据握手"。票据：32 字节随机、Redis 存 `userId|sid`、**`getAndDelete` 一次性消费**、TTL 60 秒（`RealtimeTicketService.java`）。

**服务端主动断连**：`SessionService.remove(sid)` 会调 `realtime.closeSession(sid)` —— 会话被踢掉时，对应的 WebSocket 也会立刻关闭，不会出现"网页看着还活着、其实凭据已经失效"。

**前后端共享一份通知状态**：`store/notificationSlice.ts`。顶栏铃铛和消息中心页读同一份 `items` / `unreadCount`，所以"全部标为已读"后红点**立刻**归零，不需要等下一次实时事件或切标签页。（历史上两边各持一份 `useState`，表现为"我明明都读过了，红点还在"。）

### 2.8 顶部进度条（nprogress）

`services/progress.ts`。**不要直接调 `NProgress.start()/done()`** —— 那有三个坑，缺一个它就会从"进度反馈"退化成"闪烁的噪声"：

| 坑 | 本项目的解法 |
| --- | --- |
| 接口太快，每点一次"查询"都闪一下 | **延迟 300ms** 才显示（`DELAY_MS`）。快于 300ms 的请求不留任何痕迹 |
| 首屏会并发多个请求（refresh + me + 未读数 + 实时票据），第一个返回就把还在飞的"结束"掉 | **并发计数**，归零才 `done()` |
| 300ms 内就结束的请求，计时器到点仍会把进度条画出来再立刻收掉 —— 正是那个闪烁 | 结束时**取消未触发的计时器** |

**两个接入点**（`startProgress` / `doneProgress` 必须成对）：

| 接入点 | 位置 | 覆盖的等待 |
| --- | --- | --- |
| axios 请求拦截器 + 响应拦截器 | `utils/request.ts` | 所有 API 请求 |
| 懒加载 loader | `router/componentRegistry.tsx` 里 `lazy(() => trackProgress(loader))` | **点菜单后下载页面代码的等待** —— 这一处尤其有价值，页面是 `React.lazy` 懒加载的，首次进入有几百毫秒空白 |

**⚠️ 接线时最容易漏的一点**：`request.ts` 的**错误**拦截器有 4 条 `return` 路径（不重试 / 重试成功 / 重试失败 / 抛异常），
`doneProgress()` 必须放在**所有早退分支之前**。任何一条漏掉，计数器就永久 +1 —— 表现为**进度条跑到一半卡住不再消失**。
续期彻底失败（整页跳登录）那一路还要额外调 `resetProgress()` 把计数清干净。

**样式**：基础样式来自 `nprogress/nprogress.css`（在 `main.tsx` 引入，必须排在自己的样式之前）；
品牌色适配在 `styles/global.css`，**必须同时覆盖 `.bar` 与 `.peg`** —— nprogress 默认把两个颜色都硬编码成 `#29d`，
只改 `.bar` 会看到"品牌色的线 + 蓝紫色拖尾光晕"这种怪组合。

---

## 第三部分：一次完整流程串讲（把这个背下来就懂项目了）

### 场景：新用户自助注册 → 登录 → 看到菜单 → 新增一个用户

```
【注册】
1. 前端 GET /api/auth/captcha
   → CaptchaService.issue()：来源级配额(60次/分钟) → 生成 4 位码 → Redis 存 auth:captcha:<id> (TTL 2min)
   → 返回 base64 图片

2. 前端 POST /api/auth/register {username, password, confirmPassword, captchaId, captchaCode}
   → RegistrationService.register()
      ├─ captcha.verify()  ← ★ 顺序很重要：必须在建用户和 BCrypt 之前
      │                      否则"刷接口"的成本已经付掉了（一次 SELECT + 一次 INSERT + 几十毫秒 BCrypt）
      ├─ PasswordRules.requireConfirmed()
      ├─ createUser()：查重(含逻辑删除口径) → INSERT sys_user → 分配 basic_role 角色
      ├─ savePassword()：BCrypt 加密 → INSERT sys_local_credential
      └─ issue()：Redis 建会话 → 签发 accessToken
   → Set-Cookie: lbl_refresh=<sid>; HttpOnly; SameSite=Lax; Path=/api/auth
   → 返回 { accessToken, user }

【登录（已有账号）】
3. POST /api/auth/login {username, password, rememberMe}
   → AuthService.login()
      ├─ LoginAttemptGuard.isLocked(账号+来源)  ← 双层限流：账号+IP 5次、纯 IP 20次，均锁 15 分钟
      ├─ 查 sys_user + sys_local_credential
      ├─ 密码/状态/凭据任一不通过 → recordFailure + 记 FAILURE/LOCKED 日志
      │   └─ 对外统一"用户名或密码错误"（防账号枚举），日志里区分具体原因（便于排查）
      ├─ 通过 → recordSuccess（清空该账号+来源 与 来源级 计数）
      ├─ passwordChangePolicy.requiresChange() → 是否需要强制改密
      ├─ SessionService.create()：
      │    Redis SET auth:session:<sid> = <LoginSession JSON>   TTL = 记住我?7天:2小时
      │    记住我时另 SET auth:session:absolute:<sid> TTL 14天   ← 绝对上限，持续操作也不能超
      │    SADD auth:user:sessions:<userId> <sid>
      └─ jwt.issue(sid, username, authVersion)
   → Set-Cookie: lbl_refresh=<sid>（记住我 = Max-Age 14天，否则会话级 Cookie）

【拿菜单】
4. GET /api/auth/me
   → 从 Cookie 读 sid → Redis 取会话 → 校验用户状态 + authVersion
   → 若 authMethod=PASSWORD 且会话标记了 passwordChangeRequired：
        permissions = [] 且 menus = []     ← ★ 两个字段必须"同进同退"
   → menus.selectByUserId()  （递归 CTE，见 1.6）
   → routes = routeCatalog() （全站页面目录，用于区分 403/404）
   → 返回 { principalType, user, roles, permissions, menus, routes }

5. AuthGuard 收到 → dispatch(setProfile) → AppLayout.buildMenu(menus) 渲染侧边栏

【点开用户管理】
6. 浏览器地址变 /system/user → DynamicPage
   → findMenuByPath(menus, '/system/user') 命中
   → resolveMenuComponent('system/user/index') → lazy(import('../pages/system/user/index.tsx'))
   → 渲染 UserPage

7. UserPage 的 SmartTable 发起 GET /api/system/users?pageNum=1&pageSize=10
   （详见 1.2 的 8 步生命周期）

【新增用户】
8. 点"新增用户" → UserDialog 打开（弹窗内按需 GET form-options 拿部门/角色候选）
9. 提交 POST /api/system/users
   → @PreAuthorize("hasAuthority('system:user:add')")
   → @OperationLog(module="用户管理", action="新增用户")  ← AOP 环绕，无论成功失败都记一条
   → UserService.create()
      ├─ PasswordRules.requireConfirmed()
      ├─ validateAssignment()：部门存在/未停用/在数据范围内 + 角色可分配 + 角色不含平台级权限
      ├─ requireUsernameAvailable()：两步查重（业务规则 + 含逻辑删除的唯一约束口径）
      ├─ INSERT sys_user（registration_source='ADMIN', auth_version=1）
      ├─ saveCredential(password, passwordChangeRequired=0)
      └─ replaceRoles()
```

---

## 第四部分：改代码时最容易踩的坑（务必先读）

| # | 坑 | 说明 |
| --- | --- | --- |
| 1 | **改权限码要改 4 处** | 前端 `<Permission code>` / 后端 `@PreAuthorize` / `init_data.sql` / `SystemPermissionInitializer`。漏一处就会出现"按钮看得见但点了 403"或"新库有、老库没有" |
| 2 | **`init_data.sql` 只在空库跑** | 老库升级靠 `SystemPermissionInitializer` + 手工 SQL。项目**没有迁移工具**，改表结构必须自己写 SQL 并记录 |
| 3 | **查重必须用"含逻辑删除"口径** | 见 1.7。用 `selectCount` 会"预检通过、插入失败" |
| 4 | **`ancestors` 匹配必须带逗号边界** | 见 1.5。用 `DeptPaths`，不要自己拼 `LIKE` |
| 5 | **改密/停用/改角色后必须 `authVersion++` 并清会话** | `UserService` 里都这么做了。不这么做，被停用的人靠旧会话还能继续操作 |
| 6 | **401 和 403 不能混用** | 前端逻辑依赖这个区分（401 续期、403 不续期）。新增异常处理时别破坏它 |
| 7 | **新增 `/api/auth/` 下的接口要判断归属** | 它到底"靠令牌"还是"靠 Cookie"？这决定它该不该进 `AUTH_BOOTSTRAP` 正则（`utils/request.ts:19`） |
| 8 | **新增页面必须重新 build** | `import.meta.glob` 是构建期扫描。开发模式保存即生效，生产环境必须重新 `npm run build` |
| 9 | **前端刷新 Cookie 的 Path 是 `/api/auth`** | 业务接口拿不到 sid。需要"当前会话 id"的地方，从 `authentication.getDetails()` 取（见 `AccountSessionController.currentSid`） |
| 10 | **弹窗组件在 `React.StrictMode` 下 effect 跑两遍** | 防重复提交要用 `useRef` 同步锁（见 `RoleDialog.tsx:28,57-59`） |
