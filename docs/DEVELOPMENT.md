# 二次开发指南：如何快速开发一个页面 / 一个完整模块

> 目标读者：**对这个项目刚上手、想赶紧开始写业务功能的人。**
> 本文给两条路径：**A. 五分钟加一个页面**（最简单，够大多数场景）；**B. 从零加一个完整模块**（带自己的表和权限）。
> 最后附**可直接复制粘贴的模板代码**。

---

## 〇、开始之前：必须先理解的一件事

**这个项目的页面不是"注册在路由表里"的，而是"由数据库菜单记录驱动"的。**

```
数据库 sys_menu 一条记录
   ├─ route_path = '/system/user'        → 浏览器地址
   └─ component  = 'system/user/index'   → 对应文件 frontend/src/pages/system/user/index.tsx
        ↑ 构建期由 import.meta.glob 扫描，所以文件必须真实存在
   再加上：这条菜单必须被授权给某个角色（sys_role_menu），否则登录后看不到、直接访问会 403
```

所以**新增页面的标准动作只有四步**，且**不需要改任何路由配置文件**：

| 步 | 做什么 | 在哪做 |
| --- | --- | --- |
| 1 | 建页面文件 | `frontend/src/pages/<模块>/index.tsx`（必须有 `export default`） |
| 2 | 建菜单记录（`menu_type = 'MENU'`，填 `route_path` + `component`） | 浏览器里「系统管理 → 菜单管理」 |
| 3 | 把菜单授权给需要的角色 | 「系统管理 → 角色管理 → 授权」 |
| 4 | 重新登录（或刷新页面） | — |

> ⚠️ **生产环境必须重新 `npm run build`。** `import.meta.glob` 是**构建期**扫描。开发模式下保存文件即生效；打包后的产物里没有这个文件，浏览器就无法在运行时执行一个从未被编译过的 `.tsx`。这是编译型 SPA 的固有限制，不是实现偷懒。
> 如果确实需要"不重新构建也能上新页面"，那属于微前端 / Module Federation 的范畴。

---

## 路径 A：五分钟加一个页面

### 场景

你想加一个「服务器清单」页面，地址 `/ops/server`，只读列表。

### A.1 建文件

新建 `frontend/src/pages/ops/server/index.tsx`：

```tsx
export default function ServerPage() {
  return (
    <div>
      <div className='page-head'>
        <div>
          <div className='page-kicker'>OPS</div>
          <h1 className='page-title'>服务器清单</h1>
        </div>
      </div>
      <div className='table-card'>这里放你的内容</div>
    </div>
  );
}
```

**注意三点**：
1. **必须是 `export default`。** `componentRegistry.tsx` 用的是 `lazy(() => import(...))`，`React.lazy` 要求模块有 default 导出。用命名导出会导致渲染时崩溃，而不是显示友好的诊断页。
2. 外层用 `page-head` / `page-title` / `page-kicker` / `table-card` 这些全局类（定义在 `frontend/src/styles/global.css`），这样**视觉风格自动和系统管理页一致**，不用自己写标题样式。
3. 目录名 = 菜单里的 `component` 值。`ops/server/index` ↔ `src/pages/ops/server/index.tsx`。

### A.2 建菜单

用 admin 登录 → 「系统管理 → 菜单管理」→ 新增：

| 字段 | 填什么 | 说明 |
| --- | --- | --- |
| 上级菜单 | 系统管理（或新建一个目录） | 想建新分组就先建一条 `DIR` 类型的菜单 |
| 菜单名称 | 服务器清单 | 侧边栏显示的文字 |
| 菜单类型 | **菜单** | |
| 路由名称 | `ops-server` | 路由的唯一英文名，建议 `${模块}-${页面}` |
| 路由地址 | `/ops/server` | 浏览器地址，全局唯一 |
| **前端组件** | `ops/server/index` | 这是个**下拉框**，选项来自 `availableComponents()`，即当前构建产物里真实存在的页面文件。所以**建完文件就能在下拉里选到** |
| 图标 | 任选一个 | 选项来自 `iconRegistry.tsx` 的白名单（13 个） |
| 排序 | 数字 | 同级排序 |
| 可见性 | 显示 | |
| 页面缓存 | 按需 | 勾上后页面在切换时保留 DOM（见下文"KeepAlive 的注意事项"） |

### A.3 授权

「系统管理 → 角色管理」→ 找到你的角色 → 「授权」→ 勾上新菜单 → 保存。

> **小技巧**：新建的菜单会**自动授予超级管理员**（`MenuService.grantToSuperAdmin`，`MenuService.java:67-70`），所以你用 admin 建完刷新就能看到。其他角色需要手工授权。

### A.4 刷新

刷新浏览器（或退出重新登录）。侧边栏出现「服务器清单」，点进去就是你的页面。

> **如果看到"组件不存在"的诊断页**：说明菜单里的 `component` 值和真实文件路径对不上。诊断页会直接告诉你应该创建哪个文件（`expectedPageFile`，`componentRegistry.tsx:68-70`），照着建即可。

---

## 路径 B：从零加一个完整模块

以「服务器清单」为例，做一个**带分页、搜索、增删改查、权限控制**的完整模块。

### B.1 后端：建表

