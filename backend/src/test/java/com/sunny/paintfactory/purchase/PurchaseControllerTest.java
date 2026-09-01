package com.sunny.paintfactory.purchase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseControllerTest {
    @Test void receiptThenReturnCalculatesNetAndRemainingPerLine(){
        var result=PurchaseController.progress(new java.math.BigDecimal("20"),new java.math.BigDecimal("20"),new java.math.BigDecimal("1"));
        assertThat(result.net()).isEqualByComparingTo("19");
        assertThat(result.remaining()).isEqualByComparingTo("1");
        assertThat(result.over()).isEqualByComparingTo("0");
    }

    @Test void overReceiptIsAllowedAndReportedWithoutNegativeRemaining(){
        var result=PurchaseController.progress(new java.math.BigDecimal("100"),new java.math.BigDecimal("105"),java.math.BigDecimal.ZERO);
        assertThat(result.net()).isEqualByComparingTo("105");
        assertThat(result.remaining()).isEqualByComparingTo("0");
        assertThat(result.over()).isEqualByComparingTo("5");
    }

    @Test void returnAfterOverReceiptCanReopenRemainingQuantity(){
        var result=PurchaseController.progress(
            new java.math.BigDecimal("100"),
            new java.math.BigDecimal("105"),
            new java.math.BigDecimal("10"));
        assertThat(result.net()).isEqualByComparingTo("95");
        assertThat(result.remaining()).isEqualByComparingTo("5");
        assertThat(result.over()).isEqualByComparingTo("0");
    }

    @Test void approvedReceiptCannotBeApprovedAgain(){
        PurchaseController controller=controllerWithReceipt("APPROVED",2);
        assertThatThrownBy(()->controller.approveReceipt(1,new PurchaseController.VersionOnly(2),null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409 CONFLICT");
    }

    @Test void staleReceiptCannotBeApproved(){
        PurchaseController controller=controllerWithReceipt("DRAFT",3);
        assertThatThrownBy(()->controller.approveReceipt(1,new PurchaseController.VersionOnly(2),null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409 CONFLICT");
    }

    @Test void unapprovedReceiptCannotBeConfirmedAsPrinted(){
        PurchaseController controller=controllerWithPrintableReceipt("DRAFT",2,null,0);
        assertThatThrownBy(()->controller.confirmReceiptPrint(1,new PurchaseController.VersionOnly(2),null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409 CONFLICT");
    }

    @Test void staleReceiptPrintConfirmationIsRejected(){
        PurchaseController controller=controllerWithPrintableReceipt("APPROVED",3,null,0);
        assertThatThrownBy(()->controller.confirmReceiptPrint(1,new PurchaseController.VersionOnly(2),null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409 CONFLICT");
    }

    @Test void orderReceiptRequiresPositiveQuantityAndBothOrderLinks(){
        assertThatThrownBy(()->PurchaseController.validateReceiptLineShape(10L,20L,"ORDER_RECEIPT",new java.math.BigDecimal("-1")))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("加库存数量必须大于0");
        assertThatThrownBy(()->PurchaseController.validateReceiptLineShape(10L,null,"ORDER_RECEIPT",java.math.BigDecimal.ONE))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("必须关联采购订单及订单明细");
        assertThatThrownBy(()->PurchaseController.validateReceiptLineShape(null,20L,"ORDER_RECEIPT",java.math.BigDecimal.ONE))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("必须关联采购订单及订单明细");
    }

    @Test void orderReturnRequiresNegativeQuantityAndBothOrderLinks(){
        assertThatThrownBy(()->PurchaseController.validateReceiptLineShape(10L,20L,"ORDER_RETURN",java.math.BigDecimal.ONE))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("减库存数量必须小于0");
        assertThatThrownBy(()->PurchaseController.validateReceiptLineShape(10L,null,"ORDER_RETURN",new java.math.BigDecimal("-1")))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("必须关联采购订单及订单明细");
    }

    @Test void unlinkedReturnCannotBeSubmitted(){
        assertThatThrownBy(()->PurchaseController.validateReceiptLineShape(10L,null,"UNLINKED_RETURN",new java.math.BigDecimal("-3")))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("业务类型无效");
    }

    @Test void invalidOrHistoricalBusinessTypeCannotBeSubmitted(){
        assertThatThrownBy(()->PurchaseController.validateReceiptLineShape(10L,20L,"HISTORICAL_UNCLASSIFIED",java.math.BigDecimal.ONE))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("业务类型无效");
    }

    @Test void stalePurchaseOrderDraftCannotBeDeleted(){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.query(anyString(),any(RowMapper.class),anyLong()))
            .thenReturn(java.util.Collections.singletonList(new Object[]{"DRAFT",3,null,null}));
        PurchaseController controller=new PurchaseController(jdbc);

        assertThatThrownBy(()->controller.deleteOrder(1,2))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("已被修改");
        verify(jdbc,never()).update(org.mockito.ArgumentMatchers.startsWith("DELETE"),any(Object[].class));
    }

    @Test void printedPurchaseOrderDraftCannotBeDeleted(){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.query(anyString(),any(RowMapper.class),anyLong()))
            .thenReturn(java.util.Collections.singletonList(new Object[]{"DRAFT",2,java.sql.Timestamp.valueOf("2026-08-22 12:00:00"),null}));
        PurchaseController controller=new PurchaseController(jdbc);

        assertThatThrownBy(()->controller.deleteOrder(1,2))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("只有未打印、未完成");
    }

    @Test void purchaseOrderWithReceiptHistoryCannotBeDeleted(){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.query(anyString(),any(RowMapper.class),anyLong()))
            .thenReturn(java.util.Collections.singletonList(new Object[]{"DRAFT",2,null,null}));
        when(jdbc.queryForObject(anyString(),org.mockito.ArgumentMatchers.eq(Integer.class),anyLong()))
            .thenReturn(1);
        PurchaseController controller=new PurchaseController(jdbc);

        assertThatThrownBy(()->controller.deleteOrder(1,2))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("已有收货单记录");
        verify(jdbc,never()).update(org.mockito.ArgumentMatchers.startsWith("DELETE"),any(Object[].class));
    }

    @Test void approvedReceiptCannotBeVoidedAsDraft(){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.query(anyString(),any(RowMapper.class),anyLong()))
            .thenReturn(java.util.Collections.singletonList(new Object[]{"APPROVED",2,null}));
        PurchaseController controller=new PurchaseController(jdbc);

        assertThatThrownBy(()->controller.voidReceipt(1,new PurchaseController.VersionReason(2,"测试"),null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("只有未审核、未打印");
    }

    @Test void staleReceiptDraftCannotOverwriteNewerSavedVersion(){
        PurchaseController controller=controllerWithEditableReceipt("DRAFT",3,null,10L,20L);
        var request=new PurchaseController.ReceiptUpdateRequest(
            10L,20L,null,null,null,null,List.of(),2);

        assertThatThrownBy(()->controller.updateReceipt(1,request,null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("已被修改，请刷新后重试");
    }

    @Test void approvedReceiptCannotBeEditedAgain(){
        PurchaseController controller=controllerWithEditableReceipt("APPROVED",3,null,10L,20L);
        var request=new PurchaseController.ReceiptUpdateRequest(
            10L,20L,null,null,null,null,List.of(),3);

        assertThatThrownBy(()->controller.updateReceipt(1,request,null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("只有未打印的收货单草稿可以修改");
    }

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void receiptApprovalFailureRemainsTransactionalAndStopsBeforeDocumentApproval() throws Exception {
        assertThat(PurchaseController.class
            .getMethod("approveReceipt",long.class,PurchaseController.VersionOnly.class,Authentication.class)
            .isAnnotationPresent(Transactional.class)).isTrue();

        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.query(anyString(),any(RowMapper.class),anyLong())).thenAnswer(invocation->{
            String sql=invocation.getArgument(0);
            if(sql.startsWith("SELECT receipt_no")) {
                Map<String,Object> receipt=new LinkedHashMap<>();
                receipt.put("receiptNo","CS260822-001");
                receipt.put("status","DRAFT");
                receipt.put("version",0);
                receipt.put("purchaseOrderId",10L);
                return List.of(receipt);
            }
            if(sql.startsWith("SELECT status FROM purchase_order")) return List.of("DRAFT");
            if(sql.startsWith("SELECT id,sku_id")) {
                RowMapper mapper=invocation.getArgument(1);
                var rs=mock(java.sql.ResultSet.class);
                when(rs.getLong(1)).thenReturn(101L);
                when(rs.getLong(2)).thenReturn(1001L);
                when(rs.getString(3)).thenReturn("P001");
                when(rs.getBigDecimal(4)).thenReturn(new java.math.BigDecimal("2"));
                when(rs.getString(5)).thenReturn("ORDER_RECEIPT");
                return List.of(mapper.mapRow(rs,0));
            }
            throw new AssertionError("未处理的查询："+sql);
        });
        when(jdbc.queryForObject(anyString(),any(Class.class),any(Object[].class))).thenAnswer(invocation->{
            String sql=invocation.getArgument(0);
            if(sql.startsWith("SELECT total_stock")) return new java.math.BigDecimal("8");
            if(sql.startsWith("SELECT id FROM sys_user")) return 9L;
            throw new AssertionError("未处理的单值查询："+sql);
        });
        when(jdbc.update(org.mockito.ArgumentMatchers.contains("INSERT INTO inventory_movement"),any(Object[].class)))
            .thenThrow(new RuntimeException("模拟采购库存流水写入失败"));
        Authentication auth=mock(Authentication.class);
        when(auth.getName()).thenReturn("admin");
        PurchaseController controller=new PurchaseController(jdbc);

        assertThatThrownBy(()->controller.approveReceipt(1,new PurchaseController.VersionOnly(0),auth))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("模拟采购库存流水写入失败");

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE product_sku SET total_stock=?"),any(Object[].class));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO inventory_movement"),any(Object[].class));
        verify(jdbc,never()).update(org.mockito.ArgumentMatchers.contains("UPDATE purchase_receipt SET status='APPROVED'"),any(Object[].class));
    }

    private PurchaseController controllerWithReceipt(String status,int version){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        Map<String,Object> receipt=new LinkedHashMap<>();
        receipt.put("receiptNo","CS260814-001");
        receipt.put("status",status);
        receipt.put("version",version);
        when(jdbc.query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),anyLong()))
            .thenReturn(List.of(receipt));
        return new PurchaseController(jdbc);
    }

    private PurchaseController controllerWithPrintableReceipt(String status,int version,java.sql.Timestamp printedAt,int printCount){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        Map<String,Object> receipt=new LinkedHashMap<>();
        receipt.put("status",status);
        receipt.put("version",version);
        receipt.put("printedAt",printedAt);
        receipt.put("printCount",printCount);
        when(jdbc.query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),anyLong()))
            .thenReturn(List.of(receipt));
        return new PurchaseController(jdbc);
    }

    private PurchaseController controllerWithEditableReceipt(String status,int version,java.sql.Timestamp printedAt,long orderId,long supplierId){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        Map<String,Object> receipt=new LinkedHashMap<>();
        receipt.put("status",status);
        receipt.put("version",version);
        receipt.put("printedAt",printedAt);
        receipt.put("purchaseOrderId",orderId);
        receipt.put("supplierId",supplierId);
        when(jdbc.query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),anyLong()))
            .thenReturn(List.of(receipt));
        return new PurchaseController(jdbc);
    }
}
