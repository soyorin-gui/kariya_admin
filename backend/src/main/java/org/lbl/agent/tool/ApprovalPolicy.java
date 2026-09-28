package org.lbl.agent.tool;

/** 工具执行前的人工确认策略。当前聊天协议只自动执行 NOT_REQUIRED 工具。 */
public enum ApprovalPolicy {
    NOT_REQUIRED,
    REQUIRED
}
