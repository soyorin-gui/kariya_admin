package org.lbl.system.dept.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.lbl.common.exception.BusinessException;
import org.lbl.security.context.AccessPolicy;
import org.lbl.system.dept.vo.DeptFormOptions;
import org.lbl.system.dept.mapper.DeptMapper;
import org.lbl.system.dept.vo.DeptVO;
import org.lbl.system.dept.entity.DeptEntity;
import org.lbl.system.dept.request.DeptRequest;
import org.lbl.system.user.entity.UserEntity;
import org.lbl.system.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

@Service
public class DeptService {
    private final DeptMapper depts;
    private final UserMapper users;
    private final AccessPolicy access;

    public DeptService(DeptMapper depts, UserMapper users, AccessPolicy access) {
        this.depts = depts;
        this.users = users;
        this.access = access;
    }

    public List<DeptVO> list() {
        AccessPolicy.Actor actor = access.actor();
        List<DeptEntity> all = depts.selectList(new LambdaQueryWrapper<DeptEntity>().orderByAsc(DeptEntity::getSortOrder).orderByAsc(DeptEntity::getId));
        if (actor.all()) return all.stream().map(dept -> toView(dept, actor)).toList();

        // Return management targets plus read-only ancestors. The frontend needs the ancestors
        // to render a valid tree, but only records in actor.departments are actionable.
        Set<Long> contextualIds = new HashSet<>(actor.departments());
        for (DeptEntity dept : all) {
            if (!actor.departments().contains(dept.getId())) continue;
            for (String segment : dept.getAncestors().split(",")) {
                try {
                    contextualIds.add(Long.parseLong(segment));
                } catch (NumberFormatException ignored) {
                    // Ancestors are written by this service. A corrupt legacy path should not
                    // make the entire department list unavailable.
                }
            }
        }
        return all.stream().filter(dept -> contextualIds.contains(dept.getId())).map(dept -> toView(dept, actor)).toList();
    }

