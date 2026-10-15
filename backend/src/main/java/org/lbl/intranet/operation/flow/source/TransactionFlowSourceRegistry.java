package org.lbl.intranet.operation.flow.source;

import org.lbl.common.exception.BusinessException;
import org.lbl.intranet.operation.flow.model.FlowEnvironment;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TransactionFlowSourceRegistry {
    private final List<TransactionFlowSource> sources;

    public TransactionFlowSourceRegistry(List<TransactionFlowSource> sources) {
        this.sources = List.copyOf(sources);
    }

    public List<FlowEnvironment> environments() {
        Map<String, FlowEnvironment> unique = new LinkedHashMap<>();
        sources.stream().flatMap(source -> source.environments().stream())
                .forEach(environment -> unique.putIfAbsent(environment.id(), environment));
        return unique.values().stream().sorted(Comparator.comparing(FlowEnvironment::name)).toList();
    }

    public TransactionFlowSource require(String environmentId) {
        return sources.stream()
                .filter(source -> source.environments().stream().anyMatch(environment -> environment.id().equals(environmentId)))
                .findFirst()
                .orElseThrow(() -> new BusinessException("运行环境不存在或尚未配置流水数据源"));
    }
}
