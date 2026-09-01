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
            new Case("PRODUCT_CATEGORY", "\u8d27\u54c1\u7c7b\u522b.xlsx", 20, 0),
            new Case("ROUTE", "\u8def\u7ebf\u8d44\u6599.xlsx", 41, 0),
            new Case("EMPLOYEE_TYPE", "\u5458\u5de5\u7c7b\u522b.xlsx", 9, 0),
            new Case("DEPARTMENT", "\u90e8\u95e8\u8d44\u6599.xlsx", 4, 0),
            new Case("SUPPLIER", "\u4f9b\u5e94\u5546\u8d44\u6599.xlsx", 91, 0),
            new Case("EMPLOYEE", "\u5458\u5de5\u8d44\u6599.xlsx", 94, 4),
            new Case("VEHICLE", "\u8f66\u8f86\u8d44\u6599.xlsx", 28, 0)
        );
    }

    @ParameterizedTest
    @MethodSource("legacyFiles")
    void previewsRealLegacyWorkbook(Case sample) throws Exception {
        String directory = System.getProperty("legacy.data.dir", "");
        Assumptions.assumeTrue(!directory.isBlank(), "Real legacy-file tests run only when legacy.data.dir is provided");
        Path path = Path.of(directory, sample.filename());
        Assumptions.assumeTrue(Files.exists(path), "Legacy export file does not exist");
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
