# 移入公司内网：改名、品牌清理、配置与安全清单

> 这一篇是**可执行的操作手册**。目标是：把这个私人项目变成一台能放进公司内网、看起来像"公司系统"的东西。
>
> 分四块：
> **① 必须先做的安全整改**（不做就别上内网）→ **② 后端改名清单** → **③ 前端品牌清理清单** → **④ 内网部署配置** → **⑤ 数据库与迁移** → **⑥ 验收命令**。
>
> 所有条目都带**文件名 + 行号**，可以用编辑器的全局搜索逐条核对。

---

## ① 必须最先做的安全整改（4 项，全部是"一改就完"级别）

> ⚠️ 这 4 项在**当前代码里都是"对内网可接受、对任何真实环境都不可接受"的状态**。下面的顺序就是建议的执行顺序。

### 1.1 【最高优先级】JWT 签名密钥硬编码，且生产配置没有覆盖它

**问题**：`backend/src/main/resources/application.yml:34`

```yaml
lbl:
  security:
    jwt-secret: lbl-shit-development-secret-must-be-at-least-32-characters
```

而 `backend/src/main/resources/application-prod.yml` **完全没有** `jwt-secret` 这一项（该文件只有 18 行，只覆盖了数据源、Redis、cookie/cors/proxy）。

**后果**：任何人拿到这份代码，就能**伪造任意用户的访问令牌**（JWT 只靠这个密钥验签）。而密钥就写在仓库里。

**怎么改**：

`application.yml:34`
```yaml
lbl:
  security:
    # 生产必须用环境变量提供；这个默认值只允许本地开发使用。
    jwt-secret: ${JWT_SECRET:local-dev-only-secret-change-me-at-least-32-chars}
```

`application-prod.yml` 里补上（并让它**没有默认值**，缺了就启动失败）：
```yaml
lbl:
  security:
    jwt-secret: ${JWT_SECRET}
```

**生成一个强密钥**：
```bash
# Linux / Git Bash
openssl rand -base64 48
# Windows PowerShell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Max 256 }))
```
长度必须 ≥ 32 字节（`Keys.hmacShaKeyFor` 会直接抛异常拒绝短密钥，所以启动时就会发现问题，这是好事）。

> 内网也一样要做。内网用户能访问到 jar 包、配置文件、甚至源码——**"内网"不等于"可信"**。

### 1.2 Jasypt 加密口令硬编码在代码里，而它保护的凭据也在仓库里

**问题**：
- `application.yml:13-21`：Jasypt 口令是明文 `kariya`，而且**注释里自己写了"该值仅供开发环境使用；生产环境请改用环境变量"**——但 `application-prod.yml` 里没有覆盖。
- `application-local.yml:5` 和 `:27`：一个**公网 MySQL（`124.222.152.55:3308`，root 用户）**和一个 **Redis** 的 `ENC(...)` 密文。

虽然 `application-local.yml` 被 `.gitignore` 忽略（`.gitignore:56`，已用 `git check-ignore` 确认），但**加密口令在仓库里公开 → 这份"加密"等于没有加密**。文件一旦泄露（截图、备份、误提交），凭据即刻可解。

**怎么改**（三选一，推荐第 1 个）：

1. **内网直接用环境变量，干脆不用 Jasypt**
   `application-local.yml` / `application-prod.yml` 里 `password: ${DB_PASSWORD}`，明文只存在于服务器的环境变量 / 启动脚本里。内网这是完全够用的做法，而且少一个依赖。
2. **Jasypt 口令走环境变量**
   把 `application.yml:15` 改成 `password: ${JASYPT_ENCRYPTOR_PASSWORD}`（Jasypt 原生支持这个环境变量名），然后在启动脚本里注入。
3. 继续用，但**换口令 + 重新加密所有 `ENC(...)`**。

**顺手要做的事**：`application-local.yml` 现在指向一台**公网服务器**。搬进内网时把它整体重写成本地/内网地址（`README.md` 的 4.2 节给了最小模板）。

### 1.3 确认 `.gitignore` 真的挡住了本地配置

已确认 `application-local.yml` 被忽略：

```
$ git check-ignore -v backend/src/main/resources/application-local.yml
.gitignore:56:application-local.yml	backend/src/main/resources/application-local.yml
```

**但要检查仓库历史里有没有曾经提交过它**：
```bash
git log --all --oneline -- backend/src/main/resources/application-local.yml
```
有输出就说明密码进过历史，需要 `git filter-repo` 清理或直接换密码。

**同时建议**：把 `.gitignore` 里 `dist/`、`target/` 的忽略坐实（现在 `frontend/dist/` 和 `backend/target/` 都真实存在于工作区，`backend/target/classes/db/` 里还留着 5 个已从源码删除的旧升级 SQL，很容易误导人——见第 ⑤ 节）。

### 1.4 三方登录的登录-CSRF（如果启用三方登录）

**问题**（`THIRD-PARTY-LOGIN.md` 第 5 节详述）：`state` 存在 Redis 并被一次性消费，但**没有和"发起登录的那个浏览器"绑定**（`/start` 不种任何 Cookie，`LoginTransaction` 也不记录浏览器指纹）。攻击者可以用自己的 GitHub 账号发起登录、拿到 `code` + `state`，再诱导受害者打开那个 callback URL —— 受害者会被静默登录进**攻击者的账号**，并拿到 14 天的 cookie。

**怎么改**：`ExternalLoginService.begin()` 里额外写一个短寿命的 httpOnly cookie（例如 `lbl_ext_state`，TTL 10 分钟），在 `complete()` 里比对；不一致就拒绝。只有几十行改动。

**如果内网不启用任何三方登录**：把 `lbl.external-auth.providers` 整块删掉或全部 `enabled: false` 即可，这个问题自然不存在。