新建 `backend/src/main/resources/db/migration-manual/2026xx_server.sql`（项目**没有迁移工具**，建议自己建个目录管理手工 SQL；如果你在空库上开发，也可以直接加进 `db/init_schema.sql`）。

```sql
CREATE TABLE biz_server
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    hostname     VARCHAR(120)  NOT NULL,
    ip           VARCHAR(64)   NOT NULL,
    env          VARCHAR(32)   NOT NULL DEFAULT 'PROD',   -- DEV / TEST / PROD
    owner        VARCHAR(64) NULL,
    dept_id      BIGINT NULL,                             -- ★ 想接数据范围就必须有这个字段
    remark       VARCHAR(500) NULL,
    status       TINYINT       NOT NULL DEFAULT 1,        -- 1 启用 / 0 停用
    -- ↓↓↓ 下面这几组是项目的统一约定，请照抄 ↓↓↓
    builtin      TINYINT       NOT NULL DEFAULT 0,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    created_by   BIGINT NULL,
    created_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   BIGINT NULL,
    updated_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_time DATETIME NULL,
    -- 带唯一索引的字段：注意逻辑删除不会释放唯一约束，查重必须用"含已删除"口径
    UNIQUE KEY uk_server_ip (ip),
    INDEX idx_server_dept (dept_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

**为什么这几组字段必须带上**：
- `deleted` + `deleted_time`：项目统一用逻辑删除（实体上标 `@TableLogic`）。
- `created_by/created_time/updated_by/updated_time`：由 `MybatisConfig.auditHandler` **自动填充**，你不用在 Service 里手动 set。
- `builtin`：系统内置记录标记（不可删）。业务表可以不要，但留着一个 INT 不亏。
- **⚠️ `UNIQUE KEY` + 逻辑删除的坑**：唯一索引**看不到 `deleted`**，所以"删掉一条再建同样 ip 的"会撞唯一键。所以查重必须用"含已删除"口径（见 B.3 的 `countIncludingDeletedByIp`）。

### B.2 后端：建包结构

项目统一的分包方式（照抄 `system/dept` 的结构）：

```
backend/src/main/java/org/lbl/biz/server/
├── controller/ServerController.java
├── entity/ServerEntity.java
├── mapper/ServerMapper.java
├── request/ServerRequest.java          （新增/修改的入参，用 record + 校验注解）
├── service/ServerService.java
└── vo/
    ├── ServerVO.java                    （列表/详情出参）
    └── ServerListVO.java                （列表行出参，可选）
```

#### `ServerEntity.java`

```java
package org.lbl.biz.server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_server")
public class ServerEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String hostname;
    private String ip;
    private String env;
    private String owner;
    private Long deptId;
    private String remark;
    private Integer status;
    private Integer builtin;

    @TableLogic
    private Integer deleted;
    private LocalDateTime deletedTime;

    // ★ 这几个字段名必须完全一致，MetaObjectHandler 按名字填充
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
```

#### `ServerMapper.java`

```java
package org.lbl.biz.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.lbl.biz.server.entity.ServerEntity;

@Mapper
public interface ServerMapper extends BaseMapper<ServerEntity> {

    /**
     * 统计占用该 IP 的记录数，包含已被逻辑删除的记录。
     * 口径必须与 biz_server.uk_server_ip 上的唯一索引一致 —— 逻辑删除只把 deleted 置 1，
     * 索引项还在，用 selectCount（自动附加 deleted = 0）做预检会出现"校验通过、插入失败"。
     * 见 UserMapper#countIncludingDeletedByUsername 的同类约定。
     *
     * @param excludeId 编辑时排除自身；新增时传 0
     */
    @Select("SELECT COUNT(1) FROM biz_server WHERE ip = #{ip} AND id != #{excludeId}")
    long countIncludingDeletedByIp(@Param("ip") String ip, @Param("excludeId") long excludeId);
}
```

#### `ServerRequest.java`

```java
package org.lbl.biz.server.request;

import jakarta.validation.constraints.*;

/**
 * 新增/修改服务器。
 * 注意：所有校验注解都要写 message，且是完整可展示的中文句子。
 * GlobalExceptionHandler#authoredOrFallback 会优先采用手写的 message；
 * 不写的话会回退成"字段名 + 约束类型"的机械拼装（甚至可能把正则原样漏给用户）。
 */
