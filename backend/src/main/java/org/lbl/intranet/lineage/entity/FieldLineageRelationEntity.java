package org.lbl.intranet.lineage.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
@Data @TableName("intranet_field_lineage_relation")
public class FieldLineageRelationEntity { @TableId(type = IdType.AUTO) private Long id; private String sourceType; private Long sourceFieldId; private Long targetFieldId; private String relationType; private String expression; private String remark; }
