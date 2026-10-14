package org.lbl.intranet.requirement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("intranet_requirement_flow")
public class RequirementFlowEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long requirementId; private String eventType; private String eventName; private String remark;
    private Long operatorId; private String operatorName; private LocalDateTime createdTime;
}
