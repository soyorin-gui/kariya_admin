package org.lbl.intranet.requirement.vo;

import org.lbl.intranet.requirement.entity.NewTransactionRequirementDetailEntity;
import org.lbl.intranet.requirement.entity.TransactionCallRequirementDetailEntity;
import org.lbl.intranet.requirement.entity.TransactionChangeRequirementDetailEntity;

public record RequirementApprovalDetailVO(
        RequirementListVO requirement,
        NewTransactionRequirementDetailEntity newTransactionDetail,
        TransactionChangeRequirementDetailEntity transactionChangeDetail,
        TransactionCallRequirementDetailEntity transactionCallDetail
) { }
