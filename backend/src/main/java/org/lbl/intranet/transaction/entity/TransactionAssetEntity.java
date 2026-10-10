package org.lbl.intranet.transaction.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("intranet_transaction")
public class TransactionAssetEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String transactionName; private String transactionCode; private String description; private Integer status;
    private String esfServiceName; private String esfServiceOperationId; private String esfServiceAddress; private String esfServiceOperationName;
    private String label; private String businessContact; private String dataTimeliness; private Integer printFileMode;
    private String sortRule; private String dataValidationScope; private String queryScope; private String fileGenerationScope; private String fileNameRule;
    @TableField(fill = FieldFill.INSERT) private Long createdBy;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private Long updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedTime;
}
