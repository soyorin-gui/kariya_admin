package org.lbl.system.dept.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.lbl.common.exception.BusinessException;
import org.lbl.security.context.AccessPolicy;
import org.lbl.system.dept.vo.DeptFormOptions;
import org.lbl.system.dept.mapper.DeptMapper;
import org.lbl.system.role.mapper.RoleDeptMapper;
import org.lbl.system.dept.vo.DeptVO;
import org.lbl.system.dept.entity.DeptEntity;
import org.lbl.system.dept.request.DeptRequest;
import org.lbl.system.dept.support.DeptPaths;
import org.lbl.system.user.entity.UserEntity;
import org.lbl.system.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
public class DeptService {
    private final DeptMapper depts;
    private final UserMapper users;
    private final AccessPolicy access;
    private final RoleDeptMapper roleDepts;

    public DeptService(DeptMapper depts, UserMapper users, AccessPolicy access, RoleDeptMapper roleDepts) {
        this.depts = depts;
        this.users = users;
        this.access = access;
        this.roleDepts = roleDepts;
    }

    public List<DeptVO> list() {
        AccessPolicy.Actor actor = access.actor("system:dept:list");
        List<DeptEntity> all = depts.selectList(new LambdaQueryWrapper<DeptEntity>().orderByAsc(DeptEntity::getSortOrder).orderByAsc(DeptEntity::getId));
        List<DeptEntity> visible = actor.all() ? all : withAncestorsForContext(all, actor);
        // 负责人姓名一次性批量取回：以前是每条部门各查一次用户表（N+1），部门上百时
        // 单次列表请求的 SQL 数量会线性增长。
        Map<Long, String> leaderNames = leaderNames(visible);
        return visible.stream().map(dept -> toView(dept, actor, leaderNames)).toList();
    }

    public DeptVO detail(Long id) {
        AccessPolicy.Actor actor = access.actor("system:dept:update");
        DeptEntity dept = require(id);
        access.requireManageDept(actor, dept);
        return toView(dept, actor);
    }

    /**
     * 非 ALL 范围：只保留自己可管理的部门，外加它们的祖先。
     * <p>
     * 祖先只为让前端渲染出一棵结构完整的树，它们自身不在 {@code actor.departments()} 里，
     * 因此 {@code manageable=false} —— 只读上下文，不可操作。
     */
    private List<DeptEntity> withAncestorsForContext(List<DeptEntity> all, AccessPolicy.Actor actor) {
        Set<Long> contextualIds = new HashSet<>(actor.departments());
        for (DeptEntity dept : all) {
            if (!actor.departments().contains(dept.getId())) continue;
            for (String segment : dept.getAncestors().split(",")) {
                try {
                    contextualIds.add(Long.parseLong(segment));
                } catch (NumberFormatException ignored) {
                    // 路径由本服务写入。历史脏数据不该让整个部门列表不可用。
                }
            }
        }
        return all.stream().filter(dept -> contextualIds.contains(dept.getId())).toList();
    }

    /** 批量取负责人姓名，返回 id → 姓名；负责人已被删除或不存在时 Map 里没有该 key。 */
    private Map<Long, String> leaderNames(List<DeptEntity> depts) {
        Set<Long> leaderIds = depts.stream().map(DeptEntity::getLeaderUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (leaderIds.isEmpty()) return Map.of();
        return users.selectByIds(leaderIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getRealName, (first, second) -> first));
    }

