package org.lbl.system.dept.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.lbl.system.dept.entity.DeptEntity;

/**
 * 部门物化路径（{@code sys_dept.ancestors}）的唯一口径。
 * <p>
 * 为什么必须集中到一处：{@code ancestors} 是一个<b>用字符串前缀表达树关系</b>的字段，
 * 全项目至少有三处在做"判断某条记录是否属于某棵子树"这件事
 * （数据范围展开、移动部门时重写子树路径、移动前的环检测）。
 * 只要其中一处的匹配写法与其它处不一致，就会得到"同一棵子树在不同功能里范围不同"的结果 ——
 * 而这种不一致不会报错，只会静默算错。
 * <p>
 * <b>匹配必须带逗号边界。</b>路径形如 {@code "0,1,2"}，若只用 {@code LIKE '0,1,2%'} 匹配，
 * 部门 20（{@code ancestors = "0,1,20"}）也会被算作部门 2 的后代 ——
 * 因为 {@code "0,1,20"} 的字符串前缀确实是 {@code "0,1,2"}。
 * 加上分隔符变成 {@code LIKE '0,1,2,%'} 之后，只有真正的下级路径才会命中。
 */
public final class DeptPaths {

    private DeptPaths() {
    }

    /**
     * 部门自身的完整路径：祖先路径 + 自身 id。
     * <p>
     * 例：{@code ancestors = "0,1"} 的部门 2 → {@code "0,1,2"}。
     */
    public static String selfPath(DeptEntity dept) {
        return dept.getAncestors() + "," + dept.getId();
    }

    /**
     * 「path 这棵子树下的全部记录」查询条件 —— 含直属子部门与更深层后代，<b>不含路径自身</b>。
     * <p>
     * 两条分支共同覆盖整棵子树：
     * <ul>
     *   <li>{@code ancestors = path}：直属子部门；</li>
     *   <li>{@code ancestors LIKE path + "," + "%"}：孙辈及更深。</li>
     * </ul>
     * 调用方需自行保证 {@code path} 来自 {@link #selfPath(DeptEntity)}，
     * 否则逗号边界不成立、匹配范围会错。
     */
    public static LambdaQueryWrapper<DeptEntity> subtreeQuery(String path) {
        return new LambdaQueryWrapper<DeptEntity>().and(condition ->
                condition.eq(DeptEntity::getAncestors, path)
                        .or().likeRight(DeptEntity::getAncestors, path + ","));
    }

    /**
     * 某条记录的 {@code ancestors} 是否落在 {@code path} 这棵子树内（不含 {@code path} 自身）。
     * <p>
     * 与 {@link #subtreeQuery(String)} 同一口径的内存版，供不查库的判定使用
     * （例如"把部门移动到自己的子孙下"的检查）。
     */
    public static boolean isInsideSubtree(String candidateAncestors, String path) {
        if (candidateAncestors == null || path == null) return false;
        return candidateAncestors.equals(path) || candidateAncestors.startsWith(path + ",");
    }
}