public record ServerRequest(
        @NotBlank(message = "请输入主机名")
        @Size(max = 120, message = "主机名最长 120 个字符")
        String hostname,

        @NotBlank(message = "请输入 IP 地址")
        @Pattern(regexp = "^((25[0-5]|2[0-4]\\d|[01]?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d?\\d)$",
                 message = "IP 地址格式不正确")
        String ip,

        @NotBlank(message = "请选择环境")
        @Pattern(regexp = "^(DEV|TEST|PROD)$", message = "环境必须是 DEV、TEST 或 PROD")
        String env,

        @Size(max = 64, message = "负责人最长 64 个字符")
        String owner,

        Long deptId,

        @Size(max = 500, message = "备注最长 500 个字符")
        String remark,

        @NotNull(message = "请选择状态")
        @Min(value = 0, message = "状态无效")
        @Max(value = 1, message = "状态无效")
        Integer status
) {
}
```

> **⚠️ 注意 `@Pattern`**：`GlobalExceptionHandler` 的注释里明确警告过，Bean Validation 的**内置**提示会按 JVM 默认语言本地化，而 `@Pattern` 的内置提示**会把正则插值进去**——中文环境下它是「需要匹配 ^(?=.*[a-z])…」，**把整条正则原样交给调用方**。所以**每个约束都要手写 `message`**。

#### `ServerService.java`

```java
package org.lbl.biz.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.lbl.biz.server.entity.ServerEntity;
import org.lbl.biz.server.mapper.ServerMapper;
import org.lbl.biz.server.request.ServerRequest;
import org.lbl.biz.server.vo.ServerVO;
import org.lbl.common.exception.BusinessException;
import org.lbl.common.result.PageResult;
import org.lbl.security.context.AccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ServerService {
    private final ServerMapper servers;
    private final AccessPolicy access;

    public ServerService(ServerMapper servers, AccessPolicy access) {
        this.servers = servers;
        this.access = access;
    }

    public PageResult<ServerVO> page(long pageNum, long pageSize, String keyword) {
        // ★ 分页参数必须自己校验：项目里所有分页接口都是这个口径
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException("分页参数无效，每页最多 100 条");
        }
        AccessPolicy.Actor actor = access.actor();
        LambdaQueryWrapper<ServerEntity> query = new LambdaQueryWrapper<ServerEntity>()
                .and(StringUtils.hasText(keyword), q -> q
                        .like(ServerEntity::getHostname, keyword)
                        .or().like(ServerEntity::getIp, keyword))
                .orderByDesc(ServerEntity::getId);

        // ★ 接入数据范围：ALL 不加条件；否则限定到可见部门 ∪ 本人
        //   想让这个模块也能按数据范围过滤，表里必须有 dept_id 字段，并照抄这段
        applyScopeByDeptId(query, actor);

        Page<ServerEntity> result = servers.selectPage(Page.of(pageNum, pageSize), query);
        return new PageResult<>(result.getRecords().stream().map(ServerVO::of).toList(),
                result.getTotal(), pageNum, pageSize);
    }

    /**
     * 按部门的通用数据范围过滤。写法与 AccessPolicy#applyUserScope 同构，
     * 但那里是给 UserEntity 写的，业务表需要自己拼一次。语义必须完全一致。
     */
    private void applyScopeByDeptId(LambdaQueryWrapper<ServerEntity> query, AccessPolicy.Actor actor) {
        if (actor.all()) return;
        query.and(condition -> {
            if (!actor.departments().isEmpty()) {
                condition.in(ServerEntity::getDeptId, actor.departments());
            } else {
                condition.isNull(ServerEntity::getDeptId);   // 没有部门范围的账号：只看到未分配部门的记录
            }
        });
    }

    public ServerVO detail(Long id) {
        AccessPolicy.Actor actor = access.actor();
        ServerEntity server = require(id);
        requireVisible(actor, server);
        return ServerVO.of(server);
    }

    @Transactional
    public ServerVO create(ServerRequest request) {
        AccessPolicy.Actor actor = access.actor();
        String ip = request.ip().trim();
        // ★ 查重必须用"含已删除"口径，见 Mapper 里的说明
        if (servers.countIncludingDeletedByIp(ip, 0L) > 0) {
            throw new BusinessException("该 IP 已被占用（已删除记录占用的 IP 不会被释放，请更换）");
        }
        requireDeptInScope(actor, request.deptId());
        ServerEntity server = new ServerEntity();
        apply(server, request);
        server.setBuiltin(0);
        servers.insert(server);
        return ServerVO.of(server);
    }

    @Transactional
    public ServerVO update(Long id, ServerRequest request) {
        AccessPolicy.Actor actor = access.actor();
        ServerEntity server = require(id);
        requireVisible(actor, server);
        String ip = request.ip().trim();
        if (servers.countIncludingDeletedByIp(ip, id) > 0) {
            throw new BusinessException("该 IP 已被占用（已删除记录占用的 IP 不会被释放，请更换）");
        }
        requireDeptInScope(actor, request.deptId());
        apply(server, request);
        servers.updateById(server);
        return ServerVO.of(server);
    }

    @Transactional
    public void remove(Long id) {
        AccessPolicy.Actor actor = access.actor();
        ServerEntity server = require(id);
        requireVisible(actor, server);
        if (server.getBuiltin() == 1) throw new BusinessException("内置记录不能删除");
        server.setDeletedTime(LocalDateTime.now());
        servers.updateById(server);   // 先写 deleted_time（@TableLogic 的 updateById 不写它）
        servers.deleteById(id);       // 再逻辑删除（@TableLogic 自动改成 UPDATE ... SET deleted = 1）
    }

    private void apply(ServerEntity server, ServerRequest request) {
        server.setHostname(request.hostname().trim());
        server.setIp(request.ip().trim());
        server.setEnv(request.env());
        server.setOwner(request.owner() == null ? null : request.owner().trim());
        server.setDeptId(request.deptId());
        server.setRemark(request.remark());
        server.setStatus(request.status());
    }

    private ServerEntity require(Long id) {
        ServerEntity server = servers.selectById(id);
        if (server == null) throw new BusinessException("服务器不存在");
        return server;
    }

    /** 可见 = 全部范围 / 记录没有部门 / 记录部门在自己的可见部门集合里。 */
    private void requireVisible(AccessPolicy.Actor actor, ServerEntity server) {
        if (actor.all()) return;
        if (server.getDeptId() == null) return;
        if (!actor.departments().contains(server.getDeptId())) {
            throw new BusinessException("无权操作数据范围之外的记录");
        }
    }

    private void requireDeptInScope(AccessPolicy.Actor actor, Long deptId) {
        if (deptId == null || actor.all()) return;
        if (!actor.departments().contains(deptId)) {
            throw new BusinessException("不能将记录分配到数据范围之外的部门");
        }
    }
}
```

#### `ServerVO.java`

```java
package org.lbl.biz.server.vo;

