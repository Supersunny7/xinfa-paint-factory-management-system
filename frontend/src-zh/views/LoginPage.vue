<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api'
const router=useRouter(),form=reactive({username:'',password:''}),loading=ref(false)
async function login(){if(!form.username||!form.password)return ElMessage.warning('请输入用户名和密码');loading.value=true;try{const{data}=await api.post('/auth/login',form);localStorage.setItem('accessToken',data.data.token);localStorage.setItem('displayName',data.data.displayName);localStorage.setItem('role',data.data.role);if(data.data.mustChangePassword){localStorage.setItem('mustChangePassword','1');return await router.replace('/change-password')}localStorage.removeItem('mustChangePassword');await router.replace('/home')}catch(e:any){ElMessage.error(e.response?.data?.message||'用户名或密码错误')}finally{loading.value=false}}
</script>
<template><div class="login-page"><el-card class="login-card"><h1>新发油漆业务管理系统</h1><p>企业内部账号登录</p><el-form @submit.prevent="login"><el-form-item><el-input v-model="form.username" placeholder="用户名" size="large"/></el-form-item><el-form-item><el-input v-model="form.password" type="password" show-password placeholder="密码" size="large" @keyup.enter="login"/></el-form-item><el-button type="primary" size="large" :loading="loading" class="login-button" @click="login">登录</el-button></el-form></el-card></div></template>
