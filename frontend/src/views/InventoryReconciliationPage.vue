<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api } from '../api'

const loading=ref(false),rows=ref<any[]>([]),total=ref(0),summary=ref<any>({})
const query=reactive({keyword:'',alertType:'ALL',page:1,pageSize:20})
const movementDialog=ref(false),movementLoading=ref(false),selected=ref<any>(null),movements=ref<any[]>([]),movementTotal=ref(0),movementPage=ref(1)
const movementLabels:Record<string,string>={INBOUND:'Stock In',OUTBOUND:'Outbound',ADJUSTMENT:'Stock Count Adjustment',REVERSAL:'Reverse Dispatch'}

async function load(){loading.value=true;try{const [list,stats]=await Promise.all([api.get('/inventory-reconciliation',{params:query}),api.get('/inventory-reconciliation/summary')]);rows.value=list.data.data.items;total.value=list.data.data.total;summary.value=stats.data.data}finally{loading.value=false}}
function search(){query.page=1;load()}
function inventoryText(s:string){return({NORMAL:'Normal',LOW_STOCK:'Insufficient Stock',OUT_OF_STOCK:'Out of Stock',NEGATIVE_STOCK:'Negative Stock'} as any)[s]||s}
function inventoryTag(s:string){return s==='NORMAL'?'success':s==='LOW_STOCK'?'warning':'danger'}
function reconciliationText(s:string){return({BALANCED:'Reconciled',MISMATCH:'Reconciliation Error',NO_MOVEMENT:'No movement selected'} as any)[s]||s}
function reconciliationTag(s:string){return s==='BALANCED'?'success':s==='MISMATCH'?'danger':'info'}
function signed(v:any){const n=Number(v);return `${n>0?'+':''}${n}`}
function formatTime(value:any){return value?String(value).replace('T',' ').slice(0,19):'-'}
async function openMovements(row:any){selected.value=row;movementPage.value=1;movementDialog.value=true;await loadMovements()}
async function loadMovements(){if(!selected.value)return;movementLoading.value=true;try{const{data}=await api.get(`/products/${selected.value.productId}/inventory-movements`,{params:{page:movementPage.value,pageSize:10}});movements.value=data.data.items;movementTotal.value=data.data.total}finally{movementLoading.value=false}}
onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading"><div><h1>Inventory Reconciliation & Alerts</h1><p>Reconcile current stock with inventory movements to identify shortages and inconsistencies</p></div><el-button type="primary" @click="load">Refresh Data</el-button></div>
    <el-row :gutter="16" class="summary-row">
      <el-col :span="4"><el-card shadow="never"><el-statistic title="Active Products" :value="summary.enabledProductCount||0"/></el-card></el-col>
      <el-col :span="5"><el-card shadow="never"><el-statistic title="Insufficient Stock" :value="summary.lowStockCount||0"><template #suffix> items</template></el-statistic></el-card></el-col>
      <el-col :span="5"><el-card shadow="never"><el-statistic title="Out of Stock" :value="summary.outOfStockCount||0"><template #suffix> items</template></el-statistic></el-card></el-col>
      <el-col :span="5"><el-card shadow="never"><el-statistic title="Reconciliation Error" :value="summary.mismatchCount||0"><template #suffix> items</template></el-statistic></el-card></el-col>
      <el-col :span="5"><el-card shadow="never"><el-statistic title="No movement selected" :value="summary.noMovementCount||0"><template #suffix> items</template></el-statistic></el-card></el-col>
    </el-row>
    <el-card>
      <div class="toolbar"><el-input v-model="query.keyword" clearable placeholder="Product Code / Product Name / Specification" @keyup.enter="search"/><el-select v-model="query.alertType" style="width:170px" @change="search"><el-option label="All Products" value="ALL"/><el-option label="Negative Stock" value="NEGATIVE_STOCK"/><el-option label="Insufficient Stock" value="LOW_STOCK"/><el-option label="Out of Stock" value="OUT_OF_STOCK"/><el-option label="Reconciliation Error" value="MISMATCH"/><el-option label="No movement selected" value="NO_MOVEMENT"/></el-select><el-button type="primary" @click="search">Search</el-button></div>
      <el-alert v-if="Number(summary.mismatchCount)>0" title="Reconciliation error: current stock differs from the latest movement balance. Investigate first." type="error" show-icon :closable="false" class="warning-alert"/>
      <el-table :data="rows" v-loading="loading" stripe><el-table-column prop="skuCode" label="Code" width="110"/><el-table-column prop="productName" label="Product Name" min-width="160"/><el-table-column prop="specification" label="Specification" width="120"/><el-table-column prop="salesUnit" label="Unit" width="65"/><el-table-column label="Current Stock" width="100"><template #default="s"><span :class="Number(s.row.currentStock)<0?'negative-stock':''">{{s.row.currentStock}}</span></template></el-table-column><el-table-column prop="stockLowerLimit" label="Minimum Stock" width="100"/><el-table-column prop="shortageQuantity" label="Shortage Quantity" width="100"/><el-table-column label="Stock Alerts" width="105"><template #default="s"><el-tag :type="inventoryTag(s.row.inventoryStatus)">{{inventoryText(s.row.inventoryStatus)}}</el-tag></template></el-table-column><el-table-column label="Movement Balance" width="100"><template #default="s">{{s.row.ledgerBalance??'-'}}</template></el-table-column><el-table-column label="Difference" width="85"><template #default="s">{{s.row.variance??'-'}}</template></el-table-column><el-table-column label="Reconciliation Result" width="105"><template #default="s"><el-tag :type="reconciliationTag(s.row.reconciliationStatus)">{{reconciliationText(s.row.reconciliationStatus)}}</el-tag></template></el-table-column><el-table-column label="Actions" width="105" fixed="right"><template #default="s"><el-button link type="primary" @click="openMovements(s.row)">View Movements</el-button></template></el-table-column></el-table>
      <el-pagination v-model:current-page="query.page" v-model:page-size="query.pageSize" :total="total" layout="total, prev, pager, next" @current-change="load"/>
    </el-card>
    <el-dialog v-model="movementDialog" :title="`Inventory Movements · ${selected?.skuCode||''} ${selected?.productName||''}`" width="980px"><el-alert :title="Number(selected?.currentStock)<0?`Current Stock: ${selected?.currentStock}, Inventory Status: Negative stock. Verify purchase reductions, sales issues, and historical inventory.`:`Current Stock ${selected?.currentStock??0}, Movement Balance ${selected?.ledgerBalance??'None'}`" :type="Number(selected?.currentStock)<0||selected?.reconciliationStatus==='MISMATCH'?'error':'info'" show-icon :closable="false"/><el-table :data="movements" v-loading="movementLoading" stripe height="380"><el-table-column label="Type" width="95"><template #default="s">{{movementLabels[s.row.movementType]||s.row.movementType}}</template></el-table-column><el-table-column label="Change" width="80"><template #default="s"><span :class="Number(s.row.quantityChange)>0?'stock-in':'stock-out'">{{signed(s.row.quantityChange)}}</span></template></el-table-column><el-table-column prop="beforeQuantity" label="Before" width="90"/><el-table-column label="After" width="90"><template #default="s"><span :class="Number(s.row.afterQuantity)<0?'negative-stock':''">{{s.row.afterQuantity}}</span></template></el-table-column><el-table-column label="Source Document" width="150"><template #default="s">{{s.row.referenceNo||'Manual Adjustment'}}</template></el-table-column><el-table-column prop="reason" label="Reason" min-width="190"/><el-table-column prop="operatorName" label="Operator" width="100"/><el-table-column label="Time" width="170"><template #default="s">{{formatTime(s.row.createdAt)}}</template></el-table-column></el-table><el-pagination v-model:current-page="movementPage" :total="movementTotal" :page-size="10" layout="total, prev, pager, next" @current-change="loadMovements"/></el-dialog>
  </section>
</template>

<style scoped>
.summary-row{margin-bottom:16px}.toolbar{display:flex;gap:12px;margin-bottom:16px}.toolbar .el-input{max-width:360px}.warning-alert{margin-bottom:16px}.stock-in{color:#16a34a;font-weight:600}.stock-out,.negative-stock{color:#dc2626;font-weight:700}
</style>


