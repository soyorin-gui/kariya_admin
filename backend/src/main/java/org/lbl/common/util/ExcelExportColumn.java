package org.lbl.common.util;

import java.util.function.Function;

public record ExcelExportColumn<T>(String title, Function<T, ?> valueExtractor) {
}
