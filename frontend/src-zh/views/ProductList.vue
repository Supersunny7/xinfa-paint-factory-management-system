<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'

const query=reactive({keyword:'',page:1,pageSize:20}),rows=ref<any[]>([]),total=ref(0),loading=ref(false),dialog=ref(false),editing=ref(false),importing=ref(false),categories=ref<any[]>([]),parentCategoryId=ref<number|null>(null)
const fileInput=ref<HTMLInputElement|null>(null)
const empty=()=>({id:0,skuCode:'',productName:'',specification:'',color:'',salesUnit:'桶',packageSpec:null as number|null,packageUnit:'',wholesalePrice:null as number|null,retailPrice:null as number|null,totalStock:0,stockLowerLimit:0,lastPurchasePrice:null as number|null,categoryId:null as number|null,version:0})
const form=reactive(empty())

const inventoryDialog=ref(false),inventoryLoading=ref(false),movementRows=ref<any[]>([]),movementTotal=ref(0),movementPage=ref(1),selectedProduct=ref<any>(null)
const adjustment=reactive({type:'INBOUND',quantity:null as number|null,reason:''})
const quantityLabel=computed(()=>adjustment.type==='ADJUSTMENT'?'盘点后库存':'变动数量')
const quantityMin=computed(()=>adjustment.type==='ADJUSTMENT'?0:0.01)
const movementLabels:Record<string,string>={INBOUND:'入库',OUTBOUND:'出库',ADJUSTMENT:'盘点调整',REVERSAL:'撤销出库'}

async function load(){loading.value=true;try{const{data}=await api.get('/products',{params:{...query,enabled:true}});rows.value=data.data.items;total.value=data.data.total}finally{loading.value=false}}
const childCategories=computed(()=>categories.value.find((x:any)=>x.id===parentCategoryId.value)?.children||[])
async function loadCategories(){const{data}=await api.get('/product-categories/tree');categories.value=data.data}
function search(){query.page=1;load()}
function add(){Object.assign(form,empty());parentCategoryId.value=null;editing.value=false;dialog.value=true}
function edit(row:any){Object.assign(form,row);parentCategoryId.value=row.parentCategoryId||null;if(!row.parentCategoryId)form.categoryId=null;editing.value=true;dialog.value=true}
function chooseParent(){form.categoryId=null}
async function save(){if(!form.skuCode.trim()||!form.productName.trim()||!form.salesUnit.trim())return ElMessage.warning('编号、品名和单位不能为空');if(!parentCategoryId.value||!form.categoryId)return ElMessage.warning('请先选择大类和小类');try{editing.value?await api.put(`/products/${form.id}`,form):await api.post('/products',form);ElMessage.success('保存成功');dialog.value=false;load()}catch(e:any){ElMessage.error(e.response?.data?.message||'保存失败，请检查货品编号和分类')}}
async function disable(row:any){await ElMessageBox.confirm(`确定停用货品“${row.productName}”吗？`,'停用确认',{type:'warning'});await api.patch(`/products/${row.id}/enabled`,{enabled:false,version:row.version});ElMessage.success('已停用');load()}
function chooseCsv(){fileInput.value?.click()}
async function importCsv(event:Event){const input=event.target as HTMLInputElement;const file=input.files?.[0];if(!file)return;importing.value=true;try{const body=new FormData();body.append('file',file);const{data}=await api.post('/products/import',body);const result=data.data;ElMessage.success(`导入完成：新增 ${result.inserted}，更新 ${result.updated}，失败 ${result.failed}`);if(result.errors?.length)await ElMessageBox.alert(result.errors.map((x:any)=>`第 ${x.row} 行：${x.message}`).join('\n'),'导入错误',{type:'warning'});await load()}finally{importing.value=false;input.value=''}}
function stockTag(row:any){return row.stockStatus==='NEGATIVE'||row.stockStatus==='SHORTAGE'?'danger':'success'}
function stockText(row:any){return row.stockStatus==='NEGATIVE'?'负库存':row.stockStatus==='SHORTAGE'?'缺货':'正常'}

