package com.sunny.paintfactory.returning;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesReturnControllerTest {
    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void warehouseDetailIncludesEverySalesReturnItemField() throws Exception {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.query(anyString(),any(RowMapper.class),anyLong())).thenAnswer(invocation->{
            String sql=invocation.getArgument(0);
            RowMapper mapper=invocation.getArgument(1);
            var rs=mock(java.sql.ResultSet.class);
            if(sql.startsWith("SELECT id,warehouse_no")) {
                when(rs.getLong(1)).thenReturn(1L);when(rs.getString(2)).thenReturn("TJ260820-001");
                when(rs.getDate(3)).thenReturn(java.sql.Date.valueOf("2026-08-20"));when(rs.getString(4)).thenReturn("DRAFT");
                when(rs.getString(5)).thenReturn("");when(rs.getInt(6)).thenReturn(0);
                when(rs.getTimestamp(7)).thenReturn(java.sql.Timestamp.valueOf("2026-08-20 10:00:00"));
                when(rs.getTimestamp(8)).thenReturn(java.sql.Timestamp.valueOf("2026-08-20 10:00:00"));
                return List.of(mapper.mapRow(rs,0));
            }
            if(sql.startsWith("SELECT r.id,r.return_no")) return List.of();
            if(sql.startsWith("SELECT r.return_no,r.return_date")) {
                when(rs.getString(1)).thenReturn("XT260820-001");when(rs.getDate(2)).thenReturn(java.sql.Date.valueOf("2026-08-20"));
                when(rs.getString(3)).thenReturn("Salesperson A");when(rs.getString(4)).thenReturn("C001");when(rs.getString(5)).thenReturn("Test Customer");
                when(rs.getInt(6)).thenReturn(1);when(rs.getString(7)).thenReturn("P001");when(rs.getString(8)).thenReturn("Test Paint");
                when(rs.getString(9)).thenReturn("5 kg");when(rs.getString(10)).thenReturn("Blue");when(rs.getString(11)).thenReturn("Pail");
                when(rs.getBigDecimal(12)).thenReturn(new BigDecimal("-2"));when(rs.getBigDecimal(13)).thenReturn(new BigDecimal("10"));
                when(rs.getBigDecimal(14)).thenReturn(new BigDecimal("12"));when(rs.getBigDecimal(15)).thenReturn(new BigDecimal("-20"));
                when(rs.getString(16)).thenReturn("Document remark");when(rs.getString(17)).thenReturn("Line remark");
                return List.of(mapper.mapRow(rs,0));
            }
            throw new AssertionError("Unhandled query: "+sql);
        });

        var detail=new SalesReturnController(jdbc).getWarehouse(1).data();
        var items=(List<java.util.Map<String,Object>>)detail.get("items");
        assertThat(items).singleElement().satisfies(item->{
            assertThat(item).containsEntry("returnNo","XT260820-001").containsEntry("skuCode","P001")
                    .containsEntry("quantity",new BigDecimal("-2")).containsEntry("lineRemark","Line remark");
        });
    }

    @Test
    void approvedWarehouseCannotBeApprovedAgainOrIncreaseStockAgain() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.query(anyString(),any(RowMapper.class),anyLong()))
            .thenReturn(java.util.Collections.singletonList(new Object[]{"TJ260819-001","APPROVED",1}));
        SalesReturnController controller=new SalesReturnController(jdbc);

        assertThatThrownBy(()->controller.approveWarehouse(1,new SalesReturnController.VersionRequest(1),authentication()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409 CONFLICT");

        verify(jdbc,never()).update(org.mockito.ArgumentMatchers.contains("UPDATE product_sku"),any(Object[].class));
        verify(jdbc,never()).update(org.mockito.ArgumentMatchers.contains("INSERT INTO inventory_movement"),any(Object[].class));
    }

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void warehouseApprovalIncreasesStockAndApprovesLinkedReturn() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.query(anyString(),any(RowMapper.class),anyLong())).thenAnswer(invocation->{
            String sql=invocation.getArgument(0);
            if(sql.startsWith("SELECT warehouse_no")) return java.util.Collections.singletonList(new Object[]{"TJ260819-001","DRAFT",0});
            if(sql.startsWith("SELECT sr.id")) return List.of(11L);
            if(sql.startsWith("SELECT i.id")) {
                RowMapper mapper=invocation.getArgument(1);
                var rs=mock(java.sql.ResultSet.class);
                when(rs.getLong(1)).thenReturn(101L);
                when(rs.getLong(2)).thenReturn(1001L);
                when(rs.getString(3)).thenReturn("P001");
                when(rs.getString(4)).thenReturn("Test Product");
                when(rs.getBigDecimal(5)).thenReturn(new BigDecimal("3"));
                return List.of(mapper.mapRow(rs,0));
            }
            throw new AssertionError("Unhandled query: "+sql);
        });
        when(jdbc.queryForObject(anyString(),any(Class.class),any(Object[].class))).thenAnswer(invocation->{
            String sql=invocation.getArgument(0);
            if(sql.startsWith("SELECT COUNT(*) FROM sales_return")) return 0;
            if(sql.startsWith("SELECT total_stock")) return new BigDecimal("7");
            if(sql.startsWith("SELECT id FROM sys_user")) return 9L;
            throw new AssertionError("Unhandled scalar query: "+sql);
        });
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        SalesReturnController controller=new SalesReturnController(jdbc);

        controller.approveWarehouse(1,new SalesReturnController.VersionRequest(0),authentication());

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE product_sku SET total_stock=?"),any(Object[].class));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO inventory_movement"),any(Object[].class));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE sales_return SET status='APPROVED'"),any(Object[].class));
        verify(jdbc,atLeastOnce()).update(org.mockito.ArgumentMatchers.contains("UPDATE return_warehouse SET status='APPROVED'"),any(Object[].class));
    }

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void warehouseApprovalFailureRemainsTransactionalAndStopsBeforeDocumentApproval() throws Exception {
        assertThat(SalesReturnController.class
            .getMethod("approveWarehouse",long.class,SalesReturnController.VersionRequest.class,Authentication.class)
            .isAnnotationPresent(Transactional.class)).isTrue();

        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.query(anyString(),any(RowMapper.class),anyLong())).thenAnswer(invocation->{
            String sql=invocation.getArgument(0);
            if(sql.startsWith("SELECT warehouse_no")) return java.util.Collections.singletonList(new Object[]{"TJ260819-002","DRAFT",0});
            if(sql.startsWith("SELECT sr.id")) return List.of(12L);
            if(sql.startsWith("SELECT i.id")) {
                RowMapper mapper=invocation.getArgument(1);
                var rs=mock(java.sql.ResultSet.class);
                when(rs.getLong(1)).thenReturn(102L);
                when(rs.getLong(2)).thenReturn(1002L);
                when(rs.getString(3)).thenReturn("P002");
                when(rs.getString(4)).thenReturn("Rollback Test Product");
                when(rs.getBigDecimal(5)).thenReturn(new BigDecimal("2"));
                return List.of(mapper.mapRow(rs,0));
            }
            throw new AssertionError("Unhandled query: "+sql);
        });
        when(jdbc.queryForObject(anyString(),any(Class.class),any(Object[].class))).thenAnswer(invocation->{
            String sql=invocation.getArgument(0);
            if(sql.startsWith("SELECT COUNT(*) FROM sales_return")) return 0;
            if(sql.startsWith("SELECT total_stock")) return new BigDecimal("8");
            if(sql.startsWith("SELECT id FROM sys_user")) return 9L;
            throw new AssertionError("Unhandled scalar query: "+sql);
        });
        when(jdbc.update(contains("INSERT INTO inventory_movement"),any(Object[].class)))
            .thenThrow(new RuntimeException("Simulated inventory-movement insert failure"));
        SalesReturnController controller=new SalesReturnController(jdbc);

        assertThatThrownBy(()->controller.approveWarehouse(2,new SalesReturnController.VersionRequest(0),authentication()))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Simulated inventory-movement insert failure");

        verify(jdbc).update(contains("UPDATE product_sku SET total_stock=?"),any(Object[].class));
        verify(jdbc).update(contains("INSERT INTO inventory_movement"),any(Object[].class));
        verify(jdbc,never()).update(contains("UPDATE sales_return SET status='APPROVED'"),any(Object[].class));
        verify(jdbc,never()).update(contains("UPDATE return_warehouse SET status='APPROVED'"),any(Object[].class));
    }

    private Authentication authentication(){
        Authentication auth=mock(Authentication.class);
        when(auth.getName()).thenReturn("admin");
        return auth;
    }
}
