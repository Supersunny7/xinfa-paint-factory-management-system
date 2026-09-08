<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'

const types=[
  {value:'STOCK_TAKE',label:'货品库存盘点',expected:'旧系统导出的产品资料.xlsx'},
  {value:'PRODUCT_CATEGORY',label:'货品类别',expected:'货品类别.xlsx'},
  {value:'ROUTE',label:'路线资料',expected:'路线资料.xlsx'},
  {value:'EMPLOYEE_TYPE',label:'员工类别',expected:'员工类别.xlsx'},
  {value:'DEPARTMENT',label:'部门资料',expected:'部门资料.xlsx'},
  {value:'SUPPLIER',label:'供应商资料',expected:'供应商资料.xlsx'},
  {value:'EMPLOYEE',label:'员工资料',expected:'员工资料.xlsx'},
  {value:'VEHICLE',label:'车辆资料',expected:'车辆资料.xlsx'},
]
const dataType=ref('PRODUCT_CATEGORY'),file=ref<File|null>(null),fileInput=ref<HTMLInputElement|null>(null),loading=ref(false),importing=ref(false),result=ref<any>(null),inventoryReason=ref('库存批量盘点导入')
const selectedType=computed(()=>types.find(x=>x.value===dataType.value)!)
const isStockTake=computed(()=>dataType.value==='STOCK_TAKE')
const tableHeaders=computed(()=>result.value?[...result.value.headers,'校验结果']:[])
function choose(){fileInput.value?.click()}
function onFile(event:Event){const input=event.target as HTMLInputElement;file.value=input.files?.[0]||null;result.value=null}
async function preview(){
  if(!file.value)return ElMessage.warning('请先选择旧系统导出的 Excel 文件')
  loading.value=true
  try{const body=new FormData();body.append('file',file.value);const url=isStockTake.value?'/inventory-import/preview':'/master-data-import/preview';const config=isStockTake.value?{}:{params:{dataType:dataType.value}};const{data}=await api.post(url,body,config);result.value=data.data;ElMessage.success(`预检完成：共 ${result.value.summary.total} 条`)}
  catch(e:any){ElMessage.error(e.response?.data?.message||'预检失败，请检查文件类型和表头')}
  finally{loading.value=false}
}
function reset(){file.value=null;result.value=null;if(fileInput.value)fileInput.value.value=''}
function statusType(row:any){const status=String(row['校验结果']||'');return status==='通过'?'success':status==='无需调整'?'info':status.startsWith('已跳过')?'warning':'danger'}
async function confirmImport(){
  if(!file.value||!result.value?.importSupported)return
  if(isStockTake.value&&!inventoryReason.value.trim())return ElMessage.warning('请填写库存盘点原因')
  const skipInvalid=result.value.summary.invalid>0&&result.value.canImportValidRows
  if(result.value.summary.invalid>0&&!skipInvalid)return
  const s=result.value.summary
  const skipped=skipInvalid?`，并跳过 ${s.invalid} 条缺少姓名的记录（Excel 行：${result.value.issues.map((x:any)=>x.row).join('、')}）`:''
  const message=isStockTake.value?`即将按产品编号调整 ${s.updateRecords} 种仍在销售的货品，${s.unchangedRecords} 种无需变化，跳过 ${s.ignored||0} 种无效、停用或停卖货品；每项差额都会生成库存流水。是否继续？`:`即将写入数据库：新增 ${s.newRecords} 条、更新 ${s.updateRecords} 条、无需变化 ${s.unchangedRecords} 条${skipped}。是否继续？`
  await ElMessageBox.confirm(message,isStockTake.value?'确认库存盘点导入':'确认正式导入',{type:'warning',confirmButtonText:'确认导入',cancelButtonText:'取消'})
  importing.value=true
  try{const body=new FormData();body.append('file',file.value);const url=isStockTake.value?'/inventory-import/confirm':'/master-data-import/confirm';const params=isStockTake.value?{previewToken:result.value.previewToken,reason:inventoryReason.value.trim()}:{dataType:dataType.value,skipInvalid};const{data}=await api.post(url,body,{params});const r=data.data;ElMessage.success(isStockTake.value?`库存更新完成：调整 ${r.updated}，无需变化 ${r.skipped}，无效货品跳过 ${r.ignored}；流水批次 ${r.referenceNo}`:`导入完成：新增 ${r.inserted}，更新 ${r.updated}，无需变化 ${r.skipped}，异常跳过 ${r.skippedInvalid}`);await preview()}
  catch(e:any){ElMessage.error(e.response?.data?.message||'导入失败，数据库未完成写入')}
  finally{importing.value=false}
}
</script>

