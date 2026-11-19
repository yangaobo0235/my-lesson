<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { useAuthStore } from '../../stores/authStore'
import { useIntakeStore } from '../../stores/intakeStore'
import DiagnosisPanel from './DiagnosisPanel.vue'

const auth = useAuthStore()
const description = useIntakeStore().take(auth.user?.ownerId, 'diagnosis')
</script>
<template>
  <main class="page">
    <div class="page-heading">
      <div>
        <h1>咨询与排查</h1>
        <p>发起新排查，或继续上次的会话和诊断任务。</p>
      </div>
      <RouterLink :to="{ name: 'report' }">直接报修</RouterLink>
    </div>
    <DiagnosisPanel :initial-description="description" />
  </main>
</template>
