package com.sunny.paintfactory.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class InventoryLedgerControllerTest {
    @Test void mapsStockChangingEventsToBusinessLabels() {
        assertEquals("采购收货", InventoryLedgerController.documentType("PURCHASE_RECEIPT", "PURCHASE_RECEIPT"));
        assertEquals("采购减数", InventoryLedgerController.documentType("PURCHASE_RETURN", "PURCHASE_RECEIPT"));
        assertEquals("销售出库", InventoryLedgerController.documentType("SALE_PRINT", "SALES_ORDER_PRINT"));
        assertEquals("销售退货入仓", InventoryLedgerController.documentType("SALES_RETURN", "RETURN_WAREHOUSE"));
        assertEquals("库存盘点/调整", InventoryLedgerController.documentType("ADJUSTMENT", "STOCK_TAKE_IMPORT"));
    }

    @Test void expandsCheckedBusinessTypes() {
        assertEquals(8, InventoryLedgerController.movementTypes(
            "PURCHASE_RECEIPT,SALES_OUTBOUND,SALES_RETURN,STOCK_ADJUSTMENT").size());
        assertThrows(IllegalArgumentException.class, () -> InventoryLedgerController.movementTypes("UNKNOWN"));
    }
}
