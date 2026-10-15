package org.lbl.intranet.operation.flow.service;

import org.lbl.common.exception.BusinessException;
import org.lbl.common.result.PageResult;
import org.lbl.intranet.operation.flow.model.FlowEnvironment;
import org.lbl.intranet.operation.flow.model.TransactionFlowRecord;
import org.lbl.intranet.operation.flow.parser.TransactionFlowParser;
import org.lbl.intranet.operation.flow.request.TransactionFlowQueryRequest;
import org.lbl.intranet.operation.flow.source.TransactionFlowSource;
import org.lbl.intranet.operation.flow.source.TransactionFlowSourceRegistry;
import org.lbl.intranet.operation.flow.vo.FlowFilterOptionsVO;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class TransactionFlowQueryService {
    private static final List<String> RESPONSE_STATUSES = List.of("Y", "N", "E");

    private final TransactionFlowSourceRegistry sources;
    private final TransactionFlowParser parser;

    public TransactionFlowQueryService(TransactionFlowSourceRegistry sources, TransactionFlowParser parser) {
        this.sources = sources;
        this.parser = parser;
    }

    public List<FlowEnvironment> environments() {
        return sources.environments();
    }

    public FlowFilterOptionsVO options(String environmentId) {
        TransactionFlowSource source = sources.require(environmentId);
        return new FlowFilterOptionsVO(RESPONSE_STATUSES, source.responseCodes(environmentId));
    }

    public PageResult<TransactionFlowRecord> query(TransactionFlowQueryRequest request) {
        if (request.startTime() != null && request.endTime() != null && request.startTime().isAfter(request.endTime())) {
            throw new BusinessException("查询开始时间不能晚于结束时间");
        }
        if ((request.startTime() != null && !request.startTime().toLocalDate().equals(request.queryDate()))
                || (request.endTime() != null && !request.endTime().toLocalDate().equals(request.queryDate()))) {
            throw new BusinessException("查询时间必须位于所选查询日期内");
        }
        TransactionFlowSource source = sources.require(request.environmentId());
        List<TransactionFlowRecord> matched = source.read(request.environmentId(), request.queryDate()).stream()
                .map(parser::parse)
                .flatMap(java.util.Optional::stream)
                .filter(record -> matches(record, request))
                .sorted(Comparator.comparing(TransactionFlowRecord::startTime).reversed()
                        .thenComparing(TransactionFlowRecord::globalFlowId, Comparator.reverseOrder()))
                .toList();
        long offset = (request.pageNum() - 1) * request.pageSize();
        List<TransactionFlowRecord> page = matched.stream().skip(offset).limit(request.pageSize()).toList();
        return new PageResult<>(page, matched.size(), request.pageNum(), request.pageSize());
    }

    private boolean matches(TransactionFlowRecord record, TransactionFlowQueryRequest request) {
        return contains(record.callerId(), request.callerId())
                && contains(record.serviceAddress(), request.serviceAddress())
                && equals(record.transactionCode(), request.transactionCode())
                && equals(record.responseStatus(), request.responseStatus())
                && equals(record.responseCode(), request.responseCode())
                && equals(record.globalFlowId(), request.globalFlowId())
                && (request.startTime() == null || !record.startTime().isBefore(request.startTime()))
                && (request.endTime() == null || !record.startTime().isAfter(request.endTime()));
    }

    private boolean contains(String actual, String expected) {
        return expected == null || expected.isBlank() || actual.contains(expected.trim());
    }

    private boolean equals(String actual, String expected) {
        return expected == null || expected.isBlank() || actual.equals(expected.trim());
    }
}
