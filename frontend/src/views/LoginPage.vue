<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api'
const router=useRouter(),form=reactive({username:'',password:''}),loading=ref(false)
async function login(){if(!form.username||!form.password)return ElMessage.warning('Enter username and password');loading.value=true;try{const{data}=await api.post('/auth/login',form);localStorage.setItem('accessToken',data.data.token);localStorage.setItem('displayName',data.data.displayName);localStorage.setItem('role',data.data.role);if(data.data.mustChangePassword){localStorage.setItem('mustChangePassword','1');return await router.replace('/change-password')}localStorage.removeItem('mustChangePassword');await router.replace('/home')}catch(e:any){ElMessage.error(e.response?.data?.message||'Username or password is incorrect')}finally{loading.value=false}}
</script>
<template><div class="login-page"><el-card class="login-card"><h1>Xinfa Paint Factory</h1><p>Business Management System</p><el-form @submit.prevent="login"><el-form-item><el-input v-model="form.username" placeholder="Username" size="large"/></el-form-item><el-form-item><el-input v-model="form.password" type="password" show-password placeholder="Password" size="large" @keyup.enter="login"/></el-form-item><el-button type="primary" size="large" :loading="loading" class="login-button" @click="login">Sign In</el-button></el-form></el-card></div></template>


