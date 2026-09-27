package org.lbl.auth.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.lbl.auth.identity.VerifiedIdentity;
import org.lbl.auth.model.SessionGrant;
import org.lbl.auth.session.LoginSession;
import org.lbl.auth.session.SessionService;
import org.lbl.auth.service.LoginAttemptGuard;
import org.lbl.common.exception.BusinessException;
import org.lbl.common.exception.TooManyRequestsException;
import org.lbl.system.log.service.LoginLogService;
import org.lbl.system.log.support.LogResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;

/**
 * 外部登录/绑定的<b>协议与网络</b>部分：发起授权、消费 state、换令牌、取用户资料。
 * <p>
 * 落库与会话签发不在这里，见 {@link ExternalLoginPersistence}（那边刻意不持有 HttpClient，
 * 以保证事务里不会出现网络 IO）。
 */
@Service
public class ExternalLoginService {
    private static final Logger log = LoggerFactory.getLogger(ExternalLoginService.class);
    private static final Duration TRANSACTION_TTL = Duration.ofMinutes(10);
    /** 未认证的 /start 入口：单来源固定窗口配额。 */
    private static final int START_QUOTA_LIMIT = 30;
    private static final Duration START_QUOTA_WINDOW = Duration.ofMinutes(1);
    /** 外部平台报错信息写入日志时的长度上限，避免把超长响应体灌进日志。 */
    private static final int PROVIDER_ERROR_MAX_LENGTH = 200;
    private final ExternalAuthProperties properties;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final LoginAttemptGuard attempts;
    private final LoginLogService loginLogs;
    private final ExternalLoginPersistence persistence;
    private final SessionService sessions;
    private final SecureRandom random = new SecureRandom();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public ExternalLoginService(ExternalAuthProperties properties, StringRedisTemplate redis, ObjectMapper json,
                                LoginAttemptGuard attempts, LoginLogService loginLogs,
                                ExternalLoginPersistence persistence, SessionService sessions) {
        this.properties = properties;
        this.redis = redis;
        this.json = json;
        this.attempts = attempts;
        this.loginLogs = loginLogs;
        this.persistence = persistence;
        this.sessions = sessions;
    }

    public List<ProviderView> providers() {
        return properties.getProviders().entrySet().stream()
                .map(entry -> new ProviderView(entry.getKey(), entry.getValue().getDisplayName(),
                        entry.getValue().getIcon(), entry.getValue().getProtocol(), isProviderUsable(entry.getKey())))
                .toList();
    }

    /**
     * 解绑保护使用的"可用"口径，不能只看数据库里是否留着一条绑定记录。
     * <p>
     * 注意这个口径比 {@code enabled} 严格得多：它要求 clientId / clientSecret 与三个端点地址
     * <b>全部非空</b>。所以"enabled=true 但按钮仍是灰的"这种情况，一定是有配置项为空 ——
     * 而且日志里不会有任何提示。这是排查三方登录时最容易卡住的一步。
     */
    public boolean isProviderUsable(String providerKey) {
        ExternalAuthProperties.Provider provider = properties.getProviders().get(providerKey);
        if (provider == null || !provider.isEnabled()) return false;
        if (isSaml(provider)) return false;
        return hasText(provider.getAuthorizationUri()) && hasText(provider.getTokenUri())
                && hasText(provider.getUserInfoUri()) && hasText(provider.getClientId())
                && hasText(provider.getClientSecret());
    }

    /** SAML（UIAS）判定的唯一口径：只认配置里的 protocol 字符串，与任何 Java 类型无关。 */
    private static boolean isSaml(ExternalAuthProperties.Provider provider) {
        return "SAML".equalsIgnoreCase(provider.getProtocol());
    }

    public String beginLogin(String providerKey, String returnTo, boolean rememberMe) {
        // 唯一一个匿名入口，而且每次调用都会往 Redis 写一条登录事务（TTL 10 分钟）。
        // 不加限制时，任何人用一段脚本就能持续灌 key 撑大 Redis，所以按来源做固定窗口配额。
        // 30 次/分钟远高于正常人的点击频率（点错了重试几次而已），
        // 共享出口的办公网里 30 个人同一分钟点击开始登录也不会被误伤。
        attempts.requireSourceQuota("external-start", START_QUOTA_LIMIT, START_QUOTA_WINDOW);
        return begin(providerKey, safeReturnTo(returnTo), rememberMe, null, null);
    }

