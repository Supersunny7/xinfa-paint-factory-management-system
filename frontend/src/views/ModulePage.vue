<script setup lang="ts">
defineProps<{ title: string; description: string }>()
</script>

<template>
  <section>
    <div class="page-heading">
      <div><h1>{{ title }}</h1><p>{{ description }}</p></div>
      <el-button type="primary">Create</el-button>
    </div>
    <el-alert title="The module shell is ready; business functions will be added in a later phase." type="success" :closable="false" show-icon />
  </section>
</template>


