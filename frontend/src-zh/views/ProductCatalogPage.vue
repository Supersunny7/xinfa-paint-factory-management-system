<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api'
const router=useRouter(),categories=ref<any[]>([]),selectedParent=ref<any>(null),selectedChild=ref<any>(null),rows=ref<any[]>([]),total=ref(0),loading=ref(false)
const exportLoading=ref(false)
const isAdmin=localStorage.getItem('role')==='ADMIN'
const query=reactive({keyword:'',page:1,pageSize:20})
const categoryNames:Record<string,string>={'000':'建筑涂料','001':'工业涂料','010':'涂装工具','016':'内部用品',PRI:'底漆',TOP:'面漆',WAL:'墙面漆',FLR:'地坪漆'}
const categoryName=(item:any)=>item?categoryNames[String(item.code)]||item.name:''
const currentTitle=computed(()=>selectedChild.value?`${categoryName(selectedParent.value)} · ${categoryName(selectedChild.value)}`:selectedParent.value?`${categoryName(selectedParent.value)} · 全部货品`:'全部货品')
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
    const filename=match?decodeURIComponent(match[1]):`货品资料分类总表-${new Date().toISOString().slice(0,10)}.xlsx`
    const url=URL.createObjectURL(response.data)
    const link=document.createElement('a')
    link.href=url;link.download=filename;link.click();URL.revokeObjectURL(url)
    ElMessage.success('货品资料 Excel 已生成')
  }catch(error:any){ElMessage.error(error?.response?.data?.message||'导出失败，请稍后重试')}
  finally{exportLoading.value=false}
}
onMounted(loadCategories)
</script>
<template><section><div class="page-heading"><div><h1>按厂家找货品</h1><p>先选厂家，再选货物小类；不需要记编号，也不用在几千条货品中翻找</p></div><div><el-button type="success" :loading="exportLoading" @click="exportProducts">导出分类 Excel</el-button><el-button v-if="isAdmin" type="warning" @click="router.push('/products/classification')">待归类货品</el-button><el-button @click="router.push('/products/manage')">货品资料管理</el-button></div></div>
<div class="catalog-layout"><el-card class="brand-panel"><template #header><strong>第一步：选择厂家</strong></template><div class="brand-list"><button v-for="item in categories" :key="item.id" class="brand-card" :class="{active:selectedParent?.id===item.id}" @click="chooseParent(item)"><span class="brand-name">{{categoryName(item)}}</span><span class="brand-code">编号 {{item.code}}</span><el-badge :value="item.productCount" :hidden="!item.productCount"/></button></div></el-card>
<div class="content-panel"><el-card class="sub-panel"><template #header><div><strong>第二步：选择 {{categoryName(selectedParent)}} 的货物小类</strong><span class="hint">选“全部”可查看该厂家的所有货品</span></div></template><div class="sub-list"><button class="sub-card" :class="{active:!selectedChild}" @click="chooseChild(null)"><b>全部</b><small>{{selectedParent?.productCount||0}} 种货品</small></button><button v-for="item in selectedParent?.children||[]" :key="item.id" class="sub-card" :class="{active:selectedChild?.id===item.id}" @click="chooseChild(item)"><b>{{categoryName(item)}}</b><span>编号 {{item.code}}</span><small>{{item.productCount}} 种货品</small></button></div></el-card>
<el-card class="products-panel"><div class="products-head"><div><h2>{{currentTitle}}</h2><span>共 {{total}} 种</span></div><div class="search"><el-input v-model="query.keyword" clearable placeholder="输入货品名称或编号" @keyup.enter="search"/><el-button type="primary" @click="search">查找</el-button></div></div><el-empty v-if="!loading&&!rows.length" description="这个分类暂时没有货品"/><el-table v-else :data="rows" v-loading="loading" stripe><el-table-column prop="skuCode" label="货品编号" width="130"/><el-table-column prop="productName" label="品名" min-width="190"/><el-table-column prop="specification" label="规格" width="130"/><el-table-column prop="salesUnit" label="单位" width="80"/><el-table-column prop="totalStock" label="库存" width="100"/><el-table-column prop="stockLowerLimit" label="库存下限" width="100"/><el-table-column prop="lastPurchasePrice" label="最近进价" width="105"/><el-table-column prop="wholesalePrice" label="批发价" width="100"/></el-table><el-pagination v-if="total>query.pageSize" v-model:current-page="query.page" :page-size="query.pageSize" :total="total" layout="total, prev, pager, next" @current-change="loadProducts"/></el-card></div></div></section></template>
<style scoped>.catalog-layout{display:grid;grid-template-columns:250px 1fr;gap:16px}.brand-panel{height:calc(100vh - 150px);overflow:auto}.brand-list{display:grid;gap:9px}.brand-card,.sub-card{border:1px solid #dcdfe6;background:#fff;border-radius:9px;cursor:pointer;text-align:left;transition:.15s}.brand-card{padding:13px 14px;display:grid;grid-template-columns:1fr auto;align-items:center}.brand-card:hover,.sub-card:hover{border-color:#409eff}.brand-card.active,.sub-card.active{background:#ecf5ff;border-color:#409eff;box-shadow:0 0 0 1px #409eff inset}.brand-name{font-size:17px;font-weight:700}.brand-code{font-size:12px;color:#909399}.content-panel{display:grid;gap:16px;min-width:0}.hint{margin-left:12px;color:#909399;font-weight:400}.sub-list{display:flex;gap:10px;flex-wrap:wrap}.sub-card{min-width:132px;padding:10px 12px;display:grid;gap:3px}.sub-card b{font-size:16px}.sub-card span,.sub-card small{font-size:12px;color:#909399}.products-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px}.products-head h2{display:inline;margin:0 10px 0 0;font-size:20px}.search{display:flex;width:390px;gap:8px}@media(max-width:1000px){.catalog-layout{grid-template-columns:1fr}.brand-panel{height:auto}.brand-list{grid-template-columns:repeat(3,1fr)}}
</style>
