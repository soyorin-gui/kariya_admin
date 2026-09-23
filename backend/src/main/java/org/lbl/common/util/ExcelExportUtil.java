package org.lbl.common.util;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ExcelExportUtil {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ExcelExportUtil() {
    }

    /**
     * 以 SXSSF 流式写入 Excel。数据提供方可以分批从数据库取数，不需要把所有记录和单元格留在内存中。
     */
    public static <T> void write(HttpServletResponse response, String filename, String sheetName, List<ExcelExportColumn<T>> columns,
                                 RowProducer<T> producer) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String encodedName = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName);
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(500)) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet(sheetName);
            CellStyle headerStyle = headerStyle(workbook);
            Row header = sheet.createRow(0);
            for (int index = 0; index < columns.size(); index++) {
                Cell cell = header.createCell(index);
                cell.setCellValue(columns.get(index).title());
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(index, 20 * 256);
            }
            int[] rowIndex = {1};
            producer.produce(record -> {
                Row row = sheet.createRow(rowIndex[0]++);
                for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                    Object value = columns.get(columnIndex).valueExtractor().apply(record);
                    row.createCell(columnIndex).setCellValue(safeText(value));
                }
            });
            workbook.write(response.getOutputStream());
        }
    }

    @FunctionalInterface
    public interface RowProducer<T> {
        void produce(RowWriter<T> writer) throws IOException;
    }

    @FunctionalInterface
    public interface RowWriter<T> {
        void write(T record) throws IOException;
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static String safeText(Object value) {
        if (value == null) return "";
        String text = value instanceof LocalDateTime time ? DATE_TIME_FORMATTER.format(time) : String.valueOf(value);
        return text.startsWith("=") || text.startsWith("+") || text.startsWith("-") || text.startsWith("@") ? "'" + text : text;
    }
}
