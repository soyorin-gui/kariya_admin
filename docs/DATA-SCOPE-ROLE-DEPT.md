# `sys_role_dept`：给角色指定"自定义数据范围"的完整落地方案

> 你问的是："`sys_role_dept` 这张表目前完全没用到，如果我要用起来应该怎么做，我对这个非常陌生没头绪。"
>
> **先纠正一个前提：这张表在当前代码里根本不存在。** 我用 `grep -r "role_dept|RoleDept|roleDept"` 扫过整个仓库（后端 Java + 前端 TS + SQL），**零匹配**。`init_schema.sql` 里也没有它。所以这不是"有一张表闲置着"，而是**"这个功能还没做"**。
>
> 这篇就是"从零把它做出来"的完整方案：原理 → 建表 → 后端 8 处改动 → 前端 3 处改动 → 测试 → 验收。**每一步都给可直接粘贴的代码。**

---

## 一、先搞清楚：现在的数据范围是怎么工作的

### 1.1 现状：数据范围只从"用户自己的部门"推导

`sys_role.data_scope` 有 4 个值（`init_schema.sql:80`，默认 `'SELF'`）：

| 值 | 含义 | 实现位置 |
| --- | --- | --- |
| `ALL` | 全部数据，不加任何条件 | `AccessPolicy.java:66` |
| `DEPT_AND_CHILDREN` | **用户本人所在部门**及其所有下级 | `AccessPolicy.java:67-77` |
| `DEPT` | **用户本人所在部门**（不含下级） | `AccessPolicy.java:78` |
| `SELF` | 只有自己的数据 | `AccessPolicy.java:79` |

核心代码在 `security/context/AccessPolicy.java` 的 `actor()` 方法（第 45-85 行）。它把角色的 `data_scope` 翻译成一个**"可见部门 id 集合"**：

```java
for (RoleEntity role : assigned) {
    if (role.getStatus() != 1) continue;                 // 停用的角色不授予任何权限
    switch (role.getDataScope()) {
        case "ALL" -> all = true;
        case "DEPT_AND_CHILDREN" -> {
            if (user.getDeptId() != null) visibleDepartments.add(user.getDeptId());
            DeptEntity ownDept = user.getDeptId() == null ? null : depts.selectById(user.getDeptId());
            if (ownDept != null) {
                // 展开整棵子树
                depts.selectList(DeptPaths.subtreeQuery(DeptPaths.selfPath(ownDept)))
                        .stream().map(DeptEntity::getId).forEach(visibleDepartments::add);
            }
        }
        case "DEPT" -> { if (user.getDeptId() != null) visibleDepartments.add(user.getDeptId()); }
        case "SELF" -> self = true;
        default -> throw new BusinessException("角色的数据范围配置无效");
    }
}
```

**关键点：`user.getDeptId()` 是唯一的来源。** 也就是说：

> **"这个人能看到哪些部门的数据" = "这个人自己坐在哪个部门" + "这个人的角色给了多大的范围"。**

由此推导出来的所有下游行为都是自动的：

| 用途 | 代码 |
| --- | --- |
| 用户列表过滤 | `UserService.page()` → `access.applyUserScope(query, actor)` |
| 用户导出过滤（**和列表同一套，很重要**） | `UserService.export()` → `access.applyUserScope(query, actor)` |
| 能不能管理某个用户 | `AccessPolicy.canSeeUser()` → `actor.departments().contains(target.getDeptId())` |
| 能不能管理某个部门 | `AccessPolicy.canManageDept()` → `actor.departments().contains(dept.getId())` |
| 部门列表只显示可见的 | `DeptService.withAncestorsForContext()` |
| 能把用户分配到哪个部门 | `UserService.validateAssignment()` → `actor.departments().contains(request.deptId())` |
| 部门负责人候选 | `DeptService.formOptions()` → `access.applyUserScope(usersQuery, actor)` |

### 1.2 现状的局限（也就是你想解决的问题）

`DEPT` / `DEPT_AND_CHILDREN` 都是"**以我自己的部门为锚点**"。所以下面这些场景做不到：

| 场景 | 现状 | 你想要的 |
| --- | --- | --- |
| 财务专员要能看"财务部 + 技术部"的数据 | ❌ 做不到 | ✅ 给角色指定这两个部门 |
| 项目负责人要能看"项目A组 + 项目B组"（跨部门） | ❌ 做不到 | ✅ 同上 |
| 华东区管理员要能看华东区所有部门（**和他在哪个部门无关**） | ❌ 做不到 | ✅ 同上 |
| 一个"审计"角色要看几个不相邻的部门 | ❌ 做不到 | ✅ 同上 |

### 1.3 `sys_role_dept` 要解决的问题

**把"可见部门"从"用户自己的部门"改成"角色上配置的一组部门"。**

```
现状：  用户 ──► 角色（只有范围类型）──► 可见部门 = f(用户自己的部门, 范围类型)
加表后：用户 ──► 角色 ──► sys_role_dept ──► 可见部门 = 角色指定的部门集合
                          （多对多）
```

这正好对应若依（RuoYi）等主流后台框架里的"**自定义数据权限**"。设计上完全同构：`sys_role.data_scope = 'CUSTOM'` + `sys_role_dept(role_id, dept_id)` 多对多表。

**好消息**：因为 `AccessPolicy.actor()` 已经把"数据范围"抽象成了一个 `Set<Long> departments`，所以**你只需要让 `CUSTOM` 分支往这个集合里加元素**，上面第 1.1 节表格里的**所有下游功能会自动生效**，一行都不用改。

---

## 二、先做两个设计决策（很重要，别跳过）

### 决策 1：自定义部门要不要包含下级？

| 方案 | 语义 | 适合 |
| --- | --- | --- |
| **A. 字面集合（推荐先做这个）** | 授予部门 X → 只能看 X，不能看 X 的子部门 | 精确控制；与若依一致 |
| **B. 字面集合 + 各授予部门的子树** | 授予部门 X → 能看 X 及其所有后代 | 更符合"授了技术部就想看他下面所有组"的直觉 |

