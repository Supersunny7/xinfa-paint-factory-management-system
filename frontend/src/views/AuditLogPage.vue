<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api } from '../api'
const rows=ref<any[]>([]),total=ref(0),loading=ref(false),query=reactive({keyword:'',page:1,pageSize:20})
const typeNames:any={suppliers:'Supplier',employees:'Employee',vehicles:'Vehicle','employee-types':'Employee Types',departments:'Department',routes:'Route',users:'User Accounts'}
const actionNames:any={CREATE:'Add',UPDATE:'Edit',ENABLE:'Enable',DISABLE:'Disable',DELETE:'Delete Permanently'}
const formatDateTime=(value:any)=>value?String(value).replace('T',' ').slice(0,19):'—'
async function load(){loading.value=true;try{const{data}=await api.get('/reference-data/audit-logs',{params:query});rows.value=data.data.items;total.value=data.data.total}finally{loading.value=false}}
function search(){query.page=1;load()} onMounted(load)
</script>
<template><section><div class="page-heading"><div><h1>Audit Log</h1><p>Search additions, edits, disables, restorations, and deletions of master data</p></div></div><el-card><div class="toolbar"><el-input v-model="query.keyword" clearable placeholder="Record Code / Name / Operator / Action" @keyup.enter="search"/><el-button type="primary" @click="search">Search</el-button></div><el-table :data="rows" v-loading="loading" stripe><el-table-column label="Operation Time" width="190"><template #default="s">{{formatDateTime(s.row.createdAt)}}</template></el-table-column><el-table-column label="Data Type" width="110"><template #default="s">{{typeNames[s.row.entityType]||s.row.entityType}}</template></el-table-column><el-table-column prop="entityCode" label="Record Code" width="150"/><el-table-column prop="entityName" label="Data Name" min-width="180"/><el-table-column label="Action" width="110"><template #default="s"><el-tag>{{actionNames[s.row.action]||s.row.action}}</el-tag></template></el-table-column><el-table-column prop="operatorName" label="Operator" width="120"/><el-table-column prop="details" label="Description" min-width="180"/></el-table><el-pagination v-model:current-page="query.page" :page-size="query.pageSize" :total="total" layout="total, prev, pager, next" @current-change="load"/></el-card></section></template>


