package org.lbl.auth.service;

import org.lbl.common.exception.BusinessException;
import org.lbl.config.SecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * 注册页的图形验证码：服务端出题、服务端校验。
 *
 * <h2>为什么必须是服务端出题</h2>
 * 纯前端画的验证码（canvas 画完在 JS 里比对）等于没有 —— 攻击者根本不用打开注册页，
 * 直接对 {@code /api/auth/register} 发请求就绕过去了。"页面上有个验证码"和
 * "接口要求验证码"是两件事，这里实现的是后者。
 *
 * <h2>它防的是什么、不防什么</h2>
 * 它拦的是"脚本无限刷注册接口"这一类低成本滥用，<b>不是</b>对抗 OCR 的强验证，
 * 也拦不住愿意花钱打码平台的人。对当前阶段（内网、低频使用）足够；
 * 真到了公网高风险的场景，应当叠加邮箱/短信验证与网关层防护。
 *
 * <h2>两个容易踩的实现点</h2>
 * <ol>
 *   <li><b>一次性消费</b>：校验时用 {@code getAndDelete} 取答案，无论对错都立刻失效。
 *       如果允许对同一个 captchaId 反复提交，攻击者只要拿到一个 id 就能在 2 分钟内
 *       高速穷举（答案空间只有 32^4），验证码就白做了。前端的做法是失败后自动换一张。</li>
 *   <li><b>TTL 很短</b>（2 分钟）：验证码的答案是明文存在 Redis 里的，
 *       有效期越长，留给"被猜/被撞"的窗口越大，也没有必要让用户慢慢想。</li>
 * </ol>
 *
 * <h2>为什么不用第三方验证码库</h2>
 * JDK 自带 Java2D + {@code ImageIO} 足以画出"带干扰的 4 位字符"，能省掉一个依赖。
 * 代价是对抗 OCR 的能力不如成熟库；但如上所述，本阶段的目标不是对抗 OCR。
 * <b>代价是运行环境必须有可用字体</b>，见构造器里的探测。
 */