**我的建议：先做 A**（实现最简单，且"要包括下级就把下级也勾上"这个心智模型很清楚）。
如果你想做 B，本文第四节末尾给了"从 A 改成 B"的**一行代码**。

### 决策 2：`maxScopeRank`（权限等级的"档位"）给多少

`AccessPolicy.scopeRank()`（第 374-382 行）把范围类型映射成一个数字档位：

```java
SELF=1 < DEPT=2 < DEPT_AND_CHILDREN=3 < ALL=4
```

这个档位被用在三处，用来实现"**不能设置/管理超过自身权限范围的东西**"：

| 方法 | 用档位做什么 |
| --- | --- |
| `requireScope(actor, dataScope)` | 不能给角色设置超过自身档位的数据范围 |
| `requireCreateDept` / `requireMoveDept` | 要求 `maxScopeRank() >= 3` 才能建子部门 / 移动部门 |
| `canAssignRole` / `canManageUser` | 目标的 `dataScope` 档位必须 `<= actor.maxScopeRank()` |

**`CUSTOM` 必须是几档？** 这是个**产品决定**，因为它会影响"能不能建子部门"：

| 给几档 | 后果 |
| --- | --- |
| **3（= `DEPT_AND_CHILDREN`）—— 推荐** | 自定义范围的账号可以在他**被授予的部门**下建子部门（`requireCreateDept` 还会额外用 `departments().contains(parentId)` 卡住范围，所以不会越界） |
| 2（= `DEPT`） | 自定义范围的账号**不能建子部门**，`requireCreateDept` 会直接拒绝（"只能在本部门及下级部门范围内新增子部门"） |
| 4（= `ALL`） | ❌ **不要**。`canManageUser`/`canAssignRole` 里 `targetScopes.allMatch(rank <= actor.maxScopeRank())` 会因此放行对 `ALL` 范围角色的管理，等于提权 |

**结论：给 3。** 下面代码按 3 写，并加了注释说明为什么不能给 4。

---

## 三、动手：后端（8 处改动）

### 3.1 建表

**① 加进 `backend/src/main/resources/db/init_schema.sql`**（新建库用）——放在 `sys_role_menu` 后面（第 129 行之后）：

```sql
-- 角色的「自定义数据范围」：指定该角色能看到哪些部门的数据。
-- 只在 sys_role.data_scope = 'CUSTOM' 时生效；其它范围类型下本表内容被忽略
-- （刻意不删除，这样把范围临时改回 DEPT 再改回 CUSTOM，配置不会丢）。
CREATE TABLE sys_role_dept
(
    role_id BIGINT NOT NULL,
    dept_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, dept_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

> **为什么用复合主键而不是自增 id**：这是纯关联表，复合主键天然防重复，也和 `sys_user_role` / `sys_role_menu` 的写法一致。
> **为什么不加外键**：项目里所有表都没加外键约束（靠应用层保证），保持一致。

**② 同时写一个迁移脚本**（老库升级用）。项目没有迁移工具，所以建议建目录 `backend/src/main/resources/db/migration-manual/`（参考 `INTRANET-MIGRATION.md` 第 5.4 节）：

`backend/src/main/resources/db/migration-manual/20260101_add_role_dept.sql`
```sql
-- 为「角色的自定义数据范围」新增关联表。
-- 执行前提：sys_role / sys_dept 已存在（即已执行过 init_schema.sql）。
-- 本脚本幂等，但请只在一个环境执行一次。

