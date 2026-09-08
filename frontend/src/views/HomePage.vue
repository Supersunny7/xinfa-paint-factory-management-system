<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { useI18n } from 'vue-i18n'

type FlowRow = {
  id:number; orderNo:string; printedAt:string; customerName:string
  salesAmount:number; estimatedCost:number; estimatedGrossProfit:number; printStatus:string
}
type OwnerDashboard = {
  businessDate:string; salesAmount:number; estimatedCost:number; estimatedGrossProfit:number
  salesOrderCount:number; missingCostItemCount:number; grossProfitChangePercent:number|null; unprintedSalesCount:number
  draftDispatchCount:number; draftReceiptCount:number; stockWarningCount:number; salesFlow:FlowRow[]
}

const router = useRouter()
const { locale, t } = useI18n()
const role = localStorage.getItem('role') || 'ADMIN'
const storedDisplayName = localStorage.getItem('displayName')
const displayName = computed(() => storedDisplayName || t('common.currentUser'))
const loading = ref(false)
const loadError = ref('')
const dashboard = ref<OwnerDashboard|null>(null)
const activeTodo = ref('all')
const activeView = ref<'owner'|'business'>('owner')

const masterDataPath = role === 'DISPATCH' ? '/employees' : '/customers'
const entries = computed(() => [
  { path:'/purchases', title:t('home.entryPurchase'), description:t('home.entryPurchaseDesc'), icon:locale.value==='zh-CN'?'采':'P', roles:['ADMIN','WAREHOUSE'], tone:'gold' },
  { path:'/sales-orders', title:t('home.entrySales'), description:t('home.entrySalesDesc'), icon:locale.value==='zh-CN'?'单':'S', roles:['ADMIN','SALES'], tone:'blue' },
  { path:'/dispatch-sheets', title:t('home.entryDispatch'), description:t('home.entryDispatchDesc'), icon:locale.value==='zh-CN'?'车':'D', roles:['ADMIN','DISPATCH'], tone:'green' },
  { path:'/products', title:t('home.entryProducts'), description:t('home.entryProductsDesc'), icon:locale.value==='zh-CN'?'货':'P', roles:['ADMIN','SALES','WAREHOUSE'], tone:'orange' },
  { path:'/inventory-reconciliation', title:t('home.entryInventory'), description:t('home.entryInventoryDesc'), icon:locale.value==='zh-CN'?'库':'I', roles:['ADMIN','WAREHOUSE'], tone:'purple' },
  { path:masterDataPath, title:t('home.entryMaster'), description:t('home.entryMasterDesc'), icon:locale.value==='zh-CN'?'资':'M', roles:['ADMIN','SALES','DISPATCH'], tone:'teal' },
  { path:'/users', title:t('home.entrySystem'), description:t('home.entrySystemDesc'), icon:locale.value==='zh-CN'?'管':'A', roles:['ADMIN'], tone:'gray' },
])
const visibleEntries = computed(() => entries.value.filter(item => item.roles.includes(role)))
const isOwnerView = computed(() => role === 'ADMIN' && activeView.value === 'owner')
const todos = computed(() => {
  if (!dashboard.value) return []
  return [
    { kind:'sales', type:t('home.sales'), label:t('home.taskSales'), count:dashboard.value.unprintedSalesCount, path:'/sales-orders?workflowStatus=UNPRINTED&dateScope=ALL', tone:'warning' },
    { kind:'dispatch', type:t('home.dispatch'), label:t('home.taskDispatch'), count:dashboard.value.draftDispatchCount, path:'/dispatch-sheets', tone:'warning' },
    { kind:'purchase', type:t('home.purchase'), label:t('home.taskPurchase'), count:dashboard.value.draftReceiptCount, path:'/purchases', tone:'warning' },
    { kind:'inventory', type:t('home.inventory'), label:t('home.taskInventory'), count:dashboard.value.stockWarningCount, path:'/inventory-reconciliation', tone:'danger' },
  ].filter(x => activeTodo.value === 'all' || x.kind === activeTodo.value)
})

const money = (value:number|undefined|null) => new Intl.NumberFormat(locale.value,{style:'currency',currency:'CNY'}).format(Number(value||0))
const dateTime = (value:string) => value ? value.replace('T',' ').slice(0,16) : '—'
const changeText = computed(() => {
  const value = dashboard.value?.grossProfitChangePercent
  if (value == null) return t('home.noComparison')
  return t('home.previousDay',{value:`${value >= 0 ? '+' : ''}${value}`})
})

async function loadDashboard(){
  if(role !== 'ADMIN') return
  loading.value=true;loadError.value=''
  try{const {data}=await api.get('/dashboard/owner');dashboard.value=data.data}
  catch(e:any){loadError.value=e?.response?.data?.message||t('home.loadFailed')}
  finally{loading.value=false}
}

onMounted(loadDashboard)
</script>

