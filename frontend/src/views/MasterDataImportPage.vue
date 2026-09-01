<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'

const types=[
  {value:'STOCK_TAKE',label:'Product Stock Count',expected:'Product data exported from the legacy system.xlsx'},
  {value:'PRODUCT_CATEGORY',label:'Product Category',expected:'Product Category.xlsx'},
  {value:'ROUTE',label:'Routes',expected:'Routes.xlsx'},
  {value:'EMPLOYEE_TYPE',label:'Employee Types',expected:'Employee Types.xlsx'},
  {value:'DEPARTMENT',label:'Departments',expected:'Departments.xlsx'},
  {value:'SUPPLIER',label:'Suppliers',expected:'Suppliers.xlsx'},
  {value:'EMPLOYEE',label:'Employees',expected:'Employees.xlsx'},
  {value:'VEHICLE',label:'Vehicles',expected:'Vehicles.xlsx'},
]
const dataType=ref('PRODUCT_CATEGORY'),file=ref<File|null>(null),fileInput=ref<HTMLInputElement|null>(null),loading=ref(false),importing=ref(false),result=ref<any>(null),inventoryReason=ref('Batch Stock Count Import')
const selectedType=computed(()=>types.find(x=>x.value===dataType.value)!)
const isStockTake=computed(()=>dataType.value==='STOCK_TAKE')
const tableHeaders=computed(()=>result.value?[...result.value.headers,'Validation Results']:[])
function choose(){fileInput.value?.click()}
function onFile(event:Event){const input=event.target as HTMLInputElement;file.value=input.files?.[0]||null;result.value=null}
async function preview(){
  if(!file.value)return ElMessage.warning('Select the Excel file exported from the legacy system first')
  loading.value=true
  try{const body=new FormData();body.append('file',file.value);const url=isStockTake.value?'/inventory-import/preview':'/master-data-import/preview';const config=isStockTake.value?{}:{params:{dataType:dataType.value}};const{data}=await api.post(url,body,config);result.value=data.data;ElMessage.success(`ValidateCompleted: total   ${result.value.summary.total} records`)}
  catch(e:any){ElMessage.error(e.response?.data?.message||'Validation failed. Check the file type and column headers.')}
  finally{loading.value=false}
}
function reset(){file.value=null;result.value=null;if(fileInput.value)fileInput.value.value=''}
function statusType(row:any){const status=String(row['Validation Results']||'');return status==='Passed'?'success':status==='No Adjustment Needed'?'info':status.startsWith('Skipped')?'warning':'danger'}
async function confirmImport(){
  if(!file.value||!result.value?.importSupported)return
  if(isStockTake.value&&!inventoryReason.value.trim())return ElMessage.warning('Enter InventoryCount Reason')
  const skipInvalid=result.value.summary.invalid>0&&result.value.canImportValidRows
  if(result.value.summary.invalid>0&&!skipInvalid)return
  const s=result.value.summary
  const skipped=skipInvalid?`,  and skipped ${s.invalid} records with missing names (Excel rows: ${result.value.issues.map((x:any)=>x.row).join(', ')})`:''
  const message=isStockTake.value?`Will adjust by product code ${s.updateRecords}  active saleable products, ${s.unchangedRecords}  items unchanged, Skipped ${s.ignored||0}  invalid, disabled, or non-saleable products; Each difference creates an inventory movement. Continue?`:`Will write to database: add  ${s.newRecords} records, update ${s.updateRecords} records, unchanged ${s.unchangedRecords} records${skipped}.Continue?`
  await ElMessageBox.confirm(message,isStockTake.value?'ConfirmStock Count Import':'Confirm Import Data',{type:'warning',confirmButtonText:'Confirm Import',cancelButtonText:'Cancel'})
  importing.value=true
  try{const body=new FormData();body.append('file',file.value);const url=isStockTake.value?'/inventory-import/confirm':'/master-data-import/confirm';const params=isStockTake.value?{previewToken:result.value.previewToken,reason:inventoryReason.value.trim()}:{dataType:dataType.value,skipInvalid};const{data}=await api.post(url,body,{params});const r=data.data;ElMessage.success(isStockTake.value?`Inventory update completed: adjusted  ${r.updated}, No Change ${r.skipped}, invalid products skipped ${r.ignored}; Movement Batch ${r.referenceNo}`:`Import completed: added  ${r.inserted}, update ${r.updated}, No Change ${r.skipped}, Invalid Rows Skipped ${r.skippedInvalid}`);await preview()}
  catch(e:any){ElMessage.error(e.response?.data?.message||'ImportFailed, Database write did not complete')}
  finally{importing.value=false}
}
</script>

