package com.sunny.paintfactory.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class SalesLedgerControllerTest {
    @Test void labelsSalesLinesAndActualReturnDocuments() {
        assertEquals("Sales Outbound", SalesLedgerController.lineTypeName("SALE", "PRODUCT"));
        assertEquals("Gift", SalesLedgerController.lineTypeName("SALE", "GIFT"));
        assertEquals("Sales Return", SalesLedgerController.lineTypeName("RETURN", "RETURN"));
    }

    @Test void supportsCheckboxStyleMultipleTypes() {
        assertEquals(2, SalesLedgerController.selectedTypes("SALE,RETURN").size());
        assertThrows(IllegalArgumentException.class, () -> SalesLedgerController.selectedTypes("UNKNOWN"));
    }
}
