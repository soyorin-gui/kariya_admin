package org.lbl.intranet.lineage.entity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
@Data @TableName("intranet_transaction_field_source_config")
public class TransactionFieldSourceConfigEntity { @TableId private Long targetFieldId; private String sourceType; private String sourceDetail; }
