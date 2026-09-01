<script setup lang="ts">
import {onMounted,reactive,ref} from 'vue'
import {ElMessage} from 'element-plus'
import {api} from '../api'
import BusinessDocumentDialog from '../components/BusinessDocumentDialog.vue'
import {localToday} from '../utils/date'

const today=localToday
const rows=ref<any[]>([]),lines=ref<any[]>([]),selected=ref<any>(),loading=ref(false),detailLoading=ref(false)
const documentDialog=ref(false),documentId=ref<number|null>(null)
const summary=reactive<any>({documentCount:0,validDocumentCount:0,voidedDocumentCount:0,effectiveExpenseAmount:0})
const query=reactive({dateFrom:today(),dateTo:today(),documentNo:'',handler:'',category:'',printStatus:'',auditStatus:'',voidStatus:'',sortBy:'date',sortDirection:'asc'})
const money=(v:any)=>`¥${Number(v||0).toFixed(2)}`
const dash=(v:any)=>v||'—'
const errorMessage=(e:any)=>e?.response?.data?.message||e?.message||'Search failed'
function handleSortChange({prop,order}:{prop:string,order:string}){query.sortBy=prop==='documentNo'?'documentNo':'date';query.sortDirection=order==='descending'?'desc':'asc';load()}
async function load(){loading.value=true;try{const {data}=await api.get('/ledgers/cashflow',{params:query});rows.value=data.data.items;Object.assign(summary,data.data.summary);selected.value=undefined;lines.value=[]}catch(e){ElMessage.error(errorMessage(e))}finally{loading.value=false}}
async function selectRow(row:any){selected.value=row;detailLoading.value=true;try{const {data}=await api.get(`/ledgers/cashflow/${row.id}/lines`);lines.value=data.data}catch(e){ElMessage.error(errorMessage(e));lines.value=[]}finally{detailLoading.value=false}}
function clear(){Object.assign(query,{dateFrom:today(),dateTo:today(),documentNo:'',handler:'',category:'',printStatus:'',auditStatus:'',voidStatus:'',sortBy:'date',sortDirection:'asc'});load()}
function rowClass({row}:any){return row.status==='VOIDED'?'voided-row':''}
function openDocument(row:any){documentId.value=row.id;documentDialog.value=true}
onMounted(load)
</script>

