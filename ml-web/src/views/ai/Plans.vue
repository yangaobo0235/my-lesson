<script setup>
import {onMounted, ref} from 'vue';
import AiPageHeader from './AiPageHeader.vue';
import {aiApi} from '../../api/ai.js';
const plans = ref([]);
const loading = ref(false);
onMounted(async () => {
  loading.value = true;
  try { plans.value = await aiApi.plans(); } finally { loading.value = false; }
});
</script>

<template>
  <div>
    <AiPageHeader title="学习计划" description="已确认并持久化的学习计划"/>
    <div v-loading="loading">
      <el-empty v-if="!plans.length" description="暂无学习计划，可在对话中描述目标后生成"/>
      <el-card v-for="plan in plans" :key="plan.id" class="plan-card">
        <template #header><div class="plan-title">
          <div><strong>{{ plan.goal }}</strong><small>{{ new Date(plan.createdAt).toLocaleString() }}</small></div>
          <el-tag :type="plan.status === 'ACTIVE' ? 'success' : 'info'">{{ plan.status }}</el-tag>
        </div></template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="每日时间">{{ plan.availableMinutesPerDay }} 分钟</el-descriptions-item>
          <el-descriptions-item label="预计周期">{{ plan.estimatedWeeks }} 周</el-descriptions-item>
          <el-descriptions-item label="课程数量">{{ plan.courses.length }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="plan.courses" class="course-table">
          <el-table-column prop="order" label="#" width="60"/>
          <el-table-column prop="title" label="课程" min-width="180"/>
          <el-table-column prop="author" label="作者" width="140"/>
          <el-table-column prop="objective" label="学习目标" min-width="260"/>
        </el-table>
      </el-card>
    </div>
  </div>
</template>
<style scoped>
.plan-card { margin-bottom:16px; } .plan-title { display:flex; align-items:center; justify-content:space-between; }
.plan-title small { display:block; margin-top:5px; color:var(--el-text-color-secondary); } .course-table { margin-top:16px; }
</style>
