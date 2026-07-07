<script setup>
import {computed} from 'vue';
import {getAccessibleAiNavItems, getLoginRoles} from '../../util/auth.js';

defineProps({
  title: {type: String, required: true},
  description: {type: String, default: ''}
});

const navItems = computed(() => getAccessibleAiNavItems(getLoginRoles()));
</script>

<template>
  <header class="ai-page-header">
    <div><h2>{{ title }}</h2><p v-if="description">{{ description }}</p></div>
    <nav>
      <router-link v-for="item in navItems" :key="item.path" :to="item.path">{{ item.label }}</router-link>
    </nav>
  </header>
</template>

<style scoped lang="scss">
.ai-page-header { display:flex; align-items:flex-end; justify-content:space-between; gap:20px; margin-bottom:20px; padding-bottom:16px; border-bottom:1px solid var(--ml-border); }
h2 { margin:0; font-size:22px; font-weight:700; color:var(--ml-text); } p { margin:6px 0 0; color:var(--el-text-color-secondary); }
nav { display:flex; flex-wrap:wrap; gap:8px; }
a { padding:7px 12px; color:var(--el-text-color-regular); text-decoration:none; border-radius:6px; border:1px solid transparent; }
a.router-link-active { color:var(--el-color-primary); background:var(--el-color-primary-light-9); }
</style>
