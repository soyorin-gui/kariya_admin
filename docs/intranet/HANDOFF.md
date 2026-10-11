# Intranet 开发交接

> 当前工作现场；长期模型见 `ARCHITECTURE.md` / `DECISIONS.md`，计划见 `PLAN.md`，脚本见 `MIGRATION.md`。

## 1. Git 与阶段

- 日期：2026-10-11（Asia/Shanghai）
- 分支：`main`，跟踪 `origin/main`
- 本轮开始时 HEAD：`a3b8d0b`，ahead/behind = 0/0
- 当前阶段：P1-03 数据资产字段设计与通用查询能力收尾
- 本轮未执行 commit、push、pull、merge、rebase、reset、clean 或数据库脚本

## 2. 本轮实现

### 数据资产字段隐藏页

- 新增 `/intranet/dataasset/fields?assetType=HBASE|ES&assetId=...`。
- HBase/ES 列表的“字段设计”跳转隐藏页；返回时通过 URL `tab` 恢复原 HBase/ES Tab。
- 字段页采用与交易字段一致的行内新增、编辑、保存和取消。
- HBase 字段列：列族、字段名、数据类型、顺序、说明。
- ES 字段列：字段名、数据类型、可搜索、可排序、顺序、说明。
- ES 固定 `id` 字段编辑时不能改名，也不显示删除操作。
- 字段名支持前端快速搜索；编辑期间禁用搜索和新增；不分页。
- 说明使用单行输入 + Popover TextArea，展示时省略并悬停查看全文。

### 废弃代码清理

- 已删除数据资产页字段 Drawer、字段 Table、字段加载/编辑状态和相关 API 调用。
- 已删除 `HbaseFieldDialog`、`EsFieldDialog` 以及对应依赖和导入。
- 交易字段页当前本来就没有旧字段 Drawer/Modal；本轮确认后未误删交易详情 Drawer 或交易新增/编辑 Modal。

### intranet 内部共享

- 新增 `frontend/src/pages/intranet/components/InlineEditableTable.tsx`。
- 新增 `FieldText.tsx` / `field-text.css`。
- 交易字段、HBase 字段、ES 字段共同使用上述编辑骨架和长文本交互。
- 组件没有提升为基线业务组件；树形、字段列、校验、权限和 API 仍由页面负责。

### 基线 SmartTable（用户已批准）

- 修改 `frontend/src/components/SmartTable/SmartTable.tsx` 并新增组件专属 CSS。
- `searchConfig` 新增字段标签、宽度、自定义 render、高级条件和展开/收起。
- 新增 `searchActions`，控制查询/重置文字、重置显示、回车提交和默认展开。
- 查询仍使用 `search` / `submittedSearch` 双状态，只有 submit 后才请求。
- 既有 `searchRender` 页面逻辑不变。

### 交易列表搜索样式与资产表单提示

- 交易列表改用 SmartTable `searchConfig`，展示“交易名称/编码”“ESF 服务名/操作名”“关联索引/HBase 表”三个条件以及查询、重置按钮。
- 按用户要求，本轮只有交易名称/编码继续传给既有后端 `keyword`；ESF 和关联资源值尚未加入请求参数，也不会实际过滤数据。
- HBase 表、ES 索引新增/编辑 Modal 的表名、索引名、命名模式、数据深度、RowKey、关联 HBase 表和说明等字段已补齐 placeholder。

### 数据库脚本

- V10：虚构数据资产演示数据，上一轮生成，本轮未执行。
- V11：新增数据资产字段设计隐藏菜单及超管授权，待执行。
- 用户已在页面看到演示数据和资产页面，但本会话没有查询数据库，故 V8~V10 的具体执行状态记录为“待目标环境复核”。

## 3. 验证

| 项目 | 结果 |
| --- | --- |
| `npm run build` | 通过；最新搜索样式和 placeholder 修改后再次构建成功，5311 个模块完成转换，仅有既存 chunk 体积告警 |
| 旧字段 Drawer/Modal 搜索 | 数据资产目录无 `HbaseFieldDialog`、`EsFieldDialog`、`fieldOwner`、字段 Drawer 残留 |
| 后端测试 | 本轮未重复执行；只新增 SQL，无 Java 改动。上一轮当前仓库现存测试 2/2 通过，但 P1 定向测试缺失 |
| 数据库脚本 | 本轮未执行；V11 明确待执行 |
| 浏览器人工联调 | 未执行 |

## 4. 待用户执行与验收

1. 执行 `V11__seed_intranet_data_asset_field_design_menu.sql`，重新登录或刷新菜单权限后访问隐藏页。
2. 分别从 HBase、ES 列表进入字段设计，验证新增、编辑、删除、搜索、说明展开和返回 Tab。
3. 验证 ES `id` 字段不可改名且不能删除。
4. 用非超管角色验证隐藏菜单授权和 `intranet:dataasset:*` 接口权限。
5. 后续恢复 P1 定向测试，覆盖删除保护、固定 `id`、资源整体替换和交易删除清理。

## 5. 风险与边界

- V11 沿用既有菜单脚本的软删除幂等边界：同路径软删除记录可能导致脚本跳过。
- `pages/**/*.tsx` 动态扫描仍会发现组件型 TSX，用户已接受通过菜单配置规范规避；本轮没有修改路由扫描基线。
- SmartTable 是本轮唯一修改的基线公共代码，已按用户批准实施；前端全量构建通过，仍建议人工回归用户、角色、部门、日志、交易和资产列表。
- 当前 HEAD 中没有 P1 领域单元测试，构建通过不能替代业务行为测试。
- HEAD `a3b8d0b` 中已有的 intranet 之外改动不是本轮产生，本轮未触碰。

## 6. 本轮文件

新增：

- `frontend/src/components/SmartTable/SmartTable.css`
- `frontend/src/pages/intranet/components/InlineEditableTable.tsx`
- `frontend/src/pages/intranet/components/FieldText.tsx`
- `frontend/src/pages/intranet/components/field-text.css`
- `frontend/src/pages/intranet/dataasset/fields/index.tsx`
- `frontend/src/pages/intranet/dataasset/fields/index.css`
- `backend/src/main/resources/db/intranet/V11__seed_intranet_data_asset_field_design_menu.sql`

修改：

- `frontend/src/components/SmartTable/SmartTable.tsx`
- `frontend/src/components/SmartTable/index.ts`
- `frontend/src/pages/intranet/dataasset/index.tsx`
- `frontend/src/pages/intranet/dataasset/AssetDialogs.tsx`
- `frontend/src/pages/intranet/transaction/fields/index.tsx`
- `frontend/src/pages/intranet/transaction/fields/index.css`
- `docs/intranet/ARCHITECTURE.md`
- `docs/intranet/DECISIONS.md`
- `docs/intranet/PLAN.md`
- `docs/intranet/MIGRATION.md`
- `docs/intranet/HANDOFF.md`

上一轮仍未提交的新文件：

- `backend/src/main/resources/db/intranet/V10__insert_intranet_data_asset_demo_data.sql`
