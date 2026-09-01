package com.sunny.paintfactory.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;

class MasterDataImportControllerTest {
    private final MasterDataImportController controller = new MasterDataImportController(mock(JdbcTemplate.class));

    @Test
    void keepsLeadingZerosAndReportsDuplicateAndEmptyName() {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue("Route Data");
            Row header = sheet.createRow(1); header.createCell(0).setCellValue("Code"); header.createCell(1).setCellValue("Name");
            Row first = sheet.createRow(2); first.createCell(0).setCellValue("001"); first.createCell(1).setCellValue("Refueling");
            Row duplicate = sheet.createRow(3); duplicate.createCell(0).setCellValue("001"); duplicate.createCell(1).setCellValue("");

            Map<String, Object> result = controller.readSheet(sheet, List.of("Code", "Name")).toResponse();
            Map<?, ?> summary = (Map<?, ?>) result.get("summary");
            assertThat(summary.get("total")).isEqualTo(2);
            assertThat(summary.get("valid")).isEqualTo(1);
            assertThat(summary.get("duplicates")).isEqualTo(1);
            assertThat(summary.get("emptyName")).isEqualTo(1);
            List<?> rows = (List<?>) result.get("rows");
            assertThat(((Map<?, ?>) rows.get(0)).get("Code")).isEqualTo("001");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    void rejectsMissingRequiredHeader() {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            Row header = sheet.createRow(0); header.createCell(0).setCellValue("Code");
            assertThatThrownBy(() -> controller.readSheet(sheet, List.of("Code", "Name")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Missing required columns");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
