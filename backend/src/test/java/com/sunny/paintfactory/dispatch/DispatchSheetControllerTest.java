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
            .hasMessageContaining("只在销售单首次确认打印时扣减")
            .hasMessageContaining("出车表审核、完成和打印均不改变库存");
    }
    @Test void translatesDispatchStatusesForBusinessMessages(){
        assertThat(DispatchSheetController.dispatchStatusText("DRAFT")).isEqualTo("草稿");
        assertThat(DispatchSheetController.dispatchStatusText("APPROVED")).isEqualTo("已审核");
        assertThat(DispatchSheetController.dispatchStatusText("VOIDED")).isEqualTo("已作废");
    }
    @Test void emptyDispatchSheetCannotBeApproved(){
        assertThatThrownBy(()->DispatchSheetController.validateHasOrders(0))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("没有销售单");
        DispatchSheetController.validateHasOrders(1);
    }
    @Test void voidedDispatchKeepsHistoryWithoutOccupyingSalesOrder(){
        assertThat(DispatchSheetController.dispatchOccupiesSalesOrder("DRAFT")).isTrue();
        assertThat(DispatchSheetController.dispatchOccupiesSalesOrder("APPROVED")).isTrue();
        assertThat(DispatchSheetController.dispatchOccupiesSalesOrder("VOIDED")).isFalse();
    }
    @Test void onlyPrintedActiveSalesOrdersCanJoinDispatchSheets(){
        assertThatThrownBy(()->DispatchSheetController.validateOrderEligibility("DRAFT",null,"XS260813-001"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("尚未打印送货单");
        assertThatThrownBy(()->DispatchSheetController.validateOrderEligibility("VOIDED",Timestamp.valueOf(LocalDateTime.now()),"XS260813-002"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("已作废");
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
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("末尾的数字");
    }
}
