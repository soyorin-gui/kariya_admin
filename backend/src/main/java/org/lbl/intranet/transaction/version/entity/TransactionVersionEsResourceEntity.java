package org.lbl.intranet.transaction.version.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("intranet_transaction_version_es_resource")
public class TransactionVersionEsResourceEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long versionId; private Long esIndexId;
}
