/**
 * 密码策略的前端副本。
 *
 * ⚠️ **必须与后端 `org.lbl.auth.service.PasswordRules` 的 `REGEX` / `MESSAGE` 完全一致。**
 *
 * 前端不能 import Java，所以这两个值是手抄的一份字面量副本，两边一旦漂移，
 * 表现会非常难查：用户在前端看到"通过"，提交后被后端拒绝——而后端回显的是
 * 密码规则文案（见 GlobalExceptionHandler#preferredMessage），于是用户会看到
 * 两条互相矛盾的提示，或者觉得"明明填对了却说格式不对"。
 *
 * 修改其中一边时，请同时改另一边，并保证：
 *  - `PASSWORD_PATTERN` 与 `PasswordRules.REGEX` 逐字符相同；
 *  - `PASSWORD_MESSAGE` 与 `PasswordRules.MESSAGE` 逐字符相同
 *    （后端那句由 `MIN_LENGTH`/`MAX_LENGTH` 常量拼出，所以改长度时这句话会自动跟着变）。
 *
 * 适用范围与后端一致：**只用于"设置新密码"**（自助注册、外部身份开户、管理员新增用户、本人改密）。
 * 登录密码、"原密码"、以及外部身份绑定已有账号时输入的密码都**不能**套这个规则——
 * 那些是历史密码，可能是旧的 12-72 位且含符号，套上就会让老用户登不进去。
 */
export const PASSWORD_PATTERN =
  /^(?=.{8,32}$)(?=[\x21-\x7E]+$)(?:(?=.*[a-z])(?=.*[A-Z])(?=.*\d)|(?=.*[a-z])(?=.*[A-Z])(?=.*[^A-Za-z0-9])|(?=.*[a-z])(?=.*\d)(?=.*[^A-Za-z0-9])|(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9])).*$/;
export const PASSWORD_MESSAGE = '密码须为 8-32 位，且至少包含大写字母、小写字母、数字、特殊字符中的三类（不含空格）';
