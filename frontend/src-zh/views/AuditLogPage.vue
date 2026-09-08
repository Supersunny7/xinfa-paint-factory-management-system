<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api } from '../api'
const rows=ref<any[]>([]),total=ref(0),loading=ref(false),query=reactive({keyword:'',page:1,pageSize:20})
const typeNames:any={suppliers:'供应商',employees:'员工',vehicles:'车辆','employee-types':'员工类别',departments:'部门',routes:'路线',users:'用户账号'}
const actionNames:any={CREATE:'新增',UPDATE:'编辑',ENABLE:'恢复使用',DISABLE:'停用',DELETE:'永久删除'}
const formatDateTime=(value:any)=>value?String(value).replace('T',' ').slice(0,19):'—'
async function load(){loading.value=true;try{const{data}=await api.get('/reference-data/audit-logs',{params:query});rows.value=data.data.items;total.value=data.data.total}finally{loading.value=false}}
function search(){query.page=1;load()} onMounted(load)
</script>
<template><section><div class="page-heading"><div><h1>操作日志</h1><p>查询基础资料的新增、编辑、停用、恢复和删除记录</p></div></div><el-card><div class="toolbar"><el-input v-model="query.keyword" clearable placeholder="资料编号 / 名称 / 操作人 / 动作" @keyup.enter="search"/><el-button type="primary" @click="search">查询</el-button></div><el-table :data="rows" v-loading="loading" stripe><el-table-column label="操作时间" width="190"><template #default="s">{{formatDateTime(s.row.createdAt)}}</template></el-table-column><el-table-column label="资料类型" width="110"><template #default="s">{{typeNames[s.row.entityType]||s.row.entityType}}</template></el-table-column><el-table-column prop="entityCode" label="资料编号" width="150"/><el-table-column prop="entityName" label="资料名称" min-width="180"/><el-table-column label="动作" width="110"><template #default="s"><el-tag>{{actionNames[s.row.action]||s.row.action}}</el-tag></template></el-table-column><el-table-column prop="operatorName" label="操作人" width="120"/><el-table-column prop="details" label="说明" min-width="180"/></el-table><el-pagination v-model:current-page="query.page" :page-size="query.pageSize" :total="total" layout="total, prev, pager, next" @current-change="load"/></el-card></section></template>
