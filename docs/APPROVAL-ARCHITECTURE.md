# 审批申请领域

## 决策

`departmentchange` 不是一个应与 `auth`、`notification` 并列的顶层能力。它实现的是
“部门调动”这一种**审批申请类型**；真正可复用的领域能力是申请的提交、审批、拒绝、撤销、
可见性、步骤记录、通知和并发控制。

因此当前实现已迁入 `org.lbl.approval.departmenttransfer`，HTTP 路由
`/api/account/department-change` 和原有数据表保持不变。这是一次无 API / 无数据迁移的
边界修正，而非把成熟流程冒险替换为一个尚未验证的“万能工作流”。

未来不要创建顶层 `rolechange`。角色提权属于 `approval` 的另一个申请类型。

## 目标包结构

在第二种申请类型上线时，应按以下结构提炼已经重复的部分：

```text
org.lbl.approval
├── api/
│   └── ApprovalRequestController        # 通用查询、通过、拒绝、撤销
├── application/
│   ├── ApprovalWorkflowService          # 锁定、状态迁移、步骤推进、授权、通知
│   └── ApprovalRequestTypeHandler       # 申请类型的扩展点
├── domain/
│   ├── ApprovalRequest                  # 通用主单
│   ├── ApprovalStep                     # 通用步骤与决定记录
│   ├── ApprovalRequestType              # DEPARTMENT_TRANSFER、ROLE_ELEVATION …
│   └── ApprovalStatus
├── infrastructure/
│   └── persistence/                     # 主单/步骤 mapper 与实体
├── departmenttransfer/
│   ├── DepartmentTransferHandler        # 目标部门校验、两级审批链、更新 user.dept_id
│   └── DepartmentTransferView
└── roleelevation/
    ├── RoleElevationHandler             # 角色白名单、风险分级、更新 user_role
    └── RoleElevationView
```

这里的关键不是把所有差异塞进 JSON 或 `switch`：

- `ApprovalWorkflowService` 只处理通用生命周期：排他锁、当前节点授权、禁止自审、
  通过/拒绝/撤销、步骤审计、参与者可见性、通知和乐观版本号。
- `ApprovalRequestTypeHandler` 只处理类型特有规则：提交数据校验、审批链计算、审批前
  再校验、全部通过后的业务副作用，以及面向 UI 的详情投影。
- Handler 是后端注册的白名单，客户端不能指定类名、审批人或最终写入动作。

## 通用数据模型

目前 `sys_department_change_request` / `sys_department_change_step` 的列已经把“部门”写死，
不应直接给角色提权复用。出现第二种类型时，应通过**可执行数据库迁移**替换为：

```text
sys_approval_request
  id, request_type, requester_id, subject_user_id, reason,
  payload_json, status, current_step, policy_version, version,
  created_time, updated_time, finished_time

sys_approval_step
  id, request_id, step_order, step_key, assigned_user_id,
  status, decided_by, decision_reason, decided_time,
  created_time, updated_time
```

`payload_json` 只保存每一种申请的不可变提交快照（例如 `fromDeptId`、`targetDeptId` 或
`requestedRoleIds`），不是无约束的业务模型；写入和读取均由相应 Handler 校验。可检索、
跨类型的字段必须保持普通列，避免把审批列表查询建立在 JSON 扫描上。

## 角色提权的安全规则

角色提权不能复用“目标部门负责人”这一部门规则。`RoleElevationHandler` 应在服务端定义审批
策略，例如直属负责人 + 指定角色管理员 / 超级管理员，并按角色风险等级增加节点。提交和最终
落地前都要重新验证：角色仍启用、申请人仍有资格、操作者未审批自己、策略版本未改变；最后在
同一事务内锁定用户和 `user_role` 关系后写入。

## 迁移策略

本次没有改物理表名，因而不会影响已有申请或前端。真正引入第二种类型前，先为项目加入迁移
工具（如 Flyway），再创建可回滚 / 可审计的迁移：建通用表、迁移现有部门主单与步骤、校验
总数和状态、切换读写、最后才清理旧表。不要仅修改 `init_schema.sql` 或直接 `RENAME TABLE`；
那会让已部署环境丢失历史审批记录或无法回滚。
