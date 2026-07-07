<script setup>
import {onMounted, ref} from 'vue';
import {ElMessage} from 'element-plus';
import AiPageHeader from './AiPageHeader.vue';
import {aiApi} from '../../api/ai.js';

const plans = ref([]);
const loading = ref(false);
const savingId = ref('');
const progressForms = ref({});

async function loadPlans() {
  loading.value = true;
  try {
    plans.value = await aiApi.plans();
    progressForms.value = Object.fromEntries(plans.value.map(plan => [
      plan.id,
      {
        progressPercent: plan.progressPercent || 0,
        note: plan.progressNote || ''
      }
    ]));
  } finally {
    loading.value = false;
  }
}

async function saveProgress(plan) {
  const form = progressForms.value[plan.id];
  savingId.value = plan.id;
  try {
    const updated = await aiApi.updatePlanProgress(
        plan.id,
        Number(form.progressPercent || 0),
        form.note || null);
    plans.value = plans.value.map(item => item.id === updated.id ? updated : item);
    progressForms.value[updated.id] = {
      progressPercent: updated.progressPercent || 0,
      note: updated.progressNote || ''
    };
    ElMessage.success('学习进度已更新');
  } finally {
    savingId.value = '';
  }
}

function progressStatus(value) {
  if (value >= 100) return 'success';
  if (value >= 70) return 'warning';
  return '';
}

onMounted(loadPlans);
</script>

<template>
  <div>
    <AiPageHeader title="学习计划" description="已确认并持久化的学习计划、进度追踪和调整建议"/>
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

        <div class="progress-panel">
          <el-progress :percentage="plan.progressPercent || 0" :status="progressStatus(plan.progressPercent || 0)"/>
          <div class="progress-form">
            <el-input-number v-model="progressForms[plan.id].progressPercent" :min="0" :max="100"/>
            <el-input v-model="progressForms[plan.id].note" clearable placeholder="本次进度备注"/>
            <el-button type="primary" :loading="savingId === plan.id" @click="saveProgress(plan)">保存进度</el-button>
          </div>
          <div v-if="plan.adjustments?.length" class="adjustments">
            <el-alert v-for="item in plan.adjustments" :key="item.type + item.message"
                      :title="item.message" type="info" show-icon :closable="false"/>
          </div>
        </div>

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
.plan-card { margin-bottom:16px; }
.plan-title { display:flex; align-items:center; justify-content:space-between; }
.plan-title small { display:block; margin-top:5px; color:var(--el-text-color-secondary); }
.progress-panel { display:grid; gap:12px; margin-top:16px; }
.progress-form { display:grid; grid-template-columns:140px minmax(180px, 1fr) 110px; gap:10px; }
.adjustments { display:grid; gap:8px; }
.course-table { margin-top:16px; }
@media (max-width:800px) {
  .progress-form { grid-template-columns:1fr; }
}
</style>