CREATE TABLE IF NOT EXISTS sys_role_dept
(
    role_id BIGINT NOT NULL,
    dept_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, dept_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 执行后验证（应返回 0 行）：
--   SELECT COUNT(1) FROM sys_role_dept;
-- 验证表结构：
--   SHOW CREATE TABLE sys_role_dept;
```

> ⚠️ **不需要给任何角色预置数据**。`data_scope` 默认还是 `'SELF'`，现有角色的行为**完全不变**，这是一个纯增量的改动。

### 3.2 新建 Mapper

`backend/src/main/java/org/lbl/system/role/mapper/RoleDeptMapper.java`

```java
package org.lbl.system.role.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 角色的「自定义数据范围」：role ↔ dept 多对多。
 * <p>
 * 写法与 {@link RoleMenuMapper} 完全同构 —— 项目里的关联表都长这个样子：
 * 复合主键 + 一个插入 + 一个按 roleId 删除 + 一个按 roleId 查询。
 */
@Mapper
public interface RoleDeptMapper {

    @Select("SELECT dept_id FROM sys_role_dept WHERE role_id = #{roleId} ORDER BY dept_id")
    List<Long> selectDeptIds(Long roleId);

    @Delete("DELETE FROM sys_role_dept WHERE role_id = #{roleId}")
    void deleteByRoleId(Long roleId);

    /**
     * 部门被删除时的级联清理。
     * <p>
     * 这个方法必须存在：sys_dept 是逻辑删除，如果这里不清理，被删掉的部门 id 会
     * 永远留在 sys_role_dept 里。它不会造成越权（AccessPolicy 展开时会因为查不到
     * 部门而跳过），但会让"这个角色到底配了几个部门"和界面显示对不上。
     */
    @Delete("DELETE FROM sys_role_dept WHERE dept_id = #{deptId}")
    void deleteByDeptId(Long deptId);

    @Insert("INSERT INTO sys_role_dept (role_id, dept_id) VALUES (#{roleId}, #{deptId})")
    void insert(@Param("roleId") Long roleId, @Param("deptId") Long deptId);

    /**
     * 批量取多个角色的自定义部门，供 AccessPolicy#actor 一次性装配，避免 N+1。
     * <p>
     * 写法注意：注解式动态 SQL 必须用 &lt;script&gt; 包裹，且文本块的第一行会被
     * 当作换行——项目里既有的批量查询都是这个写法（见 MenuMapper#selectPermissionCodesByUserIds）。
     */
    @Select("""
            <script>
            SELECT role_id AS roleId, dept_id AS deptId
            FROM sys_role_dept
            WHERE role_id IN
            <foreach collection='roleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            ORDER BY role_id, dept_id
            </script>
            """)
    List<RoleDeptAssignment> selectByRoleIds(@Param("roleIds") List<Long> roleIds);
}
```

**还要建一个 VO**，与 `RolePermissionAssignment` / `RoleUserCount` 保持同一风格：

`backend/src/main/java/org/lbl/system/role/vo/RoleDeptAssignment.java`

```java
package org.lbl.system.role.vo;

/** 批量查询「角色 → 自定义部门」的返回行。与 RolePermissionAssignment / RoleUserCount 同一风格。 */
public record RoleDeptAssignment(Long roleId, Long deptId) {
}
```

> **注意**：MyBatis 用 `role_id AS roleId` 的别名映射到 record 需要开启下划线转驼峰或显式别名。项目里 `RoleUserCount`（`SELECT role_id AS roleId, COUNT(1) AS userCount`）就是这么写的，且 `BatchMapperSqlTest` 专门测了这类批量 SQL 的 `foreach` 展开，所以照抄即可。

### 3.3 放开 `data_scope` 的取值范围（4 处）

新增 `CUSTOM` 之后，**所有校验 data_scope 合法性的地方都要放行它**，否则会出现"表单能选、后端 400"。

**① `system/role/request/RoleRequest.java:22`**

```java
// 改前
@Pattern(regexp = "^(ALL|DEPT_AND_CHILDREN|DEPT|SELF)$", message = "数据范围必须是 ALL、DEPT_AND_CHILDREN、DEPT 或 SELF")

// 改后
@Pattern(regexp = "^(ALL|DEPT_AND_CHILDREN|DEPT|SELF|CUSTOM)$",
         message = "数据范围必须是 ALL、DEPT_AND_CHILDREN、DEPT、SELF 或 CUSTOM")
String dataScope,
```

**② `security/context/AccessPolicy.java:374-382` 的 `scopeRank`**

```java
private int scopeRank(String scope) {
    return switch (scope) {
        case "SELF" -> 1;
        case "DEPT" -> 2;
        case "DEPT_AND_CHILDREN" -> 3;
        // ★ CUSTOM 与 DEPT_AND_CHILDREN 同为 3 档，理由见文档第二节「决策 2」：
        //   - 给 2 会导致自定义范围的账号无法建子部门；
        //   - 给 4 会让 canAssignRole / canManageUser 里的
        //     "目标档位 <= 自身档位" 判定放行 ALL 范围的角色，等于提权。
        case "CUSTOM" -> 3;
        case "ALL" -> 4;
        default -> throw new BusinessException("数据范围必须是 ALL、DEPT_AND_CHILDREN、DEPT、SELF 或 CUSTOM");
    };
}
```

**③ `RoleDialog.tsx:15-20`** 的 `dataScopeOptions`（见 3.7 节前端部分）。

**④ `frontend/src/types/role.ts:5`**

```ts
// 改前
dataScope: 'ALL' | 'DEPT_AND_CHILDREN' | 'DEPT' | 'SELF';
// 改后
dataScope: 'ALL' | 'DEPT_AND_CHILDREN' | 'DEPT' | 'SELF' | 'CUSTOM';
```

### 3.4 【核心】改 `AccessPolicy.actor()` —— 唯一的逻辑改动点

`security/context/AccessPolicy.java`。

**第一步：注入 `RoleDeptMapper`。**

```java
// 字段
private final RoleDeptMapper roleDepts;

// 构造器（把 RoleDeptMapper 加进参数列表最后一个）
public AccessPolicy(UserMapper users, RoleMapper roles, RoleMenuMapper roleMenus, MenuMapper menus,
                    DeptMapper depts, RoleDeptMapper roleDepts) {
    this.users = users;
    this.roles = roles;
    this.roleMenus = roleMenus;
    this.menus = menus;
    this.depts = depts;
    this.roleDepts = roleDepts;
}
```

（记得 `import org.lbl.system.role.mapper.RoleDeptMapper;`）

**第二步：在 `actor()` 的 switch 里加 `CUSTOM` 分支。**

先改方法开头，把"需要 CUSTOM 部门集合的角色 id"收集起来，**一次性批量查**（不要在循环里查库）：

```java
public Actor actor() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) throw new UnauthorizedException("登录状态已失效");
    UserEntity user = users.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, authentication.getName()));
    if (user == null || user.getStatus() != 1) throw new UnauthorizedException("登录状态已失效");
    List<RoleEntity> assigned = roles.selectAssignedByUserId(user.getId());
    boolean superAdmin = containsSuperAdmin(assigned);
    Set<String> permissions = permissions(user.getId());
    Set<Long> visibleDepartments = new HashSet<>();
    boolean all = superAdmin;
    boolean self = false;

    // ★ 新增：先算出哪些生效角色是 CUSTOM 范围，一次性把它们的部门配好。
    //   放在循环外批量查，避免"角色多的时候一个角色一次查询"（N+1）。
    List<Long> customRoleIds = assigned.stream()
            .filter(role -> role.getStatus() == 1 && "CUSTOM".equals(role.getDataScope()))
            .map(RoleEntity::getId)
            .toList();
    Map<Long, List<Long>> customDeptsByRole = new HashMap<>();
    if (!customRoleIds.isEmpty()) {
        roleDepts.selectByRoleIds(customRoleIds).forEach(row ->
                customDeptsByRole.computeIfAbsent(row.roleId(), ignored -> new ArrayList<>()).add(row.deptId()));
    }

    for (RoleEntity role : assigned) {
        if (role.getStatus() != 1) continue;
        switch (role.getDataScope()) {
            case "ALL" -> all = true;
            case "DEPT_AND_CHILDREN" -> {
                if (user.getDeptId() != null) visibleDepartments.add(user.getDeptId());
                DeptEntity ownDept = user.getDeptId() == null ? null : depts.selectById(user.getDeptId());
                if (ownDept != null) {
                    depts.selectList(DeptPaths.subtreeQuery(DeptPaths.selfPath(ownDept)))
                            .stream().map(DeptEntity::getId).forEach(visibleDepartments::add);
                }
            }
            case "DEPT" -> { if (user.getDeptId() != null) visibleDepartments.add(user.getDeptId()); }
            // ★ 新增分支：可见部门完全来自 sys_role_dept，与用户自己坐在哪个部门无关。
            //   这里刻意 <b>不校验部门是否存在 / 是否停用</b>：
            //   ① 不存在（部门已被删除）的 id 在 sys_dept 里查不到，下游按 dept_id 匹配时
            //      天然不会命中任何记录，不会造成越权；
            //   ② 停用部门的语义是"不能再往里面放人"，不是"整条分支下线"——与 actor()
            //      上面那段关于 status 的注释保持同一口径（已经在里面的人不受影响）。
            case "CUSTOM" -> customDeptsByRole.getOrDefault(role.getId(), List.of())
                    .forEach(visibleDepartments::add);
            case "SELF" -> self = true;
            default -> throw new BusinessException("角色的数据范围配置无效");
        }
    }
    return new Actor(user, superAdmin, permissions, all, self, visibleDepartments,
            assigned.stream().filter(role -> role.getStatus() == 1).mapToInt(role -> scopeRank(role.getDataScope())).max().orElse(0));
}
```

**就这些。** `applyUserScope` / `canSeeUser` / `canManageDept` / `requireCreateDept` / `requireMoveDept` / `DeptService.withAncestorsForContext` / `UserService.validateAssignment` **全部自动生效**，因为它们读的都是 `actor.departments()`。

需要补的 import：
```java
import org.lbl.system.role.mapper.RoleDeptMapper;
import org.lbl.system.role.vo.RoleDeptAssignment;
import java.util.ArrayList;
import java.util.List;
```
（`HashMap` / `Map` / `HashSet` / `Set` / `Collectors` 已经在文件里 import 了。）

**从方案 A 改成方案 B（授予部门时连子树一起包含）**：只改 `CUSTOM` 那一行：

```java
case "CUSTOM" -> {
    for (Long deptId : customDeptsByRole.getOrDefault(role.getId(), List.of())) {
        visibleDepartments.add(deptId);
        DeptEntity custom = depts.selectById(deptId);
        // ★ 用 DeptPaths 展开子树，绝不能自己拼 LIKE 'ancestors%'（见 DeptPaths 类注释：
        //   无逗号边界的前缀匹配会把兄弟部门的子孙也算成后代，静默算错、不报错）
        if (custom != null && custom.getStatus() == 1) {
            depts.selectList(DeptPaths.subtreeQuery(DeptPaths.selfPath(custom)))
                    .stream().map(DeptEntity::getId).forEach(visibleDepartments::add);
        }
    }
}
```
（成本：每个 CUSTOM 部门一次查询。部门数量多时建议改成一次性 `selectList` 拿全表再在内存里算。）

### 3.5 改 `RoleService` —— 授权与校验

`system/role/service/RoleService.java`。

**① 注入 `RoleDeptMapper` 和 `DeptMapper`。**

```java
private final RoleDeptMapper roleDepts;
private final DeptMapper depts;

