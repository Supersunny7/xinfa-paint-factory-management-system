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
const statusText=(s:string)=>s==='APPROVED'?'Approved/Stock In':'Draft'
const startReturnNo=()=>selected.value?.returns?.[0]?.returnNo||'—'
const endReturnNo=()=>selected.value?.returns?.at(-1)?.returnNo||'—'

async function load(){loading.value=true;try{const{data}=await api.get('/return-warehouses',{params:query});rows.value=data.data||[]}finally{loading.value=false}}
function handleSortChange({prop,order}:{prop:string,order:string}){query.sortBy=prop==='warehouseNo'?'documentNo':'date';query.sortDirection=order==='descending'?'desc':'asc';load()}
async function select(row:any){if(!row)return;const{data}=await api.get(`/return-warehouses/${row.id}`);selected.value=data.data}
async function openDetail(row:any){await select(row);detailDialog.value=true}
function openSalesReturn(row:any){const linked=selected.value?.returns?.find((x:any)=>x.returnNo===row.returnNo);if(!linked)return ElMessage.warning('The linked sales return was not found');returnViewId.value=linked.id;returnDialog.value=true}
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
async function save(){if(saving.value)return;if(!form.salesReturnIds.length)return ElMessage.warning('selectedDocument No.none in the rangehas available warehouseSales Return');saving.value=true;try{await api.post('/return-warehouses',{warehouseDate:form.warehouseDate,remark:form.remark,salesReturnIds:form.salesReturnIds});createDialog.value=false;await load();ElMessage.success('Return warehousing draft saved')}catch(e:any){ElMessage.error(e.response?.data?.message||'Unable to save the return warehousing draft, Refresh and try again')}finally{saving.value=false}}
async function approve(row:any){try{await ElMessageBox.confirm(`Approve ${row.warehouseNo}？Approval increases stock and approves the linked sales returns.`,'Approve Return Warehousing',{confirmButtonText:'ApproveStock In',type:'warning'});if(approvingId.value!==null)return;approvingId.value=row.id;await api.post(`/return-warehouses/${row.id}/approve`,{version:row.version});await load();await select(row);ElMessage.success('Approved successfully; returned products were added to inventory')}catch(e:any){if(e==='cancel'||e==='close')return;await load();selected.value=undefined;ElMessage.error(e.response?.data?.message||'Approval Failed, DataRefresh, Please ConfirmDocumentStatus')}finally{approvingId.value=null}}
onMounted(load)
</script>

<template>
  <section class="page">
    <header><div><h1>Return Warehousing</h1><p>Click to view product lines; double-click to view the warehouse receipt. Stock increases only after approval.</p></div><el-button type="primary" @click="openNew">Create Return Warehouse Receipt</el-button></header>
    <el-card>
      <el-form inline class="filters"><el-form-item label="Date"><el-date-picker v-model="query.dateFrom" value-format="YYYY-MM-DD"/><span>to</span><el-date-picker v-model="query.dateTo" value-format="YYYY-MM-DD"/></el-form-item><el-form-item><el-input v-model="query.keyword" clearable placeholder="Warehouse Receipt No. / Return No. / Customer" @keyup.enter="load"/></el-form-item><el-form-item><el-select v-model="query.status" clearable placeholder="All Approval Statuses"><el-option label="Not Approved" value="DRAFT"/><el-option label="Approved" value="APPROVED"/></el-select></el-form-item><el-button type="primary" @click="load">Search</el-button></el-form>
      <div class="split">
        <div class="upper"><el-table :data="rows" v-loading="loading" highlight-current-row :default-sort="{prop:'warehouseDate',order:'ascending'}" @sort-change="handleSortChange" @current-change="select" @row-dblclick="openDetail"><el-table-column prop="warehouseNo" label="Warehouse Receipt No." width="150" sortable="custom" :sort-orders="['ascending','descending']"/><el-table-column prop="warehouseDate" label="Date" width="120" sortable="custom" :sort-orders="['ascending','descending']"/><el-table-column prop="returnCount" label="Return Documents" width="110"/><el-table-column prop="totalAmount" label="Return Amount" width="130"/><el-table-column label="Status" width="150"><template #default="{row}"><el-tag :type="row.status==='APPROVED'?'success':'info'">{{statusText(row.status)}}</el-tag></template></el-table-column><el-table-column prop="approvedAt" label="Approved At" min-width="180"><template #default="{row}">{{row.approvedAt||'—'}}</template></el-table-column><el-table-column label="Actions" width="110" fixed="right"><template #default="{row}"><el-button v-if="row.status==='DRAFT'" link type="success" :loading="approvingId===row.id" :disabled="approvingId!==null" @click.stop="approve(row)">Approve</el-button></template></el-table-column></el-table></div>
        <div class="detail-pane">
