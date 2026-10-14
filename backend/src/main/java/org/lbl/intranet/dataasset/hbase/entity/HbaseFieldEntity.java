package org.lbl.intranet.dataasset.hbase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("intranet_hbase_field")
public class HbaseFieldEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long hbaseTableId;
    private String columnFamily;
    private String fieldName;
    private String dataType;
    private String description;
    private Integer sortOrder;
    @TableField(fill = FieldFill.INSERT) private Long createdBy;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private Long updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedTime;
}
