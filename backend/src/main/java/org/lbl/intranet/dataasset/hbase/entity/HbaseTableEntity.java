package org.lbl.intranet.dataasset.hbase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("intranet_hbase_table")
public class HbaseTableEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String marketName;
    private String tableName;
    private String description;
    private String rowkeyRule;
    private String lockKey;
    private String dataVolume;
    private String dataDepth;
    @TableField(fill = FieldFill.INSERT) private Long createdBy;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private Long updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedTime;
}
