package org.lbl.intranet.operation.flow.parser;

import org.lbl.intranet.operation.flow.model.TransactionFlowRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;

@Component
public class TransactionFlowParser {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> RESPONSE_STATUSES = Set.of("Y", "N", "E");

    public Optional<TransactionFlowRecord> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            String[] fields = raw.split(",", -1);
            if (fields.length != 10) return Optional.empty();
            for (int index = 0; index < fields.length; index++) fields[index] = fields[index].trim();
            for (String field : fields) if (field.isEmpty()) return Optional.empty();
            if (!RESPONSE_STATUSES.contains(fields[6])) return Optional.empty();
            BigDecimal elapsed = new BigDecimal(fields[5]);
            if (elapsed.signum() < 0) return Optional.empty();
            return Optional.of(new TransactionFlowRecord(
                    fields[0], fields[1], fields[2],
                    LocalDateTime.parse(fields[3], DATE_TIME),
                    LocalDateTime.parse(fields[4], DATE_TIME),
                    elapsed, fields[6], fields[7], LocalDate.parse(fields[8]), fields[9]));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }
}
