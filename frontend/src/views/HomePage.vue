<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'

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
const role = localStorage.getItem('role') || 'ADMIN'
const storedDisplayName = localStorage.getItem('displayName')
const displayName = storedDisplayName === '系统管理员' ? 'System Administrator' : storedDisplayName || 'Current User'
const loading = ref(false)
const loadError = ref('')
const dashboard = ref<OwnerDashboard|null>(null)
const activeTodo = ref('all')
const activeView = ref<'owner'|'business'>('owner')

const roleText: Record<string, string> = { ADMIN: 'System Administrator', SALES: 'Sales Staff', WAREHOUSE: 'Warehouse Staff', DISPATCH: 'Dispatch Staff' }
const masterDataPath = role === 'DISPATCH' ? '/employees' : '/customers'
const entries = [
  { path:'/purchases', title:'Purchasing', description:'Create purchase orders, record receipts, and approve stock-in', icon:'P', roles:['ADMIN','WAREHOUSE'], tone:'gold' },
  { path:'/sales-orders', title:'Sales Order Entry', description:'Create orders, check inventory, and print delivery notes', icon:'S', roles:['ADMIN','SALES'], tone:'blue' },
  { path:'/dispatch-sheets', title:'Dispatch Management', description:'Add printed sales orders and approve dispatch sheets', icon:'D', roles:['ADMIN','DISPATCH'], tone:'green' },
  { path:'/products', title:'Product Catalog', description:'Find products quickly by manufacturer, category, and subcategory', icon:'products', roles:['ADMIN','SALES','WAREHOUSE'], tone:'orange' },
  { path:'/inventory-reconciliation', title:'Inventory Reconciliation', description:'Review stock, shortages, and inventory movements', icon:'I', roles:['ADMIN','WAREHOUSE'], tone:'purple' },
  { path:masterDataPath, title:'Master Data', description:'Manage customers, suppliers, dispatch data, and organization records', icon:'M', roles:['ADMIN','SALES','DISPATCH'], tone:'teal' },
  { path:'/users', title:'System Settings', description:'Manage user accounts, permissions, and audit logs', icon:'A', roles:['ADMIN'], tone:'gray' },
]
const visibleEntries = computed(() => entries.filter(item => item.roles.includes(role)))
const isOwnerView = computed(() => role === 'ADMIN' && activeView.value === 'owner')
const todos = computed(() => {
  if (!dashboard.value) return []
  return [
    { kind:'sales', type:'Sales', label:'Sales Orders Awaiting Print', count:dashboard.value.unprintedSalesCount, path:'/sales-orders?workflowStatus=UNPRINTED&dateScope=ALL', tone:'warning' },
    { kind:'dispatch', type:'Dispatch', label:'Dispatch Sheets Awaiting Approval', count:dashboard.value.draftDispatchCount, path:'/dispatch-sheets', tone:'warning' },
    { kind:'purchase', type:'Purchasing', label:'Purchase Receipts Awaiting Approval', count:dashboard.value.draftReceiptCount, path:'/purchases', tone:'warning' },
    { kind:'inventory', type:'Inventory', label:'Stock Alerts', count:dashboard.value.stockWarningCount, path:'/inventory-reconciliation', tone:'danger' },
  ].filter(x => activeTodo.value === 'all' || x.kind === activeTodo.value)
})

const money = (value:number|undefined|null) => new Intl.NumberFormat('en-US',{style:'currency',currency:'CNY'}).format(Number(value||0))
const dateTime = (value:string) => value ? value.replace('T',' ').slice(0,16) : '—'
const changeText = computed(() => {
  const value = dashboard.value?.grossProfitChangePercent
  if (value == null) return 'No comparison data for the previous day'
  return `vs. previous day ${value >= 0 ? '+' : ''}${value}%`
})

async function loadDashboard(){
  if(role !== 'ADMIN') return
  loading.value=true;loadError.value=''
  try{const {data}=await api.get('/dashboard/owner');dashboard.value=data.data}
  catch(e:any){loadError.value=e?.response?.data?.message||'Failed to load operational data. Try again later.'}
  finally{loading.value=false}
}

onMounted(loadDashboard)
</script>

