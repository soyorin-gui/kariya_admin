package org.lbl.intranet.dataasset.es.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("intranet_es_field")
public class EsFieldEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long esIndexId;
    private String fieldName;
    private String dataType;
    private Integer searchableFlag;
    private Integer sortableFlag;
    private String description;
    private Integer sortOrder;
    @TableField(fill = FieldFill.INSERT) private Long createdBy;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private Long updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedTime;
}
