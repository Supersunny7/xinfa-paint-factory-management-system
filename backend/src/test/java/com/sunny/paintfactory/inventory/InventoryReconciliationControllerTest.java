package com.sunny.paintfactory.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InventoryReconciliationControllerTest {
    @Test void classifiesInventoryWarnings(){assertEquals("OUT_OF_STOCK",InventoryReconciliationController.inventoryStatus(new BigDecimal("0"),new BigDecimal("10")));assertEquals("LOW_STOCK",InventoryReconciliationController.inventoryStatus(new BigDecimal("5"),new BigDecimal("10")));assertEquals("NORMAL",InventoryReconciliationController.inventoryStatus(new BigDecimal("10"),new BigDecimal("10")));}
    @Test void classifiesLedgerReconciliation(){assertEquals("NO_MOVEMENT",InventoryReconciliationController.reconciliationStatus(BigDecimal.TEN,null));assertEquals("BALANCED",InventoryReconciliationController.reconciliationStatus(BigDecimal.TEN,new BigDecimal("10.0000")));assertEquals("MISMATCH",InventoryReconciliationController.reconciliationStatus(BigDecimal.TEN,new BigDecimal("9")));}
}
