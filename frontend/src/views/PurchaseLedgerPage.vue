<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import BusinessDocumentDialog from '../components/BusinessDocumentDialog.vue'
import { localToday } from '../utils/date'

const today = localToday
const rows = ref<any[]>([])
const selected = ref<any>()
const loading = ref(false)
const documentDialog=ref(false),documentId=ref<number|null>(null)
const summary = reactive<any>({ rowCount: 0, documentCount: 0, receiptQuantity: 0, returnQuantity: 0, netQuantity: 0, netAmount: 0 })
const query = reactive({ dateFrom: today(), dateTo: today(), businessTypes: ['RECEIPT', 'RETURN'], keyword: '', supplier: '', receiptNo: '', sortBy: 'date', sortDirection: 'asc' })
const money = (value: any) => value === null || value === undefined ? '—' : `¥${Number(value).toFixed(2)}`
const qty = (value: any) => value === null || value === undefined ? '—' : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
const errorMessage = (error: any) => error?.response?.data?.message || error?.message || 'Search failed'

function handleSortChange({prop,order}:{prop:string,order:string}){query.sortBy=prop==='receiptNo'?'documentNo':'date';query.sortDirection=order==='descending'?'desc':'asc';load()}

async function load() {
  if (!query.businessTypes.length) {
    rows.value = []
    selected.value = undefined
    Object.assign(summary, { rowCount: 0, documentCount: 0, receiptQuantity: 0, returnQuantity: 0, netQuantity: 0, netAmount: 0 })
    return
  }
  loading.value = true
  try {
    const { businessTypes, ...params } = query
    const { data } = await api.get('/ledgers/purchases', { params: { ...params, businessType: businessTypes.join(',') } })
    rows.value = data.data.items
    Object.assign(summary, data.data.summary)
    selected.value = rows.value[0]
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

function clear() {
  Object.assign(query, { dateFrom: today(), dateTo: today(), businessTypes: ['RECEIPT', 'RETURN'], keyword: '', supplier: '', receiptNo: '', sortBy: 'date', sortDirection: 'asc' })
  load()
}
function openDocument(row:any){documentId.value=row.documentId;documentDialog.value=true}

onMounted(load)
</script>

<template>
  <div class="ledger-page">
    <div class="page-heading">
      <div><h1>Purchase Ledger</h1><p>Read-only view of approved purchase receipt lines. Drafts do not appear in the ledger.</p></div>
    </div>
    <el-card>
      <div class="filters">
        <el-date-picker v-model="query.dateFrom" type="date" value-format="YYYY-MM-DD"/><span>to</span>
        <el-date-picker v-model="query.dateTo" type="date" value-format="YYYY-MM-DD"/>
        <el-checkbox-group v-model="query.businessTypes" class="type-checks">
          <el-checkbox value="RECEIPT">Purchase Receipt</el-checkbox>
          <el-checkbox value="RETURN">Purchase Reduction</el-checkbox>
        </el-checkbox-group>
        <el-input v-model="query.keyword" clearable placeholder="Product Code / Product / Specification" @keyup.enter="load"/>
        <el-input v-model="query.supplier" clearable placeholder="Supplier Code / Name" @keyup.enter="load"/>
        <el-input v-model="query.receiptNo" clearable placeholder="Receipt No. / Purchase Order No." @keyup.enter="load"/>
        <el-button type="primary" @click="load">Search</el-button><el-button @click="clear">Clear Filters</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" height="46vh" highlight-current-row stripe class="document-ledger" :default-sort="{prop:'receiptDate',order:'ascending'}" @sort-change="handleSortChange" @row-click="selected=$event" @row-dblclick="openDocument">
        <el-table-column prop="receiptNo" label="Receipt No." width="155" sortable="custom" :sort-orders="['ascending','descending']"/>
        <el-table-column prop="receiptDate" label="Date" width="110" sortable="custom" :sort-orders="['ascending','descending']"/>
        <el-table-column prop="businessTypeName" label="Type" width="100"/>
        <el-table-column prop="skuCode" label="Code" width="120"/>
        <el-table-column label="Product / Specification" min-width="260"><template #default="scope">{{scope.row.productName}} {{scope.row.specification}} {{scope.row.color}}</template></el-table-column>
        <el-table-column prop="unit" label="Unit" width="75"/>
        <el-table-column label="Quantity" width="105" align="right"><template #default="scope"><span :class="{negative:Number(scope.row.quantity)<0}">{{qty(scope.row.quantity)}}</span></template></el-table-column>
        <el-table-column label="Unit Price" width="105" align="right"><template #default="scope">{{money(scope.row.unitPrice)}}</template></el-table-column>
        <el-table-column label="Amount" width="120" align="right"><template #default="scope"><span :class="{negative:Number(scope.row.amount)<0}">{{money(scope.row.amount)}}</span></template></el-table-column>
        <el-table-column prop="supplierCode" label="Supplier Code" width="115"/>
        <el-table-column prop="supplierName" label="Supplier Name" min-width="180"/>
        <el-table-column prop="orderNo" label="Purchase Order No." width="155"/>
      </el-table>
      <div class="summary">
        <span>Line Items {{summary.rowCount}}  rows</span><span>Receipt {{summary.documentCount}}  documents</span>
        <span>Received Quantity {{qty(summary.receiptQuantity)}}</span><span>Return Quantity {{qty(summary.returnQuantity)}}</span>
        <span>Net Quantity {{qty(summary.netQuantity)}}</span><span>Net Amount {{money(summary.netAmount)}}</span>
      </div>
      <section class="inline-detail">
        <div class="detail-title"><strong>{{selected?`${selected.receiptNo} · ${selected.skuCode} Purchase Details`:'Click a movement above to view details'}}</strong></div>
        <div v-if="selected" class="detail-grid">
          <span>Product: {{selected.productName}} {{selected.specification}} {{selected.color}}</span><span>Reference Price: {{money(selected.referencePrice)}}</span><span>Warehouse: {{selected.warehouseName}}</span>
          <span>Supplier: {{selected.supplierCode}} {{selected.supplierName}}</span><span>Payment Method: {{selected.settlementMethod}}</span><span>Approved By: {{selected.approvedByName||'—'}}</span>
          <span>Purchase Order: {{selected.orderNo||'—'}}</span><span>Approved At: {{selected.approvedAt||'—'}}</span><span>Line No.: {{selected.lineNo}}</span>
          <span class="wide">Line Notes: {{selected.lineRemark||'—'}}; Document Notes: {{selected.receiptRemark||'—'}}</span>
        </div>
        <el-empty v-else :image-size="48" description="No purchase movement selected"/>
      </section>
    </el-card><BusinessDocumentDialog v-model="documentDialog" type="purchase-receipt" :id="documentId"/>
  </div>
</template>

<style scoped>
.page-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px}.page-heading h1{margin:0}.page-heading p{margin:6px 0 0;color:#6b7280}.filters{display:flex;gap:9px;align-items:center;margin-bottom:12px;flex-wrap:wrap}.filters .el-input{width:205px}.type-checks{display:flex;gap:10px;padding:6px 10px;border:1px solid #dcdfe6;border-radius:4px}.type-checks :deep(.el-checkbox){margin-right:0}.summary{display:flex;justify-content:flex-end;gap:20px;padding:10px 4px;color:#0f766e;font-weight:600;border-bottom:1px solid #dcdfe6}.inline-detail{padding-top:10px;min-height:138px}.detail-title{margin-bottom:10px}.detail-grid{display:grid;grid-template-columns:2fr 1fr 1fr;gap:10px 20px;color:#606266;max-height:118px;overflow:auto}.detail-grid .wide{grid-column:span 3}.negative{color:#f56c6c}.ledger-page :deep(.el-card__body){padding-bottom:10px}
.document-ledger :deep(.el-table__row){cursor:pointer}
</style>