<template>
  <section class="home-page">
    <div class="home-titlebar">
      <div><h1>{{ isOwnerView ? $t('nav.dashboard') : $t('home.selectOperation',{name:displayName}) }}</h1><p>{{ isOwnerView ? $t('home.ownerSubtitle') : $t(`role.${role}`) }}</p></div>
      <el-radio-group v-if="role==='ADMIN'" v-model="activeView" size="large">
        <el-radio-button value="owner">{{ $t('home.ownerView') }}</el-radio-button><el-radio-button value="business">{{ $t('home.businessView') }}</el-radio-button>
      </el-radio-group>
    </div>

    <template v-if="isOwnerView">
      <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" class="dashboard-alert" />
      <div v-loading="loading" class="owner-dashboard">
        <div class="metrics-grid">
          <article class="profit-card">
            <div class="metric-label"><span>{{ $t('home.estimatedProfit') }}</span><el-tag effect="plain" type="warning">{{ $t('home.estimated') }}</el-tag></div>
            <strong>{{ money(dashboard?.estimatedGrossProfit) }}</strong>
            <b>{{ changeText }}</b>
            <small>{{ $t('home.profitNote') }}</small>
            <small v-if="dashboard?.missingCostItemCount" class="missing-cost">{{ $t('home.missingCost',{count:dashboard.missingCostItemCount}) }}</small>
          </article>
          <article class="metric-card"><span>{{ $t('home.yesterdaySales') }}</span><strong>{{ money(dashboard?.salesAmount) }}</strong><small>{{ dashboard?.businessDate || '—' }}</small></article>
          <article class="metric-card"><span>{{ $t('home.yesterdayOrders') }}</span><strong>{{ dashboard?.salesOrderCount || 0 }} <em>{{ $t('home.orders') }}</em></strong><small>{{ $t('home.firstPrintCount') }}</small></article>
          <article class="metric-card warning-card"><span>{{ $t('home.stockAlerts') }}</span><strong>{{ dashboard?.stockWarningCount || 0 }} <em>{{ $t('home.items') }}</em></strong><small>{{ $t('home.stockLimitNote') }}</small></article>
        </div>

        <div class="dashboard-row">
          <article class="panel todo-panel">
            <div class="panel-head"><h2>{{ $t('home.todayTasks') }}</h2><el-radio-group v-model="activeTodo" size="small"><el-radio-button value="all">{{ $t('common.all') }}</el-radio-button><el-radio-button value="sales">{{ $t('home.sales') }}</el-radio-button><el-radio-button value="dispatch">{{ $t('home.dispatch') }}</el-radio-button><el-radio-button value="purchase">{{ $t('home.purchase') }}</el-radio-button><el-radio-button value="inventory">{{ $t('home.inventory') }}</el-radio-button></el-radio-group></div>
            <el-table :data="todos" border size="small">
              <el-table-column prop="type" :label="$t('home.businessType')" width="90"/><el-table-column prop="label" :label="$t('home.tasks')" min-width="160"/>
              <el-table-column :label="$t('home.quantity')" width="90"><template #default="s"><strong>{{s.row.count}}</strong></template></el-table-column>
              <el-table-column :label="$t('home.status')" width="110"><template #default="s"><el-tag :type="s.row.tone">{{s.row.label}}</el-tag></template></el-table-column>
              <el-table-column :label="$t('home.actions')" width="80"><template #default="s"><el-button link type="primary" @click="router.push(s.row.path)">{{s.row.kind==='inventory'?$t('common.view'):$t('common.handle')}}</el-button></template></el-table-column>
            </el-table>
          </article>
          <aside class="side-stack">
            <article class="panel quick-panel"><h2>{{ $t('home.quickActions') }}</h2><div class="quick-grid"><el-button type="primary" @click="router.push('/sales-orders')">{{ $t('home.createSales') }}</el-button><el-button type="primary" @click="router.push('/dispatch-sheets')">{{ $t('home.createDispatch') }}</el-button><el-button type="primary" @click="router.push('/purchases')">{{ $t('home.createPurchase') }}</el-button><el-button type="primary" @click="router.push('/inventory-reconciliation')">{{ $t('home.reconcile') }}</el-button></div></article>
            <article class="panel reminder-panel"><h2>{{ $t('home.operationalAlerts') }}</h2><button v-for="item in todos" :key="item.kind" @click="router.push(item.path)"><span>{{item.label}}</span><strong>{{item.count}} {{item.kind==='inventory'?$t('home.items'):$t('home.documents')}}</strong></button></article>
          </aside>
        </div>

        <article class="panel flow-panel">
          <div class="panel-head"><h2>{{ $t('home.yesterdayActivity') }}</h2><el-button link type="primary" @click="router.push('/sales-orders')">{{ $t('home.viewSales') }}</el-button></div>
          <el-table :data="dashboard?.salesFlow||[]" border size="small" show-summary>
            <el-table-column prop="printedAt" :label="$t('home.time')" width="145"><template #default="s">{{dateTime(s.row.printedAt)}}</template></el-table-column>
            <el-table-column prop="orderNo" :label="$t('home.orderNo')" width="145"/><el-table-column prop="customerName" :label="$t('home.customer')" min-width="180" show-overflow-tooltip/>
            <el-table-column :label="$t('home.salesAmount')" width="120" align="right"><template #default="s">{{money(s.row.salesAmount)}}</template></el-table-column>
            <el-table-column :label="$t('home.referenceCost')" width="120" align="right"><template #default="s">{{money(s.row.estimatedCost)}}</template></el-table-column>
            <el-table-column :label="$t('home.estimatedGrossProfit')" width="125" align="right"><template #default="s">{{money(s.row.estimatedGrossProfit)}}</template></el-table-column>
            <el-table-column prop="printStatus" :label="$t('home.printStatus')" width="90"><template #default="s"><el-tag type="success">{{s.row.printStatus}}</el-tag></template></el-table-column>
            <el-table-column :label="$t('home.actions')" width="70"><template #default="s"><el-button link type="primary" @click="router.push('/sales-orders')">{{ $t('common.view') }}</el-button></template></el-table-column>
            <template #empty><el-empty :description="$t('home.emptySales')" :image-size="60"/></template>
          </el-table>
        </article>
      </div>
    </template>

    <div v-else class="home-grid">
      <button v-for="item in visibleEntries" :key="item.path" class="home-entry" type="button" @click="router.push(item.path)">
        <span class="entry-icon" :class="`tone-${item.tone}`">{{ item.icon }}</span><span class="entry-copy"><strong>{{ item.title }}</strong><small>{{ item.description }}</small></span><span class="entry-arrow">›</span>
      </button>
    </div>
  </section>
