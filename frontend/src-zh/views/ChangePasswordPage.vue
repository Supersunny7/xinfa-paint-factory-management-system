<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api'
const router=useRouter(),loading=ref(false),form=reactive({currentPassword:'',newPassword:'',confirmPassword:''})
async function submit(){if(!form.currentPassword||!form.newPassword)return ElMessage.warning('请填写当前密码和新密码');if(form.newPassword!==form.confirmPassword)return ElMessage.warning('两次输入的新密码不一致');loading.value=true;try{await api.post('/auth/change-password',{currentPassword:form.currentPassword,newPassword:form.newPassword});localStorage.removeItem('mustChangePassword');ElMessage.success('密码修改成功，请使用新密码继续');const role=localStorage.getItem('role');const home:any={ADMIN:'/customers',SALES:'/sales-orders',WAREHOUSE:'/inventory-reconciliation',DISPATCH:'/dispatch-sheets'};await router.replace(home[role||'']||'/login')}catch(e:any){ElMessage.error(e.response?.data?.message||'修改失败，请检查密码格式')}finally{loading.value=false}}
</script>
<template><div class="change-page"><el-card class="change-card"><h1>首次登录，请修改密码</h1><p>为了账号安全，初始密码或管理员重置的密码不能长期使用。</p><el-form label-position="top"><el-form-item label="当前密码"><el-input v-model="form.currentPassword" type="password" show-password/></el-form-item><el-form-item label="新密码"><el-input v-model="form.newPassword" type="password" show-password placeholder="至少8位，包含字母和数字"/></el-form-item><el-form-item label="确认新密码"><el-input v-model="form.confirmPassword" type="password" show-password @keyup.enter="submit"/></el-form-item><el-button type="primary" class="submit" :loading="loading" @click="submit">确认修改</el-button></el-form></el-card></div></template>
<style scoped>.change-page{min-height:100vh;display:flex;align-items:center;justify-content:center;background:#f3f6fa}.change-card{width:460px}.change-card h1{margin:0 0 8px}.change-card p{color:#606266;margin:0 0 24px}.submit{width:100%}</style>
