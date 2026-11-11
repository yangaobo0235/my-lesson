<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <div class="app-layout">
    <header class="app-header">
      <RouterLink class="app-brand" to="/"
        >Service Desk · 企业 IT 服务台</RouterLink
      >
    </header>
    <RouterView />
  </div>
</template>
