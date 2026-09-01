package com.sunny.paintfactory.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;

class MasterDataImportLegacyFilesTest {
    record Case(String type, String filename, int expectedRows, int expectedInvalid) {}

    static Stream<Case> legacyFiles() {
        return Stream.of(
            new Case("PRODUCT_CATEGORY", "货品类别.xlsx", 20, 0),
            new Case("ROUTE", "路线资料.xlsx", 41, 0),
            new Case("EMPLOYEE_TYPE", "员工类别.xlsx", 9, 0),
            new Case("DEPARTMENT", "部门资料.xlsx", 4, 0),
            new Case("SUPPLIER", "供应商资料.xlsx", 91, 0),
            new Case("EMPLOYEE", "员工资料.xlsx", 94, 4),
            new Case("VEHICLE", "车辆资料.xlsx", 28, 0)
        );
    }

    @ParameterizedTest
    @MethodSource("legacyFiles")
    void previewsRealLegacyWorkbook(Case sample) throws Exception {
        String directory = System.getProperty("legacy.data.dir", "");
        Assumptions.assumeTrue(!directory.isBlank(), "仅在提供 legacy.data.dir 时运行真实旧系统文件测试");
        Path path = Path.of(directory, sample.filename());
        Assumptions.assumeTrue(Files.exists(path), "旧系统导出文件不存在");
        var file = new MockMultipartFile("file", sample.filename(),
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Files.readAllBytes(path));

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, Object> result = new MasterDataImportController(jdbc).preview(sample.type(), file).data();
        Map<?, ?> summary = (Map<?, ?>) result.get("summary");
        assertThat(summary.get("total")).isEqualTo(sample.expectedRows());
        assertThat(summary.get("invalid")).isEqualTo(sample.expectedInvalid());
        assertThat(summary.get("valid")).isEqualTo(sample.expectedRows() - sample.expectedInvalid());
        assertThat(result.get("canImportValidRows")).isEqualTo("EMPLOYEE".equals(sample.type()));
        assertThat((List<?>) result.get("rows")).isNotEmpty();
    }
}
