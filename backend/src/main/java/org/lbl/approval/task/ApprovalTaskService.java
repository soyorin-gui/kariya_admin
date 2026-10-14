package org.lbl.approval.task;

import org.lbl.common.exception.BusinessException;
import org.lbl.common.result.PageResult;
import org.lbl.security.context.AccessPolicy;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ApprovalTaskService {
    private final ApprovalTaskMapper mapper;
    private final AccessPolicy access;

    public ApprovalTaskService(ApprovalTaskMapper mapper, AccessPolicy access) {
        this.mapper = mapper;
        this.access = access;
    }

    public PageResult<ApprovalTaskView> page(String scope, long pageNum, long pageSize) {
        AccessPolicy.Actor actor = access.actor();
        long page = Math.max(1, pageNum);
        long size = Math.min(100, Math.max(1, pageSize));
        long offset = (page - 1) * size;
        List<ApprovalTaskView> records;
        long total;
        switch (scope == null ? "pending" : scope) {
            case "pending" -> {
                records = mapper.pending(actor.user().getId(), actor.superAdmin(), offset, size);
                total = mapper.countPending(actor.user().getId(), actor.superAdmin());
            }
            case "processed" -> {
                records = mapper.processed(actor.user().getId(), offset, size);
                total = mapper.countProcessed(actor.user().getId());
            }
            case "mine" -> {
                records = mapper.mine(actor.user().getId(), offset, size);
                total = mapper.countMine(actor.user().getId());
            }
            default -> throw new BusinessException("不支持的审批列表类型");
        }
        return new PageResult<>(records, total, page, size);
    }

    public long pendingCount() {
        AccessPolicy.Actor actor = access.actor();
        return mapper.countPending(actor.user().getId(), actor.superAdmin());
    }
}
