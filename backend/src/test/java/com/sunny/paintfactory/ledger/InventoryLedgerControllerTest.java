package com.sunny.paintfactory.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class InventoryLedgerControllerTest {
    @Test void mapsStockChangingEventsToBusinessLabels() {
        assertEquals("Purchase Receipt", InventoryLedgerController.documentType("PURCHASE_RECEIPT", "PURCHASE_RECEIPT"));
        assertEquals("Purchase Reduction", InventoryLedgerController.documentType("PURCHASE_RETURN", "PURCHASE_RECEIPT"));
        assertEquals("Sales Outbound", InventoryLedgerController.documentType("SALE_PRINT", "SALES_ORDER_PRINT"));
        assertEquals("Sales Return Warehousing", InventoryLedgerController.documentType("SALES_RETURN", "RETURN_WAREHOUSE"));
        assertEquals("Stock Count / Adjustment", InventoryLedgerController.documentType("ADJUSTMENT", "STOCK_TAKE_IMPORT"));
    }

    @Test void expandsCheckedBusinessTypes() {
        assertEquals(8, InventoryLedgerController.movementTypes(
            "PURCHASE_RECEIPT,SALES_OUTBOUND,SALES_RETURN,STOCK_ADJUSTMENT").size());
        assertThrows(IllegalArgumentException.class, () -> InventoryLedgerController.movementTypes("UNKNOWN"));
    }
}