### 1.5 顺手检查：把 `/api/auth/external/**` 的 callback 加上限流

`WebSecurityConfig.java:56` 的 `/api/auth/**` 是全放行的，而 `ExternalLoginService.complete()` 在**任何**失败路径上都会往 `sys_login_log` 写一行 FAILURE（`:169-170`、`:177`），且这个入口**没有任何限流**（对比 `/start` 有 30 次/分钟的来源配额，`:42-43`、`:90`）。匿名脚本可以用伪造的 `code`/`state` 把日志表刷大。加一句 `attempts.requireSourceQuota("external-callback", N, WINDOW)` 即可。

---

## ② 后端改名清单

"LBL""SHIT""Kariya"在**后端**一共有 6 类出现位置。按"改动成本从低到高"排列，**你可以只做前 3 类**（对外可见的部分），不做包名重命名。

### 2.1 对外可见的字符串与启动横幅（**必做**）

| # | 文件:行 | 当前值 | 说明 |
| --- | --- | --- | --- |
| 1 | `backend/src/main/resources/application.yml:3` | `name: lbl-shit` | `spring.application.name`，出现在日志、监控、Actuator 里 |
| 2 | `backend/pom.xml:11-12` | `<groupId>org.lbl</groupId>` / `<artifactId>lbl-shit</artifactId>` | 打成 `lbl-shit-1.0.0.jar` |
| 3 | **`backend/src/main/resources/banner.txt:21-30`** | ASCII 艺术字 `SHIT` + 语录 | ⚠️ **必须改**。见下 |

**`banner.txt` 的内容（第 20-30 行）**：
```
${AnsiColor.BRIGHT_YELLOW}
  ____   _   _  ___  _____
 / ___| | | | ||_ _||_   _|
 \___ \ | |_| | | |   | |
  ___) ||  _  | | |   | |
 |____/ |_| |_||___|  |_|
${AnsiColor.BRIGHT_RED}
------------------------------
   你把核心系统交给劳务派遣开发
      说明你也没把它当核心
------------------------------
${AnsiColor.DEFAULT}
```

启动一次就会在控制台打印这段话。**做内网迁移时，这个文件建议直接清空**（Spring Boot 会用默认 banner），或者换成公司名：