<template>
  <section>
    <div class="page-heading"><div><h1>Legacy Data Import</h1><p>Preview and validate the file before confirming the database import.</p></div></div>
    <el-alert type="info" :closable="false" show-icon title="Safe mode: choosing a file and starting validation does not write data. Import occurs only after validation passes and you confirm again."/>
    <el-card class="import-card">
      <div class="steps"><div class="step active"><b>1</b><span>Select Data</span></div><div class="line"></div><div class="step" :class="{active:result}"><b>2</b><span>Preview Validation</span></div><div class="line"></div><div class="step" :class="{active:result?.importSupported&&result?.summary.invalid===0}"><b>3</b><span>Confirm Import</span></div></div>
      <el-form label-width="100px" class="selector">
        <el-form-item label="Data Type"><el-select v-model="dataType" style="width:260px" @change="reset"><el-option v-for="item in types" :key="item.value" :label="item.label" :value="item.value"/></el-select></el-form-item>
        <el-form-item label="Excel File"><input ref="fileInput" hidden type="file" accept=".xlsx,.xls" @change="onFile"><el-button @click="choose">Choose File</el-button><span class="filename">{{file?.name||`Please select ${selectedType.expected}`}}</span></el-form-item>
        <el-form-item v-if="isStockTake" label="FileDescription"><span class="filename">available directlySelectProduct data exported from the legacy system Excel; also available </span><el-link type="primary" href="/templates/Product Stock Count Import Template.xlsx" download>DownloadSimplified Template</el-link></el-form-item>
        <el-form-item v-if="isStockTake" label="Count Reason" required><el-input v-model="inventoryReason" maxlength="500" placeholder="For example: 2026year8month endInventorystock count"/></el-form-item>
        <el-form-item><el-button type="primary" :loading="loading" @click="preview">Start Validation</el-button><el-button @click="reset">Clear</el-button></el-form-item>
      </el-form>
    </el-card>
    <template v-if="result">
      <div class="summary-grid"><el-card><small>Total Records</small><strong>{{result.summary.total}}</strong></el-card><el-card class="ok"><small>available updateProduct</small><strong>{{result.summary.valid}}</strong></el-card><el-card v-if="isStockTake"><small>Auto-skip</small><strong>{{result.summary.ignored||0}}</strong></el-card><el-card :class="{bad:result.summary.invalid}"><small>Needs Attention</small><strong>{{result.summary.invalid}}</strong></el-card><el-card><small>Duplicate Code</small><strong>{{result.summary.duplicates}}</strong></el-card></div>
      <el-card v-if="result.importSupported&&(result.summary.invalid===0||result.canImportValidRows)" class="change-card">
        <el-alert v-if="isStockTake" class="skip-alert" type="warning" :closable="false" show-icon title="The system matches exact product codes and updates only enabled, saleable products. Missing, disabled, or non-saleable products are skipped. Products are not added and names, prices, categories, and statuses are not changed. Negative stock values are preserved from the file."/>
        <el-alert v-if="result.canImportValidRows" class="skip-alert" type="warning" :closable="false" show-icon :title="`You may import only  ${result.summary.valid} records valid employees; the system will skip  ${result.summary.invalid} records records missing names.`"/>
        <div class="change-summary"><span>Expected Additions <b class="new">{{result.summary.newRecords}}</b> records</span><span>Expected Updates <b class="update">{{result.summary.updateRecords}}</b> records</span><span>No Change <b>{{result.summary.unchangedRecords}}</b> records</span><el-button type="danger" :loading="importing" @click="confirmImport">{{result.canImportValidRows?'Import Valid Records Only':'Confirm Import Data'}}</el-button></div>
      </el-card>
      <el-alert v-else-if="result.summary.invalid>0" class="preview-only" type="error" :closable="false" show-icon title="Invalid data exists. Fix it and validate again before importing."/>
      <el-alert v-else class="preview-only" type="warning" :closable="false" show-icon title="This data is preview-only until the database structure is ready."/>
      <el-card><template #header><div class="card-title"><span>Data Preview (showing up to  {{result.previewLimit}} records)</span><el-tag type="info">Header is on row  {{result.headerRow}}  rows</el-tag></div></template>
        <el-table :data="result.rows" stripe max-height="520"><el-table-column v-for="header in tableHeaders" :key="header" :prop="header" :label="header" min-width="130" show-overflow-tooltip><template v-if="header==='Validation Results'" #default="scope"><el-tag :type="statusType(scope.row)">{{scope.row[header]}}</el-tag></template></el-table-column></el-table>
      </el-card>
      <el-card v-if="result.issues.length" class="issues"><template #header>Problem Rows (showing up to 100)</template><el-table :data="result.issues"><el-table-column prop="row" label="Excel Line No." width="120"/><el-table-column prop="message" label="Issue"/></el-table></el-card>
    </template>
  </section>
</template>

<style scoped>
.import-card{margin-top:16px}.selector{max-width:720px;margin-top:26px}.filename{margin-left:12px;color:#606266}.steps{display:flex;align-items:center;justify-content:center;max-width:680px;margin:4px auto 20px}.step{display:flex;align-items:center;gap:8px;color:#909399}.step b{display:grid;place-items:center;width:28px;height:28px;border-radius:50%;background:#dcdfe6;color:white}.step.active{color:#2563eb}.step.active b{background:#2563eb}.line{width:110px;height:2px;background:#dcdfe6;margin:0 14px}.summary-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin:16px 0}.summary-grid :deep(.el-card__body){display:flex;flex-direction:column;gap:8px}.summary-grid small{color:#606266}.summary-grid strong{font-size:28px}.summary-grid .ok strong{color:#16a34a}.summary-grid .bad strong{color:#dc2626}.change-card{margin-bottom:16px}.skip-alert{margin-bottom:16px}.change-summary{display:flex;align-items:center;gap:28px}.change-summary b{font-size:22px}.change-summary .new{color:#16a34a}.change-summary .update{color:#d97706}.change-summary .el-button{margin-left:auto}.preview-only{margin-bottom:16px}.card-title{display:flex;justify-content:space-between;align-items:center}.issues{margin-top:16px}@media(max-width:900px){.summary-grid{grid-template-columns:repeat(2,1fr)}.line{width:35px}.change-summary{align-items:flex-start;flex-direction:column;gap:10px}.change-summary .el-button{margin-left:0}}
</style>


