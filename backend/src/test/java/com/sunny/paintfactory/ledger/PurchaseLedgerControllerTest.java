package com.sunny.paintfactory.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import org.junit.jupiter.api.Test;

class PurchaseLedgerControllerTest {
    @Test
    void labelsReceiptAndReturn() {
        assertEquals("Purchase Receipt", PurchaseLedgerController.businessTypeName("ORDER_RECEIPT"));
        assertEquals("Purchase Reduction", PurchaseLedgerController.businessTypeName("ORDER_RETURN"));
        assertEquals("Historical Unlinked Reduction", PurchaseLedgerController.businessTypeName("UNLINKED_RETURN"));
        assertEquals("Historical Unclassified", PurchaseLedgerController.businessTypeName("HISTORICAL_UNCLASSIFIED"));
    }

    @Test
    void parsesCheckboxStyleBusinessTypes() {
        assertEquals(List.of(), PurchaseLedgerController.selectedTypes(""));
        assertEquals(List.of("RECEIPT"), PurchaseLedgerController.selectedTypes("RECEIPT"));
        assertEquals(List.of("RECEIPT", "RETURN"), PurchaseLedgerController.selectedTypes("RECEIPT,RETURN"));
        assertEquals(List.of("RETURN"), PurchaseLedgerController.selectedTypes(" RETURN,RETURN "));
    }
}
