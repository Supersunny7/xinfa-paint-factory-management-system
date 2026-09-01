package com.sunny.paintfactory.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import org.junit.jupiter.api.Test;

class PurchaseLedgerControllerTest {
    @Test
    void labelsReceiptAndReturn() {
        assertEquals("采购收货", PurchaseLedgerController.businessTypeName("ORDER_RECEIPT"));
        assertEquals("采购减数", PurchaseLedgerController.businessTypeName("ORDER_RETURN"));
        assertEquals("历史订单外减数", PurchaseLedgerController.businessTypeName("UNLINKED_RETURN"));
        assertEquals("历史未分类", PurchaseLedgerController.businessTypeName("HISTORICAL_UNCLASSIFIED"));
    }

    @Test
    void parsesCheckboxStyleBusinessTypes() {
        assertEquals(List.of(), PurchaseLedgerController.selectedTypes(""));
        assertEquals(List.of("RECEIPT"), PurchaseLedgerController.selectedTypes("RECEIPT"));
        assertEquals(List.of("RECEIPT", "RETURN"), PurchaseLedgerController.selectedTypes("RECEIPT,RETURN"));
        assertEquals(List.of("RETURN"), PurchaseLedgerController.selectedTypes(" RETURN,RETURN "));
    }
}
