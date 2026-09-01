package com.sunny.paintfactory.masterdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ProductExcelExportServiceTest {
    @Test
    void createsOneSheetPerManufacturerAndPageBreakPerSubcategory() throws Exception {
        var rows = List.of(
            row("001", "Twin Guns", "A", "Paint", "A1"),
            row("001", "Twin Guns", "B", "Supplies", "B1"),
            row("002", "Hanbang", "C", "Coating", "C1"));

        byte[] bytes = ProductExcelExportService.buildWorkbook(rows, LocalDateTime.of(2026, 8, 14, 12, 0));

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            assertThat(workbook.getSheetName(0)).isEqualTo("Contents");
            assertThat(workbook.getSheet("001 Twin Guns").getRowBreaks()).hasSize(1);
            assertThat(workbook.getSheet("002 Hanbang").getRowBreaks()).isEmpty();
            assertThat(workbook.getSheet("001 Twin Guns").getPrintSetup().getFitWidth()).isEqualTo((short) 1);
        }
    }

    @Test
    void sanitizesAndDeduplicatesExcelSheetNames() {
        var used = new HashSet<String>();
        String first = ProductExcelExportService.uniqueSheetName("Very Long Manufacturer/Category Name 123456789", used);
        String second = ProductExcelExportService.uniqueSheetName("Very Long Manufacturer/Category Name 123456789", used);
        assertThat(first).doesNotContain("/").hasSizeLessThanOrEqualTo(31);
        assertThat(second).isNotEqualTo(first).hasSizeLessThanOrEqualTo(31);
    }

    private static ProductExcelExportService.ExportRow row(String parentCode, String parentName,
                                                            String childCode, String childName, String sku) {
        return new ProductExcelExportService.ExportRow(parentCode, parentName, childCode, childName,
            sku, "Test Product", "20kg", null, BigDecimal.TEN, "Pail", BigDecimal.ONE,
            BigDecimal.ZERO, new BigDecimal("8.50"), new BigDecimal("10.00"));
    }
}