import org.lbl.biz.server.entity.ServerEntity;

import java.time.LocalDateTime;

public record ServerVO(Long id, String hostname, String ip, String env, String owner,
                       Long deptId, String remark, Integer status, LocalDateTime createdTime) {
    public static ServerVO of(ServerEntity server) {
        return new ServerVO(server.getId(), server.getHostname(), server.getIp(), server.getEnv(),
                server.getOwner(), server.getDeptId(), server.getRemark(), server.getStatus(),
                server.getCreatedTime());
    }
}
```

#### `ServerController.java`

```java
package org.lbl.biz.server.controller;

import jakarta.validation.Valid;
import org.lbl.biz.server.request.ServerRequest;
import org.lbl.biz.server.service.ServerService;
import org.lbl.biz.server.vo.ServerVO;
import org.lbl.common.result.PageResult;
import org.lbl.common.result.Result;
import org.lbl.system.log.aspect.OperationLog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/biz/servers")
public class ServerController {
    private final ServerService service;

    public ServerController(ServerService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('biz:server:list')")
    Result<PageResult<ServerVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                       @RequestParam(defaultValue = "10") long pageSize,
                                       @RequestParam(required = false) String keyword) {
        return Result.ok(service.page(pageNum, pageSize, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('biz:server:add', 'biz:server:update')")
    Result<ServerVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('biz:server:add')")
    @OperationLog(module = "服务器管理", action = "新增服务器")
    Result<ServerVO> create(@Valid @RequestBody ServerRequest request) {
        return Result.ok(service.create(request), "新增服务器成功");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('biz:server:update')")
    @OperationLog(module = "服务器管理", action = "修改服务器")
    Result<ServerVO> update(@PathVariable Long id, @Valid @RequestBody ServerRequest request) {
        return Result.ok(service.update(id, request), "修改服务器成功");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('biz:server:delete')")
    @OperationLog(module = "服务器管理", action = "删除服务器")
    Result<Void> delete(@PathVariable Long id) {
        service.remove(id);
        return Result.ok(null, "删除服务器成功");
    }
}
```

**Controller 的约定**（照抄即可）：
- 路由前缀 `/api/<域>/<资源复数>`，例如 `/api/system/users`、`/api/biz/servers`。
- **每个方法都要有 `@PreAuthorize`**，没有例外。
- **写操作都要有 `@OperationLog(module, action)`**，读操作不用。
- 返回 `Result.ok(data)` 或 `Result.ok(data, "中文成功提示")`。
- Controller 里**不写业务逻辑**，也不直接注入 Mapper。

### B.3 后端：注册权限码

新模块的权限码 `biz:server:list` / `add` / `update` / `delete` 需要**同时**加进两个地方：

**① `db/init_data.sql`**（新建数据库时生效）

```sql
-- 先建一个目录（如果还没有）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, route_name, route_path, component, icon, sort_order, builtin)
VALUES (100, 0, '运维管理', 'DIR', 'ops', '/ops', NULL, 'ToolOutlined', 3, 1);

-- 再建页面菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, route_name, route_path, component, icon, sort_order, builtin)
VALUES (101, 100, '服务器清单', 'MENU', 'ops-server', '/ops/server', 'ops/server/index', 'ToolOutlined', 1, 1);

-- 再建按钮权限
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, permission_code, sort_order, builtin)
VALUES (102, 101, '查询服务器', 'BUTTON', 'biz:server:list',   1, 1),
       (103, 101, '新增服务器', 'BUTTON', 'biz:server:add',    2, 1),
       (104, 101, '更新服务器', 'BUTTON', 'biz:server:update', 3, 1),
       (105, 101, '删除服务器', 'BUTTON', 'biz:server:delete', 4, 1);
```

**② `config/SystemPermissionInitializer.java`**（老库升级时自动补建 —— **不加这里，只能靠人工去菜单管理里点出来**）

```java
private static final List<PageSeed> PAGES = List.of(
        new PageSeed("登录日志", "system-login-log", "/system/login-log", "system/loginlog/index", "FileSearchOutlined", 5),
        new PageSeed("操作日志", "system-operation-log", "/system/operation-log", "system/operatelog/index", "HistoryOutlined", 6),
        // ↓ 新增这行
        new PageSeed("服务器清单", "ops-server", "/ops/server", "ops/server/index", "ToolOutlined", 1)
);

private static final List<PermissionSeed> PERMISSIONS = List.of(
        // ... 已有的 ...
        // ↓ 新增这四行。parentRoute 指向所属页面的 route_path
        new PermissionSeed("查询服务器", "biz:server:list",   "/ops/server", 1),
        new PermissionSeed("新增服务器", "biz:server:add",    "/ops/server", 2),
        new PermissionSeed("更新服务器", "biz:server:update", "/ops/server", 3),
        new PermissionSeed("删除服务器", "biz:server:delete", "/ops/server", 4)
);
```

> ⚠️ **`PermissionSeed` 的 `parentRoute` 必须指向页面的 `route_path`**，否则启动日志会打 `Skipped seeding button permission ... : the '...' page menu does not exist` 并跳过。

重启后端 → `SystemPermissionInitializer` 会自动补建这些菜单并**授予 super_admin**。

### B.4 前端：类型 + API 封装

#### `frontend/src/types/server.ts`

```ts
export interface ServerItem {
  id: number;
  hostname: string;
  ip: string;
  env: 'DEV' | 'TEST' | 'PROD';
  owner?: string;
  deptId?: number;
  remark?: string;
  status: number;
  createdTime?: string;
}

export interface ServerRequest {
  hostname: string;
  ip: string;
  env: ServerItem['env'];
  owner?: string;
  deptId?: number;
  remark?: string;
  status: number;
}
```

#### `frontend/src/api/server.ts`

```ts
import request from '../utils/request';
import type { PageResult, Result } from '../types/common';
import type { ServerItem, ServerRequest } from '../types/server';

export const getServers = (params: { pageNum: number; pageSize: number; keyword?: string }) =>
  request.get<Result<PageResult<ServerItem>>>('/biz/servers', { params }).then((r) => r.data.data);

export const getServer = (id: number) =>
  request.get<Result<ServerItem>>(`/biz/servers/${id}`).then((r) => r.data.data);

export const createServer = (payload: ServerRequest) =>
  request.post<Result<ServerItem>>('/biz/servers', payload).then((r) => r.data);

export const updateServer = (id: number, payload: ServerRequest) =>
  request.put<Result<ServerItem>>(`/biz/servers/${id}`, payload).then((r) => r.data);

export const deleteServer = (id: number) =>
  request.delete<Result<void>>(`/biz/servers/${id}`).then((r) => r.data);
```

**API 层约定**：`request` 的 `baseURL` 已经是 `/api`（`VITE_API_BASE_URL`），所以这里写 `/biz/servers` 而不是 `/api/biz/servers`。
返回 `Result<T>` 时用 `.then(r => r.data.data)` 直接拿数据；需要 `message` 的时候用 `.then(r => r.data)`。

### B.5 前端：列表页（用 `SmartTable`）

`frontend/src/pages/ops/server/index.tsx`：

```tsx
import { useRef, useState } from 'react';
import { App, Button, Input, Popconfirm, Space } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { deleteServer, getServers } from '../../../api/server';
import { SmartTable, type SmartTableRef } from '../../../components/SmartTable';
import { StatusTag } from '../../../components/StatusTag';
import { Permission } from '../../../permission/Permission';
import type { ServerItem } from '../../../types/server';
import { getApiErrorMessage } from '../../../utils/apiError';
import { ServerDialog } from './ServerDialog';

type ServerSearch = { keyword: string };

const ENV_LABEL: Record<string, string> = { DEV: '开发', TEST: '测试', PROD: '生产' };
const ENV_COLOR: Record<string, string> = { DEV: 'default', TEST: 'blue', PROD: 'red' };

export default function ServerPage() {
  const { message } = App.useApp();
  const tableRef = useRef<SmartTableRef<ServerSearch>>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<ServerItem | null>(null);

  const openDialog = (server: ServerItem | null = null) => {
    setEditing(server);
    setDialogOpen(true);
  };

  const columns: ColumnsType<ServerItem> = [
    { title: '主机名', dataIndex: 'hostname', width: 180 },
    { title: 'IP', dataIndex: 'ip', width: 150 },
    { title: '环境', dataIndex: 'env', width: 90,
      render: (value: string) => <StatusTag status={ENV_COLOR[value] as never} label={ENV_LABEL[value] ?? value} /> },
    { title: '负责人', dataIndex: 'owner', width: 110, render: (v) => v || '-' },
    { title: '备注', dataIndex: 'remark', render: (v) => v || '-' },
    { title: '状态', dataIndex: 'status', width: 90,
      render: (v: number) => <StatusTag status={v === 1 ? 'success' : 'default'} label={v === 1 ? '启用' : '停用'} /> },
    { title: '创建时间', dataIndex: 'createdTime', width: 170, render: (v?: string) => v?.replace('T', ' ') },
    {
      title: '操作', width: 160, fixed: 'right',
      render: (_, server) => (
        <Space size='middle'>
          <Permission code='biz:server:update'>
            <a onClick={() => openDialog(server)}><EditOutlined /> 编辑</a>
          </Permission>
          <Permission code='biz:server:delete'>
            <Popconfirm title='确定删除此服务器？' okText='删除' cancelText='取消'
              onConfirm={() => void deleteServer(server.id)
                .then(() => { message.success('删除成功'); tableRef.current?.reload(); })
                .catch((e) => message.error(getApiErrorMessage(e)))}>
              <a className='danger'><DeleteOutlined /> 删除</a>
            </Popconfirm>
          </Permission>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div className='page-head'>
        <div>
          <div className='page-kicker'>OPS</div>
          <h1 className='page-title'>服务器清单</h1>
        </div>
      </div>
      <div className='table-card'>
        <SmartTable<ServerItem, ServerSearch>
          ref={tableRef}
          rowKey='id'
          columns={columns}
          initialSearch={{ keyword: '' }}
          request={async ({ page, pageSize, search }) => {
            const result = await getServers({ pageNum: page, pageSize, keyword: search.keyword });
            // ★ 只需要返回 { list, total }，其余交给 SmartTable
            return { list: result.records, total: result.total };
          }}
          onRequestError={(error) => message.error(getApiErrorMessage(error, '无法获取服务器列表'))}
          toolbarClassName='table-toolbar'
          searchRender={({ search, setSearch, submit }) => (
            <Input value={search.keyword} prefix={<SearchOutlined />} placeholder='搜索主机名 / IP'
              onChange={(e) => setSearch({ keyword: e.target.value })} onPressEnter={() => submit()} />
          )}
          toolbarRender={({ submit }) => (
            <Space wrap>
              <Button icon={<ReloadOutlined />} onClick={() => submit()}>刷新</Button>
              <Permission code='biz:server:add'>
                <Button type='primary' icon={<PlusOutlined />} onClick={() => openDialog()}>新增服务器</Button>
              </Permission>
            </Space>
          )}
          pagination={{ pageSize: 10, showSizeChanger: false, showTotal: (v) => `共 ${v} 条记录` }}
          scroll={{ x: 1100 }}
        />
      </div>
      <ServerDialog open={dialogOpen} serverId={editing?.id ?? null}
        onClose={() => setDialogOpen(false)} onSaved={() => tableRef.current?.reload()} />
    </div>
  );
}
```

**关于 `SmartTable` 你需要知道的**：

| 能力 | 怎么用 |
| --- | --- |
| 自定义搜索区 | `searchRender={({ search, setSearch, submit, reset }) => ...}` —— 完全自由布局 |
| 声明式搜索区 | `searchConfig={[{ name: 'keyword', type: 'input', label: '关键字' }]}` —— 支持 `input`/`select`/`date`/`dateRange` |
| 自定义工具栏 | `toolbarRender={({ search, submit, reload, exportData }) => ...}` |
| 刷新表格 | `tableRef.current?.reload()`；删掉当前页最后一条时用 `reload({ page: currentPage - 1 })` |
| 读当前查询条件 | `tableRef.current?.getQueryParams()` |
| 导出 | 传 `onExport`，或像 `user/index.tsx` 那样自己在工具栏里调导出接口 |

**它内部已经帮你处理的**（所以你不要再自己写）：
- **请求竞态**：只有最后一次请求的结果会写入 state，快速翻页不会被慢响应覆盖。
- **输入与提交分离**：`search`（输入中）与 `submittedSearch`（已提交）是两个 state，**输入时不发请求**。
- **排序/筛选归一化**：帮你把 AntD 的 `sorter` 转成 `{ field, order }`。

### B.6 前端：表单弹窗

`frontend/src/pages/ops/server/ServerDialog.tsx`（模板，从 `RoleDialog.tsx` 改的）：

```tsx
import { useLayoutEffect, useRef, useState } from 'react';
import { App, Form, Input, Modal, Radio, Select, Spin } from 'antd';
import { createServer, getServer, updateServer } from '../../../api/server';
import { FieldLabel } from '../../../components/FieldLabel';
import type { ServerRequest } from '../../../types/server';
import { getApiErrorMessage } from '../../../utils/apiError';

interface ServerDialogProps {
  open: boolean;
  serverId: number | null;
  onClose: () => void;
  onSaved: () => void;
}

export function ServerDialog({ open, serverId, onClose, onSaved }: ServerDialogProps) {
  const [form] = Form.useForm<ServerRequest>();
  const { message } = App.useApp();
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(false);
  // ★ 防重复提交：ref 同步挡住同一轮渲染内的第二次调用（state 是异步的，挡不住）
  const submittingRef = useRef(false);
  const editing = serverId !== null;

  useLayoutEffect(() => {
    if (!open) return;
    let active = true;                      // ★ 防止"弹窗已关闭但请求才回来"造成的 setState 泄漏
    form.resetFields();
    setLoading(false);
    if (serverId === null) {
      form.setFieldsValue({ env: 'PROD', status: 1 } as Partial<ServerRequest>);
      return;
    }
    setLoading(true);
    void getServer(serverId)
      .then((detail) => { if (active) form.setFieldsValue(detail as unknown as ServerRequest); })
      .catch((error) => {
        if (!active) return;
        message.error(getApiErrorMessage(error, '无法加载服务器详情'));
        onClose();
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [form, message, onClose, open, serverId]);

  const submit = async () => {
    if (submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);
    try {
      const values = await form.validateFields();
      const response = editing ? await updateServer(serverId!, values) : await createServer(values);
      message.success(response.message || (editing ? '修改成功' : '新增成功'));
      onSaved();
      onClose();
    } catch (error) {
      // ★ 表单校验失败时 errorFields 有值，此时不要弹 message（字段下面已经有红字了）
      if ((error as { errorFields?: unknown }).errorFields) return;
      message.error(getApiErrorMessage(error, '保存失败'));
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  };

  return (
    <Modal
      className='system-dialog'        // ★ 统一弹窗外观（圆角/内边距/滚动区），别自己写
      open={open}
      width={600}
      title={editing ? '编辑服务器' : '新增服务器'}
      okText='确定' cancelText='取消'
      onCancel={onClose} onOk={() => void submit()}
      confirmLoading={submitting}
      okButtonProps={{ disabled: loading }}
      cancelButtonProps={{ disabled: submitting || loading }}
      closable={!submitting && !loading}
      maskClosable={!submitting && !loading}
      keyboard={!submitting && !loading}
      destroyOnHidden
      forceRender
    >
      {loading ? <div className='dialog-loading'><Spin /><span>正在加载服务器详情...</span></div> : (
        <Form form={form} layout='horizontal' labelCol={{ flex: '0 0 96px' }} colon={false} labelWrap requiredMark={false}>
          <Form.Item name='hostname' label='主机名'
            rules={[{ required: true, message: '请输入主机名' }, { max: 120, message: '最长 120 个字符' }]}>
            <Input placeholder='例如：prod-web-01' />
          </Form.Item>
          <Form.Item name='ip' label={<FieldLabel text='IP' hint='全局唯一。已删除记录占用的 IP 不会被释放。' />}
            rules={[{ required: true, message: '请输入 IP 地址' }]}>
            <Input placeholder='例如：10.0.1.20' />
          </Form.Item>
          <Form.Item name='env' label='环境' rules={[{ required: true, message: '请选择环境' }]}>
            <Select options={[{ value: 'DEV', label: '开发' }, { value: 'TEST', label: '测试' }, { value: 'PROD', label: '生产' }]} />
          </Form.Item>
          <Form.Item name='owner' label='负责人'><Input placeholder='可选' /></Form.Item>
          <Form.Item name='remark' label='备注' rules={[{ max: 500, message: '最长 500 个字符' }]}>
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
          <Form.Item name='status' label='状态' rules={[{ required: true }]}>
            <Radio.Group><Radio value={1}>启用</Radio><Radio value={0}>停用</Radio></Radio.Group>
          </Form.Item>
        </Form>
      )}
    </Modal>
  );
}
```

**弹窗模板的四个要点**（全项目的弹窗都遵守）：
1. `useRef` + `useState` 双重防重复提交。
2. `useLayoutEffect` + `active` 标志：防止弹窗关了、请求才回来时把状态写到已卸载的组件上。
3. `submitting` / `loading` 期间禁用 `closable` / `maskClosable` / `keyboard`，避免用户点蒙层把正在提交的表单关掉。
4. `className='system-dialog'` —— 统一外观（`pages/system/shared.css` 里定义）。**这个 CSS 由 `AppLayout` 全局引入，所以任何页面都能直接用。**

### B.7 走一遍验证清单

- [ ] 后端能启动，日志里没有 `Skipped seeding button permission`
- [ ] `sys_menu` 里有新菜单，`sys_role_menu` 里 `super_admin` 已授权
- [ ] 前端 `npm run build` 通过（`tsc --noEmit` 是 build 的一部分）
- [ ] 侧边栏出现新入口，点进去正常渲染
- [ ] 增删改查都成功，并且 `sys_operation_log` 里有对应记录
- [ ] 用一个只授了 `biz:server:list` 的角色登录：看不到新增/编辑/删除按钮；直接调 POST 接口返回 **403**
- [ ] 数据范围：用一个 `DEPT` 范围的角色登录，只能看到本部门的服务器

---

## 附录 A：常用代码片段

### 后端：在当前 Service 里拿"当前登录人"

```java
AccessPolicy.Actor actor = access.actor();   // 注入 AccessPolicy
Long userId   = actor.user().getId();
String name   = actor.user().getUsername();
Long deptId   = actor.user().getDeptId();
boolean isSuperAdmin = actor.superAdmin();
boolean canSeeAll    = actor.all();
Set<Long> visibleDepts = actor.departments();
```

### 后端：给查询加数据范围（业务表）

```java
// 表里必须有 dept_id 字段
private void applyScopeByDeptId(LambdaQueryWrapper<XxxEntity> query, AccessPolicy.Actor actor) {
    if (actor.all()) return;
    query.and(c -> {
        if (!actor.departments().isEmpty()) c.in(XxxEntity::getDeptId, actor.departments());
        else c.isNull(XxxEntity::getDeptId);
    });
}
```
（`AccessPolicy.applyUserScope` 是给 `UserEntity` 专用的，业务表需要自己拼，但**语义必须与它一致**。）

### 后端：分页

```java
Page<XxxEntity> result = mapper.selectPage(Page.of(pageNum, pageSize), query);
return new PageResult<>(voList, result.getTotal(), pageNum, pageSize);
```
（分页插件已在 `MybatisConfig` 注册，会自动加 `LIMIT`。参数校验 `pageSize <= 100` 要自己写。）

### 后端：逻辑删除

```java
entity.setDeletedTime(LocalDateTime.now());
mapper.updateById(entity);   // ★ 必须先写 deleted_time —— @TableLogic 的 updateById 不会写它
mapper.deleteById(id);       // @TableLogic 自动改成 UPDATE ... SET deleted = 1
```

### 后端：发一条站内通知

```java
notificationService.create(
    recipientUserId,
    "SERVER_OFFLINE",                          // type
    "服务器离线告警",                            // title（≤120）
    "prod-web-01 已连续 5 分钟无响应",           // content（≤550）
    "SERVER",                                  // businessType
    serverId                                   // businessId
);
```
通知会**在事务提交后**通过 WebSocket 推给目标用户（`NotificationService.afterCommit`）。

### 前端：导出 Excel

后端（流式写，不会把百万行读进内存）：
```java
@GetMapping("/export")
@PreAuthorize("hasAuthority('biz:server:export')")
void export(@RequestParam(required = false) String keyword, HttpServletResponse response) throws IOException {
    service.export(keyword, response);
}
```
Service 里照抄 `UserService.export`（`UserService.java:334-354`）的 `ExcelExportUtil.write(...)` 用法。

前端：
```ts
const response = await exportServers(keyword);
downloadBlob(response.data as Blob, `服务器清单_${new Date().toISOString().slice(0, 10)}.xlsx`);
```
（`downloadBlob` 在 `utils/download.ts`。）

### 前端：跳转与取值

```tsx
const navigate = useNavigate();
navigate(`/ops/server?businessId=${id}`);        // 站内跳转
const [params] = useSearchParams();              // 读 query
const businessId = params.get('businessId');
```

### 前端：时间格式化

项目里到处都是内联写法（`value?.replace('T', ' ')`），**没有统一工具函数**。如果你要新建，建议放 `utils/datetime.ts` 并逐步替换（`dayjs` 已在依赖里）。

---

## 附录 B：改这个项目时的禁忌清单

| # | 不要做 | 为什么 |
| --- | --- | --- |
| 1 | **不要绕过 `AccessPolicy` 自己写权限判断** | 它是"这个人能不能干这件事"的唯一真相来源。第二套判定必然与它漂移，而漂移的方向通常是**提权** |
| 2 | **不要用 `selectCount` 做带唯一索引字段的查重** | MyBatis-Plus 会自动附 `deleted = 0`，而唯一索引看不到 `deleted` → "预检通过、插入失败" |
| 3 | **不要自己拼 `LIKE 'ancestors%'`** | 必须用 `DeptPaths`。字符串前缀匹配会把兄弟部门的子孙也算成自己的后代（静默算错，不报错） |
| 4 | **改密/停用/改角色后不要忘记 `authVersion++` + `sessions.removeAll()`** | 否则被停用的人靠旧会话还能继续操作 |
| 5 | **不要在 `@Transactional` 里做网络 IO** | 会占住 Hikari 连接（池只有 10），几个并发就能把全站拖死。见 `ExternalLoginPersistence` 的类注释 |
| 6 | **不要让审计日志与业务共用事务** | 业务回滚会把"失败记录"一起回滚掉，而那恰恰是最该留的。审计用 `REQUIRES_NEW` |
| 7 | **不要给 `@Pattern` / `@Size` 等约束省略 `message`** | Bean Validation 的内置提示会把**正则原样**交给调用方（中文环境下也是），等于泄露实现细节且不可读 |
| 8 | **不要混用 401 和 403** | 前端逻辑依赖这个区分：401 触发静默续期，403 直接展示。混了会导致"权限不足被反复续期"或"登录失效却被当权限问题" |
| 9 | **不要在 `componentRegistry` 的 glob 目录下放非页面文件** | `src/pages/**` 下所有 `.tsx` 都会进候选清单（`error/`、`login/`、`register/`、`auth/`、`account/` 已被排除）。一个辅助组件放在 `pages/` 里会出现在菜单的"前端组件"下拉中 |
| 10 | **页面文件必须 `export default`** | `React.lazy` 要求 default 导出。写成命名导出会在渲染时崩溃，而不是显示 `PageUnavailable` 诊断页 |
| 11 | **新增系统级页面要改 4 处** | 前端 `<Permission>` / 后端 `@PreAuthorize` / `init_data.sql` / `SystemPermissionInitializer`。漏一处就会出现"看得见但 403"或"新库有、老库没有" |
| 12 | **不要假设有数据库迁移工具** | 项目**没有** Flyway/Liquibase。改表结构必须自己写 SQL、自己记录、自己在每个环境执行 |

---

## 附录 C：给"搬去公司内网"的额外提醒

详细的改名与配置清单在 `INTRANET-MIGRATION.md`。这里只提和开发直接相关的三条：

1. **`lbl.*` 配置前缀是自定义配置的命名空间**（`lbl.security.*`、`lbl.file-upload.*`、`lbl.log.retention.*`、`lbl.external-auth.*`）。改名要同时改 `application*.yml` 和 4 个 `@ConfigurationProperties` / `@Scheduled` 里的 `${lbl.xxx}` 占位符。
2. **`org.lbl` 包名**。改名会波及所有 Java 文件的 `package`、所有 `import org.lbl.*`、`LblShitApplication`、`pom.xml` 的 `groupId`，以及 `frontend/src/utils/passwordPolicy.ts` 里引用它的注释。
3. **`component` 字段里存的是路径字符串**（如 `system/user/index`），与包名无关，改名时**不受影响**——但如果你把 `src/pages/system` 目录也改名了，就要同步改数据库里所有 `component` 值。
