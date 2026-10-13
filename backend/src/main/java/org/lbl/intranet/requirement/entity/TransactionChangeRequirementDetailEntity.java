package org.lbl.intranet.requirement.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("intranet_requirement_transaction_change_detail")
public class TransactionChangeRequirementDetailEntity {
    @TableId private Long requirementId;
    private String esfChange; private String dataLogicChange; private String transactionLogicChange; private String remarks;
}