async function openInventory(row:any){selectedProduct.value=row;Object.assign(adjustment,{type:'INBOUND',quantity:null,reason:''});movementPage.value=1;inventoryDialog.value=true;await loadMovements()}
async function loadMovements(){if(!selectedProduct.value)return;inventoryLoading.value=true;try{const{data}=await api.get(`/products/${selectedProduct.value.id}/inventory-movements`,{params:{page:movementPage.value,pageSize:10}});movementRows.value=data.data.items;movementTotal.value=data.data.total}finally{inventoryLoading.value=false}}
async function submitAdjustment(){if(adjustment.quantity===null||adjustment.quantity<quantityMin.value)return ElMessage.warning(adjustment.type==='ADJUSTMENT'?'盘点库存不能小于 0':'请输入大于 0 的数量');if(!adjustment.reason.trim())return ElMessage.warning('请填写变动原因');try{await api.post(`/products/${selectedProduct.value.id}/inventory-adjustments`,adjustment);ElMessage.success('库存变动已保存');await load();selectedProduct.value=rows.value.find(x=>x.id===selectedProduct.value.id)||selectedProduct.value;adjustment.quantity=null;adjustment.reason='';movementPage.value=1;await loadMovements()}catch(e:any){ElMessage.error(e.response?.data?.message||'库存调整失败')}}
function signed(value:any){const n=Number(value);return n>0?`+${n}`:`${n}`}
function formatTime(value:string){return value?value.replace('T',' ').slice(0,19):''}
onMounted(()=>{loadCategories();load()})
</script>

