/**
 * 用户主动提交、由一个或多个审批节点处理、最终对业务对象产生变更的审批领域。
 *
 * <p>各申请类型是此领域的子模块，而不是新的顶层业务包。例如当前的部门调动位于
 * {@code approval.departmenttransfer}；后续角色提权应位于
 * {@code approval.roleelevation}。它们共享审批生命周期，但保留各自的输入校验、
 * 审批人计算和最终业务写入。</p>
 */
package org.lbl.approval;