```
${AnsiColor.BRIGHT_BLUE}
  ____                 _
 / ___|  ___  _ __ ___| |_ __ _
 \___ \ / _ \| '__/ _ \ __/ _` |
  ___) | (_) | | |  __/ || (_| |
 |____/ \___/|_|  \___|\__\__,_|
${AnsiColor.DEFAULT}
 :: 公司内部管理系统 ::  ${spring-boot.version}
```

> **注意 `banner.txt` 里第 1-19 行还有一段 ASCII 图案**（占用 19 行、由 `+#%=*:-` 组成的图形），也是这个项目的私人风格，一并不需要就删掉。

**另外**：`application-local.yml:34` 有一行 `org.kariya: DEBUG`——包名压根不存在（真实包名是 `org.lbl`），所以这行是**无效配置**，属于历史遗留。改名时顺手删掉或改成新包名。

### 2.2 HTTP 层带品牌的标识符（**必做**，改了要前后端同步）

| # | 文件:行 | 当前值 | 谁依赖它 |
| --- | --- | --- | --- |
| 4 | `auth/controller/AuthController.java:32` | `REFRESH_COOKIE = "lbl_refresh"` | 前端无法读它（httpOnly），但**浏览器里会存这个名字** |
| 5 | `auth/controller/ExternalAuthController.java:26` | `REFRESH_COOKIE = "lbl_refresh"` | 同上，**两处必须一致** |
| 6 | `auth/controller/AccountIdentityController.java:55` | `@CookieValue(value = "lbl_refresh")` | 同上 |
| 7 | `auth/controller/OnboardingAccountController.java:43` | `@CookieValue(value = "lbl_refresh")` | 同上 |
| 8 | `auth/controller/OnboardingAccountController.java:85` | `ResponseCookie.from("lbl_refresh", sid)` | 同上 |
| 9 | `system/user/controller/UserController.java:105` | `@CookieValue(value = "lbl_refresh")` | 同上 |

**怎么改**：把 `lbl_refresh` 全部替换成 `yourco_refresh`（6 处，纯字符串替换，零风险）。
⚠️ **必须 6 处一起改**。注释里已经警告过：*"拼错一个字就会变成另一个 Cookie"*。
⚠️ **改完必须让所有人重新登录**——旧 Cookie 的名字不再被识别。

### 2.3 配置命名空间 `lbl.*`（**建议做**）

自定义配置全部挂在 `lbl` 前缀下（前缀名会出现在 `application.yml` 和代码里的 `${}` 占位符中）。

**改成 `yourapp`（或公司名）需要改这些地方**：

| 文件 | 位置 |
| --- | --- |
| `application.yml` | `:25` `lbl:` 根节点；`:27-32` 的 `lbl.file-upload.*`；`:33-56` 的 `lbl.security.*`；`:57-66` 的 `lbl.log.retention.*` |
| `application-local.yml` | `:42` `lbl:`；`:43-109` 的 `lbl.external-auth.*` |
| `application-prod.yml` | `:10` `lbl:` |
| `config/SecurityProperties.java` | `:29` `@ConfigurationProperties(prefix = "lbl.security")` |
| `file/FileUploadProperties.java` | `:14` `@ConfigurationProperties(prefix = "lbl.file-upload")` |
| `system/log/retention/LogRetentionProperties.java` | `@ConfigurationProperties(prefix = "lbl.log.retention")` |
| `auth/external/ExternalAuthProperties.java` | `:9` `@ConfigurationProperties(prefix = "lbl.external-auth")` |
| `file/LocalStagedFileStorage.java` | `:87` `@Scheduled(fixedDelayString = "${lbl.file-upload.cleanup-interval-ms:600000}")` |
| `system/log/retention/LogRetentionJob.java` | `:45` `@Scheduled(cron = "${lbl.log.retention.cron:0 30 3 * * *}")` |
| `config/WebSecurityConfig.java` | `:91` 日志文案里的 `lbl.security.cors-allowed-origins` |
| `security/proxy/TrustedProxyResolver.java` | `:35`、`:57` 注释/日志里的 `lbl.security.trusted-proxies` |
| `system/log/support/RequestInfo.java` | `:46` 注释 |
| `auth/service/CaptchaService.java` | `:95` 告警文案 |
| `auth/controller/AuthController.java` | `:177` 注释 |

**共约 14 个文件。** 环境变量名（`JWT_SECRET`、`DB_URL`、`TRUSTED_PROXIES` 等）**不受影响**，因为它们不带 `lbl` 前缀。

### 2.4 Java 包名 `org.lbl`（**可选，成本最高**）

`org.lbl` 出现在**全部 129 个 Java 文件**里。改包名要动：

- 每个 `.java` 文件的 `package` 行；
- 每个文件的 `import org.lbl.*`；
- `LblShitApplication.java` 的**类名本身**（以及 `@SpringBootApplication` 扫描的起点——`@SpringBootApplication` 默认扫描启动类所在包及其子包，所以包名改了之后所有 `org.lbl.*` 下的东西必须跟着搬）;
- `pom.xml` 的 `<groupId>`；
- `frontend/src/utils/passwordPolicy.ts:4` 的注释（引用 `org.lbl.auth.service.PasswordRules`）。

**建议**：如果只是"搬去内网自己用"，**跳过这一项**。包名对内网用户完全不可见（除非他们看日志里的 class 名）。把精力花在 2.1-2.3 和 ③ 上。

**特别提醒**：如果决定改包名，**`@ConfigurationProperties` 的 prefix 与包名无关**（它是运行时字符串），所以 2.3 和 2.4 是独立的两件事，可以分两次做。

### 2.5 后端其他带品牌的注释与文案

这些不影响运行，但会出现在日志/注释里，看你的洁癖程度：

| 文件:行 | 内容 |
| --- | --- |
| `auth/service/PasswordChangePolicy.java:23` | 注释里提到 `GitHub/微信/UIAS`（这是你要保留的功能名，不用改） |
| `conf/WebSecurityConfig.java:91` | 日志文案含 `lbl.security.cors-allowed-origins` |
| `db/init_schema.sql:1` / `db/init_data.sql:1` | 注释 `-- Kariya Admin 全新数据库表结构` |
| `application-local.yml:3` | 数据库名 `kariya_admin`（**改库名要同步改 JDBC URL 和所有建库脚本**，建议保留） |
| `application-local.yml:34` | `org.kariya: DEBUG`（无效配置，可删） |
| `src/test/java/org/lbl/JasyptEncryptorTest.java:23` | `encryptor.setPassword("kariya")` |

---

## ③ 前端品牌清理清单

> 前端是品牌暴露的**主要**地方。一共 **12 处产品名 + 14 处标语 + 5 个功能 key + 4 个资源文件**。

### 3.1 产品名 "LBL SHIT" / "LBL Shit"（12 处）

| # | 文件:行 | 当前值 | 改成 |
| --- | --- | --- | --- |
| 1 | `frontend/index.html:7` | `<title>LBL SHIT</title>` | **浏览器标签页标题**（最显眼） |
| 2 | `frontend/index.html:80` | `alt="LBL SHIT"` | 首屏 loading 图的 alt |
| 3 | `frontend/src/layouts/SiderBrand.tsx:7` | `alt='LBL SHIT'` | 侧边栏 logo |
| 4 | `frontend/src/layouts/SiderBrand.tsx:9` | `<strong>LBL SHIT</strong>` | **侧边栏系统名**（最显眼） |
| 5 | `frontend/src/pages/login/LoginBrand.tsx:7` | `alt='LBL SHIT'` | 登录页 logo |
| 6 | `frontend/src/pages/login/LoginBrand.tsx:9` | `<strong>LBL SHIT</strong>` | **登录页系统名** |
| 7 | `frontend/src/components/common/LoadingScreen.tsx:11` | `alt='LBL SHIT'` | 全屏加载图 |
| 8 | `frontend/src/pages/login/index.tsx:75` | `<h1>LBL SHIT</h1>` | **登录页左侧大标题** |
| 9 | `frontend/src/pages/login/index.tsx:142` | `© 2026 LBL SHIT　版权所有` | **版权行**（注意中间是全角空格 U+3000） |
| 10 | `frontend/src/pages/register/index.tsx:117` | `<h1>LBL SHIT</h1>` | 注册页大标题 |
| 11 | `frontend/src/pages/register/index.tsx:187` | `© 2026 LBL SHIT　版权所有` | 注册页版权行 |
| 12 | `frontend/src/pages/home/index.tsx:126` | `<dd>LBL Shit</dd>` | **首页"系统基础信息"里的系统名称**（注意大小写不同：`Shit` 不是 `SHIT`） |

> **建议做法**：新建一个 `frontend/src/config/brand.ts`，集中定义系统名/版权/标语，然后让这 12 处引用它。这样下次改品牌只改一个文件。
> 不过要先决定：**是"一次性替换完事"还是"抽成配置"**。如果只想快速上内网，全局搜索替换更快。

### 3.2 标语 / 口号 / 私人化文案（14 处）—— **这些是内网里最显眼的**

| # | 文件:行 | 当前值 | 说明 |
| --- | --- | --- | --- |
| 1 | `src/layouts/SiderBrand.tsx:10` | `<small>全栈摸鱼基地</small>` | 侧边栏副标题 |
| 2 | `src/pages/login/LoginBrand.tsx:10` | `<small>全栈摸鱼基地</small>` | 登录页副标题 |
| 3 | `src/layouts/AppLayout.tsx:173` | `CRUD · BUG · CV` | **侧边栏底部第一行** |
| 4 | `src/layouts/AppLayout.tsx:175` | `Kariya的个人开发工作台` | **侧边栏底部第二行——全前端唯一一处 "Kariya"** |
| 5 | `src/pages/login/index.tsx:76` | `你把核心系统交给劳务派遣来开发` | 登录页左侧标语 |
| 6 | `src/pages/login/index.tsx:77` | `说明你也没把他当核心` | 登录页左侧标语 |
| 7 | `src/pages/login/index.tsx:81` | `只要不报错，就是好系统` | 登录页左下角 |
| 8 | `src/pages/register/index.tsx:118` | `你把核心系统交给劳务派遣来开发` | 注册页标语 |
| 9 | `src/pages/register/index.tsx:119` | `说明你也没把他当核心` | 注册页标语 |
| 10 | `src/pages/register/index.tsx:123` | `只要不报错，就是好系统` | 注册页底部 |
| 11 | `index.html:84` | `少女祈祷中...` | **首屏加载文案**（乱码已修复 ✅，文案待改品牌） |
| 12 | `src/components/common/LoadingScreen.tsx:22` | `少女祈祷中...` | 全屏加载文案（与 11 重复，两处都要改） |
| 13 | `src/components/ai/AiAssistant.tsx:14` | `你好，我是 LBL 智能助手。有什么可以帮你？` | AI 助手开场白 |
| 14 | `src/components/ai/AiAssistant.tsx:40` | `你好，我是 LBL 智能助手` | AI 助手标题 |

**另外这些"私人风格"文案建议一并换掉（不算品牌，但在公司内网会显得奇怪）**：

| 文件:行 | 当前值 |
| --- | --- |
| `src/pages/error/NotFound.tsx:21-24` | `糟糕，页面飘进银河了` / `宇航员找了一圈…或者正在宇宙里摸鱼。` |
| `src/pages/error/NotFound.tsx:17` | 引用了 `public/astronaut-404.png`（宇航员插图） |
| `src/components/ai/AiAssistant.tsx:45` | 三条预设建议：`帮我查看当前用户的权限配置` / `如何新增一个系统角色？` / `帮我整理本周的登录情况` |
| `src/layouts/AppLayout.tsx:74` | 兜底显示名 `'管理员'`（一般不用改） |
| `src/layouts/AppLayout.tsx:204` | 顶栏搜索框 placeholder（**这个搜索框本身是个空壳，没有任何逻辑**，建议直接删掉——见 `ASSESSMENT.md`） |
| `src/pages/login/login.css:5` | 引用 `assets/background.png`（**文件名已由拼错的 `backgroud.png` 改正，CSS 引用也已同步** ✅） |

### 3.3 功能性的 `lbl` 标识（改名会让用户侧状态重置）

| # | 文件:行 | 当前值 | 影响 |
| --- | --- | --- | --- |
| 1 | `frontend/package.json:2` | `"name": "lbl-shit-frontend"` | npm 包名 |
| 2 | `frontend/package-lock.json:2,8` | 同上 | 锁文件 |
| 3 | `src/theme/ThemeProvider.tsx:31` | `const STORAGE_KEY = 'lbl-ui-preference-v1'` | **localStorage key**。改了之后所有用户的主题偏好会重置（一次性，可接受） |
| 4 | `src/layouts/AppLayout.tsx:64` | `localStorage.getItem('lbl-sider-collapsed')` | 侧边栏折叠状态 |
| 5 | `src/layouts/AppLayout.tsx:89` | `localStorage.setItem('lbl-sider-collapsed', ...)` | 同上（**两处必须一起改**） |

**改 `package.json` 的 name 之后记得重跑 `npm install` 更新 lockfile。**

### 3.4 资源文件（**必做**）

| 资源 | 大小 | 被谁引用 | 处理方式 |
| --- | --- | --- | --- |
| `frontend/src/assets/logo.png` | **995 KB** | `layouts/SiderBrand.tsx:1`、`pages/login/LoginBrand.tsx:1` | **换成公司 logo**。建议同时压缩（995KB 的 logo 太大了，正常应该 < 50KB） |
| `frontend/public/favicon.ico` | **995 KB** | `index.html:6`（标签页图标）、`index.html:80`（首屏 loading）、`LoadingScreen.tsx:11` | **⚠️ 它和 `logo.png` 字节数完全相同——就是把 logo.png 改名成 .ico**。换成公司 favicon，并压到几 KB |
| `frontend/src/assets/background.png` | **974 KB**（1916×821 px） | `pages/login/login.css:5`（登录页背景） | 换公司背景图，或**直接删掉改成纯色渐变**（能省约 1MB 首屏体积）。文件名已由拼错的 `backgroud.png` 改正 ✅ |
| `frontend/public/astronaut-404.png` | 144 KB | `pages/error/NotFound.tsx:17` | 404 插图，可换可留 |

**关于文件名的坑（已解决 ✅）**：`backgroud.png` 曾拼错（少了个 n）。现已改名为 `background.png`，且
`pages/login/login.css:5` 的引用已同步，全仓 `backgroud` 零残留。**注意 `frontend/dist/` 里仍是旧产物的
`backgroud-*.png`，重新 build 后会消失。**

### 3.5 主题主色 —— **现在有两种改法，改法变了**

> **⚠️ 这一节在本次改动后已重写。** 之前"改 `theme.css` 的 `--brand`"和"改 `tokens.ts` 的 `colorPrimary`"**都是无效的**（会被运行时静默覆盖）。
> 完整原理与排查方法见 **`THEME.md`**，这里只给结论。

#### 改法 A：运行时改（推荐，零改码）

登录后 → 右上角**齿轮图标** → 「品牌主题色」→ 用**取色器**填公司 VI 色。
立即生效、自动保存到 localStorage。**所有强调色一起变**：按钮、侧边栏选中项、小标签、图表、顶部进度条、以及自定义 CSS。

> 注意：这是**当前浏览器**的偏好。要让所有同事默认就是这个颜色，用改法 B。

#### 改法 B：改默认值（换掉代码里的默认色）

**唯一需要改的文件是 `frontend/src/theme/tokens.ts`**：

```ts
// 1) 把公司主色放在第一位（它同时是 DEFAULT_PREFERENCE.primaryColor 的来源）
export const BRAND_COLORS = [
  { name: '公司主色', value: '#你的HEX色' },   // ← 改这里
  { name: '极光紫', value: '#7656d6' },
  // …其余预设可保留可删
] as const;

// 2) 如果不想让第一个预设成为默认，直接改默认偏好
export const DEFAULT_PREFERENCE = {
  primaryColor: '#你的HEX色',   // ← 或改这里
  fontFamily: 'system',
  fontSize: 14,
  borderRadius: 8,
  // …
} as const;
```

**顺带可以一起改的默认值**（同一个文件）：`DEFAULT_PREFERENCE.fontFamily` / `.fontSize` / `.borderRadius`。
比如公司规定用等宽字体、字号 13px、圆角 4px，就在这一个对象里改完。

`--brand-deep`（hover / 渐变用的深色变体）**不需要手写** —— `theme.css` 用
`color-mix(in srgb, var(--brand) 82%, #000)` 从 `--brand` 自动派生，所以任意颜色都能得到一个协调的深色。

#### 还剩哪些地方不跟主题色

| 位置 | 状态 |
| --- | --- |
| `frontend/index.html` 的内联 loading CSS | **已改成 `var(--brand, #1677ff)`** ✅ 换主题色时会跟（首屏那几十毫秒用 fallback 默认值） |
| `src/pages/home/index.tsx` 的图表与卡片强调色 | **已改成读 `useThemePreference()`** ✅ |
| `src/components/common/loadingScreen.css` 的圆点 | **已改成 `var(--brand)`** ✅ |
| `src/pages/system/shared.css` 的树形展开箭头 | **已改成从 `--brand` 派生** ✅ |
| `src/pages/login/login.css`（约 25 处） | ❌ **仍是浅色专属设计**，换品牌时需要整页重做（这本来就在你的改品牌清单里） |
| `src/pages/error/notFound.css` 的大字 | ❌ 视口驱动的装饰元素，刻意不跟字号 |
| 第三方平台色（微信/ GitHub / Google 图标） | ❌ 刻意保留的平台品牌色（深色主题下有单独覆盖） |

**改完之后请按 `THEME.md` 第八节做一次 30 秒自测**，确认"AntD 组件"和"自定义 CSS"两边都跟着变了。

---

## ④ 内网部署配置

### 4.1 推荐拓扑（同源部署，最省事）

```
内网用户 ──▶ Nginx (80/443) ──┬── /            → 前端静态文件（frontend/dist）
                              ├── /api/         → 后端 http://127.0.0.1:8080
                              └── /ws/          → 后端 WebSocket（需要 Upgrade 头）
```

**为什么推荐同源**：
- 刷新 Cookie 是 `SameSite=Lax`（`AuthController.java:40`），**跨站 SPA 在回调后拿不到 Cookie**。同源就完全不用考虑这个问题。
- 同源不需要 CORS，`cors-allowed-origins` 留空即可（**留空就是"不放行任何跨域"，这正是同源部署的正确默认值**）。
- `frontend/.env.production` 已经是 `VITE_API_BASE_URL=/api`（相对路径），天生就是给同源部署用的。

**Nginx 参考配置**：

```nginx
server {
    listen 80;
    server_name admin.内网域名;

    root /opt/kariya_admin/frontend/dist;
    index index.html;

    # SPA 前端：所有非静态文件请求都交给 index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 后端 API
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 12m;          # 与 application.yml 的 max-request-size: 11MB 匹配
    }

    # WebSocket（实时通知）。缺 Upgrade/Connection 头会导致连不上并疯狂重连
    location /ws/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade    $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host       $host;
        proxy_read_timeout 3600s;          # 后端有心跳（25 秒），但读超时要给足
    }

    # 静态资源缓存（文件名带 hash，可以长缓存）
    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

### 4.2 后端必须设置的环境变量

```bash
# ── 数据库 ────────────────────────────────────────────────
DB_URL=jdbc:mysql://内网MySQL:3306/kariya_admin?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
DB_USERNAME=kariya
DB_PASSWORD=强密码

# ── Redis（会话/限流/验证码/三方登录事务/WebSocket 票据全靠它）──
REDIS_HOST=内网Redis
REDIS_PORT=6379

# ── 安全（★ 必做，见第 ① 节）────────────────────────────────
JWT_SECRET=<openssl rand -base64 48 生成>
COOKIE_SECURE=false          # 内网走 http 时必须是 false，否则浏览器丢弃 Cookie

# ── ★★ 反向代理下必须设置 ★★ ─────────────────────────────
# 不设置的话：① 审计日志里所有 IP 都是网关地址；② 所有用户共享同一个限流 key，
# 一个人密码错 20 次就把整个内网的人锁 15 分钟。
TRUSTED_PROXIES=127.0.0.1,::1
# 如果 Nginx 在另一台机器：TRUSTED_PROXIES=10.0.0.0/8,172.16.0.0/12

# ── CORS（同源部署留空即可；不同源时逐个列全，不能用通配符）──
CORS_ALLOWED_ORIGINS=

# ── 可选 ──────────────────────────────────────────────────
CAPTCHA_ENABLED=true         # 内网可以设 false 省事（会打印告警）
PASSWORD_MAX_AGE_DAYS=0      # 0 = 关闭定期改密
FILE_STAGING_DIRECTORY=/data/kariya/uploads/staging
```

> **`TRUSTED_PROXIES` 是最容易漏掉的一项。** `TrustedProxyResolver` 的类注释里专门解释了两种极端都不行的原因。Nginx 在同机时填 `127.0.0.1`；在另一台时填那台的内网网段（支持 CIDR）。

### 4.3 前端构建

```bash
cd frontend
# .env.production 已是 VITE_API_BASE_URL=/api，无需修改
npm ci
npm run build          # = tsc --noEmit -p tsconfig.app.json && vite build
# 产物在 frontend/dist，拷贝到 Nginx 的 root 目录
```

**注意**：
- 构建时 `import.meta.glob` 会扫描 `src/pages`，所以**必须确保所有要用的页面文件都在**，否则菜单会指向不存在的组件并显示"组件不存在"诊断页。
- **新增页面后必须重新 build**（生产环境没有运行时编译能力）。
- `frontend/dist/` 现在是**提交进工作区的**（`.gitignore` 里有 `dist/`，但工作区里真实存在）。部署前记得重新构建，避免把旧产物发上去。

### 4.4 数据库与中间件

**MySQL 8.0+**：
```sql
CREATE DATABASE kariya_admin DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER 'kariya'@'%' IDENTIFIED BY '强密码';
GRANT ALL PRIVILEGES ON kariya_admin.* TO 'kariya'@'%';
FLUSH PRIVILEGES;
```

**Redis**：建议设 `requirepass`、绑定内网地址、设 `maxmemory-policy noeviction`。
⚠️ **不要用 `allkeys-lru` 之类会主动淘汰的 policy**——`auth:session:*` 被静默淘汰等于用户随机掉线，而且极难排查。

**Java 运行环境**：JDK 17。
⚠️ **如果用精简容器镜像（alpine/slim），必须装字体**，否则图形验证码渲染成空白图、注册完全不可用。应用会**主动拒绝启动**并提示：
```
Debian/Ubuntu: apt-get install -y fontconfig fonts-dejavu-core
Alpine:        apk add fontconfig ttf-dejavu
或者设置 CAPTCHA_ENABLED=false 关闭验证码
```

### 4.5 启动与守护

```bash
java -jar backend/target/lbl-shit-1.0.0.jar \
  --spring.profiles.active=prod
```

生产用 systemd 或容器编排，别用 `nohup`：
```ini
# /etc/systemd/system/kariya-admin.service
[Unit]
Description=Kariya Admin Backend
After=network.target mysql.service redis.service

[Service]
User=kariya
WorkingDirectory=/opt/kariya_admin
EnvironmentFile=/etc/kariya-admin/env
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -jar /opt/kariya_admin/backend.jar --spring.profiles.active=prod
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```
（`--spring.profiles.active=prod` 也可以写进 `application.yml` 的 `spring.profiles.active`，但推荐用命令行参数，改起来不用重新打包。）

---

## ⑤ 数据库与迁移（**这一节能救你一次事故**）

### 5.1 现状：项目没有迁移工具

- **没有 Flyway / Liquibase**，`pom.xml` 里也没有相关依赖。
- `db/` 目录下只有两个文件：`init_schema.sql`（8.8 KB，13 张表的建表语句）和 `init_data.sql`（4.4 KB，初始数据）。**两个文件头部都明确写着"仅面向空数据库"**。
- **源码里原本有的 5 个升级脚本已经被删除**（`git status` 显示 `D backend/src/main/resources/db/upgrade__schema.sql`）。

### 5.2 那 5 个被删的升级脚本：**在 jar 里，可以完整捞回来** ✅

⚠️ **注意**：这些文件**曾经**在 `backend/target/classes/db/` 有一份解压副本，但该目录已被清理，现在只剩
`init_data.sql` + `init_schema.sql`。**不过它们仍然完整地保存在已经打好的 jar 里**：

```
backend/target/lbl-shit-1.0.0.jar
  └── BOOT-INF/classes/db/
        ├── init__data.sql                    3879 B
        ├── init__schema.sql                  8880 B
        ├── upgrade__audit_fields.sql          492 B
        ├── upgrade__basic_role.sql           1149 B
        ├── upgrade__department_workflow.sql  3250 B
        ├── upgrade__password_policy.sql       632 B
        └── upgrade__schema.sql               5242 B
```

**捞回来的命令**（PowerShell，无需装任何工具）：

```powershell
cd D:\kariya_admin\backend
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path target\lbl-shit-1.0.0.jar))
$dest = 'src\main\resources\db\migration-manual'
New-Item -ItemType Directory -Force -Path $dest | Out-Null
$zip.Entries | Where-Object { $_.FullName -match '^BOOT-INF/classes/db/upgrade__' } | ForEach-Object {
    $out = Join-Path $dest ($_.Name -replace '^upgrade__', '')
    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($_, $out, $true)
    "  已导出 $out"
}
$zip.Dispose()
```

> **编码提示**：jar 里的内容是干净的 **UTF-8**。如果你用 Windows PowerShell 5.1 的 `Get-Content` 查看
> （`chcp` 为 936 时默认按 GBK 解码），会看到乱码 —— **那是查看方式的问题，不是文件的问题**。
> 用 `[System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8)` 或 VS Code 打开即为正常中文。

**5 个脚本各自的内容**：

| 文件 | 内容 |
| --- | --- |
| `schema.sql`（原 `upgrade__schema.sql`） | sys_user 加 `password_change_required`；审计日志索引；`sys_menu` 的 route_path/permission_code 唯一索引；开放注册与外部身份登录改造（拆出 `sys_local_credential`、建 `sys_external_identity`）；建 `basic_role` |
| `audit_fields.sql` | 给 sys_role / sys_menu 补 `created_by` / `updated_by` / `deleted_time` |
| `basic_role.sql` | 把历史上的 `registered_user` / `internal_user` 两个角色收敛成统一的 `basic_role` |
| `department_workflow.sql` | 建 `sys_department_change_request` / `_step` / `sys_notification` 三张表；含 `content VARCHAR(550)` 的说明（为什么是 550 而不是 500：拒绝通知文案 = 6 字前缀 + 500 字原因，超列宽会在审批事务里抛 Data too long 导致整次审批回滚） |
| `password_policy.sql` | `sys_local_credential` 加 `password_changed_time`，用 `updated_time` 回填历史数据 |

> 💡 **历史线索**：最早的提交 `64d5678` 用的是 **Flyway 风格**的目录（`db/migration/V1__schema.sql` ~
> `V4__reset_builtin_admin_password.sql`）。也就是说**这个项目曾经有过正式的迁移体系，后来被换成了手工方案**。
> 如果你想恢复 Flyway，`git show 64d5678 --stat` 能看到当时的全貌。

### 5.3 你现在有两条路

**路 A：全新建库（推荐，尤其是搬去内网时）**
```bash
mysql -u root -p -e "CREATE DATABASE kariya_admin DEFAULT CHARSET utf8mb4;"
mysql -u root -p kariya_admin < backend/src/main/resources/db/init_schema.sql
mysql -u root -p kariya_admin < backend/src/main/resources/db/init_data.sql
```
启动后端 → `SystemPermissionInitializer` 自动补建缺失的内置菜单/按钮并授予 `super_admin`。
登录 `admin` / `Admin@123` → 立刻改密。

**路 B：把现有的那个公网库整体导出搬进来**
```bash
# 在源库上导出（含结构 + 数据）
mysqldump -h 124.222.152.55 -P 3308 -u root -p --single-transaction --default-character-set=utf8mb4 \
  kariya_admin > kariya_admin_dump.sql

