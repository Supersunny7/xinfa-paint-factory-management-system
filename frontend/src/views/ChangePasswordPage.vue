<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api'
const router=useRouter(),loading=ref(false),form=reactive({currentPassword:'',newPassword:'',confirmPassword:''})
async function submit(){if(!form.currentPassword||!form.newPassword)return ElMessage.warning('Enter the current and new passwords');if(form.newPassword!==form.confirmPassword)return ElMessage.warning('The two new passwords do not match');loading.value=true;try{await api.post('/auth/change-password',{currentPassword:form.currentPassword,newPassword:form.newPassword});localStorage.removeItem('mustChangePassword');ElMessage.success('PasswordEditSuccessful, Continue with the new password');const role=localStorage.getItem('role');const home:any={ADMIN:'/customers',SALES:'/sales-orders',WAREHOUSE:'/inventory-reconciliation',DISPATCH:'/dispatch-sheets'};await router.replace(home[role||'']||'/login')}catch(e:any){ElMessage.error(e.response?.data?.message||'EditFailed, Check the password format')}finally{loading.value=false}}
</script>
<template><div class="change-page"><el-card class="change-card"><h1>First sign-in: change your password</h1><p>For account security, an initial or administrator-reset password must be changed.</p><el-form label-position="top"><el-form-item label="Current Password"><el-input v-model="form.currentPassword" type="password" show-password/></el-form-item><el-form-item label="New Password"><el-input v-model="form.newPassword" type="password" show-password placeholder="At least 8 characters including letters and numbers"/></el-form-item><el-form-item label="ConfirmNew Password"><el-input v-model="form.confirmPassword" type="password" show-password @keyup.enter="submit"/></el-form-item><el-button type="primary" class="submit" :loading="loading" @click="submit">ConfirmEdit</el-button></el-form></el-card></div></template>
<style scoped>.change-page{min-height:100vh;display:flex;align-items:center;justify-content:center;background:#f3f6fa}.change-card{width:460px}.change-card h1{margin:0 0 8px}.change-card p{color:#606266;margin:0 0 24px}.submit{width:100%}</style>


