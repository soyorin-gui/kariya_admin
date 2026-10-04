package org.lbl.system.user.service;

import org.lbl.auth.session.SessionService;
import org.lbl.auth.session.SessionView;
import org.lbl.common.exception.BusinessException;
import org.lbl.security.context.AccessPolicy;
import org.lbl.system.user.entity.UserEntity;
import org.lbl.system.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 管理员查看和强制结束用户会话的独立用例。 */
@Service
public class UserSessionAdministrationService {
    private final UserMapper users;
    private final SessionService sessions;
    private final AccessPolicy access;

    public UserSessionAdministrationService(UserMapper users, SessionService sessions, AccessPolicy access) {
        this.users = users;
        this.sessions = sessions;
        this.access = access;
    }

    public List<SessionView> list(Long userId, String currentSid) {
        UserEntity target = require(userId);
        access.requireManageUser(access.actor("system:user:update"), target);
        return sessions.listMemberSessions(userId, currentSid);
    }

    public void remove(Long userId, String reference) {
        UserEntity target = require(userId);
        access.requireManageUser(access.actor("system:user:update"), target);
        if (!sessions.removeByReference(userId, reference)) throw new BusinessException("登录会话不存在或已失效");
    }

    public void removeAll(Long userId) {
        UserEntity target = require(userId);
        access.requireManageUser(access.actor("system:user:update"), target);
        sessions.removeAll(userId);
    }

    private UserEntity require(Long id) {
        UserEntity user = users.selectById(id);
        if (user == null) throw new BusinessException("用户不存在");
        return user;
    }
}
