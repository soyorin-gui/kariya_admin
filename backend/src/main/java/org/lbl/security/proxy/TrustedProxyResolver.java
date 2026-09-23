package org.lbl.security.proxy;

import org.lbl.config.SecurityProperties;
import org.lbl.system.log.support.RequestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * 从请求头推导真实客户端 IP —— 只在转发链"确实来自可信代理"时才采信。
 * <p>
 * 为什么必须有这个东西：审计日志里的 IP 是溯源证据，登录限流的 key 也依赖它。
 * 如果无条件相信 {@code X-Forwarded-For}，任何人都能塞一个假 IP，于是
 * <ul>
 *   <li>审计日志失去价值（可以把自己伪装成任意地址）；</li>
 *   <li>登录限流可以被轻易绕过（每次换一个假 IP，账号级锁定永不触发）。</li>
 * </ul>
 * 反过来，如果完全不看转发头，部署在 Nginx 后面时所有请求的 TCP 源地址都是网关，
 * 于是全站用户共享同一个限流 key —— 一个人失败 20 次就把所有人锁在门外。
 * 两种极端都不行，正确做法是显式声明"哪些地址是可信代理"，只信它们的转发头。
 *
 * <h2>算法：从右往左找第一个不可信地址</h2>
 * X-Forwarded-For 由每一跳追加，形如 {@code 客户端, 代理1, 代理2}。攻击者可以伪造
 * 左边的部分（那是他自己加进去的），但<b>右边由我们的可信代理追加，不可伪造</b>。
 * 因此从最右端开始向左走，跳过所有可信代理，遇到的第一个不可信地址就是真实的上一层
 * 客户端 —— 这正是 nginx realip 模块的做法。
 *
 * <h2>配置</h2>
 * <pre>
 * lbl.security.trusted-proxies: 127.0.0.1,10.0.0.0/8,2001:db8::/32
 * </pre>
 * 留空 = 一个转发头都不信，直接返回 TCP 源地址（直连部署下的正确行为）。
 */
@Component
public class TrustedProxyResolver {
    private static final Logger log = LoggerFactory.getLogger(TrustedProxyResolver.class);
    private static final int MAX_HEADER_LENGTH = 512;
    private static final String UNKNOWN = "unknown";

    private final List<Rule> rules;

    public TrustedProxyResolver(SecurityProperties properties) {
        this.rules = properties.trustedProxiesList().stream()
                .map(TrustedProxyResolver::parseRule)
                .flatMap(List::stream)
                .toList();
        // 把自己交给 RequestInfo 这个静态工具类。见 RequestInfo.useResolver 的说明：
        // 这是一个刻意的、有边界的折中，不要把这里当成"可以随便塞静态引用"的先例。
        RequestInfo.useResolver(this);
        if (rules.isEmpty()) {
            log.info("No trusted proxies configured; X-Forwarded-* is ignored and the TCP peer address is used. "
                    + "Set lbl.security.trusted-proxies when running behind a reverse proxy.");
        } else {
            log.info("Trusting forwarding headers from {} configured proxy rule(s)", rules.size());
        }
    }

    /** 是否配置了可信代理。没有配置时调用方可直接走"直连"分支，省掉无意义的头部解析。 */
    public boolean hasTrustedProxies() {
        return !rules.isEmpty();
    }

    /**
     * 解析真实客户端 IP。
     *
     * @param remoteAddr      TCP 对端地址（{@code request.getRemoteAddr()}）
     * @param forwardedFor    {@code X-Forwarded-For} 原始值，可为 null
     * @param forwardedHeader {@code Forwarded}（RFC 7239）原始值，可为 null
     * @return 最多 64 字符的地址；无法判断时回退到 remoteAddr
     */
    public String resolve(String remoteAddr, String forwardedFor, String forwardedHeader) {
        String peer = normalise(remoteAddr);
        if (!hasTrustedProxies()) return peer;
        // 对端不是可信代理：它无权替别人声明来源，转发头一律忽略。
        if (!isTrusted(peer)) return peer;

        List<String> chain = parseForwardedFor(forwardedFor);
        if (chain.isEmpty()) chain = parseForwardedHeader(forwardedHeader);

        for (int index = chain.size() - 1; index >= 0; index--) {
            String candidate = chain.get(index);
            if (!isTrusted(candidate)) return candidate;
        }
        // 整条链都是可信代理（或链为空）：没有更外层的客户端信息，回退到对端地址。
        return peer;
    }

