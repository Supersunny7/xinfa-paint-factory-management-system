<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { LANGUAGE_STORAGE_KEY, type AppLocale } from '../i18n'

const { locale } = useI18n()
function changeLanguage(value: AppLocale) {
  locale.value = value
  localStorage.setItem(LANGUAGE_STORAGE_KEY, value)
  document.documentElement.lang = value
  document.title = value === 'zh-CN' ? '新发油漆业务管理系统' : 'Xinfa Paint Factory Management System'
}
</script>

<template>
  <el-select class="language-switcher" :model-value="locale" :aria-label="$t('language.label')" @change="changeLanguage">
    <el-option label="简体中文" value="zh-CN" />
    <el-option label="English" value="en-US" />
  </el-select>
</template>

<style scoped>
.language-switcher{width:122px}
</style>
