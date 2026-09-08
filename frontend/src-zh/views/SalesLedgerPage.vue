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
const qty=(v:any)=>v===null||v===undefined?'—':Number(v).toLocaleString('zh-CN',{maximumFractionDigits:4})
const errorMessage=(e:any)=>e?.response?.data?.message||e?.message||'查询失败'

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
  <div class="page-heading"><div><h1>货品销售流水账</h1><p>只读显示已打印销售单和已审核销售退货单；退货以负数量、负金额显示</p></div></div>
  <el-card>
    <div class="filters">
      <el-date-picker v-model="query.dateFrom" type="date" value-format="YYYY-MM-DD"/><span>至</span><el-date-picker v-model="query.dateTo" type="date" value-format="YYYY-MM-DD"/>
      <el-checkbox-group v-model="query.businessTypes"><el-checkbox value="SALE">销售单</el-checkbox><el-checkbox value="RETURN">退货单</el-checkbox></el-checkbox-group>
      <el-input v-model="query.keyword" clearable placeholder="货品编号 / 品名规格" @keyup.enter="load"/><el-input v-model="query.customer" clearable placeholder="客户编号 / 名称" @keyup.enter="load"/>
      <el-input v-model="query.orderNo" clearable placeholder="销售单或退货单号" @keyup.enter="load"/><el-input v-model="query.salesperson" clearable placeholder="业务员" @keyup.enter="load"/>
      <el-button type="primary" @click="load">查询</el-button><el-button @click="clear">清空条件</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" height="46vh" highlight-current-row stripe class="document-ledger" :default-sort="{prop:'orderDate',order:'ascending'}" @sort-change="handleSortChange" @row-click="selected=$event" @row-dblclick="openDocument">
      <el-table-column prop="orderNo" label="单据号码" width="155" sortable="custom" :sort-orders="['ascending','descending']"/><el-table-column prop="orderDate" label="日期" width="110" sortable="custom" :sort-orders="['ascending','descending']"/><el-table-column prop="lineTypeName" label="类型" width="100"/>
      <el-table-column prop="skuCode" label="编号" width="120"/><el-table-column label="品名规格" min-width="260"><template #default="s">{{s.row.productName}} {{s.row.specification}} {{s.row.color}}</template></el-table-column>
      <el-table-column prop="unit" label="单位" width="75"/><el-table-column label="数量" width="105" align="right"><template #default="s"><span :class="{negative:Number(s.row.quantity)<0}">{{qty(s.row.quantity)}}</span></template></el-table-column>
      <el-table-column label="单价" width="105" align="right"><template #default="s">{{money(s.row.unitPrice)}}</template></el-table-column><el-table-column label="参考价" width="105" align="right"><template #default="s">{{money(s.row.referencePrice)}}</template></el-table-column>
      <el-table-column label="金额" width="120" align="right"><template #default="s"><span :class="{negative:Number(s.row.amount)<0}">{{money(s.row.amount)}}</span></template></el-table-column>
      <el-table-column prop="customerCode" label="客户编号" width="115"/><el-table-column prop="customerName" label="客户名称" min-width="180"/><el-table-column prop="salespersonName" label="业务员" width="110"/>
    </el-table>
    <div class="summary"><span>明细 {{summary.rowCount}} 行</span><span>单据 {{summary.documentCount}} 张</span><span>销售数量 {{qty(summary.salesQuantity)}}</span><span>退货数量 {{qty(summary.returnQuantity)}}</span><span>净数量 {{qty(summary.netQuantity)}}</span><span>净金额 {{money(summary.netAmount)}}</span></div>
    <section class="inline-detail"><div class="detail-title"><strong>{{selected?`${selected.orderNo} · ${selected.skuCode} 流水详情`:'单击上方流水查看详情'}}</strong></div>
      <div v-if="selected" class="detail-grid"><span>货品：{{selected.productName}} {{selected.specification}} {{selected.color}}</span><span>客户：{{selected.customerCode}} {{selected.customerName}}</span><span>业务员：{{selected.salespersonName||'—'}}</span><span>结算方式：{{selected.settlementMethod}}</span><span>打印人：{{selected.printedByName||'—'}}</span><span>打印时间：{{selected.printedAt||'—'}}</span><span class="wide">明细备注：{{selected.lineRemark||'—'}}；单据备注：{{selected.orderRemark||'—'}}</span></div>
      <el-empty v-else :image-size="48" description="暂无销售流水"/>
    </section>
  </el-card><BusinessDocumentDialog v-model="documentDialog" :type="documentType" :id="documentId"/>
</div></template>

<style scoped>
.page-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px}.page-heading h1{margin:0}.page-heading p{margin:6px 0 0;color:#6b7280}.filters{display:flex;gap:9px;align-items:center;margin-bottom:12px;flex-wrap:wrap}.filters .el-input{width:190px}.summary{display:flex;justify-content:flex-end;gap:20px;padding:10px 4px;color:#0f766e;font-weight:600;border-bottom:1px solid #dcdfe6}.inline-detail{padding-top:10px;min-height:138px}.detail-title{margin-bottom:10px}.detail-grid{display:grid;grid-template-columns:2fr 1fr 1fr;gap:10px 20px;color:#606266;max-height:118px;overflow:auto}.detail-grid .wide{grid-column:span 3}.negative{color:#f56c6c}.ledger-page :deep(.el-card__body){padding-bottom:10px}
.document-ledger :deep(.el-table__row){cursor:pointer}
</style>
