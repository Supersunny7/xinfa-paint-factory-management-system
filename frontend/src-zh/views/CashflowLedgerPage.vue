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
const errorMessage=(e:any)=>e?.response?.data?.message||e?.message||'查询失败'
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
  <div class="page-heading"><div><h1>收支流水账</h1><p>查询其它支出单形成的资金流水；本页只读，单据修改请到“财务管理－其它支出”</p></div></div>
  <el-card>
    <div class="filters">
      <el-date-picker v-model="query.dateFrom" type="date" value-format="YYYY-MM-DD"/><span>至</span><el-date-picker v-model="query.dateTo" type="date" value-format="YYYY-MM-DD"/>
      <el-input v-model="query.documentNo" clearable placeholder="单据号码" @keyup.enter="load"/><el-input v-model="query.handler" clearable placeholder="经手人" @keyup.enter="load"/><el-input v-model="query.category" clearable placeholder="收支类别编号 / 名称" @keyup.enter="load"/>
      <el-select v-model="query.printStatus" clearable placeholder="全部打印状态"><el-option label="已打印" value="PRINTED"/><el-option label="未打印" value="UNPRINTED"/></el-select>
      <el-select v-model="query.auditStatus" clearable placeholder="全部审核状态"><el-option label="已审核" value="APPROVED"/><el-option label="未审核" value="UNAPPROVED"/></el-select>
      <el-select v-model="query.voidStatus" clearable placeholder="全部有效状态"><el-option label="有效单" value="ACTIVE"/><el-option label="已作废" value="VOIDED"/></el-select>
      <el-button type="primary" @click="load">查询</el-button><el-button @click="clear">清空条件</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" height="46vh" highlight-current-row stripe class="document-ledger" :row-class-name="rowClass" :default-sort="{prop:'documentDate',order:'ascending'}" @sort-change="handleSortChange" @row-click="selectRow" @row-dblclick="openDocument">
      <el-table-column prop="documentNo" label="单据号码" width="155" sortable="custom" :sort-orders="['ascending','descending']"/><el-table-column prop="documentDate" label="日期" width="110" sortable="custom" :sort-orders="['ascending','descending']"/><el-table-column prop="accountName" label="账户" width="90"/><el-table-column prop="handlerName" label="经手人" width="120"/>
      <el-table-column label="收入金额" width="115" align="right"><template #default>—</template></el-table-column><el-table-column label="支出金额" width="125" align="right"><template #default="s">{{money(s.row.expenseAmount)}}</template></el-table-column>
      <el-table-column label="打印" width="90"><template #default="s"><el-tag :type="s.row.printedAt?'warning':'info'">{{s.row.printedAt?'已打印':'未打印'}}</el-tag></template></el-table-column>
      <el-table-column label="审核" width="90"><template #default="s"><el-tag :type="s.row.status==='APPROVED'?'success':'info'">{{s.row.status==='APPROVED'?'已审核':'未审核'}}</el-tag></template></el-table-column>
      <el-table-column label="状态" width="90"><template #default="s"><el-tag :type="s.row.status==='VOIDED'?'danger':'success'">{{s.row.status==='VOIDED'?'已作废':'有效'}}</el-tag></template></el-table-column>
      <el-table-column prop="createdByName" label="录入人" width="110"/><el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip/>
    </el-table>
    <div class="summary"><span>单据 {{summary.documentCount}} 张</span><span>有效 {{summary.validDocumentCount}} 张</span><span>作废 {{summary.voidedDocumentCount}} 张</span><span>有效支出合计 {{money(summary.effectiveExpenseAmount)}}</span></div>
    <section class="inline-detail">
      <div class="detail-title"><strong>{{selected?`${selected.documentNo} 支出明细`:'单击上方单据查看明细'}}</strong><span v-if="selected">录入：{{dash(selected.createdByName)}} {{dash(selected.createdAt)}}　审核：{{dash(selected.approvedByName)}} {{dash(selected.approvedAt)}}　打印：{{dash(selected.printedByName)}} {{dash(selected.printedAt)}}</span></div>
      <div v-if="selected?.status==='VOIDED'" class="void-info">作废：{{dash(selected.voidedByName)}} {{dash(selected.voidedAt)}}　作废原因：{{dash(selected.voidReason)}}</div>
      <el-table v-if="selected" :data="lines" v-loading="detailLoading" height="150" size="small" border><el-table-column prop="lineNo" label="行号" width="70"/><el-table-column prop="categoryCode" label="编号" width="120"/><el-table-column prop="categoryName" label="收支类别" width="220"/><el-table-column prop="summary" label="摘要" min-width="320"/><el-table-column label="金额" width="130" align="right"><template #default="s">{{money(s.row.amount)}}</template></el-table-column></el-table>
      <el-empty v-else :image-size="42" description="暂未选择单据"/>
    </section>
  </el-card><BusinessDocumentDialog v-model="documentDialog" type="other-expense" :id="documentId"/>
</div>
</template>

<style scoped>
.page-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px}.page-heading h1{margin:0}.page-heading p{margin:6px 0 0;color:#6b7280}.filters{display:flex;gap:9px;align-items:center;margin-bottom:12px;flex-wrap:wrap}.filters .el-input{width:190px}.filters .el-select{width:145px}.summary{display:flex;justify-content:flex-end;gap:20px;padding:10px 4px;color:#0f766e;font-weight:600;border-bottom:1px solid #dcdfe6}.inline-detail{padding-top:10px;min-height:180px}.detail-title{display:flex;justify-content:space-between;gap:20px;margin-bottom:9px;color:#606266}.void-info{margin:-2px 0 9px;color:#c45656}.ledger-page :deep(.voided-row td){color:#f56c6c!important;background:#fff1f0!important}.ledger-page :deep(.el-card__body){padding-bottom:10px}
.document-ledger :deep(.el-table__row){cursor:pointer}
</style>
