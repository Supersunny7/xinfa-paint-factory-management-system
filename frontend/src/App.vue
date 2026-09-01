<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route=useRoute(),router=useRouter()
const isLogin=computed(()=>route.path==='/login')
const displayName=computed(()=>{route.path;const name=localStorage.getItem('displayName');return name==='系统管理员'?'System Administrator':name||'Current User'})
const role=computed(()=>{route.path;return localStorage.getItem('role')||'ADMIN'})
const now=ref(new Date());let clockTimer:number|undefined
const dateTime=computed(()=>new Intl.DateTimeFormat('en-CA',{year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(now.value).replace(',',''))
const can=(roles:string[])=>roles.includes(role.value)
onMounted(()=>{clockTimer=window.setInterval(()=>{now.value=new Date()},1000)})
onBeforeUnmount(()=>{if(clockTimer)window.clearInterval(clockTimer)})
function logout(){localStorage.removeItem('accessToken');localStorage.removeItem('displayName');localStorage.removeItem('role');router.replace('/login')}
</script>

<template>
  <router-view v-if="isLogin"/>
  <el-container v-else class="app-shell">
    <el-aside class="app-sidebar" width="220px">
      <div class="brand"><strong>Xinfa Paint Factory</strong><small>Business Management System</small></div>
      <el-menu router :default-active="$route.path" :default-openeds="['sales','dispatch','purchase','inventory','ledger','finance']">
        <el-menu-item index="/home"><span>Dashboard</span></el-menu-item>
        <el-sub-menu v-if="can(['ADMIN','SALES','WAREHOUSE'])" index="sales"><template #title><span>Sales</span></template><el-menu-item v-if="can(['ADMIN','SALES'])" index="/sales-orders">Sales Order</el-menu-item><el-menu-item v-if="can(['ADMIN','SALES'])" index="/sales-returns">Sales Returns</el-menu-item><el-menu-item v-if="can(['ADMIN','WAREHOUSE'])" index="/return-warehouses">Return Warehousing</el-menu-item></el-sub-menu>
        <el-sub-menu v-if="can(['ADMIN','DISPATCH'])" index="dispatch"><template #title><span>Dispatch</span></template><el-menu-item index="/dispatch-sheets">Dispatch Sheet</el-menu-item></el-sub-menu>
        <el-sub-menu v-if="can(['ADMIN','WAREHOUSE'])" index="purchase"><template #title><span>Purchasing</span></template><el-menu-item index="/purchases">Purchase Orders & Receipts</el-menu-item></el-sub-menu>
        <el-sub-menu v-if="can(['ADMIN','SALES','WAREHOUSE'])" index="inventory"><template #title><span>Products & Inventory</span></template><el-menu-item index="/products">Product Catalog</el-menu-item><el-menu-item v-if="can(['ADMIN','WAREHOUSE'])" index="/inventory-reconciliation">Inventory Reconciliation</el-menu-item></el-sub-menu>
        <el-sub-menu v-if="can(['ADMIN','SALES','WAREHOUSE'])" index="ledger"><template #title><span>Ledgers</span></template><el-menu-item v-if="can(['ADMIN','WAREHOUSE'])" index="/ledgers/inventory">Inventory Movement Ledger</el-menu-item><el-menu-item v-if="can(['ADMIN','WAREHOUSE'])" index="/ledgers/purchases">Purchase Ledger</el-menu-item><el-menu-item v-if="can(['ADMIN','SALES'])" index="/ledgers/sales">Sales Ledger</el-menu-item><el-menu-item v-if="can(['ADMIN'])" index="/ledgers/cashflow">Cashflow Ledger</el-menu-item></el-sub-menu>
        <el-sub-menu v-if="can(['ADMIN'])" index="finance"><template #title><span>Finance</span></template><el-menu-item index="/other-expenses">Other Expenses</el-menu-item></el-sub-menu>
        <el-sub-menu v-if="can(['ADMIN','SALES','DISPATCH'])" index="master-data"><template #title><span>Master Data</span></template><el-menu-item v-if="can(['ADMIN','SALES'])" index="/customers">Customers</el-menu-item><el-menu-item v-if="can(['ADMIN'])" index="/suppliers">Suppliers</el-menu-item><el-menu-item v-if="can(['ADMIN','DISPATCH'])" index="/employees">Employees</el-menu-item><el-menu-item v-if="can(['ADMIN','DISPATCH'])" index="/vehicles">Vehicles</el-menu-item><el-menu-item v-if="can(['ADMIN','DISPATCH'])" index="/routes">Routes</el-menu-item><el-menu-item v-if="can(['ADMIN'])" index="/departments">Departments</el-menu-item><el-menu-item v-if="can(['ADMIN'])" index="/employee-types">Employee Types</el-menu-item><el-menu-item v-if="can(['ADMIN'])" index="/master-data-import">Legacy Data Import</el-menu-item></el-sub-menu>
        <el-sub-menu v-if="can(['ADMIN'])" index="system-settings"><template #title><span>System Settings</span></template><el-menu-item index="/users">User Accounts</el-menu-item><el-menu-item index="/audit-logs">Audit Log</el-menu-item></el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container class="content-shell">
      <el-header class="app-header" height="56px"><strong>Xinfa Paint Factory · Business Management System</strong><span>{{displayName}} ｜ {{dateTime}} ｜ <el-button link class="logout-button" @click="logout">Sign Out</el-button></span></el-header>
      <el-main class="app-main"><router-view/></el-main>
      <footer class="app-status"><span>System Healthy</span><span>Current User: {{displayName}}</span></footer>
    </el-container>
  </el-container>
</template>


