package org.lbl.auth.controller;

import org.lbl.auth.service.CaptchaService;
import org.lbl.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图形验证码出题接口。
 * <p>
 * 放在 {@code /api/auth/captcha} 下，因此随 {@code /api/auth/**} 一起对外放行 —— 这是对的：
 * 拿题目本来就不需要登录，真正的门在提交时（{@code RegistrationService} 校验答案）。
 * <p>
 * 图片走 JSON 里的 base64 而不是单独的图片地址，有两个原因：
 * <ul>
 *   <li>少一次请求、少一个匿名接口，滥用控制只需要管这一个入口；</li>
 *   <li>前端不必处理"图片请求失败"的第三种状态，也不需要为了显示验证码去绕过 axios 实例。</li>
 * </ul>
 * 代价是响应体从 ~3KB 涨到 ~4KB，可以忽略。
 */
@RestController
@RequestMapping("/api/auth/captcha")
public class CaptchaController {
    private final CaptchaService captcha;

    public CaptchaController(CaptchaService captcha) {
        this.captcha = captcha;
    }

    @GetMapping
    public Result<CaptchaService.Challenge> issue() {
        return Result.ok(captcha.issue());
    }
}
