<script setup lang="ts">
import {onMounted,reactive,ref} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import {api} from '../api'
import BusinessDocumentDialog from '../components/BusinessDocumentDialog.vue'
import {localToday} from '../utils/date'

const today=localToday
const query=reactive({dateFrom:today(),dateTo:today(),keyword:'',status:'',sortBy:'date',sortDirection:'asc'})
const rows=ref<any[]>([]),selected=ref<any>(),eligible=ref<any[]>([])
const createDialog=ref(false),detailDialog=ref(false),loading=ref(false),saving=ref(false),approvingId=ref<number|null>(null)
const returnDialog=ref(false),returnViewId=ref<number|null>(null)
const form=reactive<any>({warehouseDate:today(),remark:'',startReturnNo:'',endReturnNo:'',salesReturnIds:[]})
const statusText=(s:string)=>s==='APPROVED'?'已审核/已入库':'草稿'
const startReturnNo=()=>selected.value?.returns?.[0]?.returnNo||'—'
const endReturnNo=()=>selected.value?.returns?.at(-1)?.returnNo||'—'

async function load(){loading.value=true;try{const{data}=await api.get('/return-warehouses',{params:query});rows.value=data.data||[]}finally{loading.value=false}}
function handleSortChange({prop,order}:{prop:string,order:string}){query.sortBy=prop==='warehouseNo'?'documentNo':'date';query.sortDirection=order==='descending'?'desc':'asc';load()}
async function select(row:any){if(!row)return;const{data}=await api.get(`/return-warehouses/${row.id}`);selected.value=data.data}
async function openDetail(row:any){await select(row);detailDialog.value=true}
function openSalesReturn(row:any){const linked=selected.value?.returns?.find((x:any)=>x.returnNo===row.returnNo);if(!linked)return ElMessage.warning('未找到对应的销售退货单');returnViewId.value=linked.id;returnDialog.value=true}
async function openNew(){
  const{data}=await api.get('/sales-returns',{params:{status:'DRAFT'}})
  eligible.value=(data.data||[]).filter((x:any)=>!x.warehouseId).sort((a:any,b:any)=>a.returnNo.localeCompare(b.returnNo))
  Object.assign(form,{warehouseDate:today(),remark:'',startReturnNo:eligible.value[0]?.returnNo||'',endReturnNo:eligible.value.at(-1)?.returnNo||'',salesReturnIds:[]})
  applyRange();createDialog.value=true
}
function applyRange(){
  if(!form.startReturnNo||!form.endReturnNo||form.startReturnNo.localeCompare(form.endReturnNo)>0){form.salesReturnIds=[];return}
  form.salesReturnIds=eligible.value.filter((x:any)=>x.returnNo.localeCompare(form.startReturnNo)>=0&&x.returnNo.localeCompare(form.endReturnNo)<=0).map((x:any)=>x.id)
}
async function save(){if(saving.value)return;if(!form.salesReturnIds.length)return ElMessage.warning('所选单号范围内没有可入仓销售退货单');saving.value=true;try{await api.post('/return-warehouses',{warehouseDate:form.warehouseDate,remark:form.remark,salesReturnIds:form.salesReturnIds});createDialog.value=false;await load();ElMessage.success('退货入仓草稿已保存')}catch(e:any){ElMessage.error(e.response?.data?.message||'退货入仓草稿保存失败，请刷新后重试')}finally{saving.value=false}}
async function approve(row:any){try{await ElMessageBox.confirm(`确认审核 ${row.warehouseNo}？审核后将增加库存，并同步审核关联销售退货单。`,'审核退货入仓',{confirmButtonText:'确认审核入库',type:'warning'});if(approvingId.value!==null)return;approvingId.value=row.id;await api.post(`/return-warehouses/${row.id}/approve`,{version:row.version});await load();await select(row);ElMessage.success('审核成功，退货库存已增加')}catch(e:any){if(e==='cancel'||e==='close')return;await load();selected.value=undefined;ElMessage.error(e.response?.data?.message||'审核失败，数据已刷新，请确认单据状态')}finally{approvingId.value=null}}
onMounted(load)
</script>

