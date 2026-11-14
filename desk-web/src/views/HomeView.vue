<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { useAuthStore } from '../stores/authStore'
const auth = useAuthStore()
</script>
<template>
  <main>
    <p class="eyebrow">办公 IT 支持与协同排障</p>
    <h1>让每一次办公 IT 求助都有清晰的进展</h1>
    <p>
      连接终端、打印、网络与办公软件支持。提交问题、协同处理，并确认最终结果。
    </p>
    <div class="service-grid">
      <RouterLink v-if="auth.has('CUSTOMER_USER')" class="card" to="/support"
        ><h2>我的支持范围</h2>
        <p>查看可用服务，提交新问题。</p></RouterLink
      >
      <RouterLink
        v-if="
          auth.has('CUSTOMER_USER') ||
          auth.has('SERVICE_AGENT') ||
          auth.has('TECHNICIAN')
        "
        class="card"
        to="/tickets"
        ><h2>工单工作台</h2>
        <p>查看进展、补充信息与处理结果。</p></RouterLink
      >
      <RouterLink
        v-if="auth.has('SYSTEM_ADMIN')"
        class="card"
        to="/administration/identity"
        ><h2>组织与账号</h2>
        <p>开通组织、维护角色与客户范围。</p></RouterLink
      >
      <RouterLink
        v-if="auth.has('SYSTEM_ADMIN') || auth.has('SERVICE_AGENT')"
        class="card"
        to="/administration/support"
        ><h2>支持管理</h2>
        <p>维护服务项目、资格与设备。</p></RouterLink
      >
    </div>
  </main>
</template>
