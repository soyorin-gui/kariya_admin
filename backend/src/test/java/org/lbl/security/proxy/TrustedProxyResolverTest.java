package org.lbl.security.proxy;

import org.junit.jupiter.api.Test;
import org.lbl.config.SecurityProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 可信代理 IP 推导的行为约定。
 * <p>
 * 这些用例锁死的是安全性最关键的一条：<b>转发头只在请求确实来自可信代理时才被采信</b>。
 * 如果哪天有人把这里改成"无条件取 XFF 第一个值"，本测试会立刻变红。
 */
class TrustedProxyResolverTest {

    private static TrustedProxyResolver resolver(String trustedProxies) {
        return new TrustedProxyResolver(properties(trustedProxies));
    }

    private static SecurityProperties properties(String trustedProxies) {
        return new SecurityProperties("test-secret-at-least-32-characters-long", 15, 2, 7, 14, false, "", trustedProxies);
    }

    @Test
    void withoutTrustedProxiesForwardingHeadersAreIgnoredEntirely() {
        TrustedProxyResolver resolver = resolver("");
        // 攻击者直连并自带 XFF：必须被无视，否则限流可以随意绕过。
        assertEquals("203.0.113.9", resolver.resolve("203.0.113.9", "1.2.3.4", "for=1.2.3.4"));
        assertEquals("203.0.113.9", resolver.resolve("203.0.113.9", null, null));
    }

    @Test
    void directRequestCannotSpoofClientIpEvenWhenProxiesAreConfigured() {
        TrustedProxyResolver resolver = resolver("10.0.0.1");
        // 对端不是可信代理 → 它无权替别人声明来源。
        assertEquals("203.0.113.9", resolver.resolve("203.0.113.9", "1.2.3.4", null));
    }

    @Test
    void singleTrustedProxyYieldsRealClient() {
        TrustedProxyResolver resolver = resolver("10.0.0.1");
        assertEquals("198.51.100.9", resolver.resolve("10.0.0.1", "198.51.100.9", null));
    }

    @Test
    void leftmostSpoofedEntryIsNotTrusted() {
        TrustedProxyResolver resolver = resolver("10.0.0.1");
        // nginx 追加真实客户端到右侧；左侧的 9.9.9.9 是客户端自己伪造的，必须被跳过。
        assertEquals("198.51.100.9", resolver.resolve("10.0.0.1", "9.9.9.9, 198.51.100.9", null));
    }

    @Test
    void chainOfTrustedProxiesWalksRightToLeft() {
        TrustedProxyResolver resolver = resolver("10.0.0.1,10.0.0.2");
        assertEquals("198.51.100.9", resolver.resolve("10.0.0.2", "198.51.100.9, 10.0.0.1", null));
    }

    @Test
    void allHopsTrustedFallsBackToPeerAddress() {
        TrustedProxyResolver resolver = resolver("10.0.0.1");
        // 整条链都是可信代理、没有更外层客户端信息：宁可回退对端地址，也不要凭空取一个值。
        assertEquals("10.0.0.1", resolver.resolve("10.0.0.1", "10.0.0.1", null));
    }

    @Test
    void cidrAndExactIpRulesBothWork() {
        TrustedProxyResolver resolver = resolver("10.0.0.0/8,192.168.1.5");
        assertEquals("198.51.100.9", resolver.resolve("10.1.2.3", "198.51.100.9", null));
        assertEquals("198.51.100.9", resolver.resolve("192.168.1.5", "198.51.100.9", null));
        // 203.0.113.7 与精确规则 192.168.1.5 不同网段，也不是 10/8 → 不可信。
        assertEquals("203.0.113.7", resolver.resolve("203.0.113.7", "198.51.100.9", null));
    }

