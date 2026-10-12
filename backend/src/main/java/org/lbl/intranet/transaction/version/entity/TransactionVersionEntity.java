package org.lbl.intranet.transaction.version.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("intranet_transaction_version")
public class TransactionVersionEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long transactionId; private Long requirementId; private Long approvalInstanceId;
    private Integer versionNo; private Integer activeFlag;
    private String transactionName; private String transactionCode; private String description; private Integer transactionStatus;
    private String esfServiceName; private String esfServiceOperationId; private String esfServiceAddress; private String esfServiceOperationName;
    private String label; private String businessContact; private String dataTimeliness; private Integer printFileMode;
    private String sortRule; private String dataValidationScope; private String queryScope; private String fileGenerationScope; private String fileNameRule;
    private Long activatedBy; private LocalDateTime activatedTime; private LocalDateTime createdTime;
}