<template>
  <section>
    <div class="page-heading"><div><h1>旧系统资料导入</h1><p>先预览校验，再确认写入数据库</p></div></div>
    <el-alert type="info" :closable="false" show-icon title="安全模式：选择文件和开始预检不会写入数据；只有预检通过并再次确认后才会正式导入。"/>
    <el-card class="import-card">
      <div class="steps"><div class="step active"><b>1</b><span>选择资料</span></div><div class="line"></div><div class="step" :class="{active:result}"><b>2</b><span>预览校验</span></div><div class="line"></div><div class="step" :class="{active:result?.importSupported&&result?.summary.invalid===0}"><b>3</b><span>确认导入</span></div></div>
      <el-form label-width="100px" class="selector">
        <el-form-item label="资料类型"><el-select v-model="dataType" style="width:260px" @change="reset"><el-option v-for="item in types" :key="item.value" :label="item.label" :value="item.value"/></el-select></el-form-item>
        <el-form-item label="Excel 文件"><input ref="fileInput" hidden type="file" accept=".xlsx,.xls" @change="onFile"><el-button @click="choose">选择文件</el-button><span class="filename">{{file?.name||`请选择 ${selectedType.expected}`}}</span></el-form-item>
        <el-form-item v-if="isStockTake" label="文件说明"><span class="filename">可直接选择旧系统导出的产品资料 Excel；也可</span><el-link type="primary" href="/templates/货品库存盘点导入模板.xlsx" download>下载简化模板</el-link></el-form-item>
        <el-form-item v-if="isStockTake" label="盘点原因" required><el-input v-model="inventoryReason" maxlength="500" placeholder="例如：2026年8月月末库存盘点"/></el-form-item>
        <el-form-item><el-button type="primary" :loading="loading" @click="preview">开始预检</el-button><el-button @click="reset">清空</el-button></el-form-item>
      </el-form>
    </el-card>
    <template v-if="result">
      <div class="summary-grid"><el-card><small>总记录</small><strong>{{result.summary.total}}</strong></el-card><el-card class="ok"><small>可更新货品</small><strong>{{result.summary.valid}}</strong></el-card><el-card v-if="isStockTake"><small>自动跳过</small><strong>{{result.summary.ignored||0}}</strong></el-card><el-card :class="{bad:result.summary.invalid}"><small>需要处理</small><strong>{{result.summary.invalid}}</strong></el-card><el-card><small>重复编号</small><strong>{{result.summary.duplicates}}</strong></el-card></div>
      <el-card v-if="result.importSupported&&(result.summary.invalid===0||result.canImportValidRows)" class="change-card">
        <el-alert v-if="isStockTake" class="skip-alert" type="warning" :closable="false" show-icon title="系统按产品编号精确匹配，只更新启用且仍销售的现有货品；不存在、停用或停卖货品自动跳过，不新增货品，也不修改品名、价格、分类和状态。负库存按文件真实保存。"/>
        <el-alert v-if="result.canImportValidRows" class="skip-alert" type="warning" :closable="false" show-icon :title="`可以仅导入 ${result.summary.valid} 条合格员工，系统将跳过 ${result.summary.invalid} 条缺少姓名的记录。`"/>
        <div class="change-summary"><span>预计新增 <b class="new">{{result.summary.newRecords}}</b> 条</span><span>预计更新 <b class="update">{{result.summary.updateRecords}}</b> 条</span><span>无需变化 <b>{{result.summary.unchangedRecords}}</b> 条</span><el-button type="danger" :loading="importing" @click="confirmImport">{{result.canImportValidRows?'仅导入合格记录':'确认正式导入'}}</el-button></div>
      </el-card>
      <el-alert v-else-if="result.summary.invalid>0" class="preview-only" type="error" :closable="false" show-icon title="存在问题数据，处理完成并重新预检后才能正式导入。"/>
      <el-alert v-else class="preview-only" type="warning" :closable="false" show-icon title="该资料目前只支持预览，数据库对应结构完成后再开放正式导入。"/>
      <el-card><template #header><div class="card-title"><span>数据预览（最多显示 {{result.previewLimit}} 条）</span><el-tag type="info">表头位于第 {{result.headerRow}} 行</el-tag></div></template>
        <el-table :data="result.rows" stripe max-height="520"><el-table-column v-for="header in tableHeaders" :key="header" :prop="header" :label="header" min-width="130" show-overflow-tooltip><template v-if="header==='校验结果'" #default="scope"><el-tag :type="statusType(scope.row)">{{scope.row[header]}}</el-tag></template></el-table-column></el-table>
      </el-card>
      <el-card v-if="result.issues.length" class="issues"><template #header>问题明细（最多显示100条）</template><el-table :data="result.issues"><el-table-column prop="row" label="Excel 行号" width="120"/><el-table-column prop="message" label="问题"/></el-table></el-card>
    </template>
  </section>
</template>

<style scoped>
.import-card{margin-top:16px}.selector{max-width:720px;margin-top:26px}.filename{margin-left:12px;color:#606266}.steps{display:flex;align-items:center;justify-content:center;max-width:680px;margin:4px auto 20px}.step{display:flex;align-items:center;gap:8px;color:#909399}.step b{display:grid;place-items:center;width:28px;height:28px;border-radius:50%;background:#dcdfe6;color:white}.step.active{color:#2563eb}.step.active b{background:#2563eb}.line{width:110px;height:2px;background:#dcdfe6;margin:0 14px}.summary-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin:16px 0}.summary-grid :deep(.el-card__body){display:flex;flex-direction:column;gap:8px}.summary-grid small{color:#606266}.summary-grid strong{font-size:28px}.summary-grid .ok strong{color:#16a34a}.summary-grid .bad strong{color:#dc2626}.change-card{margin-bottom:16px}.skip-alert{margin-bottom:16px}.change-summary{display:flex;align-items:center;gap:28px}.change-summary b{font-size:22px}.change-summary .new{color:#16a34a}.change-summary .update{color:#d97706}.change-summary .el-button{margin-left:auto}.preview-only{margin-bottom:16px}.card-title{display:flex;justify-content:space-between;align-items:center}.issues{margin-top:16px}@media(max-width:900px){.summary-grid{grid-template-columns:repeat(2,1fr)}.line{width:35px}.change-summary{align-items:flex-start;flex-direction:column;gap:10px}.change-summary .el-button{margin-left:0}}
</style>