# 在内网库导入
mysql -u kariya -p kariya_admin < kariya_admin_dump.sql
```
⚠️ **搬完必须做两件事**：
1. **清掉所有测试数据**，特别是 `sys_user` / `sys_local_credential` / `sys_external_identity` / 各日志表。初始 `admin` 之外的用户都该删掉。
2. **所有 `auth_version` +1**，强制所有人重新登录（否则旧的 JWT/会话如果也被搬过来了会继续有效）：
   ```sql
   UPDATE sys_user SET auth_version = auth_version + 1;
   ```
   （Redis 里的会话不会被 mysqldump 带走，所以实际上不会有残留；但加上这条更稳。）

### 5.4 【建议】现在就补一个迁移方案

因为你接下来必然要改表（比如加 `sys_role_dept`，见 `DATA-SCOPE-ROLE-DEPT.md`），**强烈建议先建立习惯**：

```
backend/src/main/resources/db/
├── init_schema.sql          # 空库建表（新装用）
├── init_data.sql            # 初始数据（新装用）
└── migration-manual/        # 手工迁移脚本，按日期命名，每个只执行一次
    ├── 20260101_add_role_dept.sql
    └── 20260215_add_server_table.sql
```

每个脚本头部写清楚"在什么状态下的库上执行"和"执行后如何验证"（照抄 `upgrade__schema.sql` 的风格——它做得很好：带了预检 SQL、执行后验证 SQL、以及"为什么要这么写"的注释）。

**如果愿意引入工具**：Flyway 是最小改动量的选择（`pom.xml` 加依赖 + `spring.flyway.enabled=true` + 把脚本放 `db/migration/V1__xxx.sql`）。但要注意 Flyway 对**已有的、没有 flyway 历史表的库**需要 `baseline-on-migrate=true`。

---

## ⑥ 验收命令（改完之后逐条跑一遍）

### 6.1 确认品牌字符串清干净了

```bash
# 前端（应该 0 结果，或只剩注释里的 org.lbl 引用）
cd frontend
grep -rniE "lbl|shit|kariya|摸鱼|劳务派遣|少女祈祷" src index.html package.json \
  --include='*.ts' --include='*.tsx' --include='*.css' --include='*.html' --include='*.json'

