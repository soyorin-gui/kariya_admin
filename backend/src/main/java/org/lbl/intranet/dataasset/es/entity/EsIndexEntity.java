package org.lbl.intranet.dataasset.es.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("intranet_es_index")
public class EsIndexEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String indexName;
    private String indexPattern;
    private String partitionRule;
    private Long hbaseTableId;
    private String description;
    @TableField(fill = FieldFill.INSERT) private Long createdBy;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private Long updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedTime;
}
