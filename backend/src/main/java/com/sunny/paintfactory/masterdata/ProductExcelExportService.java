package com.sunny.paintfactory.masterdata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProductExcelExportService {
    private static final String[] HEADERS = {
        "SKU", "Product", "Total Stock", "Unit", "Reorder Level", "Shortage", "Last Purchase Price", "Wholesale Price"
    };
    private final JdbcTemplate jdbc;

    public ProductExcelExportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public byte[] export() {
        List<ExportRow> rows = jdbc.query("""
            SELECT COALESCE(parent.category_code,category.category_code),
                   COALESCE(parent.category_name,category.category_name),
                   CASE WHEN parent.id IS NULL THEN 'UNCATEGORIZED' ELSE category.category_code END,
                   CASE WHEN parent.id IS NULL THEN 'Uncategorized' ELSE category.category_name END,
                   sku.sku_code,sku.product_name,sku.specification,sku.color,
                   sku.total_stock,sku.sales_unit,sku.stock_lower_limit,
                   GREATEST(sku.stock_lower_limit-sku.total_stock,0),
                   sku.last_purchase_price,sku.wholesale_price
            FROM product_sku sku
            JOIN product_category category ON category.id=sku.category_id AND category.enabled=1
            LEFT JOIN product_category parent ON parent.id=category.parent_id AND parent.enabled=1
            WHERE sku.enabled=1 AND sku.saleable=1
            ORDER BY COALESCE(parent.sort_order,category.sort_order),
                     COALESCE(parent.category_code,category.category_code),
                     CASE WHEN parent.id IS NULL THEN 0 ELSE category.sort_order END,
                     CASE WHEN parent.id IS NULL THEN '' ELSE category.category_code END,
                     sku.sku_code
            """, (rs, n) -> new ExportRow(
                rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                rs.getBigDecimal(9), rs.getString(10), rs.getBigDecimal(11),
                rs.getBigDecimal(12), rs.getBigDecimal(13), rs.getBigDecimal(14)));
        return buildWorkbook(rows, LocalDateTime.now());
    }

    static byte[] buildWorkbook(List<ExportRow> rows, LocalDateTime generatedAt) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            Map<String, List<ExportRow>> byManufacturer = new LinkedHashMap<>();
            for (ExportRow row : rows) {
                byManufacturer.computeIfAbsent(row.manufacturerCode() + " " + row.manufacturerName(), key -> new ArrayList<>()).add(row);
            }
            Sheet toc = workbook.createSheet("Contents");
            createToc(toc, byManufacturer, generatedAt, styles, workbook);
            Set<String> usedNames = new HashSet<>();
            usedNames.add("Contents");
            for (Map.Entry<String, List<ExportRow>> entry : byManufacturer.entrySet()) {
                String sheetName = uniqueSheetName(entry.getKey(), usedNames);
                Sheet sheet = workbook.createSheet(sheetName);
                createManufacturerSheet(sheet, entry.getKey(), entry.getValue(), styles);
            }
            workbook.setActiveSheet(0);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate the product Excel workbook", exception);
        }
    }

    private static void createToc(Sheet sheet, Map<String, List<ExportRow>> groups,
                                  LocalDateTime generatedAt, Styles styles, XSSFWorkbook workbook) {
        sheet.setColumnWidth(0, 14 * 256);
        sheet.setColumnWidth(1, 28 * 256);
        sheet.setColumnWidth(2, 14 * 256);
        sheet.setColumnWidth(3, 14 * 256);
        Row title = sheet.createRow(0);
        title.setHeightInPoints(28);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("Product Catalog by Category");
        titleCell.setCellStyle(styles.title);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 3));
        Row meta = sheet.createRow(1);
        meta.createCell(0).setCellValue("Generated At");
        meta.createCell(1).setCellValue(generatedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        meta.createCell(2).setCellValue("Product Count");
        meta.createCell(3).setCellValue(groups.values().stream().mapToInt(List::size).sum());
        Row header = sheet.createRow(3);
        String[] tocHeaders = {"Category Code", "Manufacturer / Category", "Subcategories", "Products"};
        for (int i = 0; i < tocHeaders.length; i++) cell(header, i, tocHeaders[i], styles.header);
        int rowIndex = 4;
        Set<String> usedNames = new HashSet<>();
        usedNames.add("Contents");
        for (Map.Entry<String, List<ExportRow>> entry : groups.entrySet()) {
            String sheetName = uniqueSheetName(entry.getKey(), usedNames);
            ExportRow first = entry.getValue().get(0);
            Row row = sheet.createRow(rowIndex++);
            cell(row, 0, first.manufacturerCode(), styles.body);
            Cell link = cell(row, 1, first.manufacturerName(), styles.link);
            var hyperlink = workbook.getCreationHelper().createHyperlink(org.apache.poi.common.usermodel.HyperlinkType.DOCUMENT);
            hyperlink.setAddress("'" + sheetName.replace("'", "''") + "'!A1");
            link.setHyperlink(hyperlink);
            cell(row, 2, entry.getValue().stream().map(ExportRow::subcategoryCode).distinct().count(), styles.integer);
            cell(row, 3, entry.getValue().size(), styles.integer);
        }
        sheet.createFreezePane(0, 4);
        configurePrint(sheet);
    }

    private static void createManufacturerSheet(Sheet sheet, String manufacturer,
                                                List<ExportRow> rows, Styles styles) {
        int[] widths = {14, 42, 14, 10, 14, 14, 14, 14};
        for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
        int rowIndex = 0;
        String currentSubcategory = null;
        for (ExportRow item : rows) {
            String subcategory = item.subcategoryCode() + " " + item.subcategoryName();
            if (!subcategory.equals(currentSubcategory)) {
                if (currentSubcategory != null) sheet.setRowBreak(rowIndex - 1);
                Row title = sheet.createRow(rowIndex++);
                title.setHeightInPoints(27);
                Cell titleCell = title.createCell(0);
                titleCell.setCellValue(manufacturer + " / " + subcategory);
                titleCell.setCellStyle(styles.sectionTitle);
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(title.getRowNum(), title.getRowNum(), 0, HEADERS.length - 1));
                Row header = sheet.createRow(rowIndex++);
                for (int i = 0; i < HEADERS.length; i++) cell(header, i, HEADERS[i], styles.header);
                currentSubcategory = subcategory;
            }
            Row row = sheet.createRow(rowIndex++);
            cell(row, 0, item.skuCode(), styles.body);
            cell(row, 1, item.displayName(), styles.body);
            cell(row, 2, item.totalStock(), styles.decimal);
            cell(row, 3, item.salesUnit(), styles.center);
            cell(row, 4, item.stockLowerLimit(), styles.decimal);
            cell(row, 5, item.shortageQuantity(), styles.decimal);
            cell(row, 6, item.lastPurchasePrice(), styles.money);
            cell(row, 7, item.wholesalePrice(), styles.money);
        }
        sheet.setAutobreaks(true);
        configurePrint(sheet);
        sheet.getPrintSetup().setLandscape(true);
    }

    private static void configurePrint(Sheet sheet) {
        sheet.setFitToPage(true);
        PrintSetup print = sheet.getPrintSetup();
        print.setFitWidth((short) 1);
        print.setFitHeight((short) 0);
        sheet.setMargin(Sheet.LeftMargin, 0.25);
        sheet.setMargin(Sheet.RightMargin, 0.25);
        sheet.setMargin(Sheet.TopMargin, 0.5);
        sheet.setMargin(Sheet.BottomMargin, 0.5);
    }

    static String uniqueSheetName(String raw, Set<String> used) {
        String base = raw.replaceAll("[\\\\/?*\\[\\]:]", "_").trim();
        if (base.isEmpty()) base = "Unnamed Category";
        if (base.length() > 31) base = base.substring(0, 31);
        String candidate = base;
        int suffix = 2;
        while (used.contains(candidate)) {
            String ending = " (" + suffix++ + ")";
            candidate = base.substring(0, Math.min(base.length(), 31 - ending.length())) + ending;
        }
        used.add(candidate);
        return candidate;
    }

    private static Cell cell(Row row, int column, Object value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value instanceof Number number) cell.setCellValue(number.doubleValue());
        else if (value != null) cell.setCellValue(value.toString());
        cell.setCellStyle(style);
        return cell;
    }

    record ExportRow(String manufacturerCode, String manufacturerName,
                     String subcategoryCode, String subcategoryName,
                     String skuCode, String productName, String specification, String color,
                     BigDecimal totalStock, String salesUnit, BigDecimal stockLowerLimit,
                     BigDecimal shortageQuantity, BigDecimal lastPurchasePrice, BigDecimal wholesalePrice) {
        String displayName() {
            return String.join(" ", java.util.stream.Stream.of(productName, specification, color)
                .filter(value -> value != null && !value.isBlank()).toList());
        }
    }

    private static final class Styles {
        final CellStyle title;
        final CellStyle sectionTitle;
        final CellStyle header;
        final CellStyle body;
        final CellStyle center;
        final CellStyle decimal;
        final CellStyle money;
        final CellStyle integer;
        final CellStyle link;

        Styles(XSSFWorkbook workbook) {
            title = style(workbook, true, 16, IndexedColors.WHITE, IndexedColors.DARK_BLUE, HorizontalAlignment.CENTER);
            sectionTitle = style(workbook, true, 14, IndexedColors.WHITE, IndexedColors.DARK_TEAL, HorizontalAlignment.LEFT);
            header = style(workbook, true, 11, IndexedColors.WHITE, IndexedColors.BLUE_GREY, HorizontalAlignment.CENTER);
            body = style(workbook, false, 10, IndexedColors.BLACK, null, HorizontalAlignment.LEFT);
            center = style(workbook, false, 10, IndexedColors.BLACK, null, HorizontalAlignment.CENTER);
            decimal = numeric(style(workbook, false, 10, IndexedColors.BLACK, null, HorizontalAlignment.RIGHT), "0.####", workbook);
            money = numeric(style(workbook, false, 10, IndexedColors.BLACK, null, HorizontalAlignment.RIGHT), "0.00", workbook);
            integer = numeric(style(workbook, false, 10, IndexedColors.BLACK, null, HorizontalAlignment.RIGHT), "0", workbook);
            link = style(workbook, false, 10, IndexedColors.BLUE, null, HorizontalAlignment.LEFT);
            link.getFontIndex();
        }

        private static CellStyle numeric(CellStyle style, String format, XSSFWorkbook workbook) {
            style.setDataFormat(workbook.createDataFormat().getFormat(format));
            return style;
        }

        private static CellStyle style(XSSFWorkbook workbook, boolean bold, int size,
                                       IndexedColors fontColor, IndexedColors fill,
                                       HorizontalAlignment alignment) {
            Font font = workbook.createFont();
            font.setBold(bold);
            font.setFontHeightInPoints((short) size);
            font.setColor(fontColor.getIndex());
            CellStyle style = workbook.createCellStyle();
            style.setFont(font);
            style.setAlignment(alignment);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setWrapText(true);
            if (fill != null) {
                style.setFillForegroundColor(fill.getIndex());
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            return style;
        }
    }
}
