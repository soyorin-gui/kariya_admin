package org.lbl.system.log.risk.support;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** 多条风险规则共用的主体标识和证据裁剪方法。 */
public final class RiskRuleSupport {
    private RiskRuleSupport() {
    }

    public static String actorKey(Long userId, String username) {
        return userId == null ? "name:" + display(username) : "id:" + userId;
    }

    public static String subjectId(Long userId, String fallback) {
        return userId == null ? display(fallback) : String.valueOf(userId);
    }

    public static String display(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    public static <E> List<Long> evidenceIds(List<E> events, Function<E, Long> id, int limit) {
        return events.stream().map(id).filter(Objects::nonNull).distinct().limit(limit).toList();
    }
}