    /**
     * 新增/编辑部门表单的候选数据。
     * <p>
     * 只返回负责人候选与 {@code canCreateRoot} 两项：
     * <ul>
     *   <li>上级部门候选用的是列表接口的完整部门集合（前端需要 {@code parentId} 才能渲染层级树，
     *       平铺的候选列表给不出层级），所以这里不再重复返回一份部门列表；</li>
     *   <li>{@code canCreateRoot} 必须由后端给出：它决定"上级部门"能不能被清空（清空 = 建顶级部门），
     *       而这条规则的唯一依据是 {@code AccessPolicy.canCreateRootDept}。
     *       前端自己拿"能不能管这个部门/有没有可建的父部门"去推是推不出来的 ——
     *       一个数据范围是"本部门及下级"、本人又恰好在根部门的账号，
     *       这两项都是 true，但后端会一律拒绝 parentId≤0，表现为一个与表单无关的 403。</li>
     * </ul>
     */
    public DeptFormOptions formOptions(String operation) {
        if (!Set.of("add", "update").contains(operation)) throw new BusinessException("表单操作无效");
        AccessPolicy.Actor actor = access.actor("system:dept:" + operation);
        LambdaQueryWrapper<UserEntity> usersQuery = new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getStatus, 1).orderByAsc(UserEntity::getUsername);
        access.applyUserScope(usersQuery, actor);
        List<DeptFormOptions.Option> leaderOptions = users.selectList(usersQuery)
                .stream().map(value -> new DeptFormOptions.Option(value.getId(), value.getRealName() + "（" + value.getUsername() + "）")).toList();
        List<Long> parentDeptIds = actor.context().departments().values().stream()
                .filter(dept -> dept.getStatus() == 1 && access.canCreateChildDept(actor, dept))
                .map(DeptEntity::getId).sorted().toList();
        return new DeptFormOptions(leaderOptions, access.canCreateRootDept(actor), parentDeptIds);
    }

    @Transactional
    public DeptVO create(DeptRequest request) {
        AccessPolicy.Actor actor = access.actor("system:dept:add");
        String code = request.deptCode().trim();
        // 口径与 sys_dept.dept_code 的唯一索引一致（含已逻辑删除的记录）：
        // 删除只置 deleted=1，编码不会被释放，所以"删掉再建同编码"必须在这里就被挡住，
        // 而不是让 INSERT 撞唯一键、抛一个与用户输入无关的失败。
        if (depts.countIncludingDeletedByDeptCode(code, 0L) > 0) throw new BusinessException("部门编码已被占用（已删除部门占用的编码不会被释放，请换一个）");
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
        AccessPolicy.Actor actor = access.actor("system:dept:update");
        DeptEntity dept = require(id);
        access.requireManageDept(actor, dept);
        String code = request.deptCode().trim();
        if (depts.countIncludingDeletedByDeptCode(code, id) > 0) throw new BusinessException("部门编码已被占用（已删除部门占用的编码不会被释放，请换一个）");
        if (dept.getBuiltin() == 1 && (request.status() != 1 || (request.parentId() != null && request.parentId() > 0))) {
            throw new BusinessException("根部门不能移动或禁用");
        }
        String oldPath = DeptPaths.selfPath(dept);
        Long oldParentId = dept.getParentId();
        apply(dept, request, id);
        access.requireMoveDept(actor, oldParentId, dept.getParentId());
        if (!oldParentId.equals(dept.getParentId()) && !actor.all()) {
            boolean outside = depts.selectList(DeptPaths.subtreeQuery(oldPath)).stream()
                    .anyMatch(child -> !actor.departments().contains(child.getId()));
            if (outside) throw new BusinessException("不能移动包含数据范围之外部门的子树");
        }
        requireLeaderInScope(actor, dept.getLeaderUserId());
        depts.updateById(dept);
        String newPath = DeptPaths.selfPath(dept);
        if (!oldPath.equals(newPath)) {
            // 子树范围必须走 DeptPaths 的统一口径：带逗号边界，只会命中真正的后代。
            // 先前这里用的是无边界的前缀匹配（LIKE 'oldPath%'），会把兄弟部门的子孙
            // 也当成自己的后代一起改写路径，详见 DeptPaths 的类注释。
            // 匹配到的行其 ancestors 必然以 oldPath 开头，因此下面的 substring 拼接是安全的。
            depts.selectList(DeptPaths.subtreeQuery(oldPath)).forEach(child -> {
                child.setAncestors(newPath + child.getAncestors().substring(oldPath.length()));
                depts.updateById(child);
            });
        }
        return toView(dept, actor);
    }

    @Transactional
    public void remove(Long id) {
        AccessPolicy.Actor actor = access.actor("system:dept:delete");
        DeptEntity dept = require(id);
        access.requireManageDept(actor, dept);
        if (dept.getBuiltin() == 1) throw new BusinessException("根部门不能删除");
        if (depts.selectCount(new LambdaQueryWrapper<DeptEntity>().eq(DeptEntity::getParentId, id)) > 0) throw new BusinessException("请先删除该部门下的子部门");
        if (users.selectCount(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getDeptId, id)) > 0) throw new BusinessException("该部门下仍有用户，不能删除");
        if (roleDepts.countByDeptId(id) > 0) throw new BusinessException("该部门已被角色指定为数据范围，请先调整角色配置");
        dept.setDeletedTime(LocalDateTime.now());
        depts.updateById(dept);
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
                String ownPath = DeptPaths.selfPath(dept);
                if (parent.getId().equals(currentId) || DeptPaths.isInsideSubtree(parent.getAncestors(), ownPath)) {
                    throw new BusinessException("不能将部门移动到自身的子部门下");
                }
            }
            ancestors = DeptPaths.selfPath(parent);
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
        return toView(dept, actor, leaderNames(List.of(dept)));
    }

    private DeptVO toView(DeptEntity dept, AccessPolicy.Actor actor, Map<Long, String> leaderNames) {
        String leaderName = dept.getLeaderUserId() == null ? null : leaderNames.get(dept.getLeaderUserId());
        boolean manageable = access.canManageDept(access.forPermissions(actor, "system:dept:update"), dept);
        boolean deletable = access.canManageDept(access.forPermissions(actor, "system:dept:delete"), dept);
        boolean canCreateChildren = dept.getStatus() == 1 && access.canCreateChildDept(access.forPermissions(actor, "system:dept:add"), dept);
        return new DeptVO(dept.getId(), dept.getParentId(), dept.getAncestors(), dept.getDeptName(), dept.getDeptCode(), dept.getLeaderUserId(), leaderName == null ? "-" : leaderName, dept.getSortOrder(), dept.getStatus(), dept.getBuiltin(), dept.getCreatedTime(), manageable, canCreateChildren, deletable);
    }
}
