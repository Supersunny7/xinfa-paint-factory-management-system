import { createRouter, createWebHistory } from 'vue-router'
import LoginPage from '../views/LoginPage.vue'
import CustomerList from '../views/CustomerList.vue'
import ProductList from '../views/ProductList.vue'
import ProductCatalogPage from '../views/ProductCatalogPage.vue'
import SalesOrderPage from '../views/SalesOrderPage.vue'
import DispatchSheetPage from '../views/DispatchSheetPage.vue'
import InventoryReconciliationPage from '../views/InventoryReconciliationPage.vue'
import MasterDataImportPage from '../views/MasterDataImportPage.vue'
import ReferenceDataList from '../views/ReferenceDataList.vue'
import AuditLogPage from '../views/AuditLogPage.vue'
import UserManagementPage from '../views/UserManagementPage.vue'
import ChangePasswordPage from '../views/ChangePasswordPage.vue'
import ProductClassificationPage from '../views/ProductClassificationPage.vue'
import HomePage from '../views/HomePage.vue'
import PurchasePage from '../views/PurchasePage.vue'
import OtherExpensePage from '../views/OtherExpensePage.vue'
import InventoryLedgerPage from '../views/InventoryLedgerPage.vue'
import PurchaseLedgerPage from '../views/PurchaseLedgerPage.vue'
import SalesLedgerPage from '../views/SalesLedgerPage.vue'
import CashflowLedgerPage from '../views/CashflowLedgerPage.vue'
import SalesReturnPage from '../views/SalesReturnPage.vue'
import ReturnWarehousePage from '../views/ReturnWarehousePage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/home' },
    { path: '/login', component: LoginPage, meta: { public: true } },
    { path: '/change-password', component: ChangePasswordPage },
    { path: '/home', component: HomePage },
    { path: '/sales-orders', component: SalesOrderPage, meta:{roles:['ADMIN','SALES']} },
    { path: '/sales-returns', component: SalesReturnPage, meta:{roles:['ADMIN','SALES']} },
    { path: '/dispatch-sheets', component: DispatchSheetPage, meta:{roles:['ADMIN','DISPATCH']} },
    { path: '/purchases', component: PurchasePage, meta:{roles:['ADMIN','WAREHOUSE']} },
    { path: '/return-warehouses', component: ReturnWarehousePage, meta:{roles:['ADMIN','WAREHOUSE']} },
    { path: '/other-expenses', component: OtherExpensePage, meta:{roles:['ADMIN']} },
    { path: '/ledgers/inventory', component: InventoryLedgerPage, meta:{roles:['ADMIN','WAREHOUSE']} },
    { path: '/ledgers/purchases', component: PurchaseLedgerPage, meta:{roles:['ADMIN','WAREHOUSE']} },
    { path: '/ledgers/sales', component: SalesLedgerPage, meta:{roles:['ADMIN','SALES']} },
    { path: '/ledgers/cashflow', component: CashflowLedgerPage, meta:{roles:['ADMIN']} },
    { path: '/customers', component: CustomerList, meta:{roles:['ADMIN','SALES']} },
    { path: '/products', component: ProductCatalogPage, meta:{roles:['ADMIN','SALES','WAREHOUSE']} },
    { path: '/products/manage', component: ProductList, meta:{roles:['ADMIN','SALES','WAREHOUSE']} },
    { path: '/products/classification', component: ProductClassificationPage, meta:{roles:['ADMIN']} },
    { path: '/inventory-reconciliation', component: InventoryReconciliationPage, meta:{roles:['ADMIN','WAREHOUSE']} },
    { path: '/master-data-import', component: MasterDataImportPage, meta:{roles:['ADMIN']} },
    { path: '/suppliers', component: ReferenceDataList, meta: { kind: 'suppliers',roles:['ADMIN'] } },
    { path: '/employees', component: ReferenceDataList, meta: { kind: 'employees',roles:['ADMIN','DISPATCH'] } },
    { path: '/vehicles', component: ReferenceDataList, meta: { kind: 'vehicles',roles:['ADMIN','DISPATCH'] } },
    { path: '/employee-types', component: ReferenceDataList, meta: { kind: 'employee-types',roles:['ADMIN'] } },
    { path: '/departments', component: ReferenceDataList, meta: { kind: 'departments',roles:['ADMIN'] } },
    { path: '/routes', component: ReferenceDataList, meta: { kind: 'routes',roles:['ADMIN','DISPATCH'] } },
    { path: '/audit-logs', component: AuditLogPage, meta:{roles:['ADMIN']} },
    { path: '/users', component: UserManagementPage, meta:{roles:['ADMIN']} },
  ],
})

router.beforeEach((to) => {
  if (!to.meta.public && !localStorage.getItem('accessToken')) return '/login'
  if (to.path === '/login' && localStorage.getItem('accessToken')) return '/home'
  if(localStorage.getItem('mustChangePassword')==='1'&&to.path!=='/change-password')return '/change-password'
  const roles=to.meta.roles as string[]|undefined,role=localStorage.getItem('role')||'ADMIN'
  if(roles&&!roles.includes(role))return '/home'
})

export default router


