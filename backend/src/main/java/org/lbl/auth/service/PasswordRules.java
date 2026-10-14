package org.lbl.auth.service;

import org.lbl.common.exception.BusinessException;

import java.security.SecureRandom;
import java.util.Objects;

/**
 * 全站新密码规则的<b>唯一来源</b>：长度、字符集、复杂度、提示文案都在这里定义。
 *
 * <h2>规则</h2>
 * 长度 {@value #MIN_LENGTH}-{@value #MAX_LENGTH} 位，允许可见 ASCII 字符（不含空格），
 * 且至少包含大写字母、小写字母、数字、特殊字符中的三类。
 *
 * <h2>为什么长度要写成常量再拼进正则</h2>
 * 之前 {@code MIN_LENGTH}/{@code MAX_LENGTH} 是常量，但正则里写的是字面量 {@code {8,32}}，
 * 两者是<b>两套独立的值</b>：改常量不会带动正则，改正则也不会带动常量，
 * 漂移时不会有任何报错，只会表现为"提示说 8 位、实际按 10 位校验"。
 * 现在正则与提示文案都由常量拼出（都是编译期常量，因此仍可用于 {@code @Pattern} 注解），
 * 并且 {@code PasswordRulesTest} 会用边界密码把这条一致性钉住。
 *
 * <h2>适用范围（重要）</h2>
 * 这套规则只用于<b>设置新密码</b>的那些入口：自助注册、外部身份开户、管理员新增用户、
 * 本人改密。它<b>绝不能</b>用于校验登录密码或"原密码"——历史密码可能是旧的
 * 12-72 位、且含符号，套上新规则会让所有老用户立刻登不进去。
 * 这两处至今保留着各自的 {@code @Size(max = 72)}。
 *
 * <h2>前端副本</h2>
 * 前端无法 import Java，因此 {@code frontend/src/utils/passwordPolicy.ts} 里有一份字面量副本，
 * 两边必须完全一致。该文件顶部有对应的提醒注释。
 */
public final class PasswordRules {
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 32;
    public static final String REGEX =
            "^(?=.{" + MIN_LENGTH + "," + MAX_LENGTH + "}$)(?=[\\x21-\\x7E]+$)"
                    + "(?:(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)"
                    + "|(?=.*[a-z])(?=.*[A-Z])(?=.*[^A-Za-z0-9])"
                    + "|(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9])"
                    + "|(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9])).*$";
    public static final String MESSAGE =
            "密码须为 " + MIN_LENGTH + "-" + MAX_LENGTH
                    + " 位，且至少包含大写字母、小写字母、数字、特殊字符中的三类（不含空格）";

    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String ALL = UPPER + LOWER + DIGITS;

    private PasswordRules() {
    }

    /**
     * 两次输入的密码是否一致。
     * <p>
     * 表单里"两次输入一致"通常由前端拦下，这里再判一次是纵深防御：
     * 直接调接口时前端校验形同不存在，而"确认密码"本身不落库、只用来做这个判断，
     * 放在服务端是唯一能保证它真的被检查过的地方。
     */
    public static void requireConfirmed(String password, String confirmation) {
        if (!Objects.equals(password, confirmation)) throw new BusinessException("两次输入的密码不一致");
    }

    /**
     * 生成只含字母数字、且必定同时含大小写字母和数字的随机密码。
     * <p>
     * 前三位分别从大写/小写/数字里取，保证复杂度约束一定成立，
     * 再随机打乱位置（否则生成的密码永远是"大写开头、第二位小写、第三位数字"的固定形状）。
     *
     * @param length 生成长度，必须落在 [3, {@link #MAX_LENGTH}] 内；
     *               至少要 3 才有位置放齐三类字符
     */
    public static String randomCompliantPassword(SecureRandom random, int length) {
        if (length < 3 || length > MAX_LENGTH) throw new IllegalArgumentException("密码长度不合法");
        char[] result = new char[length];
        result[0] = randomChar(random, UPPER);
        result[1] = randomChar(random, LOWER);
        result[2] = randomChar(random, DIGITS);
        for (int i = 3; i < result.length; i++) result[i] = randomChar(random, ALL);
        for (int i = result.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char value = result[i];
            result[i] = result[j];
            result[j] = value;
        }
        return new String(result);
    }

    private static char randomChar(SecureRandom random, String candidates) {
        return candidates.charAt(random.nextInt(candidates.length()));
    }
}
