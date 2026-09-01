<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'

const active=ref('orders'),orders=ref<any[]>([]),receipts=ref<any[]>([]),suppliers=ref<any[]>([]),products=ref<any[]>([])
const loading=ref(false),dialog=ref(false),mode=ref<'order'|'receipt'>('order'),keyword=ref(''),productKeyword=ref(''),productId=ref<number|null>(null),sourceOrder=ref<any>(null)
const receiptOrderId=ref<number|null>(null),availableOrders=ref<any[]>([])
const detailDialog=ref(false),detail=ref<any>(null),detailType=ref<'order'|'receipt'>('order')
const inlinePurchase=ref<any>(null),inlinePurchaseLoading=ref(false),inlinePurchaseType=ref<'order'|'receipt'>('order'),selectedPurchaseKey=ref('')
const editingOrderId=ref<number|null>(null),editingOrderVersion=ref(0),editingReceiptId=ref<number|null>(null),editingReceiptVersion=ref(0)
const printDialog=ref(false)
const detailPrintDialog=ref(false)
import { localToday } from '../utils/date'
const form=reactive<any>({supplierId:null,orderDate:localToday(),receiptDate:localToday(),expectedDeliveryDate:'',deliveryLocation:'',warehouseName:'Warehouse',settlementMethod:'Cash',remark:'',items:[]})
const query=reactive({dateFrom:localToday(),dateTo:localToday(),status:'',printStatus:'',sortBy:'date',sortDirection:'asc'})
const total=computed(()=>form.items.reduce((sum:number,x:any)=>sum+Number(x.quantity||0)*Number(x.unitPrice||0),0))
const receiptItems=computed(()=>form.items.filter((x:any)=>Number(x.quantity)>0))
const returnItems=computed(()=>form.items.filter((x:any)=>Number(x.quantity)<0))
const receiptAmount=computed(()=>receiptItems.value.reduce((sum:number,x:any)=>sum+Number(x.quantity||0)*Number(x.unitPrice||0),0))
const returnAmount=computed(()=>returnItems.value.reduce((sum:number,x:any)=>sum+Number(x.quantity||0)*Number(x.unitPrice||0),0))
const inventoryNetChange=computed(()=>form.items.reduce((sum:number,x:any)=>sum+Number(x.quantity||0),0))
const printRows=computed(()=>active.value==='orders'?orders.value:receipts.value)
const printTotal=computed(()=>printRows.value.reduce((sum:number,x:any)=>sum+Number(x.totalAmount||0),0))
const printTitle=computed(()=>active.value==='orders'?'Purchase Order Summary':'Purchase Receipt Summary')
const printTime=ref('')
function openPrintSummary(){
  printTime.value=new Date().toLocaleString('zh-CN',{hour12:false})
  printDialog.value=true
}
function printSummary(){window.print()}
function openDetailPrint(){
  if(!detail.value)return
  printTime.value=new Date().toLocaleString('zh-CN',{hour12:false})
  detailPrintDialog.value=true
}
async function printDetail(){window.print();if(detailType.value==='receipt')await confirmReceiptPrint();else await confirmOrderPrint()}
async function confirmOrderPrint(){
  if(detailType.value!=='order'||!detail.value)return
  try{
    await ElMessageBox.confirm('Was the purchase order printed successfully or saved as a PDF? After confirmation, the original document becomes read-only.','Confirm Purchase Order Print Result',{type:'warning',confirmButtonText:'Print Successful',cancelButtonText:'Print Failed or Cancelled'})
    const{data}=await api.post(`/purchases/orders/${detail.value.id}/confirm-print`,{version:detail.value.version})
    ElMessage.success(data.data.firstPrint?'Purchase order marked as printed':'Purchase order reprint count updated')
    const refreshed=await api.get(`/purchases/orders/${detail.value.id}`);detail.value=refreshed.data.data;detailPrintDialog.value=true;await load()
  }catch(e:any){if(e==='cancel'||e==='close')return;ElMessage.error(e.response?.data?.message||'ConfirmPrint Failed')}
}
async function confirmReceiptPrint(){
  if(detailType.value!=='receipt'||!detail.value)return
  try{
    await ElMessageBox.confirm('Confirm only after the purchase receipt has printed successfully or been saved as a PDF. This records print status and does not change inventory again.','ConfirmReceipt Printed Successfully',{type:'warning',confirmButtonText:'Confirm Successful Print',cancelButtonText:'Not Printed Yet'})
    const{data}=await api.post(`/purchases/receipts/${detail.value.id}/confirm-print`,{version:detail.value.version})
    ElMessage.success(data.data.firstPrint?'Purchase receipt marked as printed':'Purchase receipt reprint count updated')
    const refreshed=await api.get(`/purchases/receipts/${detail.value.id}`)
    detail.value=refreshed.data.data
    await load()
  }catch(e:any){if(e==='cancel'||e==='close')return;ElMessage.error(e.response?.data?.message||'ConfirmPrint Failed')}
}
async function load(){loading.value=true;try{const path=active.value==='orders'?'/purchases/orders':'/purchases/receipts';const params:any={keyword:keyword.value,dateFrom:query.dateFrom,dateTo:query.dateTo,printStatus:query.printStatus,sortBy:query.sortBy,sortDirection:query.sortDirection};if(active.value==='receipts')params.status=query.status;const{data}=await api.get(path,{params});if(active.value==='orders')orders.value=data.data;else receipts.value=data.data}finally{loading.value=false}}
async function focusNewDocument(documentNo:string){await nextTick();const row=Array.from(document.querySelectorAll('.el-table__body-wrapper tbody tr')).find(x=>x.textContent?.includes(documentNo));if(!row)return;row.classList.add('newly-created-row');row.scrollIntoView({behavior:'smooth',block:'center'});window.setTimeout(()=>row.classList.remove('newly-created-row'),5000)}
function handleSortChange({prop,order}:{prop:string,order:string}){query.sortBy=['orderNo','receiptNo'].includes(prop)?'documentNo':'date';query.sortDirection=order==='descending'?'desc':'asc';load()}
function todayQuery(){Object.assign(query,{dateFrom:localToday(),dateTo:localToday()});load()}
function clearQuery(){keyword.value='';Object.assign(query,{dateFrom:'',dateTo:'',status:'',printStatus:'',sortBy:'date',sortDirection:'asc'});load()}
async function searchSuppliers(q=''){const{data}=await api.get('/reference-data/suppliers',{params:{keyword:q,enabled:true,pageSize:100}});suppliers.value=data.data.items}
async function searchProducts(){const q=productKeyword.value.trim();const{data}=await api.get('/products',{params:{keyword:q,enabled:true,pageSize:100}});products.value=data.data.items;const exact=products.value.filter((x:any)=>String(x.skuCode).toLowerCase()===q.toLowerCase());productId.value=exact.length===1?exact[0].id:null}
async function loadAvailableOrders(){const{data}=await api.get('/purchases/orders',{params:{keyword:''}});availableOrders.value=(data.data||[]).filter((x:any)=>x.fulfillmentStatus!=='VOIDED')}
function addProduct(){
  const p=products.value.find((x:any)=>x.id===productId.value)
  if(!p)return
  if(mode.value==='order'&&form.items.some((x:any)=>x.skuId===p.id))return ElMessage.warning('Purchase OrderwithinNo not add the same item twiceProduct')
  const referencePrice=Number(p.lastPurchasePrice??0)
  form.items.push({skuId:p.id,skuCode:p.skuCode,productName:p.productName,packageSpec:p.packageSpec,packageCount:null,packageUnit:p.packageUnit||'',quantity:1,unit:p.salesUnit,unitPrice:Number(p.lastPurchasePrice??p.wholesalePrice??0),referencePrice,remark:'',purchaseOrderItemId:null,businessType:null})
  productId.value=null;productKeyword.value='';products.value=[]
}
function receiptPriceClass(row:any){if(row.referencePrice===null||row.referencePrice===undefined||row.referencePrice==='')return 'price-equal';return Number(row.unitPrice)>Number(row.referencePrice)?'price-high':Number(row.unitPrice)<Number(row.referencePrice)?'price-low':'price-equal'}
async function newOrder(){mode.value='order';sourceOrder.value=null;editingOrderId.value=null;editingOrderVersion.value=0;editingReceiptId.value=null;editingReceiptVersion.value=0;Object.assign(form,{supplierId:null,orderDate:localToday(),expectedDeliveryDate:'',deliveryLocation:'',remark:'',items:[]});await searchSuppliers();dialog.value=true}
async function editOrder(row:any){const{data}=await api.get(`/purchases/orders/${row.id}`);const order=data.data;if(order.status!=='DRAFT'||order.printedAt||order.completedAt)return ElMessage.warning('Printed, completed, or voided purchase orders cannot be edited');await searchSuppliers();if(!suppliers.value.some((x:any)=>x.id===order.supplierId))suppliers.value.unshift({id:order.supplierId,code:order.supplierCode,name:order.supplierName});mode.value='order';sourceOrder.value=null;editingOrderId.value=order.id;editingOrderVersion.value=order.version;Object.assign(form,{supplierId:order.supplierId,orderDate:order.orderDate,expectedDeliveryDate:order.expectedDeliveryDate||'',deliveryLocation:order.deliveryLocation||'',remark:order.remark||'',items:order.items.map((x:any)=>({skuId:x.skuId,skuCode:x.skuCode,productName:x.productName,packageSpec:x.packageSpec,packageCount:x.packageCount,packageUnit:x.packageUnit,quantity:Number(x.quantity),unit:x.unit,unitPrice:Number(x.unitPrice),remark:x.remark||''}))});dialog.value=true}
async function save(){
  if(!form.supplierId||!form.items.length)return ElMessage.warning('Please Select Supplierand addProduct')
  let receiptLines:any[]=form.items
  if(mode.value==='receipt'){
    if(!sourceOrder.value)return ElMessage.warning('First SelectLinked Purchase Order')
    receiptLines=form.items.filter((x:any)=>Number(x.quantity)!==0).map((x:any)=>({...x,businessType:Number(x.quantity)<0?'ORDER_RETURN':'ORDER_RECEIPT'}))
    if(!receiptLines.length)return ElMessage.warning('Enter at least one non-zero stock-in or stock-out quantity')
    const over=receiptLines.filter((x:any)=>x.purchaseOrderItemId&&Number(x.quantity)>Number(x.remainingQuantity||0))
    if(over.length)await ElMessageBox.confirm(`has  ${over.length}  rowsThis exceeds the remaining quantity. The excess will still increase stock. Save anyway?`,'ConfirmOver-received',{type:'warning',confirmButtonText:'Allow Over-receipt and Save'})
  }
  const body=mode.value==='order'?{supplierId:form.supplierId,orderDate:form.orderDate,expectedDeliveryDate:form.expectedDeliveryDate||null,deliveryLocation:form.deliveryLocation,remark:form.remark,items:form.items}:{purchaseOrderId:sourceOrder.value?.id||null,supplierId:form.supplierId,receiptDate:form.receiptDate,warehouseName:form.warehouseName,settlementMethod:form.settlementMethod,remark:form.remark,items:receiptLines}
  const{data}=mode.value==='order'&&editingOrderId.value?await api.put(`/purchases/orders/${editingOrderId.value}`,{...body,version:editingOrderVersion.value}):mode.value==='receipt'&&editingReceiptId.value?await api.put(`/purchases/receipts/${editingReceiptId.value}`,{...body,version:editingReceiptVersion.value}):await api.post(mode.value==='order'?'/purchases/orders':'/purchases/receipts',body)
  const createdType=mode.value
  const created=editingOrderId.value||editingReceiptId.value?null:data.data
  if(editingOrderId.value)ElMessage.success('Purchase OrderDraftEditSuccessful')
  if(editingReceiptId.value)ElMessage.success('Purchase ReceiptDraftEditSuccessful')
  const updatedReceiptId=editingReceiptId.value
  dialog.value=false;editingOrderId.value=null;editingOrderVersion.value=0;editingReceiptId.value=null;editingReceiptVersion.value=0;active.value=createdType==='order'?'orders':'receipts';await load()
  if(updatedReceiptId){const row=receipts.value.find((x:any)=>x.id===updatedReceiptId);if(row)await focusNewDocument(row.receiptNo)}
  if(created){const documentNo=createdType==='order'?created.orderNo:created.receiptNo;await focusNewDocument(documentNo);ElMessage({type:'success',message:createdType==='order'?`Purchase Order ${documentNo} Created successfully, located and highlighted`:`Purchase Receipt ${documentNo} Created successfully, located and highlighted; awaiting supervisor approval`,duration:5000,showClose:true})}
}
async function view(row:any,type:'order'|'receipt'){
  const{data}=await api.get(`/purchases/${type==='order'?'orders':'receipts'}/${row.id}`)
  const document=data.data
  detail.value=document
  detailType.value=type
  detailDialog.value=true
}
function viewOrder(row:any){return view(row,'order')}
function viewReceipt(row:any){return view(row,'receipt')}
async function approve(row:any){await ElMessageBox.confirm('After approval, positive quantities add stock and negative quantities reduce stock. Negative stock is allowed, and approval cannot be repeated. Continue?','Approve Purchase Receipt',{type:'warning',confirmButtonText:'Confirm Approval'});try{const{data}=await api.post(`/purchases/receipts/${row.id}/approve`,{version:row.version});const ordersResponse=await api.get('/purchases/orders',{params:{keyword:keyword.value}});orders.value=ordersResponse.data.data;await load();ElMessage.success(data.data.orderReopened?'Approval completed and inventory updated, linked purchase order restored to Partially Received':data.data.orderAutoCompleted?'Approval completed and inventory updated, linked purchase order is Fully Received':'Approval completed; positive quantities stocked in and negative quantities stocked out')}catch(e:any){ElMessage.error(e.response?.data?.message||'Approval Failed') }}
async function voidDocument(row:any,type:'order'|'receipt'){
  try{
    const name=type==='order'?'Purchase Order':'Purchase Receipt'
    const{value}=await ElMessageBox.prompt(`Voiding retains the document number and record, but the document can no longer be edited or approved. Enter a void reason.`,`Void${name}`,{type:'warning',confirmButtonText:'Confirm Void',cancelButtonText:'Cancel',inputValidator:(v:string)=>v.trim()?true:'Enter a void reason'})
    await api.post(`/purchases/${type==='order'?'orders':'receipts'}/${row.id}/void`,{version:row.version,reason:value.trim()})
    await load();ElMessage.success(`${name}Voided`)
  }catch(e:any){if(e==='cancel'||e==='close')return;ElMessage.error(e.response?.data?.message||'VoidFailed')}
}
async function deleteDocument(row:any,type:'order'|'receipt'){
  try{
    const name=type==='order'?'Purchase OrderDraft':'Purchase ReceiptDraft'
    await ElMessageBox.confirm(`Delete after ${name} and its lines cannot be restored. Only unapproved, unprinted drafts with no business impact can be deleted.`,`Delete${name}`,{type:'warning',confirmButtonText:'Confirm Delete',cancelButtonText:'Cancel'})
    await api.delete(`/purchases/${type==='order'?'orders':'receipts'}/${row.id}`,{params:{version:row.version}})
    await load();ElMessage.success(`${name}Deleted`)
  }catch(e:any){if(e==='cancel'||e==='close')return;ElMessage.error(e.response?.data?.message||'DeleteFailed')}
}
function orderStatusText(status:string){return({ORDERED:'Pending Receipt',PARTIAL:'Partially Received',COMPLETED:'Fully Received',CLOSED:'Complete',VOIDED:'Voided'} as any)[status]||status}
function orderStatusType(status:string){return ['COMPLETED','CLOSED'].includes(status)?'success':status==='PARTIAL'?'warning':status==='VOIDED'?'danger':'info'}
async function applySourceOrder(orderId:number){
  const{data}=await api.get(`/purchases/orders/${orderId}`)
  const order=data.data
  if(!Array.isArray(order?.items)||order.items.some((x:any)=>x.remainingQuantity===undefined||x.receivedQuantity===undefined)){
    sourceOrder.value=null
    receiptOrderId.value=null
    form.items=[]
    return ElMessage.error('Purchase OrderReceivedDataloadnot complete, Please Refreshpage after retry')
  }
  sourceOrder.value=order
  receiptOrderId.value=order.id
  form.supplierId=order.supplierId
  if(!suppliers.value.some((x:any)=>x.id===order.supplierId))suppliers.value.unshift({id:order.supplierId,code:order.supplierCode,name:order.supplierName})
  form.items=order.items.map((x:any)=>({...x,purchaseOrderItemId:x.id,businessType:'ORDER_RECEIPT',orderedQuantity:Number(x.quantity),positiveReceivedQuantity:Number(x.positiveReceivedQuantity||0),returnedQuantity:Number(x.returnedQuantity||0),netReceivedQuantity:Number(x.netReceivedQuantity||0),receivedQuantity:Number(x.netReceivedQuantity||x.receivedQuantity||0),remainingQuantity:Number(x.remainingQuantity||0),overReceivedQuantity:Number(x.overReceivedQuantity||0),quantity:Number(x.remainingQuantity||0),unitPrice:Number(x.unitPrice),referencePrice:Number(x.referencePrice||0)}))
  if(form.items.every((x:any)=>Number(x.remainingQuantity)===0))ElMessage.info('This order is fully received; positive quantities may still increase stock and negative quantities may reduce stock')
}
function duplicateReceiptLine(row:any,negative=false){
  const copy={...row,quantity:negative?-1:1,packageCount:null,remark:''}
  form.items.push(copy)
}
async function editReceipt(rowOrId:any){
  const id=typeof rowOrId==='number'?rowOrId:rowOrId.id
  const{data}=await api.get(`/purchases/receipts/${id}`);const receipt=data.data
  if(receipt.status!=='DRAFT'||receipt.printedAt)return ElMessage.warning('Only unapproved, unprinted purchase receipt drafts can be edited')
  await Promise.all([searchSuppliers(),loadAvailableOrders()]);mode.value='receipt';editingOrderId.value=null;editingOrderVersion.value=0;editingReceiptId.value=receipt.id;editingReceiptVersion.value=receipt.version
  if(!suppliers.value.some((x:any)=>x.id===receipt.supplierId))suppliers.value.unshift({id:receipt.supplierId,code:receipt.supplierCode,name:receipt.supplierName})
  Object.assign(form,{supplierId:receipt.supplierId,receiptDate:receipt.receiptDate,warehouseName:receipt.warehouseName||'Warehouse',settlementMethod:receipt.settlementMethod||'Cash',remark:receipt.remark||'',items:[]})
  if(receipt.purchaseOrderId){
    const orderResponse=await api.get(`/purchases/orders/${receipt.purchaseOrderId}`);const order=orderResponse.data.data;sourceOrder.value=order;receiptOrderId.value=order.id
    const orderItems=new Map(order.items.map((x:any)=>[Number(x.id),x]))
    form.items=receipt.items.map((saved:any)=>{const x:any=orderItems.get(Number(saved.purchaseOrderItemId))||saved;return {...x,...saved,purchaseOrderItemId:saved.purchaseOrderItemId,orderedQuantity:Number(x.quantity||0),positiveReceivedQuantity:Number(x.positiveReceivedQuantity||0),returnedQuantity:Number(x.returnedQuantity||0),netReceivedQuantity:Number(x.netReceivedQuantity||0),receivedQuantity:Number(x.netReceivedQuantity||x.receivedQuantity||0),remainingQuantity:Number(x.remainingQuantity||0),overReceivedQuantity:Number(x.overReceivedQuantity||0),quantity:Number(saved.quantity),unitPrice:Number(saved.unitPrice),referencePrice:Number(saved.referencePrice||0),packageCount:saved.packageCount??x.packageCount,remark:saved.remark||''}})
    const represented=new Set(form.items.map((x:any)=>Number(x.purchaseOrderItemId)))
    form.items.push(...order.items.filter((x:any)=>!represented.has(Number(x.id))).map((x:any)=>({...x,purchaseOrderItemId:x.id,businessType:'ORDER_RECEIPT',orderedQuantity:Number(x.quantity),positiveReceivedQuantity:Number(x.positiveReceivedQuantity||0),returnedQuantity:Number(x.returnedQuantity||0),netReceivedQuantity:Number(x.netReceivedQuantity||0),receivedQuantity:Number(x.netReceivedQuantity||x.receivedQuantity||0),remainingQuantity:Number(x.remainingQuantity||0),overReceivedQuantity:Number(x.overReceivedQuantity||0),quantity:0,unitPrice:Number(x.unitPrice),referencePrice:Number(x.referencePrice||0)})))
  }else{sourceOrder.value=null;receiptOrderId.value=null;form.items=receipt.items.map((x:any)=>({...x,quantity:Number(x.quantity),unitPrice:Number(x.unitPrice),referencePrice:Number(x.referencePrice||0)}))}
  dialog.value=true
}
async function chooseReceiptOrder(orderId:number|null){sourceOrder.value=null;form.supplierId=null;form.items=[];if(!orderId)return;const selected=availableOrders.value.find((x:any)=>x.id===orderId);if(selected?.draftReceiptId)return editReceipt(selected.draftReceiptId);await applySourceOrder(orderId)}
async function selectPurchase(row:any,type:'order'|'receipt'){
  const key=`${type}-${row.id}`
  selectedPurchaseKey.value=key
  inlinePurchaseType.value=type
  inlinePurchaseLoading.value=true
  try{const{data}=await api.get(`/purchases/${type==='order'?'orders':'receipts'}/${row.id}`);if(selectedPurchaseKey.value===key)inlinePurchase.value=data.data}
  finally{if(selectedPurchaseKey.value===key)inlinePurchaseLoading.value=false}
}
function selectPurchaseOrder(row:any){return selectPurchase(row,'order')}
function selectPurchaseReceipt(row:any){return selectPurchase(row,'receipt')}
async function changePurchaseTab(){inlinePurchase.value=null;selectedPurchaseKey.value='';await load()}
async function newReceipt(row?:any){mode.value='receipt';sourceOrder.value=null;editingOrderId.value=null;editingOrderVersion.value=0;editingReceiptId.value=null;editingReceiptVersion.value=0;receiptOrderId.value=row?.id||null;Object.assign(form,{supplierId:null,receiptDate:localToday(),warehouseName:'Warehouse',settlementMethod:'Cash',remark:'',items:[]});await Promise.all([searchSuppliers(),loadAvailableOrders()]);if(row){const{data}=await api.get(`/purchases/orders/${row.id}`);if(data.data.draftReceiptId){ElMessage.info(`confirm digitPending Receipt ${data.data.draftReceiptNo}`);return editReceipt(data.data.draftReceiptId)}await applySourceOrder(row.id)}dialog.value=true}
onMounted(load)
</script>

