<script setup>
defineProps({
  title: {type: String, required: true},
  description: {type: String, default: ''}
});
</script>

<template>
  <header class="ai-page-header">
    <div><h2>{{ title }}</h2><p v-if="description">{{ description }}</p></div>
    <nav>
      <router-link to="/ai/chat">对话</router-link>
      <router-link to="/ai/conversations">历史会话</router-link>
      <router-link to="/ai/plans">学习计划</router-link>
      <router-link to="/ai/approvals">待确认操作</router-link>
      <router-link to="/ai/admin/evaluation">评测与知识库</router-link>
    </nav>
  </header>
</template>

<style scoped lang="scss">
.ai-page-header { display:flex; align-items:flex-end; justify-content:space-between; gap:20px; margin-bottom:20px; }
h2 { margin:0; font-size:26px; } p { margin:6px 0 0; color:var(--el-text-color-secondary); }
nav { display:flex; flex-wrap:wrap; gap:8px; }
a { padding:7px 12px; color:var(--el-text-color-regular); text-decoration:none; border-radius:8px; }
a.router-link-active { color:var(--el-color-primary); background:var(--el-color-primary-light-9); }
</style>
