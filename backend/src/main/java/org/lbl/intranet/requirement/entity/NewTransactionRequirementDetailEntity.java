package org.lbl.intranet.requirement.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("intranet_requirement_new_transaction_detail")
public class NewTransactionRequirementDetailEntity {
    @TableId private Long requirementId;
    private String costBusinessContact; private String businessDepartment; private String oaContactNo;
    private String interfaceDataFields; private String dataSourceSystem; private String callingSystem; private String callingSystemOwner;
    private String userQueryBehavior; private Integer dataProjectedFlag; private String thirdCategoryDetail; private String fiveCategoryDetail; private String architectureDetail;
    private String serviceWindow; private String responseTimeRequirement; private Integer concurrentUserCount; private String tpsPeak; private String tpsEstimationBasis;
    private String historyQueryRange; private String singleQueryRange; private Integer pageSize; private String dailyTransactionVolume;
    private Integer generateFileFlag; private String fileGenerationScope; private Integer calculationFlag; private String calculationScenario; private String remarks;
}
