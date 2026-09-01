package com.sunny.paintfactory.dashboard;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardControllerTest {
    @Test void changePercentUsesPreviousGrossProfitAsBase() {
        assertThat(DashboardController.changePercent(new BigDecimal("108.60"), new BigDecimal("100")))
            .isEqualByComparingTo("8.6");
    }

    @Test void changePercentIsUnavailableWithoutPreviousGrossProfit() {
        assertThat(DashboardController.changePercent(new BigDecimal("100"), BigDecimal.ZERO)).isNull();
    }

    @Test void changePercentHandlesDecline() {
        assertThat(DashboardController.changePercent(new BigDecimal("75"), new BigDecimal("100")))
            .isEqualByComparingTo("-25.0");
    }
}
