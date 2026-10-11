package org.lbl.intranet.dataasset.relation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("intranet_transaction_hbase_resource")
public class TransactionHbaseResourceEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long transactionId;
    private Long hbaseTableId;
    @TableField(fill = FieldFill.INSERT) private Long createdBy;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private Long updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedTime;
}