@Service
public class CaptchaService {
    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);

    private static final Duration TTL = Duration.ofMinutes(2);
    private static final int CODE_LENGTH = 4;
    /**
     * 字符表：去掉了三类字符。
     * <ol>
     *   <li><b>易混字符</b> {@code 0/O}、{@code 1/I/L} —— 用户看不清时的唯一补救是"换一张"，
     *       而这类误判会被归因成"验证码老是错"，是纯粹的体验损失、没有安全性收益。</li>
     *   <li><b>最宽的字形 {@code M}、{@code W} —— 这是实测出来的，不是审美问题。</b>
     *       字符是逐个随机旋转的，旋转会撑宽包围盒；即使把画布加宽到步长 29.6px，
     *       连续四个 {@code W} 仍会在像素上粘连成一个块（实测 blocks=2，正确应是 4），
     *       用户根本数不出这是四个字符。去掉这两个字形后最坏间距 ≥ 4px。
     *       代价是字符表从 32 降到 30，答案空间 32^4 → 30^4（约 -23%），
     *       而验证码的真正防线是"来的是不是人"，这个量级的损失可以忽略。</li>
     * </ol>
     */
    private static final String ALPHABET = "23456789ABCDEFGHJKLNPQRSTUVXYZ";
    /**
     * 画布尺寸。宽度必须留够余量：字符逐个随机旋转，<b>旋转会撑宽包围盒</b>。
     * 字符步长 = {@code WIDTH / (长度 + 1)}，步长一旦小于最宽字符，相邻两个字就会贴上。
     * 实测：WIDTH=132（步长 26）最坏只剩 1px 间隙；148（步长 29.6）配合上面的字符表能留 4px 以上。
     * 改这两个值时必须同步改前端的 {@code .captcha-image} 宽度，否则图片会被拉伸或裁掉两侧字符。
     */
    private static final int WIDTH = 148;
    private static final int HEIGHT = 44;
    /** 未认证入口的来源级配额：见 {@link LoginAttemptGuard} 的说明。 */
    private static final int ISSUE_QUOTA_LIMIT = 60;
    private static final Duration ISSUE_QUOTA_WINDOW = Duration.ofMinutes(1);
    private static final String QUOTA_BUCKET = "captcha";

    private final StringRedisTemplate redis;
    private final LoginAttemptGuard attempts;
    private final boolean enabled;
    private final SecureRandom random = new SecureRandom();

    public CaptchaService(StringRedisTemplate redis, LoginAttemptGuard attempts, SecurityProperties security) {
        this.redis = redis;
        this.attempts = attempts;
        this.enabled = security.captchaEnabled();
        if (this.enabled) requireRenderableFont();
        else log.warn("Graphical captcha is disabled (lbl.security.captcha-enabled=false). "
                + "The registration endpoint will accept requests without a captcha.");
    }

    /**
     * 出一个新的验证码。
     *
     * @return 关闭该功能时 {@code enabled=false}，且不会占用配额、不写 Redis；
     *         前端据此隐藏验证码输入框（而不是显示一个永远不会通过的框）
     */
    public Challenge issue() {
        if (!enabled) return new Challenge(false, null, null);
        // 生成一张图不贵，但"每次请求都生成一张图 + 写一个 Redis key"是最容易被脚本放大的动作，
        // 所以这里加一条宽松的来源级配额，避免验证码接口自己变成新的滥用入口。
        attempts.requireSourceQuota(QUOTA_BUCKET, ISSUE_QUOTA_LIMIT, ISSUE_QUOTA_WINDOW);
        String code = randomCode();
        String captchaId = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(24));
        redis.opsForValue().set(key(captchaId), code, TTL);
        return new Challenge(true, captchaId, "data:image/png;base64," + Base64.getEncoder().encodeToString(render(code)));
    }

    /**
     * 校验并<b>立刻消费</b>验证码。
     * <p>
     * 关闭该功能时直接放行：开关的唯一目的就是让本地开发不必每次都看图，
     * 若在这里仍然校验，开关就等于失效了。
     */
    public void verify(String captchaId, String code) {
        if (!enabled) return;
        if (captchaId == null || captchaId.isBlank() || code == null || code.isBlank()) {
            throw new BusinessException("请输入图形验证码");
        }
        String expected = redis.opsForValue().getAndDelete(key(captchaId));
        if (expected == null) {
            // 过期与"已经用过一次"在用户视角是同一件事，合并成一句话即可；
            // 这两种都不涉及账号枚举，说明白比含糊更有用。
            throw new BusinessException("验证码已过期，请点击图片换一张后重试");
        }
        if (!expected.equalsIgnoreCase(code.trim())) {
            throw new BusinessException("验证码不正确，请重新输入");
        }
    }

    private String randomCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int index = 0; index < CODE_LENGTH; index++) {
            builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }

    private byte[] render(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D canvas = image.createGraphics();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            canvas.setColor(new Color(247, 250, 255));
            canvas.fillRect(0, 0, WIDTH, HEIGHT);
            drawNoise(canvas);
            drawCode(canvas, code);
        } finally {
            canvas.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("无法生成验证码图片", ex);
        }
    }

    /** 干扰点 + 干扰线：目标是让"把图片丢给通用 OCR"不再免费，而不是让人也看不清。 */
    private void drawNoise(Graphics2D canvas) {
        for (int index = 0; index < 60; index++) {
            canvas.setColor(shade(180, 235));
            canvas.fillOval(random.nextInt(WIDTH), random.nextInt(HEIGHT), 2, 2);
        }
        canvas.setStroke(new BasicStroke(1.2f));
        for (int index = 0; index < 5; index++) {
            canvas.setColor(shade(150, 215));
            canvas.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT), random.nextInt(WIDTH), random.nextInt(HEIGHT));
        }
    }

    /** 逐字符绘制：每个字符独立随机旋转与上下偏移，字符之间不共基线。 */
    private void drawCode(Graphics2D canvas, String code) {
        canvas.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        FontMetrics metrics = canvas.getFontMetrics();
        int step = WIDTH / (code.length() + 1);
        for (int index = 0; index < code.length(); index++) {
            char character = code.charAt(index);
            String text = String.valueOf(character);
            int centerX = step * (index + 1);
            int baseline = HEIGHT / 2 + 10 + random.nextInt(7) - 3;
            AffineTransform saved = canvas.getTransform();
            try {
                canvas.rotate(Math.toRadians(random.nextInt(41) - 20), centerX, baseline);
                canvas.setColor(shade(20, 115));
                canvas.drawString(text, centerX - metrics.charWidth(character) / 2, baseline);
            } finally {
                canvas.setTransform(saved);
            }
        }
    }

    /** 在 [min, max] 区间内取一个随机灰阶偏色，避免出现纯黑/纯白导致对比度异常。 */
    private Color shade(int min, int max) {
        int range = max - min + 1;
        return new Color(min + random.nextInt(range), min + random.nextInt(range), min + random.nextInt(range));
    }

    private byte[] randomBytes(int length) {
        byte[] value = new byte[length];
        random.nextBytes(value);
        return value;
    }

    private String key(String captchaId) {
        return "auth:captcha:" + captchaId;
    }

    /**
     * 启动时确认"真的能把字画出来"。
     * <p>
     * 为什么需要这一步：{@code java.awt} 依赖运行环境里的系统字体，而精简的 Linux 容器镜像
     * （alpine/slim 之类）默认<b>没有装任何字体</b>。那种环境下生成的会是一张<b>纯色空白图</b>，
     * 用户永远猜不中、注册永远失败，而后端不报任何错 —— 这是最难排查的一类故障。
     * 因此这里主动探测一次：画不出来就拒绝启动，并在异常信息里给出两条明确的出路
     * （装字体，或关掉验证码）。
     * <p>
     * 捕获 {@code Throwable} 是刻意的：{@code java.awt} 在无图形环境下会抛
     * {@code HeadlessException} 甚至 {@code AWTError}（属于 Error 而非 Exception），
     * 只 catch Exception 会漏掉、把"环境不支持"变成启动期的另一种崩溃。
     */
    private static void requireRenderableFont() {
        if (canRenderText()) return;
        throw new IllegalStateException("""
                当前运行环境没有可用于绘图的字体，图形验证码会渲染成空白图、注册功能将完全不可用。
                两种处理方式：
                  1) 安装字体（Debian/Ubuntu: apt-get install -y fontconfig fonts-dejavu-core；
                     Alpine: apk add fontconfig ttf-dejavu），然后重启；
                  2) 确认不需要验证码时，设置 CAPTCHA_ENABLED=false 关闭它（会打印告警）。""");
    }

    private static boolean canRenderText() {
        try {
            BufferedImage probe = new BufferedImage(64, 40, BufferedImage.TYPE_INT_RGB);
            Graphics2D canvas = probe.createGraphics();
            try {
                canvas.setColor(Color.WHITE);
                canvas.fillRect(0, 0, probe.getWidth(), probe.getHeight());
                canvas.setColor(Color.BLACK);
                canvas.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
                canvas.drawString("A", 8, 30);
            } finally {
                canvas.dispose();
            }
            int background = Color.WHITE.getRGB();
            for (int x = 0; x < probe.getWidth(); x++) {
                for (int y = 0; y < probe.getHeight(); y++) {
                    if (probe.getRGB(x, y) != background) return true;
                }
            }
            return false;
        } catch (Throwable ex) {
            return false;
        }
    }

    /**
     * 出题结果。
     *
     * @param enabled   验证码功能是否开启；关闭时前端应隐藏输入框
     * @param captchaId 校验时回传的题目 id
     * @param image     {@code data:image/png;base64,...}，前端直接塞给 img 的 src
     */
    public record Challenge(boolean enabled, String captchaId, String image) {
    }
}