    /** 解析 {@code X-Forwarded-For}：逗号分隔，已经按"由外到内"排列。 */
    private List<String> parseForwardedFor(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_HEADER_LENGTH) return List.of();
        List<String> result = new ArrayList<>();
        for (String segment : value.split(",")) {
            String candidate = normalise(segment);
            if (isUsable(candidate)) result.add(candidate);
        }
        return result;
    }

    /**
     * 解析 {@code Forwarded}（RFC 7239），例如
     * {@code for=192.0.2.60;proto=http;by=203.0.113.43, for="[2001:db8::1]:8080"}。
     * 只取 {@code for=} 参数，因为它才是客户端地址。
     */
    private List<String> parseForwardedHeader(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_HEADER_LENGTH) return List.of();
        List<String> result = new ArrayList<>();
        for (String element : value.split(",")) {
            for (String parameter : element.split(";")) {
                String trimmed = parameter.trim();
                if (!trimmed.regionMatches(true, 0, "for=", 0, 4)) continue;
                String candidate = normalise(trimmed.substring(4));
                if (isUsable(candidate)) result.add(candidate);
            }
        }
        return result;
    }

    /**
     * 归一化单个地址：去引号、去 IPv6 方括号、去端口。
     * <p>
     * 端口必须去掉，否则 {@code 203.0.113.7:44321} 与规则里的 {@code 203.0.113.7} 匹配不上，
     * 可信代理会被误判为不可信，整条链就废了。
     */
    private static String normalise(String value) {
        if (value == null) return null;
        String text = value.trim();
        if (text.isEmpty()) return null;
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1).trim();
        }
        if (text.startsWith("[")) {
            // [2001:db8::1]:8080 或 [2001:db8::1]
            int closing = text.indexOf(']');
            if (closing < 0) return null;
            return text.substring(1, closing);
        }
        // 裸 IPv6（含多个冒号）没有端口概念，不能按冒号切。
        int firstColon = text.indexOf(':');
        if (firstColon >= 0 && text.indexOf(':', firstColon + 1) < 0) {
            return text.substring(0, firstColon);
        }
        return text;
    }

    private static boolean isUsable(String candidate) {
        return candidate != null && !candidate.isEmpty() && !UNKNOWN.equalsIgnoreCase(candidate);
    }

    private boolean isTrusted(String address) {
        if (address == null) return false;
        byte[] bytes = toBytes(address);
        if (bytes == null) return false;
        for (Rule rule : rules) {
            if (rule.matches(bytes)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 规则解析：单个 IP 直接比对；CIDR 展开成"掩码前缀"形式。
    // 之所以用字节前缀匹配而不是字符串前缀：203.0.113.7 与 203.0.113.70 的字符串前缀
    // 相同但网络不同，字符串比对会产生错误的可信判定。
    // ------------------------------------------------------------------

    private static List<Rule> parseRule(String raw) {
        String value = raw.trim();
        if (value.isEmpty()) return List.of();
        int slash = value.indexOf('/');
        if (slash < 0) {
            byte[] address = toBytes(value);
            return address == null ? warnInvalid(raw) : List.of(new Rule(address, address.length * 8));
        }
        String networkPart = value.substring(0, slash);
        byte[] network = toBytes(networkPart);
        if (network == null) return warnInvalid(raw);
        int prefixLength;
        try {
            prefixLength = Integer.parseInt(value.substring(slash + 1).trim());
        } catch (NumberFormatException ex) {
            return warnInvalid(raw);
        }
        int maxBits = network.length * 8;
        if (prefixLength < 0 || prefixLength > maxBits) return warnInvalid(raw);
        return List.of(new Rule(network, prefixLength));
    }

    private static List<Rule> warnInvalid(String raw) {
        // 配置写错时只告警并跳过，不要让应用起不来：可信代理配错最多退回"不信转发头"，
        // 而这个降级方向是安全的（见类注释）。但必须留下痕迹，否则排查限流异常会毫无头绪。
        log.warn("Ignoring invalid trusted-proxy entry '{}'; expected an IP or CIDR such as 10.0.0.0/8", raw);
        return List.of();
    }

    /** 解析 IP 字面量；域名不解析 DNS（避免启动依赖 DNS、也避免解析结果被篡改）。 */
    private static byte[] toBytes(String address) {
        String literal = address;
        int zone = literal.indexOf('%');
        if (zone >= 0) literal = literal.substring(0, zone);
        if (literal.isEmpty()) return null;
        // 只接受字面量：出现字母且不是合法 IPv6 字符时直接拒绝，防止把主机名当规则。
        if (!literal.matches("[0-9a-fA-F:.]+")) return null;
        try {
            return unwrapMappedIpv4(InetAddress.getByName(literal).getAddress());
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    /**
     * 把 IPv4-mapped IPv6（{@code ::ffff:10.0.0.1}）还原成 4 字节 IPv4。
     * <p>
     * 双栈主机上 {@code getRemoteAddr()} 可能返回这种形式。若不还原，配置里的
     * {@code 10.0.0.1} 规则会因为"16 字节 vs 4 字节"匹配失败而被判为不可信，
     * 表现为"可信代理配了却完全不生效、所有人还是共享网关地址"——这类静默失败极难排查。
     */
    private static byte[] unwrapMappedIpv4(byte[] address) {
        if (address.length != 16) return address;
        for (int index = 0; index < 10; index++) {
            if (address[index] != 0) return address;
        }
        if (address[10] != (byte) 0xFF || address[11] != (byte) 0xFF) return address;
        return new byte[]{address[12], address[13], address[14], address[15]};
    }

    private record Rule(byte[] network, int prefixLength) {
        boolean matches(byte[] candidate) {
            if (candidate.length != network.length) return false;
            int fullBytes = prefixLength / 8;
            for (int index = 0; index < fullBytes; index++) {
                if (candidate[index] != network[index]) return false;
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) return true;
            int mask = 0xFF << (8 - remainingBits) & 0xFF;
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