    public String beginBinding(String providerKey, Long userId, String initiatingSid) {
        LoginSession session = initiatingSid == null ? null : sessions.find(initiatingSid);
        if (session == null || session.onboarding() || !userId.equals(session.userId())) {
            throw new BusinessException("发起绑定的登录会话无效，请重新登录后再试");
        }
        return begin(providerKey, "/account/security", false, userId, initiatingSid);
    }

    private String begin(String providerKey, String returnTo, boolean rememberMe, Long targetUserId,
                         String initiatingSid) {
        ExternalAuthProperties.Provider provider = requireProvider(providerKey);
        // 本系统只实现了 OAuth2 / OIDC 的授权码流程，没有 SAML（UIAS）的验签实现。
        // 这条拦截与 isProviderUsable 里那条同源：即使有人把某个 provider 的 protocol 改成 SAML
        // 并打开 enabled，也不会走进一条没有实现的路径。保留它是安全网，不是死代码。
        if (isSaml(provider)) {
            throw new BusinessException("系统不支持 SAML 协议登录");
        }
        requireText(provider.getAuthorizationUri(), "未配置授权地址");
        requireText(provider.getTokenUri(), "未配置令牌地址");
        requireText(provider.getUserInfoUri(), "未配置用户资料地址");
        requireText(provider.getClientId(), "未配置客户端 ID");
        requireText(provider.getClientSecret(), "未配置客户端密钥");
        String state = token(32);
        String verifier = token(48);
        String redirectUri = callbackUri(providerKey);
        LoginTransaction transaction = new LoginTransaction(providerKey, verifier, redirectUri, returnTo, rememberMe,
                targetUserId, initiatingSid);
        try {
            redis.opsForValue().set(transactionKey(state), json.writeValueAsString(transaction), TRANSACTION_TTL);
        } catch (Exception ex) {
            throw new IllegalStateException("无法创建外部登录事务", ex);
        }
        Map<String, String> query = new LinkedHashMap<>();
        query.put(provider.getClientIdParameter(), provider.getClientId());
        query.put("redirect_uri", redirectUri);
        query.put("response_type", "code");
        query.put("scope", String.join(" ", provider.getScopes()));
        query.put("state", state);
        if (provider.isPkceEnabled()) {
            query.put("code_challenge", base64Url(sha256(verifier)));
            query.put("code_challenge_method", "S256");
        }
        query.putAll(provider.getAuthorizationParameters());
        String authorizationUrl = appendQuery(provider.getAuthorizationUri(), query);
        return provider.getAuthorizationFragment() == null || provider.getAuthorizationFragment().isBlank()
                ? authorizationUrl : authorizationUrl + "#" + provider.getAuthorizationFragment();
    }