<template>
  <section class="page">
    <header><div><h1>退货入仓</h1><p>单击查看货品明细，双击查看入仓单资料；审核成功后才增加库存。</p></div><el-button type="primary" @click="openNew">新建退货入仓单</el-button></header>
    <el-card>
      <el-form inline class="filters"><el-form-item label="日期"><el-date-picker v-model="query.dateFrom" value-format="YYYY-MM-DD"/><span>至</span><el-date-picker v-model="query.dateTo" value-format="YYYY-MM-DD"/></el-form-item><el-form-item><el-input v-model="query.keyword" clearable placeholder="入仓单号 / 退货单号 / 客户" @keyup.enter="load"/></el-form-item><el-form-item><el-select v-model="query.status" clearable placeholder="全部审核状态"><el-option label="未审核" value="DRAFT"/><el-option label="已审核" value="APPROVED"/></el-select></el-form-item><el-button type="primary" @click="load">查询</el-button></el-form>
      <div class="split">
        <div class="upper"><el-table :data="rows" v-loading="loading" highlight-current-row :default-sort="{prop:'warehouseDate',order:'ascending'}" @sort-change="handleSortChange" @current-change="select" @row-dblclick="openDetail"><el-table-column prop="warehouseNo" label="入仓单号" width="150" sortable="custom" :sort-orders="['ascending','descending']"/><el-table-column prop="warehouseDate" label="日期" width="120" sortable="custom" :sort-orders="['ascending','descending']"/><el-table-column prop="returnCount" label="退货单数" width="110"/><el-table-column prop="totalAmount" label="退货金额" width="130"/><el-table-column label="状态" width="150"><template #default="{row}"><el-tag :type="row.status==='APPROVED'?'success':'info'">{{statusText(row.status)}}</el-tag></template></el-table-column><el-table-column prop="approvedAt" label="审核时间" min-width="180"><template #default="{row}">{{row.approvedAt||'—'}}</template></el-table-column><el-table-column label="操作" width="110" fixed="right"><template #default="{row}"><el-button v-if="row.status==='DRAFT'" link type="success" :loading="approvingId===row.id" :disabled="approvingId!==null" @click.stop="approve(row)">审核</el-button></template></el-table-column></el-table></div>
        <div class="detail-pane">
          <template v-if="selected"><div class="detail-title"><span><b>{{selected.warehouseNo}}</b> 的退货货品明细（双击可查看销售退货单）</span><span>共 {{selected.items?.length||0}} 条</span></div><el-table :data="selected.items||[]" height="100%" size="small" class="linked-return-table" @row-dblclick="openSalesReturn"><el-table-column prop="businessType" label="单据类型" width="90" fixed/><el-table-column prop="returnNo" label="单据号码" width="145" fixed/><el-table-column prop="returnDate" label="日期" width="110"/><el-table-column prop="salespersonName" label="业务员" width="100"/><el-table-column prop="customerCode" label="客户编号" width="100"/><el-table-column prop="customerName" label="名称" width="160"/><el-table-column prop="skuCode" label="编号" width="100"/><el-table-column label="品名规格" width="220"><template #default="{row}">{{row.productName}}{{row.specification?' '+row.specification:''}}</template></el-table-column><el-table-column prop="color" label="颜色" width="80"/><el-table-column prop="unit" label="单位" width="70"/><el-table-column prop="quantity" label="数量" width="90"/><el-table-column prop="unitPrice" label="单价" width="90"/><el-table-column prop="referencePrice" label="参考价" width="90"/><el-table-column prop="lineAmount" label="金额" width="100"/><el-table-column prop="actualPrice" label="实价" width="90"/><el-table-column prop="actualAmount" label="实价金额" width="100"/><el-table-column prop="headerRemark" label="总表备注" width="160"/><el-table-column prop="lineRemark" label="明细备注" width="160"/></el-table></template>
          <el-empty v-else description="单击上方入仓单查看货品明细" :image-size="55"/>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="detailDialog" title="资料查看" width="520px" class="detail-dialog">
      <el-descriptions v-if="selected" :column="2" border>
        <el-descriptions-item label="日期">{{selected.warehouseDate}}</el-descriptions-item>
        <el-descriptions-item label="入仓单号">{{selected.warehouseNo}}</el-descriptions-item>
        <el-descriptions-item label="开始单号">{{startReturnNo()}}</el-descriptions-item>
        <el-descriptions-item label="结束单号">{{endReturnNo()}}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{selected.remark||'—'}}</el-descriptions-item>
      </el-descriptions>
      <template #footer><el-button type="primary" @click="detailDialog=false">确定</el-button><el-button @click="detailDialog=false">取消</el-button></template>
    </el-dialog>

    <BusinessDocumentDialog v-model="returnDialog" type="sales-return" :id="returnViewId"/>
    <el-dialog v-model="createDialog" title="新建退货入仓单" width="900px">
      <el-form inline><el-form-item label="日期"><el-date-picker v-model="form.warehouseDate" value-format="YYYY-MM-DD"/></el-form-item><el-form-item label="开始单号"><el-select v-model="form.startReturnNo" filterable style="width:170px" @change="applyRange"><el-option v-for="x in eligible" :key="x.id" :label="x.returnNo" :value="x.returnNo"/></el-select></el-form-item><el-form-item label="结束单号"><el-select v-model="form.endReturnNo" filterable style="width:170px" @change="applyRange"><el-option v-for="x in eligible" :key="x.id" :label="x.returnNo" :value="x.returnNo"/></el-select></el-form-item><el-form-item label="备注"><el-input v-model="form.remark" style="width:420px"/></el-form-item></el-form>
      <el-alert v-if="form.startReturnNo&&form.endReturnNo&&form.startReturnNo.localeCompare(form.endReturnNo)>0" title="开始单号不能大于结束单号" type="error" :closable="false"/>
      <el-table :data="eligible.filter((x:any)=>form.salesReturnIds.includes(x.id))" max-height="360"><el-table-column prop="returnNo" label="销售退货单" width="150"/><el-table-column prop="returnDate" label="日期" width="110"/><el-table-column prop="customerCode" label="客户编号" width="110"/><el-table-column prop="customerName" label="客户名称"/><el-table-column prop="totalAmount" label="金额" width="110"/><el-table-column label="打印" width="80"><template #default="{row}">{{row.printedAt?'已打印':'未打印'}}</template></el-table-column></el-table>
      <template #footer><span class="range-count">已选 {{form.salesReturnIds.length}} 张</span><el-button :disabled="saving" @click="createDialog=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!form.salesReturnIds.length" @click="save">保存草稿</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.page{padding:16px}header{display:flex;justify-content:space-between;align-items:center}h1{margin:0}header p{color:#667085}.filters{margin-bottom:2px}.el-form-item span{margin:0 8px}.split{height:calc(100vh - 250px);min-height:510px;display:grid;grid-template-rows:minmax(300px,1fr) 215px;gap:10px}.upper,.detail-pane{overflow:hidden;border:1px solid #dfe3e8;border-radius:4px}.detail-pane{display:flex;flex-direction:column}.detail-title{display:flex;justify-content:space-between;padding:8px 12px;background:#f6f8fa;color:#475467;border-bottom:1px solid #e5e7eb}.detail-pane :deep(.el-table){flex:1}.range-count{margin-right:16px;color:#667085}
.linked-return-table :deep(.el-table__row){cursor:pointer}
</style>
