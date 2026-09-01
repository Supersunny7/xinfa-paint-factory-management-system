package com.sunny.paintfactory.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class InventoryImportControllerTest {
    @Test
    void acceptsChineseAliasesAndKeepsLeadingZeroSku() throws Exception {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),eq("0018"))).thenReturn(List.of(Map.of(
            "id",18L,"sku_code","0018","product_name","Test Product","total_stock",new BigDecimal("8.0000"),"version",3,"enabled",true,"saleable",true)));
        InventoryImportController controller=new InventoryImportController(jdbc);
        try(var workbook=new XSSFWorkbook()){
            var sheet=workbook.createSheet("Inventory");Row header=sheet.createRow(0);header.createCell(0).setCellValue("Code");header.createCell(1).setCellValue("Total Stock");
            Row row=sheet.createRow(1);row.createCell(0).setCellValue("0018");row.createCell(1).setCellValue(12);
            var parsed=controller.readSheet(sheet);Map<String,Object> result=controller.buildPreview(parsed,false);Map<?,?> summary=(Map<?,?>)result.get("summary");
            assertThat(summary.get("valid")).isEqualTo(1);assertThat(summary.get("updateRecords")).isEqualTo(1);
            assertThat(((Map<?,?>)((List<?>)result.get("rows")).get(0)).get("Product Code")).isEqualTo("0018");
            assertThat(String.valueOf(result.get("previewToken"))).hasSize(64);
        }
    }

    @Test
    void skipsMissingDisabledAndUnsaleableProductsWithoutBlockingImport() throws Exception {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),eq("MISSING"))).thenReturn(List.of());
        when(jdbc.queryForList(anyString(),eq("DISABLED"))).thenReturn(List.of(Map.of(
            "id",2L,"sku_code","DISABLED","product_name","Disabled Product","total_stock",BigDecimal.ONE,"version",1,"enabled",false,"saleable",true)));
        when(jdbc.queryForList(anyString(),eq("NOTSALE"))).thenReturn(List.of(Map.of(
            "id",3L,"sku_code","NOTSALE","product_name","Non-saleable Product","total_stock",BigDecimal.ONE,"version",1,"enabled",true,"saleable",false)));
        InventoryImportController controller=new InventoryImportController(jdbc);
        var parsed=new InventoryImportController.ParsedFile("Sheet1",2,3,0,List.of(
            new InventoryImportController.StockRow(3,"MISSING",BigDecimal.TEN),
            new InventoryImportController.StockRow(4,"DISABLED",BigDecimal.TEN),
            new InventoryImportController.StockRow(5,"NOTSALE",BigDecimal.TEN)),List.of());

        Map<String,Object> result=controller.buildPreview(parsed,false);
        Map<?,?> summary=(Map<?,?>)result.get("summary");
        assertThat(summary.get("ignored")).isEqualTo(3);
        assertThat(summary.get("invalid")).isEqualTo(0);
        assertThat(summary.get("updateRecords")).isEqualTo(0);
        assertThat(((List<?>)result.get("rows")).stream().map(row->String.valueOf(((Map<?,?>)row).get("Validation Result"))))
            .allMatch(status->status.startsWith("Skipped"));
    }

    @Test
    void acceptsNegativeStockButRejectsDuplicateRows() throws Exception {
        InventoryImportController controller=new InventoryImportController(mock(JdbcTemplate.class));
        try(var workbook=new XSSFWorkbook()){
            var sheet=workbook.createSheet();Row header=sheet.createRow(0);header.createCell(0).setCellValue("Product Code");header.createCell(1).setCellValue("Counted Stock");
            Row first=sheet.createRow(1);first.createCell(0).setCellValue("A1");first.createCell(1).setCellValue(-1);
            Row duplicate=sheet.createRow(2);duplicate.createCell(0).setCellValue("A1");duplicate.createCell(1).setCellValue(2);
            var parsed=controller.readSheet(sheet);
            assertThat(parsed.total()).isEqualTo(2);assertThat(parsed.rows()).hasSize(1);assertThat(parsed.rows().get(0).stock()).isEqualByComparingTo("-1");assertThat(parsed.duplicates()).isEqualTo(1);assertThat(parsed.issues()).hasSize(1);
        }
    }
}