public RoleService(RoleMapper roles, RoleMenuMapper roleMenus, MenuMapper menus, UserRoleMapper userRoles,
                   AccessPolicy access, RoleDeptMapper roleDepts, DeptMapper depts) {
    this.roles = roles;
    this.roleMenus = roleMenus;
    this.menus = menus;
    this.userRoles = userRoles;
    this.access = access;
    this.roleDepts = roleDepts;
    this.depts = depts;
}
```
（import：`org.lbl.system.role.mapper.RoleDeptMapper`、`org.lbl.system.dept.mapper.DeptMapper`、`org.lbl.system.dept.entity.DeptEntity`）

**② 提供"读"和"写"两个方法。**

```java
/** 角色当前配置的自定义部门 id。非 CUSTOM 范围时也返回配置内容（见下方 grantDepts 的说明）。 */
public List<Long> deptIds(Long id) {
    access.requireManageRole(access.actor(), require(id));
    return roleDepts.selectDeptIds(id);
}

/**
 * 保存角色的自定义数据范围。
 * <p>
 * 三条校验，缺一不可：
 * <ol>
 *   <li><b>角色必须是 CUSTOM 范围</b>：给一个 DEPT 范围的角色配部门列表没有任何意义，
 *       而 AccessPolicy 也会忽略它 —— 允许保存只会造出"界面配了、实际不生效"的困惑。
 *       反过来（CUSTOM 范围但一个部门都没配）是允许的，那表示"看不到任何部门的数据"，
 *       是一个明确且安全的状态。</li>
 *   <li><b>部门必须真实存在</b>：与 grantMenus 里 "授权菜单中包含不存在的记录" 同一口径。</li>
 *   <li><b>不能授予自己数据范围之外的部门</b>：这是最关键的一条 —— 没有它，
 *       任何能编辑角色的管理员都能给自己配一个 ALL 等价的自定义范围，等于提权。
 *       超管豁免。</li>
 * </ol>
 */