<template v-if="selected"><div class="detail-title"><span><b>{{selected.warehouseNo}}</b> returned product lines (double-click to view the sales return)</span><span>Total: {{selected.items?.length||0}} records</span></div><el-table :data="selected.items||[]" height="100%" size="small" class="linked-return-table" @row-dblclick="openSalesReturn"><el-table-column prop="businessType" label="Document Type" width="90" fixed/><el-table-column prop="returnNo" label="Document No." width="145" fixed/><el-table-column prop="returnDate" label="Date" width="110"/><el-table-column prop="salespersonName" label="Salesperson" width="100"/><el-table-column prop="customerCode" label="Customer Code" width="100"/><el-table-column prop="customerName" label="Name" width="160"/><el-table-column prop="skuCode" label="Code" width="100"/><el-table-column label="Product / Specification" width="220"><template #default="{row}">{{row.productName}}{{row.specification?' '+row.specification:''}}</template></el-table-column><el-table-column prop="color" label="Color" width="80"/><el-table-column prop="unit" label="Unit" width="70"/><el-table-column prop="quantity" label="Quantity" width="90"/><el-table-column prop="unitPrice" label="Unit Price" width="90"/><el-table-column prop="referencePrice" label="Reference Price" width="90"/><el-table-column prop="lineAmount" label="Amount" width="100"/><el-table-column prop="actualPrice" label="Actual Price" width="90"/><el-table-column prop="actualAmount" label="Actual Amount" width="100"/><el-table-column prop="headerRemark" label="Document Notes" width="160"/><el-table-column prop="lineRemark" label="Line Notes" width="160"/></el-table></template>
          <el-empty v-else description="Click a warehouse receipt above to view product lines" :image-size="55"/>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="detailDialog" title="View Record" width="520px" class="detail-dialog">
      <el-descriptions v-if="selected" :column="2" border>
        <el-descriptions-item label="Date">{{selected.warehouseDate}}</el-descriptions-item>
        <el-descriptions-item label="Warehouse Receipt No.">{{selected.warehouseNo}}</el-descriptions-item>
        <el-descriptions-item label="Starting Document No.">{{startReturnNo()}}</el-descriptions-item>
        <el-descriptions-item label="Ending Document No.">{{endReturnNo()}}</el-descriptions-item>
        <el-descriptions-item label="Notes" :span="2">{{selected.remark||'—'}}</el-descriptions-item>
      </el-descriptions>
      <template #footer><el-button type="primary" @click="detailDialog=false">Confirm</el-button><el-button @click="detailDialog=false">Cancel</el-button></template>
    </el-dialog>

    <BusinessDocumentDialog v-model="returnDialog" type="sales-return" :id="returnViewId"/>
    <el-dialog v-model="createDialog" title="Create Return Warehouse Receipt" width="900px">
      <el-form inline><el-form-item label="Date"><el-date-picker v-model="form.warehouseDate" value-format="YYYY-MM-DD"/></el-form-item><el-form-item label="Starting Document No."><el-select v-model="form.startReturnNo" filterable style="width:170px" @change="applyRange"><el-option v-for="x in eligible" :key="x.id" :label="x.returnNo" :value="x.returnNo"/></el-select></el-form-item><el-form-item label="Ending Document No."><el-select v-model="form.endReturnNo" filterable style="width:170px" @change="applyRange"><el-option v-for="x in eligible" :key="x.id" :label="x.returnNo" :value="x.returnNo"/></el-select></el-form-item><el-form-item label="Notes"><el-input v-model="form.remark" style="width:420px"/></el-form-item></el-form>
      <el-alert v-if="form.startReturnNo&&form.endReturnNo&&form.startReturnNo.localeCompare(form.endReturnNo)>0" title="Starting Document No.not cannot exceed the endingDocument No." type="error" :closable="false"/>
      <el-table :data="eligible.filter((x:any)=>form.salesReturnIds.includes(x.id))" max-height="360"><el-table-column prop="returnNo" label="Sales Return" width="150"/><el-table-column prop="returnDate" label="Date" width="110"/><el-table-column prop="customerCode" label="Customer Code" width="110"/><el-table-column prop="customerName" label="Customer Name"/><el-table-column prop="totalAmount" label="Amount" width="110"/><el-table-column label="Print" width="80"><template #default="{row}">{{row.printedAt?'Printed':'Not Printed'}}</template></el-table-column></el-table>
      <template #footer><span class="range-count">Selected {{form.salesReturnIds.length}}  documents</span><el-button :disabled="saving" @click="createDialog=false">Cancel</el-button><el-button type="primary" :loading="saving" :disabled="!form.salesReturnIds.length" @click="save">SaveDraft</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.page{padding:16px}header{display:flex;justify-content:space-between;align-items:center}h1{margin:0}header p{color:#667085}.filters{margin-bottom:2px}.el-form-item span{margin:0 8px}.split{height:calc(100vh - 250px);min-height:510px;display:grid;grid-template-rows:minmax(300px,1fr) 215px;gap:10px}.upper,.detail-pane{overflow:hidden;border:1px solid #dfe3e8;border-radius:4px}.detail-pane{display:flex;flex-direction:column}.detail-title{display:flex;justify-content:space-between;padding:8px 12px;background:#f6f8fa;color:#475467;border-bottom:1px solid #e5e7eb}.detail-pane :deep(.el-table){flex:1}.range-count{margin-right:16px;color:#667085}
.linked-return-table :deep(.el-table__row){cursor:pointer}
</style>