    public DeptFormOptions formOptions() {
        AccessPolicy.Actor actor = access.actor();
        List<DeptEntity> accessible = depts.selectList(new LambdaQueryWrapper<DeptEntity>().eq(DeptEntity::getStatus, 1).orderByAsc(DeptEntity::getSortOrder))
                .stream().filter(value -> access.canCreateChildDept(actor, value)).toList();
        LambdaQueryWrapper<UserEntity> usersQuery = new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getStatus, 1).orderByAsc(UserEntity::getUsername);
        access.applyUserScope(usersQuery, actor);
        List<DeptFormOptions.Option> leaderOptions = users.selectList(usersQuery)
                .stream().map(value -> new DeptFormOptions.Option(value.getId(), value.getRealName() + "（" + value.getUsername() + "）")).toList();
        return new DeptFormOptions(
                accessible.stream().map(value -> new DeptFormOptions.Option(value.getId(), value.getDeptName())).toList(),
                leaderOptions,
                actor.all() || !accessible.isEmpty());
    }

    @Transactional
    public DeptVO create(DeptRequest request) {
        AccessPolicy.Actor actor = access.actor();
        String code = request.deptCode().trim();
        if (depts.selectCount(new LambdaQueryWrapper<DeptEntity>().eq(DeptEntity::getDeptCode, code)) > 0) throw new BusinessException("部门编码已存在");
        DeptEntity dept = new DeptEntity();
        apply(dept, request, null);
        access.requireCreateDept(actor, dept.getParentId());
        requireLeaderInScope(actor, dept.getLeaderUserId());
        dept.setBuiltin(0);
        depts.insert(dept);
        return toView(dept, actor);
    }

    @Transactional
    public DeptVO update(Long id, DeptRequest request) {
        AccessPolicy.Actor actor = access.actor();
        DeptEntity dept = require(id);
        access.requireManageDept(actor, dept);
        String code = request.deptCode().trim();
        if (depts.selectCount(new LambdaQueryWrapper<DeptEntity>().eq(DeptEntity::getDeptCode, code).ne(DeptEntity::getId, id)) > 0) throw new BusinessException("部门编码已存在");
        if (dept.getBuiltin() == 1 && (request.status() != 1 || (request.parentId() != null && request.parentId() > 0))) {
            throw new BusinessException("根部门不能移动或禁用");
        }
        String oldPath = dept.getAncestors() + "," + dept.getId();
        Long oldParentId = dept.getParentId();
        apply(dept, request, id);
        access.requireMoveDept(actor, oldParentId, dept.getParentId());
        requireLeaderInScope(actor, dept.getLeaderUserId());
        depts.updateById(dept);
        String newPath = dept.getAncestors() + "," + dept.getId();
        if (!oldPath.equals(newPath)) {
            depts.selectList(new LambdaQueryWrapper<DeptEntity>().likeRight(DeptEntity::getAncestors, oldPath)).forEach(child -> {
                child.setAncestors(newPath + child.getAncestors().substring(oldPath.length()));
                depts.updateById(child);
            });
        }
        return toView(dept, actor);
    }

    @Transactional
    public void remove(Long id) {
        AccessPolicy.Actor actor = access.actor();
        DeptEntity dept = require(id);
        access.requireManageDept(actor, dept);
        if (dept.getBuiltin() == 1) throw new BusinessException("根部门不能删除");
        if (depts.selectCount(new LambdaQueryWrapper<DeptEntity>().eq(DeptEntity::getParentId, id)) > 0) throw new BusinessException("请先删除该部门下的子部门");
        if (users.selectCount(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getDeptId, id)) > 0) throw new BusinessException("该部门下仍有用户，不能删除");
        depts.deleteById(id);
    }

    private void apply(DeptEntity dept, DeptRequest request, Long currentId) {
        Long parentId = request.parentId() == null ? 0L : request.parentId();
        if (currentId != null && currentId.equals(parentId)) throw new BusinessException("上级部门不能选择自身");
        String ancestors = "0";
        if (parentId > 0) {
            DeptEntity parent = require(parentId);
            if (parent.getStatus() != 1) throw new BusinessException("上级部门已停用");
            if (currentId != null) {
                String ownPath = dept.getAncestors() + "," + dept.getId();
                if (parent.getId().equals(currentId) || parent.getAncestors().equals(ownPath) || parent.getAncestors().startsWith(ownPath + ",")) throw new BusinessException("不能将部门移动到自身的子部门下");
            }
            ancestors = parent.getAncestors() + "," + parent.getId();
        }
        if (request.status() != 0 && request.status() != 1) throw new BusinessException("部门状态无效");
        if (request.leaderUserId() != null) {
            UserEntity leader = users.selectById(request.leaderUserId());
            if (leader == null || leader.getStatus() != 1) throw new BusinessException("负责人用户不存在或已停用");
        }
        dept.setParentId(parentId);
        dept.setAncestors(ancestors);
        dept.setDeptName(request.deptName().trim());
        dept.setDeptCode(request.deptCode().trim());
        dept.setLeaderUserId(request.leaderUserId());
        dept.setSortOrder(request.sortOrder());
        dept.setStatus(request.status());
    }

    private DeptEntity require(Long id) {
        DeptEntity dept = depts.selectById(id);
        if (dept == null) throw new BusinessException("部门不存在");
        return dept;
    }

    private void requireLeaderInScope(AccessPolicy.Actor actor, Long leaderUserId) {
        if (leaderUserId == null || actor.all()) return;
        UserEntity leader = users.selectById(leaderUserId);
        if (leader == null || !actor.departments().contains(leader.getDeptId())) {
            throw new BusinessException("负责人不在可管理的数据范围内");
        }
    }

    private DeptVO toView(DeptEntity dept, AccessPolicy.Actor actor) {
        UserEntity leader = dept.getLeaderUserId() == null ? null : users.selectById(dept.getLeaderUserId());
        boolean manageable = access.canManageDept(actor, dept);
        boolean canCreateChildren = dept.getStatus() == 1 && access.canCreateChildDept(actor, dept);
        return new DeptVO(dept.getId(), dept.getParentId(), dept.getAncestors(), dept.getDeptName(), dept.getDeptCode(), dept.getLeaderUserId(), leader == null ? "-" : leader.getRealName(), dept.getSortOrder(), dept.getStatus(), dept.getBuiltin(), dept.getCreatedTime(), manageable, canCreateChildren);
    }
}
