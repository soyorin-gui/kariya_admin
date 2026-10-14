package org.lbl.intranet.dataasset.relation.vo;

import java.util.List;

public record TransactionDataResourceVO(List<Long> hbaseTableIds, List<Long> esIndexIds) { }
