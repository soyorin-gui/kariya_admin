package org.lbl.agent.tool;

/** 工具的副作用等级，用于审批、审计和模型暴露策略。 */
public enum ToolRisk {
    /** 只读查询，不改变外部状态。 */
    READ_ONLY,
    /** 创建草稿或临时制品，可撤销且不直接生效。 */
    REVERSIBLE_WRITE,
    /** 会修改正式数据、触发任务或通知其他人。 */
    EFFECTFUL_WRITE
}