    @Test
    void prefixLengthPartialByteIsMatchedBitwise() {
        TrustedProxyResolver resolver = resolver("10.0.0.0/12");
        // 10.0.0.0/12 覆盖 10.0.0.0 - 10.15.255.255
        assertEquals("198.51.100.9", resolver.resolve("10.15.255.254", "198.51.100.9", null));
        // 10.16.0.1 已在 /12 之外 → 不可信 → 转发头被忽略
        assertEquals("10.16.0.1", resolver.resolve("10.16.0.1", "198.51.100.9", null));
    }

    @Test
    void portsAndIpv6BracketsAreNormalised() {
        TrustedProxyResolver resolver = resolver("10.0.0.1");
        // 带端口的地址必须能匹配上可信规则，否则整条还原链会失效。
        assertEquals("198.51.100.9", resolver.resolve("10.0.0.1:44321", "198.51.100.9:5555", null));
        assertEquals("2001:db8::1", resolver.resolve("10.0.0.1", "[2001:db8::1]:8080", null));
    }

    @Test
    void ipv6CidrRuleIsSupported() {
        TrustedProxyResolver resolver = resolver("2001:db8::/32");
        // 客户端在 2001:db8::/32 之外 → 它是真实客户端，应当被返回。
        assertEquals("2001:dead::9", resolver.resolve("2001:db8::1", "2001:dead::9", null));
        // 对端不在可信段内 → 不可信，转发头被忽略。
        assertEquals("2001:dead::1", resolver.resolve("2001:dead::1", "2001:db8:1::5", null));
    }

    @Test
    void rfc7239ForwardedHeaderIsParsedAsFallback() {
        TrustedProxyResolver resolver = resolver("10.0.0.1");
        assertEquals("192.0.2.60", resolver.resolve("10.0.0.1", null, "for=192.0.2.60;proto=http;by=203.0.113.43"));
        assertEquals("198.51.100.9", resolver.resolve("10.0.0.1", null, "for=\"198.51.100.9\""));
    }

    @Test
    void xForwardedForTakesPrecedenceOverForwarded() {
        TrustedProxyResolver resolver = resolver("10.0.0.1");
        assertEquals("198.51.100.9", resolver.resolve("10.0.0.1", "198.51.100.9", "for=192.0.2.60"));
    }

    @Test
    void unknownAndMalformedEntriesAreSkipped() {
        TrustedProxyResolver resolver = resolver("10.0.0.1");
        assertEquals("198.51.100.9", resolver.resolve("10.0.0.1", "unknown, 198.51.100.9", null));
        // 空段与空白不会让解析崩掉
        assertEquals("198.51.100.9", resolver.resolve("10.0.0.1", " ,, 198.51.100.9 , ", null));
    }

    @Test
    void invalidRulesAreIgnoredInsteadOfFailingStartup() {
        // 写错的规则只告警并跳过：宁可退回"不信转发头"，也不能让应用起不来。
        TrustedProxyResolver resolver = resolver("not-an-ip,10.0.0.1,10.0.0.0/99");
        assertEquals("198.51.100.9", resolver.resolve("10.0.0.1", "198.51.100.9", null));
        assertEquals("203.0.113.7", resolver.resolve("203.0.113.7", "198.51.100.9", null));
    }

    @Test
    void ipv4MappedIpv6PeerStillMatchesIpv4Rule() {
        TrustedProxyResolver resolver = resolver("10.0.0.1/32");
        // 双栈主机上 getRemoteAddr() 可能是 ::ffff:10.0.0.1。若不还原成 IPv4，
        // 规则会因字节长度不同而匹配失败，可信代理配置静默失效。
        assertEquals("198.51.100.9", resolver.resolve("::ffff:10.0.0.1", "198.51.100.9", null));
    }

    @Test
    void oversizedHeaderIsRejected() {
        TrustedProxyResolver resolver = resolver("10.0.0.1");
        String huge = "1.1.1.1,".repeat(200) + "198.51.100.9";
        // 超长头直接当作不可解析，回退对端地址，避免无界解析消耗。
        assertEquals("10.0.0.1", resolver.resolve("10.0.0.1", huge, null));
    }
}