<template>
  <section class="home-page">
    <div class="home-titlebar">
      <div><h1>{{ isOwnerView ? 'Dashboard' : `${displayName}, Please select an operation` }}</h1><p>{{ isOwnerView ? "Operations Overview & Today's Tasks" : roleText[role] || role }}</p></div>
      <el-radio-group v-if="role==='ADMIN'" v-model="activeView" size="large">
        <el-radio-button value="owner">Owner View</el-radio-button><el-radio-button value="business">Operations View</el-radio-button>
      </el-radio-group>
    </div>

    <template v-if="isOwnerView">
      <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" class="dashboard-alert" />
      <div v-loading="loading" class="owner-dashboard">
        <div class="metrics-grid">
          <article class="profit-card">
            <div class="metric-label"><span>Yesterday's Estimated Gross Profit</span><el-tag effect="plain" type="warning">Estimated</el-tag></div>
            <strong>{{ money(dashboard?.estimatedGrossProfit) }}</strong>
            <b>{{ changeText }}</b>
            <small>Sales amount minus recent purchase price; this is an estimate, not posted or net profit.</small>
            <small v-if="dashboard?.missingCostItemCount" class="missing-cost">{{dashboard.missingCostItemCount}} line items have no recent purchase price; estimated gross profit may be overstated.</small>
          </article>
          <article class="metric-card"><span>Yesterday's Sales</span><strong>{{ money(dashboard?.salesAmount) }}</strong><small>{{ dashboard?.businessDate || '—' }}</small></article>
          <article class="metric-card"><span>Yesterday's Sales Orders</span><strong>{{ dashboard?.salesOrderCount || 0 }} <em>orders</em></strong><small>Counted by first confirmed print</small></article>
          <article class="metric-card warning-card"><span>Stock Alerts</span><strong>{{ dashboard?.stockWarningCount || 0 }} <em>items</em></strong><small>Current stock below minimum stock</small></article>
        </div>

        <div class="dashboard-row">
          <article class="panel todo-panel">
            <div class="panel-head"><h2>Today's Tasks</h2><el-radio-group v-model="activeTodo" size="small"><el-radio-button value="all">All</el-radio-button><el-radio-button value="sales">Sales</el-radio-button><el-radio-button value="dispatch">Dispatch</el-radio-button><el-radio-button value="purchase">Purchasing</el-radio-button><el-radio-button value="inventory">Inventory</el-radio-button></el-radio-group></div>
            <el-table :data="todos" border size="small">
              <el-table-column prop="type" label="Business Type" width="90"/><el-table-column prop="label" label="Tasks" min-width="160"/>
              <el-table-column label="Quantity" width="90"><template #default="s"><strong>{{s.row.count}}</strong></template></el-table-column>
              <el-table-column label="Status" width="110"><template #default="s"><el-tag :type="s.row.tone">{{s.row.label}}</el-tag></template></el-table-column>
              <el-table-column label="Actions" width="80"><template #default="s"><el-button link type="primary" @click="router.push(s.row.path)">{{s.row.kind==='inventory'?'View':'Handle'}}</el-button></template></el-table-column>
            </el-table>
          </article>
          <aside class="side-stack">
            <article class="panel quick-panel"><h2>Quick Actions</h2><div class="quick-grid"><el-button type="primary" @click="router.push('/sales-orders')">Create Sales Order</el-button><el-button type="primary" @click="router.push('/dispatch-sheets')">Create Dispatch Sheet</el-button><el-button type="primary" @click="router.push('/purchases')">Create Purchase Order</el-button><el-button type="primary" @click="router.push('/inventory-reconciliation')">Inventory Reconciliation</el-button></div></article>
            <article class="panel reminder-panel"><h2>Operational Alerts</h2><button v-for="item in todos" :key="item.kind" @click="router.push(item.path)"><span>{{item.label}}</span><strong>{{item.count}} {{item.kind==='inventory'?'items':'documents'}}</strong></button></article>
          </aside>
        </div>

        <article class="panel flow-panel">
          <div class="panel-head"><h2>Yesterday's Sales Activity</h2><el-button link type="primary" @click="router.push('/sales-orders')">View Sales Orders</el-button></div>
          <el-table :data="dashboard?.salesFlow||[]" border size="small" show-summary>
            <el-table-column prop="printedAt" label="Time" width="145"><template #default="s">{{dateTime(s.row.printedAt)}}</template></el-table-column>
            <el-table-column prop="orderNo" label="Sales Order No." width="145"/><el-table-column prop="customerName" label="Customer" min-width="180" show-overflow-tooltip/>
            <el-table-column label="Sales Amount" width="120" align="right"><template #default="s">{{money(s.row.salesAmount)}}</template></el-table-column>
            <el-table-column label="Reference Cost" width="120" align="right"><template #default="s">{{money(s.row.estimatedCost)}}</template></el-table-column>
            <el-table-column label="Estimated Gross Profit" width="125" align="right"><template #default="s">{{money(s.row.estimatedGrossProfit)}}</template></el-table-column>
            <el-table-column prop="printStatus" label="Print Status" width="90"><template #default="s"><el-tag type="success">{{s.row.printStatus}}</el-tag></template></el-table-column>
            <el-table-column label="Actions" width="70"><template #default="s"><el-button link type="primary" @click="router.push('/sales-orders')">View</el-button></template></el-table-column>
            <template #empty><el-empty description="No sales orders were first-printed yesterday" :image-size="60"/></template>
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


