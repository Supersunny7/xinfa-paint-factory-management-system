<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {ElMessage} from 'element-plus'
import {api} from '../api'

const props=defineProps<{modelValue:boolean,type:string,id:number|null}>()
const emit=defineEmits<{(e:'update:modelValue',value:boolean):void}>()
const loading=ref(false),detail=ref<any>()
const config=computed(()=>({
  'sales-order':{title:'销售单',path:'/sales-orders'},
  'sales-return':{title:'销售退货单',path:'/sales-returns'},
  'return-warehouse':{title:'退货入仓单',path:'/return-warehouses'},
  'purchase-receipt':{title:'采购收货单',path:'/purchases/receipts'},
  'other-expense':{title:'其它支出单',path:'/other-expenses'}
} as any)[props.type])
const title=computed(()=>`${config.value?.title||'业务单据'} ${documentNo.value||''}`)
const documentNo=computed(()=>({
  'sales-order':detail.value?.orderNo,
  'sales-return':detail.value?.returnNo,
  'return-warehouse':detail.value?.warehouseNo,
  'purchase-receipt':detail.value?.receiptNo,
  'other-expense':detail.value?.expenseNo
} as any)[props.type]||detail.value?.orderNo||detail.value?.returnNo||detail.value?.warehouseNo||detail.value?.receiptNo||detail.value?.expenseNo)
const documentDate=computed(()=>({
  'sales-order':detail.value?.orderDate,
  'sales-return':detail.value?.returnDate,
  'return-warehouse':detail.value?.warehouseDate,
  'purchase-receipt':detail.value?.receiptDate,
  'other-expense':detail.value?.expenseDate
} as any)[props.type]||detail.value?.orderDate||detail.value?.returnDate||detail.value?.warehouseDate||detail.value?.receiptDate||detail.value?.expenseDate)
const items=computed(()=>detail.value?.items||detail.value?.returns||[])
const money=(v:any)=>v===null||v===undefined?'—':Number(v).toFixed(2)
const status=(v:string)=>({DRAFT:'草稿',APPROVED:'已审核',VOIDED:'已作废'} as any)[v]||v||'—'
async function load(){if(!props.modelValue||!props.id||!config.value)return;loading.value=true;detail.value=undefined;try{const{data}=await api.get(`${config.value.path}/${props.id}`);detail.value=data.data}catch(e:any){ElMessage.error(e.response?.data?.message||'单据读取失败')}finally{loading.value=false}}
watch(()=>[props.modelValue,props.type,props.id],load,{immediate:true})
</script>

<template>
  <el-dialog :model-value="modelValue" :title="title" width="820px" append-to-body @update:model-value="emit('update:modelValue',$event)">
    <div v-loading="loading">
      <el-descriptions v-if="detail" :column="3" border size="small">
        <el-descriptions-item label="单据号码">{{documentNo}}</el-descriptions-item><el-descriptions-item label="日期">{{documentDate}}</el-descriptions-item><el-descriptions-item label="状态">{{status(detail.status)}}</el-descriptions-item>
        <el-descriptions-item label="客户/供应商" :span="2">{{detail.customerCode||detail.supplierCode||''}} {{detail.customerName||detail.supplierName||''}}</el-descriptions-item><el-descriptions-item label="业务员/经手人">{{detail.salespersonName||detail.handlerName||'—'}}</el-descriptions-item>
        <el-descriptions-item label="结算方式">{{detail.settlementMethod||detail.accountName||'—'}}</el-descriptions-item><el-descriptions-item label="总金额">¥{{money(detail.totalAmount)}}</el-descriptions-item><el-descriptions-item label="备注">{{detail.remark||'—'}}</el-descriptions-item>
      </el-descriptions>
      <el-table v-if="detail" :data="items" border size="small" max-height="330" class="document-items">
        <el-table-column type="index" label="行" width="55"/>
        <el-table-column label="单据号码" width="145"><template #default="s">{{s.row.returnNo||'—'}}</template></el-table-column>
        <el-table-column label="编号" width="105"><template #default="s">{{s.row.skuCode||s.row.categoryCode||'—'}}</template></el-table-column>
        <el-table-column label="品名规格/摘要" min-width="220"><template #default="s">{{s.row.productName||s.row.categoryName||s.row.summary||s.row.customerName||'—'}} {{s.row.specification||''}}</template></el-table-column>
        <el-table-column label="数量" width="85"><template #default="s">{{s.row.quantity??'—'}}</template></el-table-column>
        <el-table-column label="单位" width="70"><template #default="s">{{s.row.salesUnit||s.row.unit||'—'}}</template></el-table-column>
        <el-table-column label="单价" width="90"><template #default="s">{{money(s.row.unitPrice)}}</template></el-table-column>
        <el-table-column label="金额" width="105"><template #default="s">{{money(s.row.lineAmount??s.row.amount??s.row.totalAmount)}}</template></el-table-column>
        <el-table-column label="备注" min-width="110"><template #default="s">{{s.row.remark||s.row.lineRemark||'—'}}</template></el-table-column>
      </el-table>
      <div v-if="detail" class="audit">录入：{{detail.createdByName||'—'}} {{detail.createdAt||'—'}}　审核：{{detail.approvedByName||'—'}} {{detail.approvedAt||'—'}}　打印：{{detail.printedByName||'—'}} {{detail.printedAt||'—'}}</div>
      <div v-if="detail?.status==='VOIDED'" class="void-audit">作废：{{detail.voidedByName||'—'}} {{detail.voidedAt||'—'}}　作废原因：{{detail.voidReason||'—'}}</div>
    </div>
    <template #footer><el-button @click="emit('update:modelValue',false)">关闭</el-button></template>
  </el-dialog>
</template>

<style scoped>.document-items{margin-top:12px}.audit{margin-top:10px;color:#667085;font-size:12px}.void-audit{margin-top:6px;color:#c45656;font-size:12px}</style>
