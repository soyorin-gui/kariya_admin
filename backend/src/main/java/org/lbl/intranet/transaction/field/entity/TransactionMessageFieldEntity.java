package org.lbl.intranet.transaction.field.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("intranet_transaction_message_field")
public class TransactionMessageFieldEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long transactionId; private String messageSide; private Long parentId; private String nodeType;
    private String fieldNameCn; private String fieldNameEn; private String dataType; private String dataLength;
    private Integer requiredFlag; private String description; private Integer sortOrder;
    @TableField(fill = FieldFill.INSERT) private Long createdBy;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private Long updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedTime;
}