@Transactional
public void grantDepts(Long id, List<Long> deptIds) {
    AccessPolicy.Actor actor = access.actor();
    RoleEntity role = require(id);
    access.requireManageRole(actor, role);
    if (!"CUSTOM".equals(role.getDataScope())) {
        throw new BusinessException("只有「自定义数据范围」的角色才能指定部门");
    }
    List<Long> ids = deptIds == null ? List.of()
            : deptIds.stream().filter(value -> value != null && value > 0).distinct().toList();
    if (!ids.isEmpty()) {
        if (depts.selectCount(new LambdaQueryWrapper<DeptEntity>().in(DeptEntity::getId, ids)) != ids.size()) {
            throw new BusinessException("自定义范围中包含不存在的部门");
        }
        if (!actor.superAdmin() && !actor.all()) {
            List<Long> outOfScope = ids.stream().filter(deptId -> !actor.departments().contains(deptId)).toList();
            if (!outOfScope.isEmpty()) {
                throw new BusinessException("不能授予自身数据范围之外的部门");
            }
        }
    }
    roleDepts.deleteByRoleId(id);
    ids.forEach(deptId -> roleDepts.insert(id, deptId));
}
```
（import：`com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper` 已在该文件里。）

**③ 角色删除时清理关联。** 改 `RoleService.remove()`（第 100-110 行）：

```java
@Transactional
public void remove(Long id) {
    AccessPolicy.Actor actor = access.actor();
    RoleEntity role = require(id);
    access.requireManageRole(actor, role);
    if (role.getBuiltin() == 1) throw new BusinessException("内置角色不能删除");
    if (userRoles.countByRoleId(id) > 0) throw new BusinessException("该角色已分配给用户，不能删除");
    roleMenus.deleteByRoleId(id);
    roleDepts.deleteByRoleId(id);          // ★ 新增这一行
    role.setDeletedTime(LocalDateTime.now());
    roles.updateById(role);
    roles.deleteById(id);
}
```

> **为什么必须加**：不加的话，删掉角色 A 再新建角色 B（自增 id 不同，所以不会撞车）—— 问题不是撞车，而是**残留的 role_id 永远挂在表里**，久了之后 `sys_role_dept` 会积累一堆指向已删除角色的记录，排查"某人的数据范围怎么不对"时会看到幽灵配置。

**④ `RoleVO` 增加 `deptIds` 字段（可选但推荐）。** 这样前端打开编辑弹窗时能一次拿到配置。

`system/role/vo/RoleVO.java`（当前只有 276 字节，是个小 record）：
```java
public record RoleVO(Long id, String roleName, String roleCode, String dataScope, Integer status,
                     Integer builtin, long userCount, LocalDateTime createdTime, boolean manageable,
                     List<Long> customDeptIds) { ... }
```

然后在 `RoleService.toView(...)` 里补上。**⚠️ 注意 N+1**：`list()` 方法对每一行都调 `toView`，如果在那里查 `roleDepts.selectDeptIds(role.getId())`，列表页就会变成 N+1 查询。正确做法是仿照现有的 `rolePermissions(ids)` 批量预取：

```java
public List<RoleVO> list(String keyword) {
    // ... 现有代码 ...
    // ★ 仿照 permissions 的做法：先按需判定，再批量取
    Map<Long, List<Long>> customDepts = actor.superAdmin() ? Map.of() : ...;
    // 实际写法：只有非超管才需要这个字段（超管不需要看"能不能配"）
    // 更简单也更省事的做法：列表不返回 customDeptIds，只在 detail(id) 里返回。
}
```

**我的建议：`list()` 不返回 `customDeptIds`，只在 `detail(id)` 里返回。** 理由：列表页不需要它（列表只显示"数据范围"这个名字），而编辑弹窗走的是 `detail` 接口（`RoleDialog.tsx:42` 的 `getRole(roleId)`）。这样一行 N+1 都不会有。

**⑤ `RoleService.detail()` 里补上：**

```java
public RoleVO detail(Long id) {
    AccessPolicy.Actor actor = access.actor();
    RoleEntity role = require(id);
    Set<String> permissions = actor.superAdmin() ? Set.of() : accessRolePermissions(id);
    access.requireManageRole(actor, role, permissions);
    return toView(role, actor, userRoles.countByRoleId(id), permissions, roleDepts.selectDeptIds(id));
}
```

（相应地给 `toView` 加一个带 `deptIds` 的重载，或者把它做成最后一个参数、其它调用点传 `List.of()`。）

### 3.6 加两个接口

`system/role/controller/RoleController.java`，仿照现有的 `menuIds` / `grantable-menus` / `PUT menu-ids` 三件套：

```java
/**
 * 角色当前配置的自定义部门 id。
 * <p>
 * 权限码用 system:role:update 而不是 system:role:grant：
 * 数据范围是角色属性的一部分（它在 RoleDialog 里和"数据范围"下拉框在一起），
 * 而 system:role:grant 管的是"菜单权限"授权（RolePermissionDialog）。
 * 混用会造成"能编辑角色却打不开数据范围"的错配。
 */
@GetMapping("/{id}/dept-ids")
@PreAuthorize("hasAuthority('system:role:update')")
Result<List<Long>> deptIds(@PathVariable Long id) {
    return Result.ok(service.deptIds(id));
}

@PutMapping("/{id}/dept-ids")
@PreAuthorize("hasAuthority('system:role:update')")
@OperationLog(module = "角色管理", action = "配置角色数据范围")
Result<Void> grantDepts(@PathVariable Long id, @RequestBody List<@NotNull Long> deptIds) {
    service.grantDepts(id, deptIds);
    return Result.ok(null, "数据范围保存成功");
}
```

> ⚠️ **`data_scope` 从别的值改成 `CUSTOM` 之后，必须再调一次这个接口才算配完**。所以前端保存流程要变成"先 PUT 角色（提交 dataScope=CUSTOM），再 PUT dept-ids"。见 3.7。

**关于 `@NotNull` 的位置**：`List<@NotNull Long>` 这种写法项目里已经在用（`RoleController.java:74` 的 `List<@NotNull Long> menuIds`），照抄即可。

### 3.7 前端：`RoleDialog` 加部门树

**① `frontend/src/types/role.ts`** —— 放开 `dataScope` 联合类型（见 3.3 第 ④ 条），并给 `Role` 加可选字段：

```ts
export interface Role {
  id: number;
  roleName: string;
  roleCode: string;
  dataScope: Role['dataScope'];
  status: number;
  builtin: number;
  userCount: number;
  createdTime?: string;
  manageable: boolean;
  /** 仅 dataScope === 'CUSTOM' 时有意义：该角色配置的部门 id。**/
  customDeptIds?: number[];
}
```
（注意：`dataScope` 被声明为 `Role['dataScope']` 是自引用，这里要改成显式的联合类型，或者单独抽一个 `export type DataScope = 'ALL' | ... | 'CUSTOM';`。推荐后者，更清楚。）

**② `frontend/src/api/role.ts`** —— 加两个接口：

```ts
export const getRoleDeptIds = (id: number) =>
  request.get<Result<number[]>>(`/system/roles/${id}/dept-ids`).then((r) => r.data.data);

