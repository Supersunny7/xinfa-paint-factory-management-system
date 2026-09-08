<script setup lang="ts">
defineProps<{ title: string; description: string }>()
</script>

<template>
  <section>
    <div class="page-heading">
      <div><h1>{{ title }}</h1><p>{{ description }}</p></div>
      <el-button type="primary">新建</el-button>
    </div>
    <el-alert title="工程骨架已就绪，本模块业务功能将在下一阶段接入。" type="success" :closable="false" show-icon />
  </section>
</template>