</template>

<style scoped>
.home-page{max-width:1380px;margin:0 auto}.home-titlebar{display:flex;align-items:center;justify-content:space-between;margin:0 0 16px}.home-titlebar h1{margin:0 0 4px;font-size:26px;color:#17352f}.home-titlebar p{margin:0;color:#64748b}.dashboard-alert{margin-bottom:12px}.owner-dashboard{min-height:520px}.metrics-grid{display:grid;grid-template-columns:1.75fr repeat(3,1fr);gap:12px}.profit-card,.metric-card,.panel{border:1px solid #dfe7e3;border-radius:9px;background:#fff}.profit-card{padding:20px;color:#fff;border:0;background:#0f5c49}.metric-label{display:flex;align-items:center;justify-content:space-between;font-size:18px;font-weight:700}.profit-card strong{display:block;margin:9px 0 2px;font-size:42px;line-height:1;font-variant-numeric:tabular-nums}.profit-card b{display:block;margin:8px 0;color:#ffd166}.profit-card small{display:block;color:#d5ece5}.metric-card{padding:20px 18px}.metric-card span{font-weight:700;color:#33413d}.metric-card strong{display:block;margin:25px 0 12px;font-size:29px;color:#15231f;font-variant-numeric:tabular-nums}.metric-card em{font-size:16px;font-style:normal}.metric-card small{color:#7b8884}.warning-card strong{color:#c2410c}.dashboard-row{display:grid;grid-template-columns:minmax(0,2fr) minmax(300px,1fr);gap:12px;margin-top:12px}.panel{padding:14px}.panel h2{margin:0;font-size:18px;color:#20352f}.panel-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}.side-stack{display:grid;gap:12px}.quick-panel h2,.reminder-panel h2{margin-bottom:12px}.quick-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}.quick-grid .el-button{height:52px;margin:0;font-size:15px;background:#176b5b;border-color:#176b5b}.reminder-panel button{display:flex;align-items:center;justify-content:space-between;width:100%;padding:8px 2px;border:0;border-bottom:1px solid #edf0ef;background:none;cursor:pointer;color:#374640}.reminder-panel button:last-child{border-bottom:0}.reminder-panel button strong{color:#b45309}.flow-panel{margin-top:12px}.home-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}.home-entry{display:flex;align-items:center;gap:14px;min-height:92px;padding:17px;border:1px solid #e4e7ed;border-radius:12px;background:#fff;text-align:left;cursor:pointer;box-shadow:0 3px 12px rgba(15,61,52,.05)}.entry-icon{display:grid;place-items:center;width:48px;height:48px;border-radius:13px;font-size:21px;font-weight:700}.entry-copy{display:flex;flex-direction:column;gap:6px}.entry-copy strong{font-size:17px}.entry-copy small{color:#64748b}.entry-arrow{margin-left:auto;font-size:26px;color:#94a3b8}.tone-blue{background:#e8f1ff;color:#2563eb}.tone-green{background:#e7f7ef;color:#178557}.tone-orange{background:#fff1df;color:#d46b08}.tone-purple{background:#f1eaff;color:#7c3aed}.tone-teal{background:#e5f5f1;color:#176b5b}.tone-gold{background:#fff7d8;color:#a16207}.tone-gray{background:#eef1f5;color:#475569}@media(max-width:1200px){.metrics-grid{grid-template-columns:1.6fr repeat(3,1fr)}.profit-card strong{font-size:34px}.dashboard-row{grid-template-columns:1fr}.side-stack{grid-template-columns:1fr 1fr}}@media(max-width:900px){.metrics-grid{grid-template-columns:1fr 1fr}.home-grid{grid-template-columns:repeat(2,1fr)}}
.profit-card .missing-cost{margin-top:5px;color:#ffe29a;font-weight:700}
</style>


