<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route=useRoute(),router=useRouter()
const isLogin=computed(()=>route.path==='/login')
const displayName=computed(()=>{route.path;return localStorage.getItem('displayName')||'当前用户'})
const role=computed(()=>{route.path;return localStorage.getItem('role')||'ADMIN'})
const now=ref(new Date());let clockTimer:number|undefined
const dateTime=computed(()=>new Intl.DateTimeFormat('zh-CN',{year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(now.value).replaceAll('/','-'))
const can=(roles:string[])=>roles.includes(role.value)
onMounted(()=>{clockTimer=window.setInterval(()=>{now.value=new Date()},1000)})
onBeforeUnmount(()=>{if(clockTimer)window.clearInterval(clockTimer)})
function logout(){localStorage.removeItem('accessToken');localStorage.removeItem('displayName');localStorage.removeItem('role');router.replace('/login')}
</script>

<template>
  <router-view v-if="isLogin"/>
  <el-container v-else class="app-shell">
    <el-aside class="app-sidebar" width="220px">
      <div class="brand"><strong>新发油漆</strong><small>业务管理系统</small></div>
      <el-menu router :default-active="$route.path" :default-openeds="['sales','dispatch','purchase','inventory','ledger','finance']">
        <el-menu-item index="/home"><span>经营首页</span></el-menu-item>
        <el-sub-menu v-if="can(['ADMIN','SALES','WAREHOUSE'])" index="sales"><template #title><span>销售管理</span></template><el-menu-item v-if="can(['ADMIN','SALES'])" index="/sales-orders">销售单</el-menu-item><el-menu-item v-if="can(['ADMIN','SALES'])" index="/sales-returns">销售退货</el-menu-item><el-menu-item v-if="can(['ADMIN','WAREHOUSE'])" index="/return-warehouses">退货入仓</el-menu-item></el-sub-menu>
        <el-sub-menu v-if="can(['ADMIN','DISPATCH'])" index="dispatch"><template #title><span>配送管理</span></template><el-menu-item index="/dispatch-sheets">出车表</el-menu-item></el-sub-menu>
        <el-sub-menu v-if="can(['ADMIN','WAREHOUSE'])" index="purchase"><template #title><span>采购管理</span></template><el-menu-item index="/purchases">采购订单与收货</el-menu-item></el-sub-menu>
        <el-sub-menu v-if="can(['ADMIN','SALES','WAREHOUSE'])" index="inventory"><template #title><span>货品与库存</span></template><el-menu-item index="/products">货品目录</el-menu-item><el-menu-item v-if="can(['ADMIN','WAREHOUSE'])" index="/inventory-reconciliation">库存对账</el-menu-item></el-sub-menu>
        <el-sub-menu v-if="can(['ADMIN','SALES','WAREHOUSE'])" index="ledger"><template #title><span>流水账</span></template><el-menu-item v-if="can(['ADMIN','WAREHOUSE'])" index="/ledgers/inventory">货品进出流水账</el-menu-item><el-menu-item v-if="can(['ADMIN','WAREHOUSE'])" index="/ledgers/purchases">货品采购流水账</el-menu-item><el-menu-item v-if="can(['ADMIN','SALES'])" index="/ledgers/sales">货品销售流水账</el-menu-item><el-menu-item v-if="can(['ADMIN'])" index="/ledgers/cashflow">收支流水账</el-menu-item></el-sub-menu>
        <el-sub-menu v-if="can(['ADMIN'])" index="finance"><template #title><span>财务管理</span></template><el-menu-item index="/other-expenses">其它支出</el-menu-item></el-sub-menu>
        <el-sub-menu v-if="can(['ADMIN','SALES','DISPATCH'])" index="master-data"><template #title><span>资料管理</span></template><el-menu-item v-if="can(['ADMIN','SALES'])" index="/customers">客户资料</el-menu-item><el-menu-item v-if="can(['ADMIN'])" index="/suppliers">供应商资料</el-menu-item><el-menu-item v-if="can(['ADMIN','DISPATCH'])" index="/employees">员工资料</el-menu-item><el-menu-item v-if="can(['ADMIN','DISPATCH'])" index="/vehicles">车辆资料</el-menu-item><el-menu-item v-if="can(['ADMIN','DISPATCH'])" index="/routes">路线资料</el-menu-item><el-menu-item v-if="can(['ADMIN'])" index="/departments">部门资料</el-menu-item><el-menu-item v-if="can(['ADMIN'])" index="/employee-types">员工类别</el-menu-item><el-menu-item v-if="can(['ADMIN'])" index="/master-data-import">旧系统资料导入</el-menu-item></el-sub-menu>
        <el-sub-menu v-if="can(['ADMIN'])" index="system-settings"><template #title><span>系统设置</span></template><el-menu-item index="/users">用户账号</el-menu-item><el-menu-item index="/audit-logs">操作日志</el-menu-item></el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container class="content-shell">
      <el-header class="app-header" height="56px"><strong>新发油漆 · 业务管理系统</strong><span>{{displayName}}　｜　{{dateTime}}　｜　<el-button link class="logout-button" @click="logout">退出</el-button></span></el-header>
      <el-main class="app-main"><router-view/></el-main>
      <footer class="app-status"><span>系统正常</span><span>当前用户：{{displayName}}</span></footer>
    </el-container>
  </el-container>
</template>