# 后端
cd ../backend
grep -rniE "lbl|shit|kariya" src pom.xml \
  --include='*.java' --include='*.yml' --include='*.sql' --include='*.txt' --include='*.xml'
```

**预期剩余项**（这些是**有意保留**的）：
- `org.lbl.*` 的 `package` / `import`（如果你跳过了 2.4 包名重命名）
- `lbl.*` 配置前缀（如果你跳过了 2.3）
- `lbl_refresh` cookie 名（如果你跳过了 2.2——**但建议别跳过**）

### 6.2 确认安全配置生效

```bash
# 1) JWT 密钥不再是默认值
grep -n "jwt-secret" backend/src/main/resources/application*.yml
#    → application.yml 应该是 ${JWT_SECRET:...}，application-prod.yml 应该是 ${JWT_SECRET}

# 2) 生产配置里没有明文密钥
grep -nE "password: (?!\$\{)" backend/src/main/resources/application-prod.yml
#    → 应该没有输出（全部走 ${ENV}）

# 3) 本地配置文件确实被忽略
git check-ignore -v backend/src/main/resources/application-local.yml
#    → .gitignore:56:application-local.yml

# 4) 历史里没有提交过
git log --all --oneline -- backend/src/main/resources/application-local.yml
#    → 应该没有输出
```

### 6.3 功能自检（部署后手工过一遍）

- [ ] 打开首页能看到登录页，**浏览器标签页标题是公司名**，**首屏 loading 文案正常**（乱码问题已修复 ✅，但当前文案仍是 `少女祈祷中...`，改品牌时要换掉）
- [ ] 广告位/标语位置是公司信息，侧边栏底部不是 `CRUD · BUG · CV`
- [ ] `admin` 登录成功 → **立刻被强制改密**（这是设计行为）
- [ ] 改完密码 → 侧边栏菜单完整，能进"用户管理"
- [ ] 建一个新角色 + 一个新用户，授权，用新用户登录 → **只能看到授权的菜单**；直接手敲未授权地址 → 显示 403；手敲不存在的地址 → 显示 404
- [ ] 新用户不点任何东西，15 分钟后继续操作 → **不跳登录页**（静默续期生效）
- [ ] 连错密码 5 次 → 提示"登录尝试过于频繁"（429），且 `sys_login_log` 里有 LOCKED 记录
- [ ] 从另一台机器登录同一账号 → "登录与安全"页能看到两个会话；踢掉一个 → 那台机器的下一个操作跳登录页
- [ ] Nginx 在前端时，`sys_login_log.login_ip` 是**真实的客户端 IP**，不是 `127.0.0.1`（验证 `TRUSTED_PROXIES` 配对）
- [ ] 站内通知能在**不刷新页面**的情况下弹出来（验证 WebSocket 的 `/ws/` 代理配置）
- [ ] `sys_operation_log` 里有"新增用户""修改角色"等记录（验证 AOP 生效）
- [ ] 注册页能显示图形验证码（**如果显示不出来或永远是空白图 → 服务器缺字体**）
- [ ] 暗色主题下：登录页、注册页、404 页、`/account/setup` 页**是否可读**（已知这几页有硬编码浅色，见 `ASSESSMENT.md`）

### 6.4 性能自检（可选）

```bash
# 前端产物大小。注意首屏关键路径上挂着 favicon(972KB) + background.png(974KB)
du -sh frontend/dist
ls -la frontend/dist/assets | sort -k5 -n | tail -20
```
**favicon.ico 和 logo.png 是同一个 972KB 的文件，background.png 又有 974KB —— 登录页首屏要多下载约 2MB。**
换成压缩过的小图（目标：每张 < 100KB）是性价比最高的一步优化。

---

## 附：一页纸速查（打印出来照着做）

```
【安全整改 — 不做别上线】
 □ application.yml:34   jwt-secret → ${JWT_SECRET:...}
 □ application-prod.yml 加 jwt-secret: ${JWT_SECRET}（无默认值）
 □ 生成强密钥并写进服务器的 EnvironmentFile
 □ application-local.yml 重写成内网地址（或改用环境变量，弃用 Jasypt）
 □ git log 检查 application-local.yml 是否进过历史
 □ 启用三方登录的话：修 state 未绑定浏览器的问题