    /**
     * 完成一次外部认证回调。
     * <p>
     * <b>这个方法刻意不加 {@code @Transactional}</b>：它要发两次外部 HTTP 请求（换令牌、取用户资料，
     * 各自超时 15 秒），事务一旦开启就会占住一条数据库连接，几个并发请求即可打满连接池。
     * 落库那一小段在 {@link ExternalLoginPersistence#settle} 里单独开事务。
     *
     * @param currentSid 发起本次流程的那个浏览器当前持有的会话 id，可为 null。
     *                   只有<b>绑定</b>流程会用到它：绑定是在一个已经登录的会话上做的附加操作，
     *                   不应该顺带把用户的会话换掉。登录流程完全忽略它。
     */
    public Completion complete(String providerKey, String code, String state, String currentSid) {
        LoginTransaction transaction = null;
        Completion completion;
        try {
            transaction = consume(state);
            if (!transaction.providerKey().equals(providerKey)) throw new BusinessException("外部登录来源不匹配");
            if (transaction.targetUserId() != null) {
                LoginSession initiating = currentSid == null ? null : sessions.find(currentSid);
                if (!Objects.equals(transaction.initiatingSid(), currentSid) || initiating == null
                        || initiating.onboarding() || !transaction.targetUserId().equals(initiating.userId())) {
                    throw new BusinessException("发起绑定的原登录会话已失效或发生变化，请重新发起绑定");
                }
            }
            ExternalAuthProperties.Provider provider = requireProvider(providerKey);
            VerifiedIdentity verified = verifyIdentity(provider, providerKey, transaction, code);
            completion = persistence.settle(providerKey, transaction, verified, currentSid);
        } catch (BusinessException | TooManyRequestsException ex) {
            // 外部登录的失败此前在 sys_login_log 里是一片空白：谁、什么时候、用哪种第三方方式、
            // 因为什么失败了都查不到。这里统一记一条 FAILURE。
            loginLogs.record(auditSubject(providerKey, transaction), null, LogResult.FAILURE,
                    "外部登录失败：" + (ex.getMessage() == null ? "未知原因" : ex.getMessage()));
            throw ex;
        } catch (RuntimeException ex) {
            // 非业务异常（唯一键冲突、SQL 异常、NPE…）也要留痕：它们同样是"这一次登录没成功"，
            // 漏掉就会出现"用户说第三方登不上，而登录日志里干干净净"的情况。
            // 消息刻意不落进审计表 —— 异常文本里可能带表名/列名/SQL，细节留在应用日志即可。
            log.warn("External login failed with an internal error: provider={}", providerKey, ex);
            loginLogs.record(auditSubject(providerKey, transaction), null, LogResult.FAILURE, "外部登录失败（内部错误）");
            throw ex;
        }
        // 成功日志刻意写在 try 之外，两个原因：
        //   ① settle 正常返回意味着它的事务已经提交并归还连接，此时再写日志（REQUIRES_NEW）
        //      不会出现"一个请求同时占两条连接"；外部登录是并发入口，这条要守住；
        //   ② 它也不会被上面的 catch 误判成失败 —— "记了成功"就一定是真的成功了。
        loginLogs.record(completion.audit().username(), completion.audit().userId(), LogResult.SUCCESS, completion.audit().message());
        return completion;
    }

    /**
     * 网络阶段：把授权码换成令牌、再取回用户资料，产出"已经确认的身份"。
     * <p>
     * 与落库彻底分开，一是为了让上面的事务边界干净，二是这一步失败的原因几乎全在外部平台那边，
     * 单独成段后"哪里出了问题"在日志里也更清楚。
     */
    private VerifiedIdentity verifyIdentity(ExternalAuthProperties.Provider provider, String providerKey,
                                            LoginTransaction transaction, String code) {
        Map<String, Object> tokenResponse = exchange(provider, transaction, code);
        String accessToken = stringValue(tokenResponse.get("access_token"));
        if (accessToken == null) {
            // 走到这里通常不是"服务坏了"，而是外部平台用自己的错误约定拒绝了这次交换
            // （例如微信固定返回 HTTP 200 + errcode）。不把原因记下来，
            // 排查时只能看到一句"未返回访问令牌"，与真实原因完全无关。
            log.warn("External token exchange returned no access_token: provider={} {}",
                    providerKey, describeProviderError(tokenResponse));
            throw new BusinessException("外部认证服务未返回访问令牌");
        }
        Map<String, Object> profile = userInfo(provider, tokenResponse, accessToken);
        logProviderError("user-info", providerKey, profile);
        String subject = claim(profile, provider.getSubjectClaim());
        if (subject == null && "wechat".equals(providerKey)) subject = stringValue(tokenResponse.get("openid"));
        if (subject == null) throw new BusinessException("外部认证结果缺少稳定用户标识");
        return new VerifiedIdentity(providerKey, normalizeIssuer(provider.getIssuer()), subject,
                claim(profile, provider.getDisplayNameClaim()), claim(profile, provider.getEmailClaim()),
                claim(profile, provider.getEmployeeNoClaim()), profile);
    }

    /**
     * 写登录日志用的主体。
     * <p>
     * 走到这个 catch 时还没有真正的用户名可用（事务可能压根没取到），
     * 因此用可检索的前缀代替：绑定流程是 {@code bind:{provider}}，开户确认是 {@code onboarding:{provider}}。
     * 成功日志里用的是真实用户名，见 {@link ExternalLoginPersistence}。
     */
    private String auditSubject(String providerKey, LoginTransaction transaction) {
        return (transaction != null && transaction.targetUserId() != null ? "bind:" : "onboarding:") + providerKey;
    }