export const grantRoleDepts = (id: number, deptIds: number[]) =>
  request.put<Result<void>>(`/system/roles/${id}/dept-ids`, deptIds).then((r) => r.data);
```
（`baseURL` 已经是 `/api`，所以路径是 `/system/roles/...`。请对照 `api/role.ts` 里现有写法确认前缀。）

**③ `RoleDialog.tsx`** —— 三处改动。

改动 1：`dataScopeOptions` 加一项

```tsx
const dataScopeOptions = [
  { value: 'ALL', label: '全部数据权限' },
  { value: 'DEPT_AND_CHILDREN', label: '本部门及下级' },
  { value: 'DEPT', label: '仅本部门数据' },
  { value: 'SELF', label: '仅本人数据' },
  // ★ 新增
  { value: 'CUSTOM', label: '自定义部门' },
];
```

改动 2：监听 `dataScope` 的变化，条件渲染部门树

```tsx
// 在组件里加：
const dataScope = Form.useWatch('dataScope', form);
const [deptTree, setDeptTree] = useState<DeptTreeNode[]>([]);
const [checkedDeptIds, setCheckedDeptIds] = useState<number[]>([]);

// 打开弹窗时，如果角色是 CUSTOM，同时拉部门树和已配的部门 id
useLayoutEffect(() => {
  if (!open) return;
  let active = true;
  setCheckedDeptIds([]);
  // 部门树用现有的 getDepts（部门管理页已经在用），拿到平铺列表后自己组树
  void getDepts().then((list) => { if (active) setDeptTree(toTree(list)); });
  if (roleId !== null) {
    void getRoleDeptIds(roleId).then((ids) => { if (active) setCheckedDeptIds(ids); });
  }
  return () => { active = false; };
}, [open, roleId]);
```

改动 3：在 `dataScope` 表单项下面条件渲染树

```tsx
{dataScope === 'CUSTOM' && (
  <Form.Item label={<FieldLabel text='可见部门'
      hint='该角色只能看到所勾选部门的数据，与成员自己坐在哪个部门无关。不勾选表示看不到任何部门的数据。' />}>
    <div className='permission-tree'>
      <Tree
        checkable
        checkedKeys={checkedDeptIds}
        onCheck={(keys) => setCheckedDeptIds(keys as number[])}
        treeData={deptTree}
        fieldNames={{ title: 'deptName', key: 'id', children: 'children' }}
        defaultExpandAll
      />
    </div>
  </Form.Item>
)}
```

> **可以直接复用的东西**：
> - `className='permission-tree'` —— 已经在 `pages/system/role/index.css:2` 里定义好了（圆角、内边距、最大高度 52vh + 滚动、内部 AntD Tree 背景透明）。`RolePermissionDialog.tsx:63` 就是用它包菜单树的。**不用写新 CSS。**
> - `toTree` —— `pages/system/menu/index.tsx:20-27` 和 `pages/system/dept/DeptDialog.tsx:16-24` 各有一份实现（这个函数的重复问题在 `ASSESSMENT.md` 里提了，理想做法是抽到 `utils/tree.ts` 共用）。
> - **注意 `getDepts` 返回的是当前登录人能看到的部门**（后端 `DeptService.list()` 会按数据范围过滤并补上祖先节点）。这正好是"你能授予的范围"，与后端 `grantDepts` 的校验口径一致。**但要注意祖先节点本身不在 `actor.departments()` 里**，勾上会被后端拒绝 —— 详见 3.8 的"已知边界"。

改动 4：`submit()` 里在保存角色之后，如果是 CUSTOM 再保存部门

```tsx
const submit = async () => {
  if (submittingRef.current) return;
  submittingRef.current = true;
  setSubmitting(true);
  try {
    const values = await form.validateFields();
    const response = editing ? await updateRole(roleId!, values) : await createRole(values);
    // ★ 角色本身保存成功后，再保存自定义部门。
    //   顺序不能反：后端 grantDepts 会校验"角色必须是 CUSTOM 范围"，
    //   所以必须先让 dataScope 落库，否则第一次保存必然报"只有自定义数据范围的角色才能指定部门"。
    const targetId = editing ? roleId! : response.data.id;
    if (values.dataScope === 'CUSTOM') {
      await grantRoleDepts(targetId, checkedDeptIds);
    }
    message.success(response.message || (editing ? '修改角色成功' : '新增角色成功'));
    onSaved();
    onClose();
  } catch (error) {
    if ((error as { errorFields?: unknown }).errorFields) return;
    message.error(getApiErrorMessage(error, '保存角色失败'));
  } finally {
    submittingRef.current = false;
    setSubmitting(false);
  }
};
```

> ⚠️ **两个接口不是一个事务**。如果 `updateRole` 成功而 `grantRoleDepts` 失败，角色会停在"dataScope=CUSTOM 但没有任何部门"的状态 —— 这个状态是**安全的**（看不到任何部门数据），但用户会困惑。
> **两个可选做法**：① 什么都不做，接受这个中间态（最简单，且失败方向安全）；② 在后端加一个组合接口 `PUT /roles/{id}` 支持一次提交 `{...roleFields, customDeptIds}`，让两端在同一个事务里。**生产环境我建议 ②**。

### 3.8 两个必须知道的边界

**① `DeptService.list()` 会给非 ALL 范围的账号补上祖先节点，但祖先不在 `actor.departments()` 里。**

回看 `DeptService.withAncestorsForContext()`（第 60-73 行）的注释：

> *祖先只为让前端渲染出一棵结构完整的树，它们自身不在 `actor.departments()` 里，因此 `manageable=false` —— 只读上下文，不可操作。*

而 `DeptVO` 里有 `manageable` 字段。所以**前端的部门树应该用 `manageable` 来禁用勾选框**，否则用户能勾上一个"看得到但不能授"的祖先部门，提交后被后端拒绝（"不能授予自身数据范围之外的部门"）—— 又变成"表单允许提交、后端一律 403"那类错配。

**改法**：给树的节点加 `disabled: !node.manageable`。同理，超级管理员应该能勾所有节点（`manageable` 对超管恒为 true）。

**② 用户部门的分配范围也跟着变了 —— 这是好事，但要知道。**

`UserService.validateAssignment()`（第 406-408 行）用 `actor.departments().contains(request.deptId())` 判断"能不能把用户分配到这个部门"。加了 CUSTOM 之后，**一个配置了"技术部 + 产品部"的 HR 角色，就能把用户分配到这两个部门**（以前只能分配到自己的部门）。

这正是"自定义数据范围"的预期语义（数据范围 = 能看/能管的数据边界）。**但如果你只想让它影响"看"、不影响"写"，就需要额外加一个开关**——目前没有。这是一个需要你确认的产品决定。

### 3.9 【强烈建议】把删掉的 `AccessPolicyTest` 加回来

`git status` 显示这三个测试被**删掉了**（共 507 行）：

```
D  backend/src/test/java/org/lbl/security/context/AccessPolicyTest.java          (246 行)
D  backend/src/test/java/org/lbl/security/proxy/TrustedProxyResolverTest.java    (123 行)
D  backend/src/test/java/org/lbl/system/user/UserServiceSecurityTest.java        (138 行)
```

`AccessPolicy` 是**整个系统里唯一一处权限判定**，出错等于被入侵，而它现在**一行测试都没有**。你在改它（加 CUSTOM 分支）——**这是恢复测试的最佳时机**。

`git show HEAD:backend/src/test/java/org/lbl/security/context/AccessPolicyTest.java` 可以取回原文件，它是纯 Mockito 单测（不依赖数据库），恢复成本极低：

```bash
git checkout HEAD -- backend/src/test/java/org/lbl/security/context/AccessPolicyTest.java
git checkout HEAD -- backend/src/test/java/org/lbl/security/proxy/TrustedProxyResolverTest.java
git checkout HEAD -- backend/src/test/java/org/lbl/system/user/UserServiceSecurityTest.java
```

然后补上 CUSTOM 的用例（`AccessPolicy` 现在多了一个构造参数 `RoleDeptMapper`，所以 `setUp()` 里的 `new AccessPolicy(...)` 要加一个 `mock(RoleDeptMapper.class)`）：

```java
@Test
void customScopeUsesRoleDeptsInsteadOfOwnDepartment() {
    // 用户坐在部门 1；角色是 CUSTOM，只授了部门 5 和 6
    when(users.selectOne(any())).thenReturn(userInDept(1L));
    when(roles.selectAssignedByUserId(1L)).thenReturn(List.of(customRole(10L)));
    when(roleDepts.selectByRoleIds(List.of(10L)))
            .thenReturn(List.of(new RoleDeptAssignment(10L, 5L), new RoleDeptAssignment(10L, 6L)));
    when(menus.selectByUserId(1L)).thenReturn(List.of());

    AccessPolicy.Actor actor = policy.actor();

    assertEquals(Set.of(5L, 6L), actor.departments());   // ★ 不含 1（自己的部门）
    assertFalse(actor.all());
    assertEquals(3, actor.maxScopeRank());               // CUSTOM 是 3 档
}

