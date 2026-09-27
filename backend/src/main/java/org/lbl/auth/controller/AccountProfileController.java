package org.lbl.auth.controller;

import jakarta.validation.Valid;
import org.lbl.common.result.Result;
import org.lbl.system.log.aspect.OperationLog;
import org.lbl.system.user.request.ContactUpdateRequest;
import org.lbl.system.user.service.UserService;
import org.lbl.system.user.vo.ContactProfile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account/profile")
@PreAuthorize("isAuthenticated() and !hasAuthority('onboarding:access')")
public class AccountProfileController {
    private final UserService users;

    public AccountProfileController(UserService users) {
        this.users = users;
    }

    @GetMapping
    public Result<ContactProfile> profile() {
        return Result.ok(users.currentContactProfile());
    }

    @PutMapping
    @OperationLog(module = "个人中心", action = "修改联系方式")
    public Result<ContactProfile> update(@Valid @RequestBody ContactUpdateRequest request) {
        return Result.ok(users.updateOwnContact(request), "联系方式已更新");
    }
}
