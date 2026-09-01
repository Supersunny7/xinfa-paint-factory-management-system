<script setup lang="ts">
import {onMounted,reactive,ref} from 'vue'
import {ElMessage} from 'element-plus'
import {api} from '../api'
import BusinessDocumentDialog from '../components/BusinessDocumentDialog.vue'
import {localToday} from '../utils/date'

const today=localToday
const rows=ref<any[]>([]),selected=ref<any>(),loading=ref(false)
const documentDialog=ref(false),documentType=ref(''),documentId=ref<number|null>(null)
const summary=reactive<any>({rowCount:0,documentCount:0,salesQuantity:0,returnQuantity:0,netQuantity:0,netAmount:0})
const query=reactive({dateFrom:today(),dateTo:today(),businessTypes:['SALE','RETURN'],keyword:'',customer:'',orderNo:'',salesperson:'',sortBy:'date',sortDirection:'asc'})
const money=(v:any)=>v===null||v===undefined?'—':`¥${Number(v).toFixed(2)}`
const qty=(v:any)=>v===null||v===undefined?'—':Number(v).toLocaleString('en-US',{maximumFractionDigits:4})
const errorMessage=(e:any)=>e?.response?.data?.message||e?.message||'Search failed'

function handleSortChange({prop,order}:{prop:string,order:string}){query.sortBy=prop==='orderNo'?'documentNo':'date';query.sortDirection=order==='descending'?'desc':'asc';load()}
async function load(){
  loading.value=true
  try{
    const {businessTypes,...rest}=query
    const {data}=await api.get('/ledgers/sales',{params:{...rest,businessType:businessTypes.join(',')}})
    rows.value=data.data.items;Object.assign(summary,data.data.summary);selected.value=rows.value[0]
  }catch(e){ElMessage.error(errorMessage(e))}finally{loading.value=false}
}
function clear(){Object.assign(query,{dateFrom:today(),dateTo:today(),businessTypes:['SALE','RETURN'],keyword:'',customer:'',orderNo:'',salesperson:'',sortBy:'date',sortDirection:'asc'});load()}
function openDocument(row:any){documentType.value=row.businessType==='RETURN'?'sales-return':'sales-order';documentId.value=row.documentId;documentDialog.value=true}
onMounted(load)
</script>

<template><div class="ledger-page">
  <div class="page-heading"><div><h1>Sales Ledger</h1><p>Read-only printed sales orders and approved sales returns; returns appear as negative quantities and amounts</p></div></div>
  <el-card>
    <div class="filters">
      <el-date-picker v-model="query.dateFrom" type="date" value-format="YYYY-MM-DD"/><span>to</span><el-date-picker v-model="query.dateTo" type="date" value-format="YYYY-MM-DD"/>
      <el-checkbox-group v-model="query.businessTypes"><el-checkbox value="SALE">Sales Order</el-checkbox><el-checkbox value="RETURN">Return Document</el-checkbox></el-checkbox-group>
      <el-input v-model="query.keyword" clearable placeholder="Product Code / Product / Specification" @keyup.enter="load"/><el-input v-model="query.customer" clearable placeholder="Customer Code / Name" @keyup.enter="load"/>
      <el-input v-model="query.orderNo" clearable placeholder="Sales order or return number" @keyup.enter="load"/><el-input v-model="query.salesperson" clearable placeholder="Salesperson" @keyup.enter="load"/>
      <el-button type="primary" @click="load">Search</el-button><el-button @click="clear">Clear Filters</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" height="46vh" highlight-current-row stripe class="document-ledger" :default-sort="{prop:'orderDate',order:'ascending'}" @sort-change="handleSortChange" @row-click="selected=$event" @row-dblclick="openDocument">
      <el-table-column prop="orderNo" label="Document No." width="155" sortable="custom" :sort-orders="['ascending','descending']"/><el-table-column prop="orderDate" label="Date" width="110" sortable="custom" :sort-orders="['ascending','descending']"/><el-table-column prop="lineTypeName" label="Type" width="100"/>
      <el-table-column prop="skuCode" label="Code" width="120"/><el-table-column label="Product / Specification" min-width="260"><template #default="s">{{s.row.productName}} {{s.row.specification}} {{s.row.color}}</template></el-table-column>
      <el-table-column prop="unit" label="Unit" width="75"/><el-table-column label="Quantity" width="105" align="right"><template #default="s"><span :class="{negative:Number(s.row.quantity)<0}">{{qty(s.row.quantity)}}</span></template></el-table-column>
      <el-table-column label="Unit Price" width="105" align="right"><template #default="s">{{money(s.row.unitPrice)}}</template></el-table-column><el-table-column label="Reference Price" width="105" align="right"><template #default="s">{{money(s.row.referencePrice)}}</template></el-table-column>
      <el-table-column label="Amount" width="120" align="right"><template #default="s"><span :class="{negative:Number(s.row.amount)<0}">{{money(s.row.amount)}}</span></template></el-table-column>
      <el-table-column prop="customerCode" label="Customer Code" width="115"/><el-table-column prop="customerName" label="Customer Name" min-width="180"/><el-table-column prop="salespersonName" label="Salesperson" width="110"/>
    </el-table>
    <div class="summary"><span>Line Items: {{summary.rowCount}}</span><span>Documents: {{summary.documentCount}}</span><span>Sales Quantity: {{qty(summary.salesQuantity)}}</span><span>Return Quantity: {{qty(summary.returnQuantity)}}</span><span>Net Quantity: {{qty(summary.netQuantity)}}</span><span>Net Amount: {{money(summary.netAmount)}}</span></div>
    <section class="inline-detail"><div class="detail-title"><strong>{{selected?`${selected.orderNo} · ${selected.skuCode} Movement Details`:'Click a movement above to view details'}}</strong></div>
      <div v-if="selected" class="detail-grid"><span>Product: {{selected.productName}} {{selected.specification}} {{selected.color}}</span><span>Customer: {{selected.customerCode}} {{selected.customerName}}</span><span>Salesperson: {{selected.salespersonName||'—'}}</span><span>Payment Method: {{selected.settlementMethod}}</span><span>Printed By: {{selected.printedByName||'—'}}</span><span>Printed At: {{selected.printedAt||'—'}}</span><span class="wide">Line Notes: {{selected.lineRemark||'—'}}; Document Notes: {{selected.orderRemark||'—'}}</span></div>
      <el-empty v-else :image-size="48" description="No sales movement selected"/>
    </section>
  </el-card><BusinessDocumentDialog v-model="documentDialog" :type="documentType" :id="documentId"/>
</div></template>

<style scoped>
.page-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px}.page-heading h1{margin:0}.page-heading p{margin:6px 0 0;color:#6b7280}.filters{display:flex;gap:9px;align-items:center;margin-bottom:12px;flex-wrap:wrap}.filters .el-input{width:190px}.summary{display:flex;justify-content:flex-end;gap:20px;padding:10px 4px;color:#0f766e;font-weight:600;border-bottom:1px solid #dcdfe6}.inline-detail{padding-top:10px;min-height:138px}.detail-title{margin-bottom:10px}.detail-grid{display:grid;grid-template-columns:2fr 1fr 1fr;gap:10px 20px;color:#606266;max-height:118px;overflow:auto}.detail-grid .wide{grid-column:span 3}.negative{color:#f56c6c}.ledger-page :deep(.el-card__body){padding-bottom:10px}
.document-ledger :deep(.el-table__row){cursor:pointer}
</style>


