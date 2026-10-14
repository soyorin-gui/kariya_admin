package org.lbl.intranet.requirement.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("intranet_requirement_transaction_call_detail")
public class TransactionCallRequirementDetailEntity {
    @TableId private Long requirementId;
    private String costBusinessContact; private String businessDepartment; private String oaContactNo;
    private String interfaceDataFields; private String callingSystem; private String callingSystemOwner;
    private LocalDate expectedOnlineDate; private String userQueryBehavior;
    private Integer currentCapabilityMetFlag; private String thirdCategoryDetail; private String fiveCategoryDetail;
    private String architectureDetail; private String remarks;
}
