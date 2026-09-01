package com.sunny.paintfactory.inventory;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryControllerTest {
    @Test void calculatesInboundOutboundAndCount() {
        assertThat(InventoryController.calculateAfter(InventoryController.MovementType.INBOUND,new BigDecimal("10"),new BigDecimal("3"))).isEqualByComparingTo("13");
        assertThat(InventoryController.calculateAfter(InventoryController.MovementType.OUTBOUND,new BigDecimal("10"),new BigDecimal("3"))).isEqualByComparingTo("7");
        assertThat(InventoryController.calculateAfter(InventoryController.MovementType.ADJUSTMENT,new BigDecimal("10"),new BigDecimal("8"))).isEqualByComparingTo("8");
        assertThat(InventoryController.calculateAfter(InventoryController.MovementType.ADJUSTMENT,new BigDecimal("10"),BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test void rejectsNegativeInventory() {
        assertThatThrownBy(()->InventoryController.calculateAfter(InventoryController.MovementType.OUTBOUND,new BigDecimal("2"),new BigDecimal("3"))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("出库数量不能大于当前库存");
    }
}
