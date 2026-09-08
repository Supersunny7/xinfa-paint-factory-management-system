<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api } from '../api'

const loading=ref(false),rows=ref<any[]>([]),total=ref(0),summary=ref<any>({})
const query=reactive({keyword:'',alertType:'ALL',page:1,pageSize:20})
const movementDialog=ref(false),movementLoading=ref(false),selected=ref<any>(null),movements=ref<any[]>([]),movementTotal=ref(0),movementPage=ref(1)
const movementLabels:Record<string,string>={INBOUND:'入库',OUTBOUND:'出库',ADJUSTMENT:'盘点调整',REVERSAL:'撤销出库'}

async function load(){loading.value=true;try{const [list,stats]=await Promise.all([api.get('/inventory-reconciliation',{params:query}),api.get('/inventory-reconciliation/summary')]);rows.value=list.data.data.items;total.value=list.data.data.total;summary.value=stats.data.data}finally{loading.value=false}}
function search(){query.page=1;load()}
function inventoryText(s:string){return({NORMAL:'正常',LOW_STOCK:'库存不足',OUT_OF_STOCK:'已无库存',NEGATIVE_STOCK:'负库存'} as any)[s]||s}
function inventoryTag(s:string){return s==='NORMAL'?'success':s==='LOW_STOCK'?'warning':'danger'}
function reconciliationText(s:string){return({BALANCED:'账面一致',MISMATCH:'账实异常',NO_MOVEMENT:'暂无流水'} as any)[s]||s}
function reconciliationTag(s:string){return s==='BALANCED'?'success':s==='MISMATCH'?'danger':'info'}
function signed(v:any){const n=Number(v);return `${n>0?'+':''}${n}`}
function formatTime(value:any){return value?String(value).replace('T',' ').slice(0,19):'-'}
async function openMovements(row:any){selected.value=row;movementPage.value=1;movementDialog.value=true;await loadMovements()}
async function loadMovements(){if(!selected.value)return;movementLoading.value=true;try{const{data}=await api.get(`/products/${selected.value.productId}/inventory-movements`,{params:{page:movementPage.value,pageSize:10}});movements.value=data.data.items;movementTotal.value=data.data.total}finally{movementLoading.value=false}}
onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading"><div><h1>库存对账与预警</h1><p>核对当前库存与库存流水，及时发现库存不足和账实异常</p></div><el-button type="primary" @click="load">刷新数据</el-button></div>
    <el-row :gutter="16" class="summary-row">
      <el-col :span="4"><el-card shadow="never"><el-statistic title="在用货品" :value="summary.enabledProductCount||0"/></el-card></el-col>
      <el-col :span="5"><el-card shadow="never"><el-statistic title="库存不足" :value="summary.lowStockCount||0"><template #suffix>种</template></el-statistic></el-card></el-col>
      <el-col :span="5"><el-card shadow="never"><el-statistic title="已无库存" :value="summary.outOfStockCount||0"><template #suffix>种</template></el-statistic></el-card></el-col>
      <el-col :span="5"><el-card shadow="never"><el-statistic title="账实异常" :value="summary.mismatchCount||0"><template #suffix>种</template></el-statistic></el-card></el-col>
      <el-col :span="5"><el-card shadow="never"><el-statistic title="暂无流水" :value="summary.noMovementCount||0"><template #suffix>种</template></el-statistic></el-card></el-col>
    </el-row>
    <el-card>
      <div class="toolbar"><el-input v-model="query.keyword" clearable placeholder="货品编号 / 品名 / 规格" @keyup.enter="search"/><el-select v-model="query.alertType" style="width:170px" @change="search"><el-option label="全部货品" value="ALL"/><el-option label="负库存" value="NEGATIVE_STOCK"/><el-option label="库存不足" value="LOW_STOCK"/><el-option label="已无库存" value="OUT_OF_STOCK"/><el-option label="账实异常" value="MISMATCH"/><el-option label="暂无流水" value="NO_MOVEMENT"/></el-select><el-button type="primary" @click="search">查询</el-button></div>
      <el-alert v-if="Number(summary.mismatchCount)>0" title="发现账实异常：当前库存与最后一条流水余额不一致，请优先核查。" type="error" show-icon :closable="false" class="warning-alert"/>
      <el-table :data="rows" v-loading="loading" stripe><el-table-column prop="skuCode" label="编号" width="110"/><el-table-column prop="productName" label="品名" min-width="160"/><el-table-column prop="specification" label="规格" width="120"/><el-table-column prop="salesUnit" label="单位" width="65"/><el-table-column label="当前库存" width="100"><template #default="s"><span :class="Number(s.row.currentStock)<0?'negative-stock':''">{{s.row.currentStock}}</span></template></el-table-column><el-table-column prop="stockLowerLimit" label="库存下限" width="100"/><el-table-column prop="shortageQuantity" label="缺货数量" width="100"/><el-table-column label="库存预警" width="105"><template #default="s"><el-tag :type="inventoryTag(s.row.inventoryStatus)">{{inventoryText(s.row.inventoryStatus)}}</el-tag></template></el-table-column><el-table-column label="流水余额" width="100"><template #default="s">{{s.row.ledgerBalance??'-'}}</template></el-table-column><el-table-column label="差异" width="85"><template #default="s">{{s.row.variance??'-'}}</template></el-table-column><el-table-column label="对账结果" width="105"><template #default="s"><el-tag :type="reconciliationTag(s.row.reconciliationStatus)">{{reconciliationText(s.row.reconciliationStatus)}}</el-tag></template></el-table-column><el-table-column label="操作" width="105" fixed="right"><template #default="s"><el-button link type="primary" @click="openMovements(s.row)">查看流水</el-button></template></el-table-column></el-table>
      <el-pagination v-model:current-page="query.page" v-model:page-size="query.pageSize" :total="total" layout="total, prev, pager, next" @current-change="load"/>
    </el-card>
    <el-dialog v-model="movementDialog" :title="`库存流水 · ${selected?.skuCode||''} ${selected?.productName||''}`" width="980px"><el-alert :title="Number(selected?.currentStock)<0?`当前库存：${selected?.currentStock}，库存状态：负库存。请核对采购退货、销售出库或历史库存。`:`当前库存 ${selected?.currentStock??0}，流水余额 ${selected?.ledgerBalance??'暂无'}`" :type="Number(selected?.currentStock)<0||selected?.reconciliationStatus==='MISMATCH'?'error':'info'" show-icon :closable="false"/><el-table :data="movements" v-loading="movementLoading" stripe height="380"><el-table-column label="类型" width="95"><template #default="s">{{movementLabels[s.row.movementType]||s.row.movementType}}</template></el-table-column><el-table-column label="变动" width="80"><template #default="s"><span :class="Number(s.row.quantityChange)>0?'stock-in':'stock-out'">{{signed(s.row.quantityChange)}}</span></template></el-table-column><el-table-column prop="beforeQuantity" label="变动前" width="90"/><el-table-column label="变动后" width="90"><template #default="s"><span :class="Number(s.row.afterQuantity)<0?'negative-stock':''">{{s.row.afterQuantity}}</span></template></el-table-column><el-table-column label="来源单据" width="150"><template #default="s">{{s.row.referenceNo||'人工调整'}}</template></el-table-column><el-table-column prop="reason" label="原因" min-width="190"/><el-table-column prop="operatorName" label="操作人" width="100"/><el-table-column label="时间" width="170"><template #default="s">{{formatTime(s.row.createdAt)}}</template></el-table-column></el-table><el-pagination v-model:current-page="movementPage" :total="movementTotal" :page-size="10" layout="total, prev, pager, next" @current-change="loadMovements"/></el-dialog>
  </section>
</template>

<style scoped>
.summary-row{margin-bottom:16px}.toolbar{display:flex;gap:12px;margin-bottom:16px}.toolbar .el-input{max-width:360px}.warning-alert{margin-bottom:16px}.stock-in{color:#16a34a;font-weight:600}.stock-out,.negative-stock{color:#dc2626;font-weight:700}
</style>
