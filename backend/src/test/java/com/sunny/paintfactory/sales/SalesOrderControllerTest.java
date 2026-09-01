package com.sunny.paintfactory.sales;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.web.server.ResponseStatusException;

class SalesOrderControllerTest {
    @Test void onlyFirstPrintMayDeductInventory(){
        assertThat(SalesOrderController.isFirstPrint(null)).isTrue();
        assertThat(SalesOrderController.isFirstPrint(Timestamp.valueOf(LocalDateTime.now()))).isFalse();
    }
    @Test void firstPrintCreatesFirstLogAndDeductsInventory(){
        var decision=SalesOrderController.decidePrint(null,0,null);
        assertThat(decision.firstPrint()).isTrue();
        assertThat(decision.deductInventory()).isTrue();
        assertThat(decision.printKind()).isEqualTo("FIRST_PRINT");
        assertThat(decision.printNo()).isEqualTo(1);
    }

    @Test void reprintRequiresReasonAndNeverDeductsInventory(){
        var printedAt=Timestamp.valueOf(LocalDateTime.now());
        assertThatThrownBy(()->SalesOrderController.decidePrint(printedAt,1," "))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("reason is required");
        var decision=SalesOrderController.decidePrint(printedAt,1,"Customer requested another copy");
        assertThat(decision.firstPrint()).isFalse();
        assertThat(decision.deductInventory()).isFalse();
        assertThat(decision.printKind()).isEqualTo("REPRINT");
        assertThat(decision.printNo()).isEqualTo(2);
    }

    @Test void salePriceBelowWholesaleReferenceIsRejected(){
        assertThatThrownBy(()->SalesOrderController.validateSalePrice("Red Oxide Primer 13 kg",new BigDecimal("58"),new BigDecimal("59")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Price too low")
            .hasMessageContaining("59");
    }

    @Test void salePriceEqualOrAboveWholesaleReferenceIsAllowed(){
        SalesOrderController.validateSalePrice("Free Sample",BigDecimal.ZERO,new BigDecimal("59"));
        SalesOrderController.validateSalePrice("Test Product",new BigDecimal("59"),new BigDecimal("59"));
        SalesOrderController.validateSalePrice("Test Product",new BigDecimal("60"),new BigDecimal("59"));
    }

    @Test void missingWholesaleReferenceIsNormalizedToZero(){
        assertThat(SalesOrderController.normalizedReferencePrice(null)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(SalesOrderController.normalizedReferencePrice(new BigDecimal("59"))).isEqualByComparingTo("59");
    }

    @Test void negativeSalePriceIsRejected(){
        assertThatThrownBy(()->SalesOrderController.validateSalePrice("Test Product",new BigDecimal("-1"),new BigDecimal("59")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("cannot be negative");
    }

    @Test void saleQuantityMustBeAtLeastOneHundredthWithAtMostTwoDecimals(){
        SalesOrderController.validateSaleQuantity(new BigDecimal("0.01"));
        SalesOrderController.validateSaleQuantity(new BigDecimal("1.23"));
        SalesOrderController.validateSaleQuantity(new BigDecimal("1.230"));
        assertThatThrownBy(()->SalesOrderController.validateSaleQuantity(BigDecimal.ZERO))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("0.01");
        assertThatThrownBy(()->SalesOrderController.validateSaleQuantity(new BigDecimal("0.001")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("0.01");
        assertThatThrownBy(()->SalesOrderController.validateSaleQuantity(new BigDecimal("1.234")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("two decimal places");
    }
}
