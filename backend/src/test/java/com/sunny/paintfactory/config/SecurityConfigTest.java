package com.sunny.paintfactory.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sunny.paintfactory.auth.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SecurityConfigTest.TestEndpoints.class)
@Import({SecurityConfig.class, SecurityConfigTest.TestEndpoints.class})
class SecurityConfigTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtAuthenticationFilter jwtFilter;

    @BeforeEach
    void passThroughJwtFilter() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    @Test
    void inventoryAdjustmentRequiresWarehouseRole() throws Exception {
        mvc.perform(post("/api/v1/products/8/inventory-adjustments").with(user("sales").roles("SALES")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/products/8/inventory-adjustments").with(user("warehouse").roles("WAREHOUSE")))
            .andExpect(status().isNoContent());
    }

    @Test
    void masterDataWritesFollowCurrentRoleMatrix() throws Exception {
        mvc.perform(post("/api/v1/customers").with(user("warehouse").roles("WAREHOUSE")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/customers").with(user("sales").roles("SALES")))
            .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/routes").with(user("sales").roles("SALES")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/routes").with(user("dispatch").roles("DISPATCH")))
            .andExpect(status().isNoContent());
    }

    @Test
    void productImportRemainsAdminOnly() throws Exception {
        mvc.perform(post("/api/v1/products/import").with(user("warehouse").roles("WAREHOUSE")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/products/import").with(user("admin").roles("ADMIN")))
            .andExpect(status().isNoContent());
    }

    @Test
    void authenticatedBusinessRolesCanReadReferenceData() throws Exception {
        mvc.perform(get("/api/v1/products/8").with(user("dispatch").roles("DISPATCH")))
            .andExpect(status().isOk());
    }

    @Test
    void ownerDashboardIsAdminOnly() throws Exception {
        mvc.perform(get("/api/v1/dashboard/owner").with(user("sales").roles("SALES")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/dashboard/owner").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk());
    }

    @Test
    void inventoryLedgerIsAvailableToWarehouseButNotSales() throws Exception {
        mvc.perform(get("/api/v1/ledgers/inventory").with(user("sales").roles("SALES")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/ledgers/inventory").with(user("warehouse").roles("WAREHOUSE")))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/ledgers/purchases").with(user("sales").roles("SALES")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/ledgers/purchases").with(user("warehouse").roles("WAREHOUSE")))
            .andExpect(status().isOk());
    }

    @Test
    void salesLedgerIsAvailableToSalesButNotWarehouse() throws Exception {
        mvc.perform(get("/api/v1/ledgers/sales").with(user("sales").roles("SALES")))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/ledgers/sales").with(user("warehouse").roles("WAREHOUSE")))
            .andExpect(status().isForbidden());
    }

    @Test
    void cashflowLedgerIsAdminOnly() throws Exception {
        mvc.perform(get("/api/v1/ledgers/cashflow").with(user("warehouse").roles("WAREHOUSE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/ledgers/cashflow").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
    }

    @RestController
    @RequestMapping("/api/v1")
    static class TestEndpoints {
        @PostMapping({
            "/products/{id}/inventory-adjustments", "/products/import",
            "/customers", "/routes"
        })
        ResponseEntity<Void> write() {
            return ResponseEntity.noContent().build();
        }

        @GetMapping({"/products/{id}", "/dashboard/owner", "/ledgers/inventory", "/ledgers/purchases", "/ledgers/sales", "/ledgers/cashflow"})
        ResponseEntity<Void> read() {
            return ResponseEntity.ok().build();
        }
    }
}