【后端改名】
 □ banner.txt            清空或换成公司 banner（1-19 行的图案一并删）
 □ application.yml:3     spring.application.name
 □ pom.xml:11-12         groupId / artifactId
 □ lbl_refresh → 新名（6 处，必须全改）
 □ application-local.yml:34  删掉无效的 org.kariya 配置
 □ （可选）lbl.* 配置前缀 → 14 个文件
 □ （可跳过）org.lbl 包名 → 129 个文件

【前端品牌】
 □ 产品名 12 处（index.html / SiderBrand / LoginBrand / LoadingScreen /
                login×2 / register×2 / home）
 □ 标语 14 处（摸鱼基地×2 / CRUD·BUG·CV / Kariya / 劳务派遣×4 / 少女祈祷×2 / AI助手×2）
 □ 资源 4 个（logo.png / favicon.ico / background.png / astronaut-404.png）
   ⚠️ favicon.ico 与 logo.png 是【同一个文件】（SHA256 相同，且 ico 里其实是 PNG 数据）→ 换 logo 时两个都要处理
   ⚠️ logo 显示尺寸只有 62px 却存成 1254×1254 / 972KB；登录背景 1916×821 / 974KB → 压到 100KB 以内
 □ localStorage key 2 处（ThemeProvider / AppLayout×2）
 □ package.json name
 □ 主色（ThemeProvider BRAND_COLORS + tokens.ts + index.html 内联 CSS）

【部署配置】
 □ Nginx 同源：/ → dist、/api/ → 8080、/ws/ → 8080（带 Upgrade 头）
 □ TRUSTED_PROXIES=127.0.0.1（★ 最容易漏，漏了限流会误伤全内网）
 □ COOKIE_SECURE=false（http）或 true（https）
 □ CORS_ALLOWED_ORIGINS 留空（同源）
 □ Redis maxmemory-policy 不要用 allkeys-*
 □ 容器镜像装字体（或用 CAPTCHA_ENABLED=false）
 □ npm run build 重新构建（新增页面后必须重跑）

【数据库】
 □ 新建库：init_schema.sql + init_data.sql
 □ 或搬迁：mysqldump → 清理测试数据 → auth_version + 1
 □ 把 jar 里那 5 个 upgrade__*.sql 捞回源码树（命令见第 5.2 节，从 target\lbl-shit-1.0.0.jar 提取）

【验收】
 □ 品牌字符串 grep 干净
 □ 部署后 12 项功能自检全过
 □ 登录页首屏体积 < 1MB（压缩图片后）
```
