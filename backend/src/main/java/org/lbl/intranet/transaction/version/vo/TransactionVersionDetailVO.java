package org.lbl.intranet.transaction.version.vo;

import org.lbl.intranet.transaction.version.entity.TransactionVersionEntity;
import org.lbl.intranet.transaction.version.entity.TransactionVersionMessageFieldEntity;

import java.util.List;

public record TransactionVersionDetailVO(TransactionVersionEntity version,
                                         List<TransactionVersionMessageFieldEntity> messageFields,
                                         List<Long> hbaseTableIds,
                                         List<Long> esIndexIds) { }
