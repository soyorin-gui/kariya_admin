package org.kariya.system.dept;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.kariya.common.exception.BusinessException;
import org.kariya.system.user.UserEntity;
import org.kariya.system.user.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DeptService {
    private final DeptMapper depts;
    private final UserMapper users;

    public DeptService(DeptMapper depts, UserMapper users) {
        this.depts = depts;
        this.users = users;
    }

    public List<DeptVO> list() {
        return depts.selectList(new LambdaQueryWrapper<DeptEntity>().orderByAsc(DeptEntity::getSortOrder).orderByAsc(DeptEntity::getId)).stream().map(this::toView).toList();
    }

    public DeptFormOptions formOptions() {
        List<DeptFormOptions.Option> departmentOptions = depts.selectList(new LambdaQueryWrapper<DeptEntity>().eq(DeptEntity::getStatus, 1).orderByAsc(DeptEntity::getSortOrder))
                .stream().map(value -> new DeptFormOptions.Option(value.getId(), value.getDeptName())).toList();
        List<DeptFormOptions.Option> leaderOptions = users.selectList(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getStatus, 1).orderByAsc(UserEntity::getUsername))
                .stream().map(value -> new DeptFormOptions.Option(value.getId(), value.getRealName() + "（" + value.getUsername() + "）")).toList();
        return new DeptFormOptions(departmentOptions, leaderOptions);
    }

    @Transactional
    public DeptVO create(DeptRequest request) {
        String code = request.deptCode().trim();
        if (depts.selectCount(new LambdaQueryWrapper<DeptEntity>().eq(DeptEntity::getDeptCode, code)) > 0) throw new BusinessException("部门编码已存在");
        DeptEntity dept = new DeptEntity();
        apply(dept, request, null);
        dept.setBuiltin(0);
        depts.insert(dept);
        return toView(dept);
    }

    @Transactional
    public DeptVO update(Long id, DeptRequest request) {
        DeptEntity dept = require(id);
        String code = request.deptCode().trim();
        if (depts.selectCount(new LambdaQueryWrapper<DeptEntity>().eq(DeptEntity::getDeptCode, code).ne(DeptEntity::getId, id)) > 0) throw new BusinessException("部门编码已存在");
        if (dept.getBuiltin() == 1 && (request.status() != 1 || (request.parentId() != null && request.parentId() > 0))) {
            throw new BusinessException("根部门不能移动或禁用");
        }
        String oldPath = dept.getAncestors() + "," + dept.getId();
        apply(dept, request, id);
        depts.updateById(dept);
        String newPath = dept.getAncestors() + "," + dept.getId();
        if (!oldPath.equals(newPath)) {
            depts.selectList(new LambdaQueryWrapper<DeptEntity>().likeRight(DeptEntity::getAncestors, oldPath)).forEach(child -> {
                child.setAncestors(newPath + child.getAncestors().substring(oldPath.length()));
                depts.updateById(child);
            });
        }
        return toView(dept);
    }

    @Transactional
    public void remove(Long id) {
        DeptEntity dept = require(id);
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
            if (currentId != null) {
                String ownPath = dept.getAncestors() + "," + dept.getId();
                if (parent.getId().equals(currentId) || parent.getAncestors().startsWith(ownPath)) throw new BusinessException("不能将部门移动到自身的子部门下");
            }
            ancestors = parent.getAncestors() + "," + parent.getId();
        }
        if (request.leaderUserId() != null && users.selectById(request.leaderUserId()) == null) throw new BusinessException("负责人用户不存在");
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

    private DeptVO toView(DeptEntity dept) {
        UserEntity leader = dept.getLeaderUserId() == null ? null : users.selectById(dept.getLeaderUserId());
        return new DeptVO(dept.getId(), dept.getParentId(), dept.getAncestors(), dept.getDeptName(), dept.getDeptCode(), dept.getLeaderUserId(), leader == null ? "-" : leader.getRealName(), dept.getSortOrder(), dept.getStatus(), dept.getBuiltin(), dept.getCreatedTime());
    }
}
