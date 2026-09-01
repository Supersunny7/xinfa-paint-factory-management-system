package com.sunny.paintfactory.dispatch;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DispatchSheetControllerTest {
    @Test void dispatchWorkflowNeverMutatesStock(){
        assertThatThrownBy(DispatchSheetController::dispatchStockMutationDisabled)
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("deducted only when a sales-order delivery note is confirmed for the first time")
            .hasMessageContaining("does not change inventory");
    }
    @Test void translatesDispatchStatusesForBusinessMessages(){
        assertThat(DispatchSheetController.dispatchStatusText("DRAFT")).isEqualTo("Draft");
        assertThat(DispatchSheetController.dispatchStatusText("APPROVED")).isEqualTo("Approved");
        assertThat(DispatchSheetController.dispatchStatusText("VOIDED")).isEqualTo("Voided");
    }
    @Test void emptyDispatchSheetCannotBeApproved(){
        assertThatThrownBy(()->DispatchSheetController.validateHasOrders(0))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("without sales orders");
        DispatchSheetController.validateHasOrders(1);
    }
    @Test void voidedDispatchKeepsHistoryWithoutOccupyingSalesOrder(){
        assertThat(DispatchSheetController.dispatchOccupiesSalesOrder("DRAFT")).isTrue();
        assertThat(DispatchSheetController.dispatchOccupiesSalesOrder("APPROVED")).isTrue();
        assertThat(DispatchSheetController.dispatchOccupiesSalesOrder("VOIDED")).isFalse();
    }
    @Test void onlyPrintedActiveSalesOrdersCanJoinDispatchSheets(){
        assertThatThrownBy(()->DispatchSheetController.validateOrderEligibility("DRAFT",null,"XS260813-001"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("delivery note has been printed");
        assertThatThrownBy(()->DispatchSheetController.validateOrderEligibility("VOIDED",Timestamp.valueOf(LocalDateTime.now()),"XS260813-002"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Voided");
        DispatchSheetController.validateOrderEligibility("DRAFT",Timestamp.valueOf(LocalDateTime.now()),"XS260813-003");
    }
    @Test void normalizesSalesOrderTailAndKeepsZeroEquivalent(){
        assertThat(DispatchSheetController.normalizeOrderTail("3")).isEqualTo("3");
        assertThat(DispatchSheetController.normalizeOrderTail("03")).isEqualTo("3");
        assertThat(DispatchSheetController.normalizeOrderTail("003")).isEqualTo("3");
        assertThat(DispatchSheetController.normalizeOrderTail("000")).isEqualTo("0");
    }
    @Test void rejectsNonNumericSalesOrderTail(){
        assertThatThrownBy(()->DispatchSheetController.normalizeOrderTail("XS003"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("digits at the end");
    }
}
