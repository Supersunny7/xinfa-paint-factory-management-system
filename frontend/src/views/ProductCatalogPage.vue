<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api'
const router=useRouter(),categories=ref<any[]>([]),selectedParent=ref<any>(null),selectedChild=ref<any>(null),rows=ref<any[]>([]),total=ref(0),loading=ref(false)
const exportLoading=ref(false)
const isAdmin=localStorage.getItem('role')==='ADMIN'
const query=reactive({keyword:'',page:1,pageSize:20})
const currentTitle=computed(()=>selectedChild.value?`${selectedParent.value.name} · ${selectedChild.value.name}`:selectedParent.value?`${selectedParent.value.name} · All Products`:'All Products')
async function loadCategories(){const{data}=await api.get('/product-categories/tree');categories.value=data.data;selectedParent.value=categories.value.find((x:any)=>x.productCount>0)||categories.value[0]||null;await loadProducts()}
async function loadProducts(){loading.value=true;try{const categoryId=selectedChild.value?.id||selectedParent.value?.id||null;const{data}=await api.get('/products',{params:{...query,enabled:true,categoryId}});rows.value=data.data.items;total.value=data.data.total}finally{loading.value=false}}
function chooseParent(item:any){selectedParent.value=item;selectedChild.value=null;query.page=1;loadProducts()}
function chooseChild(item:any|null){selectedChild.value=item;query.page=1;loadProducts()}
function search(){query.page=1;loadProducts()}
async function exportProducts(){
  exportLoading.value=true
  try{
    const response=await api.get('/products/export.xlsx',{responseType:'blob'})
    const disposition=String(response.headers['content-disposition']||'')
    const match=disposition.match(/filename\*=UTF-8''([^;]+)/i)
    const filename=match?decodeURIComponent(match[1]):`Product Classification Summary-${new Date().toISOString().slice(0,10)}.xlsx`
    const url=URL.createObjectURL(response.data)
    const link=document.createElement('a')
    link.href=url;link.download=filename;link.click();URL.revokeObjectURL(url)
    ElMessage.success('Product workbook generated')
  }catch(error:any){ElMessage.error(error?.response?.data?.message||'ExportFailed, Please try again later')}
  finally{exportLoading.value=false}
}
onMounted(loadCategories)
</script>
<template><section><div class="page-heading"><div><h1>Find Products by Manufacturer</h1><p>Select a manufacturer and product subcategory, or search directly by product code or name. </p></div><div><el-button type="success" :loading="exportLoading" @click="exportProducts">Export Classification to Excel</el-button><el-button v-if="isAdmin" type="warning" @click="router.push('/products/classification')">Products Awaiting Classification</el-button><el-button @click="router.push('/products/manage')">Product Management</el-button></div></div>
<div class="catalog-layout"><el-card class="brand-panel"><template #header><strong>Step 1: Select Manufacturer</strong></template><div class="brand-list"><button v-for="item in categories" :key="item.id" class="brand-card" :class="{active:selectedParent?.id===item.id}" @click="chooseParent(item)"><span class="brand-name">{{item.name}}</span><span class="brand-code">Code {{item.code}}</span><el-badge :value="item.productCount" :hidden="!item.productCount"/></button></div></el-card>
<div class="content-panel"><el-card class="sub-panel"><template #header><div><strong>Step 2: Select a product subcategory for {{selectedParent?.name||''}}</strong><span class="hint">Select “All” to view every product under this manufacturer</span></div></template><div class="sub-list"><button class="sub-card" :class="{active:!selectedChild}" @click="chooseChild(null)"><b>All</b><small>{{selectedParent?.productCount||0}} products</small></button><button v-for="item in selectedParent?.children||[]" :key="item.id" class="sub-card" :class="{active:selectedChild?.id===item.id}" @click="chooseChild(item)"><b>{{item.name}}</b><span>Code {{item.code}}</span><small>{{item.productCount}} products</small></button></div></el-card>
<el-card class="products-panel"><div class="products-head"><div><h2>{{currentTitle}}</h2><span>Total: {{total}} items</span></div><div class="search"><el-input v-model="query.keyword" clearable placeholder="Enter product name or code" @keyup.enter="search"/><el-button type="primary" @click="search">Find</el-button></div></div><el-empty v-if="!loading&&!rows.length" description="No products in this category"/><el-table v-else :data="rows" v-loading="loading" stripe><el-table-column prop="skuCode" label="Product Code" width="130"/><el-table-column prop="productName" label="Product Name" min-width="190"/><el-table-column prop="specification" label="Specification" width="130"/><el-table-column prop="salesUnit" label="Unit" width="80"/><el-table-column prop="totalStock" label="Inventory" width="100"/><el-table-column prop="stockLowerLimit" label="Minimum Stock" width="100"/><el-table-column prop="lastPurchasePrice" label="Recent Purchase Price" width="105"/><el-table-column prop="wholesalePrice" label="Wholesale Price" width="100"/></el-table><el-pagination v-if="total>query.pageSize" v-model:current-page="query.page" :page-size="query.pageSize" :total="total" layout="total, prev, pager, next" @current-change="loadProducts"/></el-card></div></div></section></template>
<style scoped>.catalog-layout{display:grid;grid-template-columns:250px 1fr;gap:16px}.brand-panel{height:calc(100vh - 150px);overflow:auto}.brand-list{display:grid;gap:9px}.brand-card,.sub-card{border:1px solid #dcdfe6;background:#fff;border-radius:9px;cursor:pointer;text-align:left;transition:.15s}.brand-card{padding:13px 14px;display:grid;grid-template-columns:1fr auto;align-items:center}.brand-card:hover,.sub-card:hover{border-color:#409eff}.brand-card.active,.sub-card.active{background:#ecf5ff;border-color:#409eff;box-shadow:0 0 0 1px #409eff inset}.brand-name{font-size:17px;font-weight:700}.brand-code{font-size:12px;color:#909399}.content-panel{display:grid;gap:16px;min-width:0}.hint{margin-left:12px;color:#909399;font-weight:400}.sub-list{display:flex;gap:10px;flex-wrap:wrap}.sub-card{min-width:132px;padding:10px 12px;display:grid;gap:3px}.sub-card b{font-size:16px}.sub-card span,.sub-card small{font-size:12px;color:#909399}.products-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px}.products-head h2{display:inline;margin:0 10px 0 0;font-size:20px}.search{display:flex;width:390px;gap:8px}@media(max-width:1000px){.catalog-layout{grid-template-columns:1fr}.brand-panel{height:auto}.brand-list{grid-template-columns:repeat(3,1fr)}}
</style>