<template>
<div class="ledger-page">
  <div class="page-heading"><div><h1>Cashflow Ledger</h1><p>Search cash movements created by Other Expense documents. This page is read-only; edit documents under Finance - Other Expenses.</p></div></div>
  <el-card>
    <div class="filters">
      <el-date-picker v-model="query.dateFrom" type="date" value-format="YYYY-MM-DD"/><span>to</span><el-date-picker v-model="query.dateTo" type="date" value-format="YYYY-MM-DD"/>
      <el-input v-model="query.documentNo" clearable placeholder="Document No." @keyup.enter="load"/><el-input v-model="query.handler" clearable placeholder="Handler" @keyup.enter="load"/><el-input v-model="query.category" clearable placeholder="Expense Category Code / Name" @keyup.enter="load"/>
      <el-select v-model="query.printStatus" clearable placeholder="All Print Statuses"><el-option label="Printed" value="PRINTED"/><el-option label="Not Printed" value="UNPRINTED"/></el-select>
      <el-select v-model="query.auditStatus" clearable placeholder="All Approval Statuses"><el-option label="Approved" value="APPROVED"/><el-option label="Not Approved" value="UNAPPROVED"/></el-select>
      <el-select v-model="query.voidStatus" clearable placeholder="All Validity Statuses"><el-option label="Valid Documents" value="ACTIVE"/><el-option label="Voided" value="VOIDED"/></el-select>
      <el-button type="primary" @click="load">Search</el-button><el-button @click="clear">Clear Filters</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" height="46vh" highlight-current-row stripe class="document-ledger" :row-class-name="rowClass" :default-sort="{prop:'documentDate',order:'ascending'}" @sort-change="handleSortChange" @row-click="selectRow" @row-dblclick="openDocument">
      <el-table-column prop="documentNo" label="Document No." width="155" sortable="custom" :sort-orders="['ascending','descending']"/><el-table-column prop="documentDate" label="Date" width="110" sortable="custom" :sort-orders="['ascending','descending']"/><el-table-column prop="accountName" label="Account" width="90"/><el-table-column prop="handlerName" label="Handler" width="120"/>
      <el-table-column label="Income Amount" width="115" align="right"><template #default>—</template></el-table-column><el-table-column label="Expense Amount" width="125" align="right"><template #default="s">{{money(s.row.expenseAmount)}}</template></el-table-column>
      <el-table-column label="Print" width="90"><template #default="s"><el-tag :type="s.row.printedAt?'warning':'info'">{{s.row.printedAt?'Printed':'Not Printed'}}</el-tag></template></el-table-column>
      <el-table-column label="Approve" width="90"><template #default="s"><el-tag :type="s.row.status==='APPROVED'?'success':'info'">{{s.row.status==='APPROVED'?'Approved':'Not Approved'}}</el-tag></template></el-table-column>
      <el-table-column label="Status" width="90"><template #default="s"><el-tag :type="s.row.status==='VOIDED'?'danger':'success'">{{s.row.status==='VOIDED'?'Voided':'Valid'}}</el-tag></template></el-table-column>
      <el-table-column prop="createdByName" label="Created By" width="110"/><el-table-column prop="remark" label="Notes" min-width="180" show-overflow-tooltip/>
    </el-table>
    <div class="summary"><span>Document {{summary.documentCount}}  documents</span><span>Valid {{summary.validDocumentCount}}  documents</span><span>Void {{summary.voidedDocumentCount}}  documents</span><span>Total Valid Expenses {{money(summary.effectiveExpenseAmount)}}</span></div>
    <section class="inline-detail">
      <div class="detail-title"><strong>{{selected?`${selected.documentNo} Expense Lines`:'Click a document above to view details'}}</strong><span v-if="selected">Entry: {{dash(selected.createdByName)}} {{dash(selected.createdAt)}} Approve: {{dash(selected.approvedByName)}} {{dash(selected.approvedAt)}} Print: {{dash(selected.printedByName)}} {{dash(selected.printedAt)}}</span></div>
      <div v-if="selected?.status==='VOIDED'" class="void-info">Void: {{dash(selected.voidedByName)}} {{dash(selected.voidedAt)}} Void Reason: {{dash(selected.voidReason)}}</div>
      <el-table v-if="selected" :data="lines" v-loading="detailLoading" height="150" size="small" border><el-table-column prop="lineNo" label="Line No." width="70"/><el-table-column prop="categoryCode" label="Code" width="120"/><el-table-column prop="categoryName" label="Expense Category" width="220"/><el-table-column prop="summary" label="Summary" min-width="320"/><el-table-column label="Amount" width="130" align="right"><template #default="s">{{money(s.row.amount)}}</template></el-table-column></el-table>
      <el-empty v-else :image-size="42" description="No document selected"/>
    </section>
  </el-card><BusinessDocumentDialog v-model="documentDialog" type="other-expense" :id="documentId"/>
</div>
</template>

<style scoped>
.page-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px}.page-heading h1{margin:0}.page-heading p{margin:6px 0 0;color:#6b7280}.filters{display:flex;gap:9px;align-items:center;margin-bottom:12px;flex-wrap:wrap}.filters .el-input{width:190px}.filters .el-select{width:145px}.summary{display:flex;justify-content:flex-end;gap:20px;padding:10px 4px;color:#0f766e;font-weight:600;border-bottom:1px solid #dcdfe6}.inline-detail{padding-top:10px;min-height:180px}.detail-title{display:flex;justify-content:space-between;gap:20px;margin-bottom:9px;color:#606266}.void-info{margin:-2px 0 9px;color:#c45656}.ledger-page :deep(.voided-row td){color:#f56c6c!important;background:#fff1f0!important}.ledger-page :deep(.el-card__body){padding-bottom:10px}
.document-ledger :deep(.el-table__row){cursor:pointer}
</style>