@Test
void customScopeWithoutDepartmentsSeesNothing() {
    when(users.selectOne(any())).thenReturn(userInDept(1L));
    when(roles.selectAssignedByUserId(1L)).thenReturn(List.of(customRole(10L)));
    when(roleDepts.selectByRoleIds(List.of(10L))).thenReturn(List.of());
    when(menus.selectByUserId(1L)).thenReturn(List.of());

    AccessPolicy.Actor actor = policy.actor();

    assertTrue(actor.departments().isEmpty());
    assertFalse(actor.all());                 // ★ 关键：绝不能因为"空"就退化成"全都能看"
}

@Test
void disabledCustomRoleGrantsNothing() {
    RoleEntity disabled = customRole(10L);
    disabled.setStatus(0);
    when(users.selectOne(any())).thenReturn(userInDept(1L));
    when(roles.selectAssignedByUserId(1L)).thenReturn(List.of(disabled));
    when(menus.selectByUserId(1L)).thenReturn(List.of());

    assertTrue(policy.actor().departments().isEmpty());
}
```

> **第三个用例是最重要的一条**：它钉住了"停用的 CUSTOM 角色不授予任何部门"。项目里对 `role.status = 1` 这个判定口径踩过坑（`AccessPolicy.java:351-359` 的注释记录了"停用的 super_admin 仍被当成超管"的两套口径问题），所以这个行为必须有测试守着。

---

## 四、完整改动清单（照着打勾）

### 数据库
- [ ] `db/init_schema.sql` 加 `sys_role_dept` 建表语句
- [ ] `db/migration-manual/20260101_add_role_dept.sql` 新建（含验证 SQL）
- [ ] 在每个环境执行迁移

### 后端（新增 2 个文件）
- [ ] **新建** `system/role/mapper/RoleDeptMapper.java`
- [ ] **新建** `system/role/vo/RoleDeptAssignment.java`

### 后端（修改 5 个文件）
- [ ] `security/context/AccessPolicy.java` —— 注入 `RoleDeptMapper` + `actor()` 加 `CUSTOM` 分支 + `scopeRank` 加 `CUSTOM -> 3`
- [ ] `system/role/service/RoleService.java` —— 注入两个 Mapper + `deptIds()` + `grantDepts()` + `remove()` 加一行清理 + `detail()` 返回 `customDeptIds`
- [ ] `system/role/controller/RoleController.java` —— 加 `GET/PUT /{id}/dept-ids`
- [ ] `system/role/request/RoleRequest.java:22` —— 正则加 `CUSTOM`
- [ ] `system/role/vo/RoleVO.java` —— 加 `customDeptIds` 字段
- [ ] （可选）`system/dept/service/DeptService.java` —— `remove()` 里调 `roleDepts.deleteByDeptId(id)` 清理

### 前端（修改 3 个文件）
- [ ] `src/types/role.ts` —— 抽 `DataScope` 联合类型并加 `CUSTOM`；`Role` 加 `customDeptIds?`
- [ ] `src/api/role.ts` —— 加 `getRoleDeptIds` / `grantRoleDepts`
- [ ] `src/pages/system/role/RoleDialog.tsx` —— 加选项、条件渲染部门树（用 `permission-tree` 类）、`submit()` 里级联保存、`disabled: !manageable`

### 测试
- [ ] 恢复 3 个被删的测试文件（`git checkout HEAD -- <path>`）
- [ ] `AccessPolicyTest` 补 3 个 CUSTOM 用例（含"空集合≠全部可见"和"停用角色不授权"）

---

## 五、验收清单

### 5.1 功能验收

| # | 步骤 | 期望结果 |
| --- | --- | --- |
| 1 | 超管建角色 R，数据范围选"自定义部门"，勾"技术部" | 保存成功；`SELECT * FROM sys_role_dept WHERE role_id=<R>` 有 1 行 |
| 2 | 建用户 U（**部门设为"产品部"**），只给角色 R | — |
| 3 | 用 U 登录，打开用户管理 | **只能看到技术部的用户**（**注意：U 自己是产品部的，但看不到产品部的人** —— 这正是 CUSTOM 的意义） |
| 4 | U 打开部门管理 | 只看到技术部 + 其祖先（根部门，只读不可操作） |
| 5 | U 新增用户，看"所属部门"下拉 | 只有技术部（及其下级，如果做了方案 B） |
| 6 | U 尝试直接调接口把用户分配到产品部 | 400「不能将用户分配到数据范围之外的部门」 |
| 7 | U 尝试给自己配一个包含"产品部"的自定义范围 | 400「不能授予自身数据范围之外的部门」（需先给 U 角色管理权限才能测） |
| 8 | 超管把 R 的 data_scope 改成 `DEPT` | U 刷新后只能看到自己所在的产品部数据；`sys_role_dept` 的行**保留**（改回 CUSTOM 就恢复） |
| 9 | 超管停用角色 R | U 的可见部门变空（**不是变成全部**） |
| 10 | 超管删除角色 R | `sys_role_dept` 里 role_id=R 的行被清掉 |
| 11 | 建一个 CUSTOM 但不勾任何部门的角色 | `actor().departments()` 为空 → 用户看不到任何部门的数据（**这是安全的默认，不是 bug**） |

### 5.2 边界与安全验收（**这几条最重要**）

| # | 步骤 | 期望结果 |
| --- | --- | --- |
| 12 | U（数据范围 = 技术部）导出用户列表 | **导出的 Excel 内容与列表一致**，不含产品部的人（验证 `applyUserScope` 在导出路径也生效） |
| 13 | U 直接构造 URL 访问产品部的用户详情接口 | 400/403，不能越权 |
| 14 | U 直接在请求体里塞一个产品部的 deptId 建用户 | 400 |
| 15 | 用 SQL 手工往 `sys_role_dept` 塞一个**不属于 U 可见范围**的部门，然后 U 刷新 | **可见范围变大** —— 这是预期的（应用层是边界，DBA 改库能绕过，与项目其它地方一致）。这一条是记录"边界在哪"，不是 bug |
| 16 | 用一个只有 `SELF` 范围 + 一个 `CUSTOM`（技术部）的用户 | 可见 = 技术部 ∪ {自己}（并集语义） |
| 17 | 用一个 `DEPT_AND_CHILDREN` + `CUSTOM` 的用户 | 可见 = 自己部门子树 ∪ 自定义部门 |
| 18 | `maxScopeRank` 检查：U（CUSTOM，档位 3）尝试分配一个 `ALL` 范围的角色给某人 | 拒绝 |
| 19 | 部门 X 被删除后，含 X 的 CUSTOM 角色 | 部门树里看不到 X（已被逻辑删除），可见部门集合里虽然还有那个 id 但不会命中任何数据 |
| 20 | 加/删 CUSTOM 部门后，**用户重新登录**（不是刷新） | 权限变化生效（`actor()` 是每请求重算的，刷新就生效；但 `/auth/me` 返回的菜单/权限是会话级的，所以建议重新登录确认） |

---

## 六、常见问题

**Q: 为什么不直接给 `sys_dept` 加个 `data_scope` 字段，而要建关联表？**
因为一个角色要能指定**多个**不相邻的部门，这是一对多关系。部门表上加字段只能表达"一个部门属于一个范围"，表达不了"一个角色包含多个部门"。

**Q: 为什么 `data_scope` 从 CUSTOM 改成别的值时不删 `sys_role_dept`？**
两个理由：① 用户可能只是临时改一下试试，删了就得重配；② `AccessPolicy` 只在 `CUSTOM` 分支读它，其它范围下这些行是**惰性数据、不参与任何判定**，不会造成越权。所以保留是无害的（`RoleService.grantDepts` 的注释里写了这个决定）。

**Q: 我能不能用 `sys_role_dept` 实现"用户只能看自己部门 + 一个特殊部门"？**
可以。给用户同时分配一个 `DEPT` 角色和一个 `CUSTOM`（那个特殊部门）角色，`actor()` 取并集，结果就是两者相加。

**Q: 这个改动会影响现有用户吗？**
不会。`data_scope` 的默认值还是 `'SELF'`，现有角色的值不变，`CUSTOM` 分支只有显式配置了才会走到。

**Q: 数据范围能不能按"用户"而不是"角色"配？**
技术上可以（加一张 `sys_user_dept`），但这会破坏"权限从角色来"的模型，导致"这个人为什么能看到这个部门"变得难以回答。**不建议。**

**Q: 还有别的数据范围类型值得加吗？**
常见的有两个：
- `DEPT_AND_CHILDREN` 已经有了；
- **"本部门及以下 + 自定义"** —— 就是上面 Q2 的并集用法，不需要新类型；
- **按业务字段而不是部门**（例如"我负责的项目"）—— 那属于业务逻辑，不该塞进 `AccessPolicy`，应该在各自的 Service 里用 `actor.user().getId()` 自己过滤。
