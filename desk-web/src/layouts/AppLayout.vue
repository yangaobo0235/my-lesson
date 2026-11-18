<script setup lang="ts">
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { ElButton, ElAlert } from 'element-plus'
import { useAuthStore } from '../stores/authStore'
import { useOperation } from '../composables/useOperation'
const auth = useAuthStore(),
  router = useRouter()
const { run, error, busy } = useOperation()
function logout() {
  void run(async () => {
    await auth.signOut()
    await router.replace('/login')
  })
}
</script>

<template>
  <div class="app-layout">
    <header class="app-header">
      <RouterLink class="app-brand" to="/"
        >Service Desk · 企业 IT 服务台</RouterLink
      >
      <nav aria-label="主导航">
        <RouterLink v-if="auth.has('CUSTOMER_USER')" to="/support"
          >我的支持</RouterLink
        >
        <RouterLink
          v-if="
            auth.has('CUSTOMER_USER') ||
            auth.has('SERVICE_AGENT') ||
            auth.has('TECHNICIAN')
          "
          to="/tickets"
          >工单</RouterLink
        >
        <RouterLink
          v-if="
            auth.has('CUSTOMER_USER') ||
            auth.has('SERVICE_AGENT') ||
            auth.has('TECHNICIAN')
          "
          to="/notifications"
          >通知</RouterLink
        >
        <RouterLink
          v-if="auth.has('SYSTEM_ADMIN')"
          to="/administration/identity"
          >组织与账号</RouterLink
        >
        <RouterLink
          v-if="auth.has('SYSTEM_ADMIN') || auth.has('SERVICE_AGENT')"
          to="/administration/support"
          >支持管理</RouterLink
        >
        <RouterLink
          v-if="auth.has('SYSTEM_ADMIN')"
          to="/administration/onsite-slots"
          >上门时段</RouterLink
        >
        <RouterLink v-if="auth.has('CUSTOMER_USER')" to="/diagnosis"
          >自助排障</RouterLink
        >
        <RouterLink
          v-if="auth.has('KNOWLEDGE_ADMIN')"
          to="/administration/knowledge"
          >知识管理</RouterLink
        >
        <RouterLink to="/password">修改密码</RouterLink>
      </nav>
      <span>{{ auth.user?.displayName }}</span
      ><ElButton :loading="busy" @click="logout">退出</ElButton>
    </header>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <RouterView :key="$route.fullPath" />
  </div>
</template>
