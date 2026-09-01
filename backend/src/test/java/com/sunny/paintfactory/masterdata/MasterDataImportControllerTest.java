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
            sheet.createRow(0).createCell(0).setCellValue("路线资料");
            Row header = sheet.createRow(1); header.createCell(0).setCellValue("编号"); header.createCell(1).setCellValue("名称");
            Row first = sheet.createRow(2); first.createCell(0).setCellValue("001"); first.createCell(1).setCellValue("加油");
            Row duplicate = sheet.createRow(3); duplicate.createCell(0).setCellValue("001"); duplicate.createCell(1).setCellValue("");

            Map<String, Object> result = controller.readSheet(sheet, List.of("编号", "名称")).toResponse();
            Map<?, ?> summary = (Map<?, ?>) result.get("summary");
            assertThat(summary.get("total")).isEqualTo(2);
            assertThat(summary.get("valid")).isEqualTo(1);
            assertThat(summary.get("duplicates")).isEqualTo(1);
            assertThat(summary.get("emptyName")).isEqualTo(1);
            List<?> rows = (List<?>) result.get("rows");
            assertThat(((Map<?, ?>) rows.get(0)).get("编号")).isEqualTo("001");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    void rejectsMissingRequiredHeader() {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            Row header = sheet.createRow(0); header.createCell(0).setCellValue("编号");
            assertThatThrownBy(() -> controller.readSheet(sheet, List.of("编号", "名称")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("缺少必填列");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
