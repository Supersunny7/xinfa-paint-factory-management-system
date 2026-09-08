<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import en from 'element-plus/es/locale/lang/en'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import LanguageSwitcher from './components/LanguageSwitcher.vue'

const route=useRoute(),router=useRouter()
const { locale, t }=useI18n()
const isLogin=computed(()=>route.path==='/login')
const displayName=computed(()=>{route.path;return localStorage.getItem('displayName')||t('common.currentUser')})
const role=computed(()=>{route.path;return localStorage.getItem('role')||'ADMIN'})
const now=ref(new Date());let clockTimer:number|undefined
const elementLocale=computed(()=>locale.value==='zh-CN'?zhCn:en)
const dateTime=computed(()=>new Intl.DateTimeFormat(locale.value,{year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(now.value).replaceAll('/','-').replace(',',''))
const can=(roles:string[])=>roles.includes(role.value)
onMounted(()=>{clockTimer=window.setInterval(()=>{now.value=new Date()},1000)})
onBeforeUnmount(()=>{if(clockTimer)window.clearInterval(clockTimer)})
function logout(){localStorage.removeItem('accessToken');localStorage.removeItem('displayName');localStorage.removeItem('role');router.replace('/login')}
</script>

<template>
  <el-config-provider :locale="elementLocale">
  <router-view v-if="isLogin"/>
  <el-container v-else class="app-shell">
    <el-aside class="app-sidebar" width="220px">
      <div class="brand"><strong>{{ $t('brand.name') }}</strong><small>{{ $t('brand.system') }}</small></div>
      <el-menu router :default-active="$route.path" :default-openeds="['sales','dispatch','purchase','inventory','ledger','finance']">
        <el-menu-item index="/home"><span>{{ $t('nav.dashboard') }}</span></el-menu-item>
        <el-sub-menu v-if="can(['ADMIN','SALES','WAREHOUSE'])" index="sales"><template #title><span>{{ $t('nav.sales') }}</span></template><el-menu-item v-if="can(['ADMIN','SALES'])" index="/sales-orders">{{ $t('nav.salesOrder') }}</el-menu-item><el-menu-item v-if="can(['ADMIN','SALES'])" index="/sales-returns">{{ $t('nav.salesReturns') }}</el-menu-item><el-menu-item v-if="can(['ADMIN','WAREHOUSE'])" index="/return-warehouses">{{ $t('nav.returnWarehousing') }}</el-menu-item></el-sub-menu>
        <el-sub-menu v-if="can(['ADMIN','DISPATCH'])" index="dispatch"><template #title><span>{{ $t('nav.dispatch') }}</span></template><el-menu-item index="/dispatch-sheets">{{ $t('nav.dispatchSheet') }}</el-menu-item></el-sub-menu>
        <el-sub-menu v-if="can(['ADMIN','WAREHOUSE'])" index="purchase"><template #title><span>{{ $t('nav.purchasing') }}</span></template><el-menu-item index="/purchases">{{ $t('nav.purchaseDocuments') }}</el-menu-item></el-sub-menu>
        <el-sub-menu v-if="can(['ADMIN','SALES','WAREHOUSE'])" index="inventory"><template #title><span>{{ $t('nav.productsInventory') }}</span></template><el-menu-item index="/products">{{ $t('nav.productCatalog') }}</el-menu-item><el-menu-item v-if="can(['ADMIN','WAREHOUSE'])" index="/inventory-reconciliation">{{ $t('nav.inventoryReconciliation') }}</el-menu-item></el-sub-menu>
        <el-sub-menu v-if="can(['ADMIN','SALES','WAREHOUSE'])" index="ledger"><template #title><span>{{ $t('nav.ledgers') }}</span></template><el-menu-item v-if="can(['ADMIN','WAREHOUSE'])" index="/ledgers/inventory">{{ $t('nav.inventoryLedger') }}</el-menu-item><el-menu-item v-if="can(['ADMIN','WAREHOUSE'])" index="/ledgers/purchases">{{ $t('nav.purchaseLedger') }}</el-menu-item><el-menu-item v-if="can(['ADMIN','SALES'])" index="/ledgers/sales">{{ $t('nav.salesLedger') }}</el-menu-item><el-menu-item v-if="can(['ADMIN'])" index="/ledgers/cashflow">{{ $t('nav.cashflowLedger') }}</el-menu-item></el-sub-menu>
        <el-sub-menu v-if="can(['ADMIN'])" index="finance"><template #title><span>{{ $t('nav.finance') }}</span></template><el-menu-item index="/other-expenses">{{ $t('nav.otherExpenses') }}</el-menu-item></el-sub-menu>
        <el-sub-menu v-if="can(['ADMIN','SALES','DISPATCH'])" index="master-data"><template #title><span>{{ $t('nav.masterData') }}</span></template><el-menu-item v-if="can(['ADMIN','SALES'])" index="/customers">{{ $t('nav.customers') }}</el-menu-item><el-menu-item v-if="can(['ADMIN'])" index="/suppliers">{{ $t('nav.suppliers') }}</el-menu-item><el-menu-item v-if="can(['ADMIN','DISPATCH'])" index="/employees">{{ $t('nav.employees') }}</el-menu-item><el-menu-item v-if="can(['ADMIN','DISPATCH'])" index="/vehicles">{{ $t('nav.vehicles') }}</el-menu-item><el-menu-item v-if="can(['ADMIN','DISPATCH'])" index="/routes">{{ $t('nav.routes') }}</el-menu-item><el-menu-item v-if="can(['ADMIN'])" index="/departments">{{ $t('nav.departments') }}</el-menu-item><el-menu-item v-if="can(['ADMIN'])" index="/employee-types">{{ $t('nav.employeeTypes') }}</el-menu-item><el-menu-item v-if="can(['ADMIN'])" index="/master-data-import">{{ $t('nav.legacyImport') }}</el-menu-item></el-sub-menu>
        <el-sub-menu v-if="can(['ADMIN'])" index="system-settings"><template #title><span>{{ $t('nav.systemSettings') }}</span></template><el-menu-item index="/users">{{ $t('nav.users') }}</el-menu-item><el-menu-item index="/audit-logs">{{ $t('nav.auditLog') }}</el-menu-item></el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container class="content-shell">
      <el-header class="app-header" height="56px"><strong>{{ $t('brand.full') }}</strong><span class="header-actions"><LanguageSwitcher/><span>{{displayName}} ｜ {{dateTime}} ｜ <el-button link class="logout-button" @click="logout">{{ $t('common.signOut') }}</el-button></span></span></el-header>
      <el-main class="app-main"><router-view/></el-main>
      <footer class="app-status"><span>{{ $t('common.systemHealthy') }}</span><span>{{ $t('common.currentUser') }}：{{displayName}}</span></footer>
    </el-container>
  </el-container>
  </el-config-provider>
</template>

<style scoped>.header-actions{display:flex;align-items:center;gap:14px}</style>


