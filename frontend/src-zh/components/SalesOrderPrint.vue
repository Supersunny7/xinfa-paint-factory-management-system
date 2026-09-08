<script setup lang="ts">
withDefaults(defineProps<{ detail:any; type:'delivery'|'warehouse'; settings?:{companyName?:string;showPhone?:boolean;showPrice?:boolean;showRemark?:boolean;paperSize?:'CONTINUOUS_241_140'|'A4'|'A5';orientation?:'portrait'|'landscape'} }>(),{settings:()=>({})})
function productText(item:any){return [item.productName,item.specification,item.color].filter(Boolean).join(' ')}
function dateTime(value:any){return value?String(value).replace('T',' ').slice(0,16):''}
</script>

<template>
  <article class="sales-paper" :class="[type==='warehouse'?'warehouse-paper':'delivery-paper',`paper-${settings.paperSize||'CONTINUOUS_241_140'}`,`paper-${settings.orientation||'landscape'}`]">
    <template v-if="type==='delivery'">
      <div v-if="settings.companyName" class="paper-company">{{settings.companyName}}</div><h2>送货单</h2>
      <div class="paper-meta"><span><b>客户：</b>{{detail.customerName}}</span><span><b>单号：</b>{{detail.orderNo}}</span><span><b>日期：</b>{{dateTime(detail.createdAt)||detail.orderDate}}</span><span v-if="settings.showPhone!==false" class="wide"><b>电话：</b>{{detail.customerPhone||'—'}}</span></div>
      <table><thead><tr><th>品名规格</th><th>件数</th><th>包装单位</th><th>数量</th><th>单位</th><th v-if="settings.showPrice!==false">单价</th><th v-if="settings.showPrice!==false">金额</th><th v-if="settings.showRemark!==false">备注</th></tr></thead>
        <tbody><tr v-for="item in detail.items" :key="item.id||item.skuCode"><td>{{productText(item)}}</td><td>{{item.packageCount||''}}</td><td>{{item.packageUnit||''}}</td><td>{{item.quantity}}</td><td>{{item.salesUnit}}</td><td v-if="settings.showPrice!==false">{{Number(item.unitPrice||0).toFixed(2)}}</td><td v-if="settings.showPrice!==false">{{Number(item.lineAmount||0).toFixed(2)}}</td><td v-if="settings.showRemark!==false">{{item.remark||''}}</td></tr>
        <tr v-if="settings.showPrice!==false" class="total-row"><td>合计</td><td colspan="4"></td><td colspan="3">¥ {{Number(detail.totalAmount||0).toFixed(2)}}</td></tr></tbody></table>
      <div class="paper-footer"><span>接单人：{{detail.createdByName||'—'}}</span><span>打单人：{{detail.printedByName||detail.createdByName||'—'}}</span><span>收货人：____________</span></div>
      <div v-if="settings.showRemark!==false" class="paper-remark">备注：{{detail.remark||'无'}}</div>
    </template>
    <template v-else>
      <div class="warehouse-head"><strong>{{detail.routeCode||detail.routeName||'—'}}</strong><h2>出仓单</h2><div><small>{{detail.orderDate}}</small><br>{{detail.orderNo}}</div></div>
      <div class="warehouse-customer">{{detail.customerName}} <small v-if="settings.showPhone!==false">{{detail.customerPhone||''}}</small></div>
      <table><thead><tr><th>品名规格</th><th>数量</th><th>单位</th></tr></thead><tbody><tr v-for="item in detail.items" :key="item.id||item.skuCode"><td>{{productText(item)}}</td><td>{{item.quantity}}</td><td>{{item.salesUnit}}</td></tr></tbody></table>
      <div class="warehouse-footer"><span>业务员：{{detail.salespersonName||'—'}}</span><span>制单：{{detail.createdByName||'系统'}}</span></div>
    </template>
  </article>
</template>
