<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import { useI18n } from 'vue-i18n'
import LanguageSwitcher from '../components/LanguageSwitcher.vue'
const router=useRouter(),form=reactive({username:'',password:''}),loading=ref(false)
const { t }=useI18n()
async function login(){if(!form.username||!form.password)return ElMessage.warning(t('login.required'));loading.value=true;try{const{data}=await api.post('/auth/login',form);localStorage.setItem('accessToken',data.data.token);localStorage.setItem('displayName',data.data.displayName);localStorage.setItem('role',data.data.role);if(data.data.mustChangePassword){localStorage.setItem('mustChangePassword','1');return await router.replace('/change-password')}localStorage.removeItem('mustChangePassword');await router.replace('/home')}catch(e:any){ElMessage.error(e.response?.data?.message||t('login.invalid'))}finally{loading.value=false}}
</script>
<template><div class="login-page"><div class="login-language"><LanguageSwitcher/></div><el-card class="login-card"><h1>{{ $t('brand.name') }}</h1><p>{{ $t('login.subtitle') }}</p><el-form @submit.prevent="login"><el-form-item><el-input v-model="form.username" :placeholder="$t('login.username')" size="large"/></el-form-item><el-form-item><el-input v-model="form.password" type="password" show-password :placeholder="$t('login.password')" size="large" @keyup.enter="login"/></el-form-item><el-button type="primary" size="large" :loading="loading" class="login-button" @click="login">{{ $t('login.signIn') }}</el-button></el-form></el-card></div></template>

<style scoped>.login-language{position:fixed;top:20px;right:24px;z-index:2}</style>


