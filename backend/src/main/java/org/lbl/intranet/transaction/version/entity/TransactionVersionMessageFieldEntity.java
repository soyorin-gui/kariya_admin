package org.lbl.intranet.transaction.version.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("intranet_transaction_version_message_field")
public class TransactionVersionMessageFieldEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long versionId; private Long sourceFieldId; private String messageSide; private Long parentSourceFieldId;
    private String nodeType; private String fieldNameCn; private String fieldNameEn; private String dataType; private String dataLength;
    private Integer requiredFlag; private String description; private Integer sortOrder;
}
