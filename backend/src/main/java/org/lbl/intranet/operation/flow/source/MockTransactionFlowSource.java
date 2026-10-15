package org.lbl.intranet.operation.flow.source;

import org.lbl.intranet.operation.flow.model.FlowEnvironment;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 外网本地开发数据源。只在 default/local profile 生效，不作为内网 Redis 故障时的兜底。
 */
@Component
@Profile({"default", "local"})
public class MockTransactionFlowSource implements TransactionFlowSource {
    private static final FlowEnvironment ENVIRONMENT = new FlowEnvironment("external-demo", "外网模拟环境");
    private static final List<String> RESPONSE_CODES = List.of("000000000000", "04670ERR0001", "04670ERR0002");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public List<FlowEnvironment> environments() {
        return List.of(ENVIRONMENT);
    }

    @Override
    public List<String> responseCodes(String environmentId) {
        return ENVIRONMENT.id().equals(environmentId) ? RESPONSE_CODES : List.of();
    }

    @Override
    public List<String> read(String environmentId, LocalDate queryDate) {
        if (!ENVIRONMENT.id().equals(environmentId) || queryDate.isAfter(LocalDate.now()) || queryDate.isBefore(LocalDate.now().minusDays(39))) {
            return List.of();
        }
        List<String> records = new ArrayList<>();
        for (int index = 0; index < 36; index++) {
            LocalDateTime start = LocalDateTime.of(queryDate, LocalTime.of(9, 0)).plusMinutes(index * 17L);
            long elapsed = 40L + index * 13L;
            LocalDateTime end = start.plusNanos(elapsed * 1_000_000L);
            String status = switch (index % 3) {
                case 0 -> "Y";
                case 1 -> "N";
                default -> "E";
            };
            String responseCode = RESPONSE_CODES.get(index % RESPONSE_CODES.size());
            records.add(String.join(",",
                    "DEMO-CALLER-" + (index % 4 + 1),
                    "10.10.0." + (index % 6 + 10) + "-DEMO-SERVICE-" + (index % 3 + 1),
                    "DEMO" + String.format("%03d", index % 5 + 1),
                    start.format(DATE_TIME),
                    end.format(DATE_TIME),
                    elapsed + ".0",
                    status,
                    responseCode,
                    queryDate.toString(),
                    queryDate.format(DateTimeFormatter.BASIC_ISO_DATE) + String.format("%012d", index + 1)));
        }
        records.add("这是用于验证非法记录会被忽略的外网模拟数据");
        return records;
    }
}