<template>
<section>
<div class="page-heading">
<div>
<h1>Purchasing</h1>
<p>Create a purchase order first. Record actual goods received in a purchase receipt; stock changes after supervisor approval.</p>
</div>
<div>
<el-button @click="openPrintSummary">{{active==='orders'?'Purchase Order':'Purchase Receipt'}} Summary Print Preview</el-button>
<el-button v-if="active==='receipts'" type="success" @click="newReceipt()">Create Purchase Receipt</el-button>
<el-button v-if="active==='orders'" type="primary" @click="newOrder">Create Purchase Order</el-button>
</div>
</div>
<el-card class="purchase-split-card">
<div class="toolbar">
<el-date-picker v-model="query.dateFrom" type="date" value-format="YYYY-MM-DD" placeholder="Start Date"/>
<span>to</span>
<el-date-picker v-model="query.dateTo" type="date" value-format="YYYY-MM-DD" placeholder="End Date"/>
<el-input v-model="keyword" clearable placeholder="Document No. / Supplier Code / Supplier Name" @keyup.enter="load"/>
<el-select v-if="active==='receipts'" v-model="query.status" clearable placeholder="Approval Status"><el-option label="Pending Approval" value="DRAFT"/><el-option label="Approved" value="APPROVED"/><el-option label="Voided" value="VOIDED"/></el-select>
<el-select v-model="query.printStatus" clearable placeholder="Print Status"><el-option label="Not Printed" value="UNPRINTED"/><el-option label="Printed" value="PRINTED"/></el-select>
<el-button type="primary" @click="load">Search</el-button>
<el-button @click="todayQuery">Today</el-button>
<el-button @click="clearQuery">All History</el-button>
</div>
<el-tabs v-model="active" @tab-change="changePurchaseTab">
<el-tab-pane label="Purchase Order" name="orders">
<el-table :data="orders" v-loading="loading" height="42vh" stripe highlight-current-row class="document-table" :default-sort="{prop:'orderDate',order:'ascending'}" @sort-change="handleSortChange" @row-click="selectPurchaseOrder" @row-dblclick="viewOrder">
<el-table-column prop="orderNo" label="Purchase Order No." width="155" sortable="custom" :sort-orders="['ascending','descending']"/>
<el-table-column prop="orderDate" label="Date" width="110" sortable="custom" :sort-orders="['ascending','descending']"/>
<el-table-column prop="supplierCode" label="Supplier Code" width="120"/>
<el-table-column prop="supplierName" label="Supplier Name" min-width="190"/>
<el-table-column prop="orderedQuantity" label="Ordered" width="85"/>
<el-table-column prop="receivedQuantity" label="Received" width="85"/>
<el-table-column prop="remainingQuantity" label="Remaining" width="85"/>
<el-table-column prop="totalAmount" label="Total Amount" width="120"/>
<el-table-column label="Status" width="110">
<template #default="s">
<el-tag :type="orderStatusType(s.row.fulfillmentStatus)">{{orderStatusText(s.row.fulfillmentStatus)}}</el-tag>
</template>
</el-table-column>
<el-table-column label="Print Status" width="120"><template #default="s"><el-tag :type="s.row.printedAt?'warning':'info'">{{s.row.printedAt?`Printed × ${s.row.printCount||1}`:'Not Printed'}}</el-tag></template></el-table-column>
<el-table-column label="Actions" width="330">
<template #default="s">
<el-button link type="primary" @click="view(s.row,'order')">Details</el-button>
<el-button v-if="s.row.fulfillmentStatus==='ORDERED'&&!s.row.printedAt" link type="primary" @click="editOrder(s.row)">Edit</el-button>
<el-button v-if="s.row.fulfillmentStatus!=='VOIDED'" link type="success" @click="newReceipt(s.row)">Receipt / Reduction</el-button>
<el-button v-if="s.row.fulfillmentStatus==='ORDERED'&&!s.row.printedAt" link type="warning" @click="voidDocument(s.row,'order')">Void</el-button>
<el-button v-if="s.row.fulfillmentStatus==='ORDERED'&&!s.row.printedAt" link type="danger" @click="deleteDocument(s.row,'order')">Delete</el-button>
</template>
</el-table-column>
</el-table>
</el-tab-pane>
<el-tab-pane label="Purchase Receipt" name="receipts">
<el-table :data="receipts" v-loading="loading" height="42vh" stripe highlight-current-row class="document-table" :default-sort="{prop:'receiptDate',order:'ascending'}" @sort-change="handleSortChange" @row-click="selectPurchaseReceipt" @row-dblclick="viewReceipt">
<el-table-column prop="receiptNo" label="Receipt No." width="155" sortable="custom" :sort-orders="['ascending','descending']"/>
<el-table-column prop="orderNo" label="Linked Purchase Order" width="155"/>
<el-table-column prop="receiptDate" label="Date" width="110" sortable="custom" :sort-orders="['ascending','descending']"/>
<el-table-column prop="supplierName" label="Supplier" min-width="220"/>
<el-table-column prop="totalAmount" label="Total Amount" width="120"/>
<el-table-column label="Status" width="100">
<template #default="s">
<el-tag :type="s.row.status==='APPROVED'?'success':s.row.status==='VOIDED'?'danger':'warning'">{{s.row.status==='APPROVED'?'Approved / Stock Updated':s.row.status==='VOIDED'?'Voided':'Pending Approval'}}</el-tag>
</template>
</el-table-column>
<el-table-column label="Print Status" width="120"><template #default="s"><el-tag :type="s.row.printedAt?'warning':'info'">{{s.row.printedAt?`Printed × ${s.row.printCount||1}`:'Not Printed'}}</el-tag></template></el-table-column>
<el-table-column label="Actions" width="300">
<template #default="s">
<el-button link type="primary" @click="view(s.row,'receipt')">Details</el-button>
<el-button v-if="s.row.status==='DRAFT'&&!s.row.printedAt" link type="primary" @click="editReceipt(s.row)">Edit Draft</el-button>
<el-button v-if="s.row.status==='DRAFT'&&!s.row.printedAt" link type="success" @click="approve(s.row)">Supervisor Approval</el-button>
<el-button v-if="s.row.status==='DRAFT'&&!s.row.printedAt" link type="warning" @click="voidDocument(s.row,'receipt')">Void</el-button>
<el-button v-if="s.row.status==='DRAFT'&&!s.row.printedAt" link type="danger" @click="deleteDocument(s.row,'receipt')">Delete</el-button>
</template>
</el-table-column>
</el-table>
</el-tab-pane>
</el-tabs>
<div class="purchase-inline-detail" v-loading="inlinePurchaseLoading">
<div class="purchase-inline-heading">
<div><strong>{{inlinePurchase?`${inlinePurchaseType==='order'?'Purchase Order':'Purchase Receipt'} ${inlinePurchaseType==='order'?inlinePurchase.orderNo:inlinePurchase.receiptNo}  product lines`:'Click a document above to view product lines'}}</strong><span v-if="inlinePurchase">belowareaDetailsavailable scrolls independently; Double-click aboveareaDocumentavailable open completeDetails</span></div>
<span v-if="inlinePurchase">{{inlinePurchase.items?.length||0}} rows · Total ¥{{Number(inlinePurchase.totalAmount||0).toFixed(2)}}</span>
</div>
<el-table v-if="inlinePurchase" :data="inlinePurchase.items" border height="190" size="small">
<el-table-column prop="skuCode" label="Code" width="115"/>
<el-table-column prop="productName" label="Product / Specification" min-width="230"/>
<el-table-column prop="packageCount" label="Packages" width="80"/>
<el-table-column prop="packageUnit" label="Package Unit" width="95"/>
<el-table-column prop="quantity" label="Quantity" width="90"/>
<el-table-column prop="unit" label="Unit" width="75"/>
<el-table-column prop="unitPrice" :label="inlinePurchaseType==='receipt'?'Receipt Unit Price':'Purchase Price'" width="100"/>
<el-table-column v-if="inlinePurchaseType==='receipt'" prop="referencePrice" label="Reference Price" width="90"/>
<el-table-column label="Amount" width="110"><template #default="s">{{Number(s.row.lineAmount??Number(s.row.quantity||0)*Number(s.row.unitPrice||0)).toFixed(2)}}</template></el-table-column>
<el-table-column prop="remark" label="Notes" min-width="130"/>
</el-table>
<el-empty v-else :image-size="54" description="Please select a purchase order or receipt"/>
</div>
</el-card>
<el-dialog v-model="dialog" :title="mode==='order'?(editingOrderId?'Edit Purchase Order Draft':'Create Purchase Order'):editingReceiptId?`Edit Purchase Receipt Draft · ${sourceOrder?.orderNo||''}`:sourceOrder?`Purchase Receipt · ${sourceOrder.orderNo}`:'Create Purchase Receipt'" width="1120px" class="purchase-entry-dialog">
<el-form label-width="100px">
<el-form-item v-if="mode==='receipt'" label="Linked Purchase Order">
<el-select v-model="receiptOrderId" clearable filterable placeholder="Optional; selecting an order loads its outstanding receipt lines" style="width:100%" @change="chooseReceiptOrder">
<el-option v-for="o in availableOrders" :key="o.id" :label="`${o.orderNo} ${o.supplierCode} ${o.supplierName} ${o.draftReceiptNo?`Existing Draft ${o.draftReceiptNo}`:orderStatusText(o.fulfillmentStatus)}`" :value="o.id"/>
</el-select>
<div class="field-help">A purchase order is required. Positive quantities increase stock and negative quantities reduce stock; the same product may appear on both positive and negative lines.</div>
</el-form-item>
<el-row :gutter="16">
<el-col :span="10">
<el-form-item label="Supplier" required>
<el-select v-model="form.supplierId" filterable remote :remote-method="searchSuppliers" :disabled="!!sourceOrder" style="width:100%">
<el-option v-for="s in suppliers" :key="s.id" :label="`${s.code} ${s.name}`" :value="s.id"/>
</el-select>
</el-form-item>
</el-col>
<el-col :span="6">
<el-form-item :label="mode==='order'?'Order Date':'Receipt Date'">
<el-date-picker v-model="form[mode==='order'?'orderDate':'receiptDate']" value-format="YYYY-MM-DD"/>
</el-form-item>
</el-col>
<el-col v-if="mode==='order'" :span="8">
<el-form-item label="Expected Delivery">
<el-date-picker v-model="form.expectedDeliveryDate" value-format="YYYY-MM-DD" clearable/>
</el-form-item>
</el-col>
<el-col v-else :span="8">
<el-form-item label="Warehouse">
<el-input v-model="form.warehouseName"/>
</el-form-item>
</el-col>
</el-row>
<el-form-item v-if="mode==='order'" label="addProduct">
<div class="product-picker">
<el-input v-model="productKeyword" placeholder="Enter product code or name" @keyup.enter="searchProducts"/>
<el-button @click="searchProducts">Search All Products</el-button>
<el-select v-model="productId" filterable placeholder="Select from results">
<el-option v-for="p in products" :key="p.id" :label="`${p.skuCode} ${p.productName}`" :value="p.id"/>
</el-select>
<el-button type="primary" :disabled="!productId" @click="addProduct">Add</el-button>
</div>
</el-form-item>
</el-form>
<template v-if="mode==='order'">
<el-table :data="form.items" border max-height="390">
<el-table-column prop="skuCode" label="Code" width="110"/>
<el-table-column prop="productName" label="Product / Specification" min-width="210"/>
<el-table-column label="Packages" width="105">
<template #default="s">
<el-input-number v-model="s.row.packageCount" :precision="2" :controls="false"/>
</template>
</el-table-column>
<el-table-column label="Package Unit" width="110">
<template #default="s">
<el-input v-model="s.row.packageUnit"/>
</template>
</el-table-column>
<el-table-column label="Quantity" width="115">
<template #default="s">
<el-input-number v-model="s.row.quantity" :precision="2" :controls="false" :class="{'negative-input':Number(s.row.quantity)<0}"/>
</template>
</el-table-column>
<el-table-column label="Unit" width="90">
<template #default="s">
<el-input v-model="s.row.unit"/>
</template>
</el-table-column>
<el-table-column label="Purchase Price" width="125">
<template #default="s">
<el-input-number v-model="s.row.unitPrice" :precision="2" :controls="false"/>
</template>
</el-table-column>
<el-table-column label="Amount" width="110">
<template #default="s"><span :class="{'negative-value':Number(s.row.quantity)<0}">{{(s.row.quantity*s.row.unitPrice).toFixed(2)}}</span></template>
</el-table-column>
<el-table-column label="Actions" width="70">
<template #default="s">
<el-button link type="danger" @click="form.items.splice(s.$index,1)">Delete</el-button>
</template>
</el-table-column>
</el-table>
</template>
<template v-else>
<section class="receipt-entry-section">
<div class="entry-section-title"><div><strong>Stock Change Details</strong><span>Positive quantities add stock and negative quantities reduce stock. The same product may have both positive and negative lines.</span></div><el-tag type="success" effect="plain">{{form.items.length}}  rows</el-tag></div>
<el-table :data="form.items" border max-height="390" empty-text="First Linked Purchase Order, Systemloads positivePending ReceiptLine Items">
<el-table-column prop="skuCode" label="Code" width="92"/>
<el-table-column prop="productName" label="Product / Specification" min-width="155"/>
<el-table-column v-if="!!sourceOrder" prop="orderedQuantity" label="Orders" width="72"/>
<el-table-column v-if="!!sourceOrder" prop="netReceivedQuantity" label="Net Received" width="72"/>
<el-table-column v-if="!!sourceOrder" prop="remainingQuantity" label="Pending Receipt" width="68"/>
<el-table-column label="Current Quantity" width="112"><template #default="s"><el-input-number v-model="s.row.quantity" :precision="2" :controls="false" :class="{'negative-input':Number(s.row.quantity)<0}"/></template></el-table-column>
<el-table-column prop="unit" label="Unit" width="58"/>
<el-table-column label="Unit Price" width="105"><template #default="s"><el-input-number v-model="s.row.unitPrice" :precision="2" :controls="false" :class="receiptPriceClass(s.row)"/></template></el-table-column>
<el-table-column label="Reference Price" width="76"><template #default="s">{{Number(s.row.referencePrice||0).toFixed(2)}}</template></el-table-column>
<el-table-column label="Amount" width="92"><template #default="s"><span :class="{'negative-value':Number(s.row.quantity)<0}">{{(s.row.quantity*s.row.unitPrice).toFixed(2)}}</span></template></el-table-column>
<el-table-column label="Type" width="88"><template #default="s"><el-tag :type="Number(s.row.quantity)<0?'danger':Number(s.row.quantity)>0?'success':'info'" effect="plain">{{Number(s.row.quantity)<0?'Stock Out':Number(s.row.quantity)>0?'Stock In':'Pending Entry'}}</el-tag></template></el-table-column>
<el-table-column label="Actions" width="190"><template #default="s"><el-button link type="success" @click="duplicateReceiptLine(s.row,false)">Copy Positive Row</el-button><el-button link type="danger" @click="duplicateReceiptLine(s.row,true)">Copy Negative Row</el-button><el-button link type="danger" @click="form.items.splice(s.$index,1)">Delete</el-button></template></el-table-column>
</el-table>
</section>
<div class="receipt-summary"><span>Stock-In Amount <b>¥{{receiptAmount.toFixed(2)}}</b></span><span>Stock-Out Amount <b class="negative-value">¥{{returnAmount.toFixed(2)}}</b></span><span>Net Stock Change <b :class="{'negative-value':inventoryNetChange<0}">{{inventoryNetChange}}</b></span><span class="grand-total">Document Total <b>¥{{total.toFixed(2)}}</b></span></div>
</template>
<div v-if="mode==='order'" class="total">Total: ¥{{total.toFixed(2)}}</div>
<el-form label-width="100px">
<el-form-item label="Notes">
<el-input v-model="form.remark"/>
</el-form-item>
</el-form>
<template #footer>
<el-button @click="dialog=false">Cancel</el-button>
<el-button type="primary" @click="save">{{mode==='order'?(editingOrderId?'Save Changes':'Save Purchase Order'):'Save Receipt Draft'}}</el-button>
</template>
</el-dialog>
<el-dialog v-model="detailDialog" :title="detailType==='order'?'Purchase OrderDetails':'Purchase ReceiptDetails'" width="1120px">
<template v-if="detail">
<el-descriptions :column="4" border>
<el-descriptions-item label="Document No.">{{detailType==='order'?detail.orderNo:detail.receiptNo}}</el-descriptions-item>
<el-descriptions-item label="Date">{{detail.orderDate||detail.receiptDate}}</el-descriptions-item>
<el-descriptions-item label="Supplier">{{detail.supplierCode}} {{detail.supplierName}}</el-descriptions-item>
<el-descriptions-item label="Status">
<template v-if="detailType==='order'"><el-tag :type="orderStatusType(detail.fulfillmentStatus)">{{orderStatusText(detail.fulfillmentStatus)}}</el-tag><el-tag v-if="detail.printedAt" type="primary" style="margin-left:6px">Printed {{detail.printCount}} times</el-tag></template>
<template v-else><el-tag :type="detail.status==='APPROVED'?'success':detail.status==='VOIDED'?'danger':'warning'">{{detail.status==='APPROVED'?'Approved / Stock Updated':detail.status==='VOIDED'?'Voided':'Pending Approval'}}</el-tag><el-tag v-if="detail.printedAt" type="primary" style="margin-left:6px">Printed {{detail.printCount}} times</el-tag></template>
</el-descriptions-item>
<el-descriptions-item v-if="detailType==='order'" label="Expected Delivery">{{detail.expectedDeliveryDate||'-'}}</el-descriptions-item>
<el-descriptions-item v-if="detailType==='order'" label="Delivery Location">{{detail.deliveryLocation||'-'}}</el-descriptions-item>
<el-descriptions-item v-if="detailType==='order'" label="Pending Receipt">{{Number(detail.pendingReceiptQuantity||0)}}( (excluded from stock)</el-descriptions-item>
<el-descriptions-item v-if="detailType==='order'&&detail.completedAt" label="Completion Time">{{String(detail.completedAt).replace('T',' ')}}</el-descriptions-item>
<el-descriptions-item v-if="detailType==='order'&&detail.completedAt" label="Completed By">{{detail.completedByName||'-'}}</el-descriptions-item>
<el-descriptions-item v-if="detailType==='order'&&detail.completedAt" label="Completion Reason">{{detail.completionReason||'-'}}</el-descriptions-item>
<el-descriptions-item v-if="detailType==='receipt'" label="Linked Purchase Order">{{detail.orderNo||'Direct Receipt'}}</el-descriptions-item>
<el-descriptions-item v-if="detailType==='receipt'" label="Warehouse">{{detail.warehouseName||'-'}}</el-descriptions-item>
<el-descriptions-item label="Created At">{{String(detail.createdAt||'').replace('T',' ')}}</el-descriptions-item>
<el-descriptions-item label="Notes">{{detail.remark||'-'}}</el-descriptions-item>
</el-descriptions>
<el-table :data="detail.items" border class="detail-items">
<el-table-column prop="skuCode" label="Code" width="120"/>
<el-table-column prop="productName" label="Product / Specification" min-width="240"/>
<el-table-column prop="packageCount" label="Packages" width="90"/>
<el-table-column prop="packageUnit" label="Package Unit" width="100"/>
<el-table-column label="Quantity" width="100"><template #default="s"><span :class="{'negative-value':Number(s.row.quantity)<0}">{{s.row.quantity}}</span></template></el-table-column>
<el-table-column prop="unit" label="Unit" width="85"/>
<el-table-column label="Purchase Price" width="110"><template #default="s"><span :class="detailType==='receipt'?receiptPriceClass(s.row):''">{{s.row.unitPrice}}</span></template></el-table-column>
<el-table-column v-if="detailType==='receipt'" prop="referencePrice" label="Reference Price" width="90"/>
<el-table-column label="Amount" width="120"><template #default="s"><span :class="{'negative-value':Number(s.row.lineAmount)<0}">{{Number(s.row.lineAmount||0).toFixed(2)}}</span></template></el-table-column>
<el-table-column v-if="detailType==='receipt'" label="Stock Change" width="120"><template #default="s"><el-tag :type="Number(s.row.quantity)<0?'danger':'success'" effect="plain">{{s.row.businessType==='UNLINKED_RETURN'?'Historical Unlinked Reductions':s.row.businessType==='ORDER_RETURN'?'Stock Out':s.row.businessType==='ORDER_RECEIPT'?'Stock In':'Historical Unclassified'}}</el-tag></template></el-table-column>
<el-table-column v-if="detailType==='order'" prop="positiveReceivedQuantity" label="Total Positive Receipts" width="100"/>
<el-table-column v-if="detailType==='order'" prop="returnedQuantity" label="Total Reductions" width="100"/>
<el-table-column v-if="detailType==='order'" prop="netReceivedQuantity" label="Net Receipts" width="90"/>
<el-table-column v-if="detailType==='order'" prop="remainingQuantity" label="Remaining" width="85"/>
<el-table-column v-if="detailType==='order'" prop="overReceivedQuantity" label="Over-received" width="85"/>
</el-table>
<div class="total">Total Rows: {{detail.items?.length||0}} Total Quantity: {{(detail.items||[]).reduce((a:number,x:any)=>a+Number(x.quantity||0),0)}} Total Amount: ¥{{Number(detail.totalAmount||0).toFixed(2)}}</div>
</template>
<template #footer>
<el-button v-if="detail" @click="openDetailPrint">Print Preview</el-button>
<el-button type="primary" @click="detailDialog=false">Close</el-button>
</template>
</el-dialog>
<el-dialog v-model="detailPrintDialog" :title="`${detailType==='order'?'Purchase Order':'Purchase Receipt'}Print Preview`" width="980px" class="purchase-print-dialog">
<div v-if="detail" class="purchase-print-sheet purchase-document-sheet">
<h2>{{detailType==='order'?'Purchase Order':'Purchase Receipt'}}</h2>
<div class="document-head">
<span>Supplier: {{detail.supplierCode}} {{detail.supplierName}}</span>
<span>Date: {{detail.orderDate||detail.receiptDate}}</span>
<span>Document No.: {{detailType==='order'?detail.orderNo:detail.receiptNo}}</span>
</div>
<div class="document-head secondary">
<span v-if="detailType==='order'">Expected Delivery: {{detail.expectedDeliveryDate||'-'}}</span>
<span v-if="detailType==='order'">Delivery Location: {{detail.deliveryLocation||'-'}}</span>
<span v-if="detailType==='receipt'">Linked Purchase Order: {{detail.orderNo||'Direct Receipt'}}</span>
<span v-if="detailType==='receipt'">Warehouse: {{detail.warehouseName||'-'}}</span>
<span>Print Status: {{detail.printedAt?`Printed ${detail.printCount} times`:'Not Printed'}}</span>
<span>Printed At: {{printTime}}</span>
</div>
<table>
<thead><tr><th>Code</th><th class="product-name">Product / Specification</th><th>Packages</th><th>Package Unit</th><th>Quantity</th><th>Unit</th><th>Purchase Price</th><th>Amount</th><th>Notes</th></tr></thead>
<tbody>
<tr v-for="item in detail.items" :key="item.id||item.skuId" :class="{'return-row':Number(item.quantity)<0}">
<td>{{item.skuCode}}</td><td class="product-name">{{item.productName}}</td><td>{{item.packageCount??''}}</td><td>{{item.packageUnit||''}}</td><td>{{Number(item.quantity||0)}}</td><td>{{item.unit}}</td><td class="money">{{Number(item.unitPrice||0).toFixed(2)}}</td><td class="money">{{Number(item.lineAmount||0).toFixed(2)}}</td><td>{{item.remark||''}}</td>
</tr>
</tbody>
<tfoot><tr><td colspan="2">Total</td><td colspan="3">Total Rows: {{detail.items?.length||0}} Total Quantity: {{(detail.items||[]).reduce((a:number,x:any)=>a+Number(x.quantity||0),0)}}</td><td colspan="4" class="money">Total Amount: {{Number(detail.totalAmount||0).toFixed(2)}}</td></tr></tfoot>
</table>
<div class="document-foot"><span>Created By: {{detail.createdByName||detail.createdBy||'-'}}</span><span>Created At: {{String(detail.createdAt||'').replace('T',' ')}}</span><span v-if="detailType==='receipt'">Approved By: {{detail.approvedByName||detail.approvedBy||'-'}}</span><span v-if="detailType==='receipt'">Approved At: {{detail.approvedAt?String(detail.approvedAt).replace('T',' '):'-'}}</span></div>
<div class="document-remark">Notes: {{detail.remark||''}}</div>
</div>
<template #footer><el-button @click="detailPrintDialog=false">Close</el-button><el-button type="primary" @click="printDetail">Open System Print</el-button><el-button v-if="detailType==='receipt'&&detail?.status==='APPROVED'" type="success" @click="confirmReceiptPrint">{{detail?.printedAt?'Confirm Successful Reprint':'Confirm Successful Print'}}</el-button></template>
</el-dialog>
<el-dialog v-model="printDialog" :title="`${printTitle}Print Preview`" width="900px" class="purchase-print-dialog">
<div class="purchase-print-sheet">
<h2>{{printTitle}}</h2>
<div class="print-meta">Printed At: {{printTime}}</div>
<table>
<thead>
<tr v-if="active==='orders'"><th>No.</th><th>Date</th><th>Name</th><th>Total Amount</th></tr>
<tr v-else><th>No.</th><th>Date</th><th>Name</th><th>Total Amount</th><th>Notes</th></tr>
</thead>
<tbody>
<tr v-for="(row,index) in printRows" :key="row.id">
<td>{{index+1}}</td>
<td>{{row.orderDate||row.receiptDate}}</td>
<td>{{row.supplierName}}</td>
<td class="money">{{Number(row.totalAmount||0).toFixed(2)}}</td>
<td v-if="active==='receipts'">{{row.remark||''}}</td>
</tr>
<tr v-if="!printRows.length"><td :colspan="active==='orders'?4:5" class="empty">current before recordsitembelowno has available PrintData</td></tr>
</tbody>
<tfoot><tr><td :colspan="active==='orders'?3:4">Total</td><td class="money">{{printTotal.toFixed(2)}}</td></tr></tfoot>
</table>
</div>
<template #footer>
<el-button @click="printDialog=false">Close</el-button>
<el-button type="primary" :disabled="!printRows.length" @click="printSummary">Print</el-button>
</template>
</el-dialog>
</section>
</template>
<style scoped>.toolbar{display:flex;gap:8px;margin-bottom:12px}.toolbar .el-input{width:360px}.field-help{width:100%;color:#718096;font-size:12px;line-height:20px}.product-picker{display:grid;grid-template-columns:250px 90px 1fr 70px;gap:8px;width:100%}.total{text-align:right;font-size:18px;font-weight:700;color:#0f766e;padding:14px 4px}.document-table :deep(.el-table__row){cursor:pointer}.document-table :deep(.newly-created-row>td.el-table__cell){background:#fff3bf!important;transition:background-color 1s ease}.document-table :deep(.newly-created-row>td.el-table__cell:first-child){box-shadow:inset 4px 0 #e6a23c}.detail-items{margin-top:16px}.negative-value{color:#c62828;font-weight:700}.negative-input :deep(.el-input__inner){color:#c62828;font-weight:700}.receipt-entry-section{margin-top:4px;border:1px solid #dce4ea;border-radius:6px;overflow:hidden}.receipt-entry-section+.receipt-entry-section{margin-top:14px}.entry-section-title{display:flex;align-items:center;justify-content:space-between;padding:10px 12px;background:#f5faf8}.entry-section-title strong{font-size:16px;color:#17483d}.entry-section-title span{margin-left:12px;color:#718096;font-size:12px}.return-section{border-color:#f0d7d7}.return-section .entry-section-title{background:#fff7f6}.return-section .entry-section-title strong{color:#9f2d2d}.receipt-summary{display:flex;align-items:center;justify-content:flex-end;gap:24px;padding:15px 4px 5px;color:#52606d}.receipt-summary b{margin-left:6px;color:#153e36;font-variant-numeric:tabular-nums}.receipt-summary .grand-total{font-size:18px;color:#0f766e}.receipt-summary .grand-total b{color:#0f766e}.receipt-summary b.negative-value{color:#c62828}@media(max-width:900px){.product-picker{grid-template-columns:1fr}.receipt-summary{align-items:flex-end;flex-direction:column;gap:6px}}</style>
<style scoped>
.purchase-split-card :deep(.el-card__body){padding-bottom:12px}
.purchase-inline-detail{height:245px;margin-top:10px;padding-top:10px;overflow:hidden;border-top:2px solid #c9ced6}
.purchase-inline-heading{display:flex;justify-content:space-between;align-items:center;gap:16px;height:35px;margin-bottom:7px;color:#0f766e}
.purchase-inline-heading>div{display:flex;flex-direction:column;gap:2px}
.purchase-inline-heading span{font-size:12px;color:#606266}
.price-high{color:#1677ff;font-weight:700}
.price-low{color:#dc2626;font-weight:700}
.price-high :deep(.el-input__inner){color:#1677ff;font-weight:700}
.price-low :deep(.el-input__inner){color:#dc2626;font-weight:700}
</style>
<style scoped>
.toolbar{align-items:center;flex-wrap:wrap}
.toolbar .el-date-editor{width:150px}
.toolbar .el-select{width:130px}
</style>
<style>
.purchase-print-sheet{color:#111;background:#fff;padding:12px 18px 28px}.purchase-print-sheet h2{text-align:center;letter-spacing:8px;margin:0 0 12px}.purchase-print-sheet .print-meta{margin-bottom:10px;font-size:14px}.purchase-print-sheet table{width:100%;border-collapse:collapse;table-layout:fixed}.purchase-print-sheet th,.purchase-print-sheet td{border:1px solid #222;padding:7px 9px;text-align:center;word-break:break-all}.purchase-print-sheet th:first-child,.purchase-print-sheet td:first-child{width:62px}.purchase-print-sheet .money{text-align:right;font-variant-numeric:tabular-nums}.purchase-print-sheet tfoot td{font-weight:700}.purchase-print-sheet .empty{color:#777;padding:30px}
.purchase-document-sheet .document-head{display:grid;grid-template-columns:1.4fr 1fr 1.2fr;gap:12px;margin:8px 0}.purchase-document-sheet .document-head.secondary{grid-template-columns:repeat(3,1fr);font-size:13px}.purchase-document-sheet th{font-size:13px}.purchase-document-sheet th.product-name,.purchase-document-sheet td.product-name{width:24%;text-align:left}.purchase-document-sheet .document-foot{display:flex;flex-wrap:wrap;gap:10px 30px;margin-top:14px;font-size:13px}.purchase-document-sheet .document-remark{margin-top:10px;border-bottom:1px solid #444;min-height:24px}
.purchase-document-sheet .return-row td{color:#b91c1c}
@media print{
  body *{visibility:hidden!important}
  .purchase-print-sheet,.purchase-print-sheet *{visibility:visible!important}
  .purchase-print-sheet{position:absolute;left:0;top:0;width:100%;padding:0;font-size:12pt}
  .purchase-print-sheet h2{font-size:20pt;margin-bottom:12pt}
  .purchase-print-dialog .el-dialog__header,.purchase-print-dialog .el-dialog__footer{display:none!important}
  @page{size:A4 portrait;margin:12mm}
}
</style>