    private Map<String, Object> exchange(ExternalAuthProperties.Provider provider, LoginTransaction transaction, String code) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put(provider.getClientIdParameter(), provider.getClientId());
        form.put(provider.getClientSecretParameter(), provider.getClientSecret());
        form.put("code", code);
        form.put("redirect_uri", transaction.redirectUri());
        form.put("grant_type", "authorization_code");
        if (provider.isPkceEnabled()) form.put("code_verifier", transaction.verifier());
        form.putAll(provider.getTokenParameters());
        try {
            HttpRequest.Builder builder;
            if ("GET".equalsIgnoreCase(provider.getTokenMethod())) {
                builder = HttpRequest.newBuilder(URI.create(appendQuery(provider.getTokenUri(), form))).GET();
            } else {
                builder = HttpRequest.newBuilder(URI.create(provider.getTokenUri()))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(formEncode(form)));
            }
            HttpResponse<String> response = http.send(builder.header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                // 注意不要把 provider.getTokenUri() 打进日志：GET 方式换令牌时
                // client_secret 就在查询串里，URI 一旦落盘等于把密钥写进日志文件。
                log.warn("External token endpoint rejected the exchange: status={} {}",
                        response.statusCode(), describeFailedResponse(response.body()));
                throw new BusinessException("外部认证服务拒绝了令牌交换");
            }
            return parseBody(response.body());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("无法连接外部认证服务");
        }
    }

    private Map<String, Object> userInfo(ExternalAuthProperties.Provider provider, Map<String, Object> token,
                                         String accessToken) {
        Map<String, String> query = new LinkedHashMap<>();
        provider.getUserInfoParameters().forEach((key, value) -> query.put(key, expand(value, token, accessToken)));
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(appendQuery(provider.getUserInfoUri(), query)))
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .timeout(Duration.ofSeconds(15)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("External user-info endpoint rejected the request: status={} {}",
                        response.statusCode(), describeFailedResponse(response.body()));
                throw new BusinessException("外部认证服务未能返回用户资料");
            }
            return parseBody(response.body());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("无法读取外部用户资料");
        }
    }

    private LoginTransaction consume(String state) {
        if (state == null || state.isBlank()) throw new BusinessException("外部登录状态无效或已过期");
        String key = transactionKey(state);
        String value = redis.opsForValue().getAndDelete(key);
        if (value == null) throw new BusinessException("外部登录状态无效或已过期");
        try {
            return json.readValue(value, LoginTransaction.class);
        } catch (Exception ex) {
            throw new BusinessException("外部登录状态已损坏，请重新发起登录");
        }
    }

    private ExternalAuthProperties.Provider requireProvider(String key) {
        ExternalAuthProperties.Provider provider = properties.getProviders().get(key);
        if (provider == null || !provider.isEnabled()) throw new BusinessException("该登录方式未启用");
        return provider;
    }

    private Map<String, Object> parseBody(String body) throws Exception {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.startsWith("{")) return json.readValue(trimmed, new TypeReference<>() {});
        Map<String, Object> result = new LinkedHashMap<>();
        for (String pair : trimmed.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(decode(parts[0]), parts.length == 2 ? decode(parts[1]) : "");
        }
        return result;
    }

    private String claim(Map<String, Object> source, String path) {
        if (path == null || path.isBlank()) return null;
        Object current = source;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(part);
        }
        return stringValue(current);
    }

    /**
     * 把外部平台的错误约定提取成一行可搜的文本，<b>只用于日志</b>。
     * <p>
     * 必须同时认两种约定：有的平台用 HTTP 状态码报错（GitHub），
     * 有的坚持 <b>HTTP 200 + errcode</b>（微信用的是这一套，例如 41001 = 缺 access_token）。
     * 后者在"只看状态码"的代码里会被当成成功，最终表现成一个与真实原因毫无关系的报错
     * （例如"缺少稳定用户标识"），所以这里两种都要认。
     *
     * @return 无错误时返回 {@code "no provider error reported"}，便于日志里明确区分
     *         "平台没报错"和"我们漏记了"
     */
    private String describeProviderError(Map<String, Object> body) {
        if (body == null || body.isEmpty()) return "empty response body";
        String code = firstText(body, "error", "errcode");
        if (code == null || "0".equals(code)) return "no provider error reported";
        String description = firstText(body, "error_description", "errmsg", "error_message", "message");
        return description == null ? "code=" + code : "code=" + code + ", description=" + description;
    }

    private void logProviderError(String stage, String providerKey, Map<String, Object> body) {
        String reason = describeProviderError(body);
        if (reason.startsWith("code=")) {
            log.warn("External auth provider reported an error at {}: provider={} {}", stage, providerKey, reason);
        }
    }

    private String firstText(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            String value = stringValue(body.get(key));
            if (value != null && !value.isBlank()) return truncateForLog(value);
        }
        return null;
    }

    /** 响应体可能很大，也可能带换行；日志里只留一小段，且必须单行。 */
    private String truncateForLog(String value) {
        if (value == null) return "";
        String single = value.replaceAll("\\s+", " ").trim();
        return single.length() <= PROVIDER_ERROR_MAX_LENGTH ? single : single.substring(0, PROVIDER_ERROR_MAX_LENGTH) + "…";
    }

    /**
     * 把一次<b>失败的</b> HTTP 响应压成一行日志。
     * <p>
     * 优先只取能被识别的错误字段，而不是把原始报文整段打出来：OAuth 服务的校验错误里
     * 很可能回显触发错误的参数值（例如 {@code invalid client_secret=xxx}），
     * 整段落盘等于把客户端密钥写进日志文件。只有认不出任何错误字段时
     * （网关返回的 HTML 错误页、纯文本等）才回退到原始报文，因为那时它是唯一线索。
     */
    private String describeFailedResponse(String body) {
        try {
            String reason = describeProviderError(parseBody(body));
            if (reason.startsWith("code=")) return reason;
        } catch (Exception ex) {
            // 报文既不是 JSON 也不是表单，下面回退到原始报文
        }
        String raw = truncateForLog(body);
        return raw.isEmpty() ? "empty response body" : raw;
    }

    private String expand(String template, Map<String, Object> token, String accessToken) {
        String result = template.replace("{access_token}", accessToken);
        for (Map.Entry<String, Object> entry : token.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", Objects.toString(entry.getValue(), ""));
        }
        return result;
    }

    private String callbackUri(String provider) {
        return properties.getBackendBaseUrl().replaceAll("/$", "") + "/api/auth/external/" + encode(provider) + "/callback";
    }

    public String frontendCallback(String returnTo, String error) {
        Map<String, String> query = new LinkedHashMap<>();
        if (returnTo != null) query.put("returnTo", safeReturnTo(returnTo));
        if (error != null) query.put("error", error);
        return appendQuery(properties.getFrontendBaseUrl().replaceAll("/$", "") + "/auth/callback", query);
    }

    private String safeReturnTo(String value) {
        return value != null && value.startsWith("/") && !value.startsWith("//") ? value : "/";
    }
    private String transactionKey(String state) { return "auth:external:transaction:" + state; }
    private String normalizeIssuer(String value) { return value == null ? "" : value; }
    private String stringValue(Object value) { return value == null ? null : String.valueOf(value); }
    private void requireText(String value, String message) { if (value == null || value.isBlank()) throw new BusinessException(message); }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String token(int bytes) { byte[] value = new byte[bytes]; random.nextBytes(value); return base64Url(value); }
    private byte[] sha256(String value) { try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private String base64Url(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private String decode(String value) { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
    private String formEncode(Map<String, String> values) { return values.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(Objects.toString(e.getValue(), ""))).reduce((a,b) -> a + "&" + b).orElse(""); }
    private String appendQuery(String url, Map<String, String> values) { if (values.isEmpty()) return url; return url + (url.contains("?") ? "&" : "?") + formEncode(values); }

    public record ProviderView(String key, String displayName, String icon, String protocol, boolean enabled) {}

    /**
     * 一次外部登录/绑定的结果。
     *
     * @param audit 成功时要写进 {@code sys_login_log} 的内容。之所以由返回结果带出来、
     *              而不是在落库时顺手写掉：日志写入是 {@code REQUIRES_NEW}，
     *              在事务里写会让一个请求同时占用两条数据库连接（见 complete 的注释）。
     */
    public record Completion(SessionGrant grant, String returnTo, LoginAudit audit) {}

    /** 外部登录成功的审计内容：会员记真实用户名/用户 id，开户确认态用 provider 前缀代替。 */
    public record LoginAudit(Long userId, String username, String message) {}
}
