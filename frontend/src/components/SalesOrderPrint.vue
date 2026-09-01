<script setup lang="ts">
withDefaults(defineProps<{ detail:any; type:'delivery'|'warehouse'; settings?:{companyName?:string;showPhone?:boolean;showPrice?:boolean;showRemark?:boolean;paperSize?:'CONTINUOUS_241_140'|'A4'|'A5';orientation?:'portrait'|'landscape'} }>(),{settings:()=>({})})
function productText(item:any){return [item.productName,item.specification,item.color].filter(Boolean).join(' ')}
function dateTime(value:any){return value?String(value).replace('T',' ').slice(0,16):''}
</script>

<template>
  <article class="sales-paper" :class="[type==='warehouse'?'warehouse-paper':'delivery-paper',`paper-${settings.paperSize||'CONTINUOUS_241_140'}`,`paper-${settings.orientation||'landscape'}`]">
    <template v-if="type==='delivery'">
      <div v-if="settings.companyName" class="paper-company">{{settings.companyName}}</div><h2>Delivery Note</h2>
      <div class="paper-meta"><span><b>Customer: </b>{{detail.customerName}}</span><span><b>Document No.: </b>{{detail.orderNo}}</span><span><b>Date: </b>{{dateTime(detail.createdAt)||detail.orderDate}}</span><span v-if="settings.showPhone!==false" class="wide"><b>Phone: </b>{{detail.customerPhone||'—'}}</span></div>
      <table><thead><tr><th>Product / Specification</th><th>Packages</th><th>Package Unit</th><th>Quantity</th><th>Unit</th><th v-if="settings.showPrice!==false">Unit Price</th><th v-if="settings.showPrice!==false">Amount</th><th v-if="settings.showRemark!==false">Notes</th></tr></thead>
        <tbody><tr v-for="item in detail.items" :key="item.id||item.skuCode"><td>{{productText(item)}}</td><td>{{item.packageCount||''}}</td><td>{{item.packageUnit||''}}</td><td>{{item.quantity}}</td><td>{{item.salesUnit}}</td><td v-if="settings.showPrice!==false">{{Number(item.unitPrice||0).toFixed(2)}}</td><td v-if="settings.showPrice!==false">{{Number(item.lineAmount||0).toFixed(2)}}</td><td v-if="settings.showRemark!==false">{{item.remark||''}}</td></tr>
        <tr v-if="settings.showPrice!==false" class="total-row"><td>Total</td><td colspan="4"></td><td colspan="3">¥ {{Number(detail.totalAmount||0).toFixed(2)}}</td></tr></tbody></table>
      <div class="paper-footer"><span>Order Taker: {{detail.createdByName||'—'}}</span><span>Prepared By: {{detail.printedByName||detail.createdByName||'—'}}</span><span>Received By: ____________</span></div>
      <div v-if="settings.showRemark!==false" class="paper-remark">Notes: {{detail.remark||'None'}}</div>
    </template>
    <template v-else>
      <div class="warehouse-head"><strong>{{detail.routeCode||detail.routeName||'—'}}</strong><h2>Outbound Document</h2><div><small>{{detail.orderDate}}</small><br>{{detail.orderNo}}</div></div>
      <div class="warehouse-customer">{{detail.customerName}} <small v-if="settings.showPhone!==false">{{detail.customerPhone||''}}</small></div>
      <table><thead><tr><th>Product / Specification</th><th>Quantity</th><th>Unit</th></tr></thead><tbody><tr v-for="item in detail.items" :key="item.id||item.skuCode"><td>{{productText(item)}}</td><td>{{item.quantity}}</td><td>{{item.salesUnit}}</td></tr></tbody></table>
      <div class="warehouse-footer"><span>Salesperson: {{detail.salespersonName||'—'}}</span><span>Prepared By: {{detail.createdByName||'System'}}</span></div>
    </template>
  </article>
</template>