<template>
  <section>
    <div class="page-heading"><div><h1>货品资料</h1><p>维护价格、库存下限和最近进价；库存数量通过流水调整</p></div><div><input ref="fileInput" type="file" accept=".csv,text/csv" hidden @change="importCsv"><el-button :loading="importing" @click="chooseCsv">导入货品资料 CSV（不改库存）</el-button><el-button type="primary" @click="add">新增货品</el-button></div></div>
    <el-card><div class="toolbar product-toolbar"><el-input v-model="query.keyword" clearable placeholder="货品编号 / 名称 / 条码" @keyup.enter="search"/><el-button type="primary" @click="search">查询</el-button></div>
      <el-table :data="rows" v-loading="loading" stripe><el-table-column prop="skuCode" label="编号" width="105"/><el-table-column prop="productName" label="品名" min-width="150"/><el-table-column prop="parentCategoryName" label="大类" width="100"/><el-table-column prop="categoryName" label="小类" width="110"/><el-table-column prop="specification" label="规格" width="105"/><el-table-column prop="color" label="颜色" width="75"/><el-table-column prop="salesUnit" label="单位" width="60"/><el-table-column label="总库存" width="90"><template #default="s"><span :class="Number(s.row.totalStock)<0?'negative-stock':''">{{s.row.totalStock}}</span></template></el-table-column><el-table-column prop="stockLowerLimit" label="库存下限" width="90"/><el-table-column prop="shortageQuantity" label="缺货数量" width="90"/><el-table-column prop="lastPurchasePrice" label="最近进价" width="90"/><el-table-column prop="wholesalePrice" label="批发价" width="80"/><el-table-column label="库存状态" width="90"><template #default="s"><el-tag :type="stockTag(s.row)">{{stockText(s.row)}}</el-tag></template></el-table-column><el-table-column label="操作" width="220" fixed="right"><template #default="s"><el-button link type="primary" @click="edit(s.row)">编辑</el-button><el-button link type="success" @click="openInventory(s.row)">库存调整/流水</el-button><el-button link type="danger" @click="disable(s.row)">停用</el-button></template></el-table-column></el-table>
      <el-pagination v-model:current-page="query.page" :page-size="query.pageSize" :total="total" layout="total, prev, pager, next" @current-change="load"/>
    </el-card>
    <el-dialog v-model="dialog" :title="editing?'编辑货品':'新增货品'" width="760px"><el-form label-width="100px"><el-row :gutter="16"><el-col :span="12"><el-form-item label="大类" required><el-select v-model="parentCategoryId" filterable placeholder="选择厂家/大类" style="width:100%" @change="chooseParent"><el-option v-for="c in categories" :key="c.id" :label="`${c.code} ${c.name}`" :value="c.id"/></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="小类" required><el-select v-model="form.categoryId" filterable placeholder="选择货物小类" style="width:100%" :disabled="!parentCategoryId"><el-option v-for="c in childCategories" :key="c.id" :label="`${c.code} ${c.name}`" :value="c.id"/></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="货品编号" required><el-input v-model="form.skuCode"/></el-form-item></el-col><el-col :span="12"><el-form-item label="品名" required><el-input v-model="form.productName"/></el-form-item></el-col><el-col :span="12"><el-form-item label="规格"><el-input v-model="form.specification"/></el-form-item></el-col><el-col :span="12"><el-form-item label="颜色"><el-input v-model="form.color"/></el-form-item></el-col><el-col :span="12"><el-form-item label="销售单位" required><el-input v-model="form.salesUnit"/></el-form-item></el-col><el-col :span="12"><el-form-item label="包装规格"><el-input-number v-model="form.packageSpec" :min="0"/></el-form-item></el-col><el-col :span="12"><el-form-item label="包装单位"><el-input v-model="form.packageUnit"/></el-form-item></el-col><el-col :span="12"><el-form-item label="总库存"><el-input-number v-model="form.totalStock" :disabled="editing" :min="0" :precision="2"/><div v-if="editing" class="field-tip">请使用库存调整功能</div></el-form-item></el-col><el-col :span="12"><el-form-item label="库存下限"><el-input-number v-model="form.stockLowerLimit" :min="0" :precision="2"/></el-form-item></el-col><el-col :span="12"><el-form-item label="最近进价"><el-input-number v-model="form.lastPurchasePrice" :min="0" :precision="2"/></el-form-item></el-col><el-col :span="12"><el-form-item label="批发价"><el-input-number v-model="form.wholesalePrice" :min="0" :precision="2"/></el-form-item></el-col><el-col :span="12"><el-form-item label="零售价"><el-input-number v-model="form.retailPrice" :min="0" :precision="2"/></el-form-item></el-col></el-row></el-form><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template></el-dialog>

    <el-dialog v-model="inventoryDialog" :title="`库存管理 · ${selectedProduct?.skuCode || ''} ${selectedProduct?.productName || ''}`" width="900px">
      <el-alert :closable="false" :type="Number(selectedProduct?.totalStock)<0?'error':'info'" show-icon :title="Number(selectedProduct?.totalStock)<0?`当前库存：${selectedProduct?.totalStock} ${selectedProduct?.salesUnit||''}；库存状态：负库存。请核对采购退货、销售出库或历史库存。`:`当前库存：${selectedProduct?.totalStock ?? 0} ${selectedProduct?.salesUnit || ''}`"/>
      <el-form inline class="inventory-form">
        <el-form-item label="类型"><el-select v-model="adjustment.type" style="width:130px"><el-option label="入库" value="INBOUND"/><el-option label="出库" value="OUTBOUND"/><el-option label="盘点调整" value="ADJUSTMENT"/></el-select></el-form-item>
        <el-form-item :label="quantityLabel"><el-input-number v-model="adjustment.quantity" :min="quantityMin" :precision="2"/></el-form-item>
        <el-form-item label="原因"><el-input v-model="adjustment.reason" maxlength="500" placeholder="例如：采购入库、样品领用、月末盘点" style="width:260px"/></el-form-item>
        <el-form-item><el-button type="primary" @click="submitAdjustment">保存变动</el-button></el-form-item>
      </el-form>
      <el-divider content-position="left">库存流水</el-divider>
      <el-table :data="movementRows" v-loading="inventoryLoading" stripe height="330"><el-table-column label="类型" width="100"><template #default="s">{{movementLabels[s.row.movementType]}}</template></el-table-column><el-table-column label="变动" width="90"><template #default="s"><span :class="Number(s.row.quantityChange)>0?'stock-in':'stock-out'">{{signed(s.row.quantityChange)}}</span></template></el-table-column><el-table-column prop="beforeQuantity" label="变动前" width="90"/><el-table-column label="变动后" width="90"><template #default="s"><span :class="Number(s.row.afterQuantity)<0?'negative-stock':''">{{s.row.afterQuantity}}</span></template></el-table-column><el-table-column prop="referenceNo" label="来源单据" width="145"><template #default="s">{{s.row.referenceNo||'人工调整'}}</template></el-table-column><el-table-column prop="reason" label="原因" min-width="180"/><el-table-column prop="operatorName" label="操作人" width="100"/><el-table-column label="时间" width="175"><template #default="s">{{formatTime(s.row.createdAt)}}</template></el-table-column></el-table>
      <el-pagination v-model:current-page="movementPage" :page-size="10" :total="movementTotal" layout="total, prev, pager, next" @current-change="loadMovements"/>
    </el-dialog>
  </section>
</template>

<style scoped>
.field-tip{margin-left:8px;color:#909399;font-size:12px}.inventory-form{margin-top:18px}.stock-in{color:#16a34a;font-weight:600}.stock-out,.negative-stock{color:#dc2626;font-weight:700}
</style>
