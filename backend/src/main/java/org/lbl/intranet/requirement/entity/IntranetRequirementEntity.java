package org.lbl.intranet.requirement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Data
@TableName("intranet_requirement")
public class IntranetRequirementEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String oaNo;
    private Integer requirementType;
    private String title;
    private String description;
    private Long transactionId;
    private LocalDate expectedOnlineDate;
    private Integer status;
    private Long developerUserId;
    private String developerName;
    @TableField(fill = FieldFill.INSERT) private Long createdBy;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private Long updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedTime;
}
