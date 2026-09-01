package com.sunny.paintfactory.ledger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class CashflowLedgerControllerTest {
    @Test void endpointClassIsAdminRestricted() {
        var annotation=CashflowLedgerController.class.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
        assertTrue(annotation!=null&&annotation.value().contains("ADMIN"));
    }
}
