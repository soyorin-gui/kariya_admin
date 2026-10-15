package org.lbl.intranet.operation.compare.model;

public record ResponseFieldDifference(
        String fieldPath,
        String differenceType,
        String valueA,
        String valueB
) {
}
