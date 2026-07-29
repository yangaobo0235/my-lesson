<script setup>
import {onMounted, ref} from 'vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import AiPageHeader from './AiPageHeader.vue';
import {aiApi} from '../../api/ai.js';

const plans = ref([]);
const drafts = ref([]);
const loading = ref(false);
const savingId = ref('');
const progressForms = ref({});
const adjustmentForms = ref({});
const versionsByDraft = ref({});

async function loadPlans() {
  loading.value = true;
  try {
    [drafts.value, plans.value] = await Promise.all([
      aiApi.learningPlanDrafts(),
      aiApi.plans()
    ]);
    progressForms.value = Object.fromEntries(plans.value.map(plan => [
      plan.id,
      {progressPercent: plan.progressPercent || 0, note: plan.progressNote || ''}
    ]));
    adjustmentForms.value = Object.fromEntries(drafts.value.map(draft => [
      draft.id,
      adjustmentForms.value[draft.id] || ''
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
        plan.id, Number(form.progressPercent || 0), form.note || null);
    plans.value = plans.value.map(item => item.id === updated.id ? updated : item);
    ElMessage.success('学习进度已更新');
  } finally {
    savingId.value = '';
  }
}

async function approveDraft(draft) {
  await ElMessageBox.confirm(`确认采用 V${draft.state.version} 草案？`, '确认学习计划', {
    type: 'warning', confirmButtonText: '确认', cancelButtonText: '返回'
  });
  savingId.value = draft.id;
  try {
    await aiApi.approveLearningPlanDraft(draft.id);
    ElMessage.success('学习计划已创建');
    await loadPlans();
  } finally {
    savingId.value = '';
  }
}

async function cancelDraft(draft) {
  await ElMessageBox.confirm(`取消 V${draft.state.version} 草案？`, '取消草案', {
    type: 'warning', confirmButtonText: '取消草案', cancelButtonText: '返回'
  });
  savingId.value = draft.id;
  try {
    await aiApi.cancelLearningPlanDraft(draft.id);
    ElMessage.success('草案已取消');
    await loadPlans();
  } finally {
    savingId.value = '';
  }
}

async function adjustDraft(draft) {
  const adjustment = adjustmentForms.value[draft.id]?.trim();
  if (!adjustment) {
    ElMessage.warning('请输入调整内容');
    return;
  }
  savingId.value = draft.id;
  try {
    await aiApi.adjustLearningPlanDraft(draft.id, adjustment, crypto.randomUUID());
    ElMessage.success('已生成新版本草案');
    adjustmentForms.value[draft.id] = '';
    await loadPlans();
  } finally {
    savingId.value = '';
  }
}

async function toggleVersions(draft) {
  if (versionsByDraft.value[draft.id]) {
    delete versionsByDraft.value[draft.id];
    return;
  }
  versionsByDraft.value[draft.id] = await aiApi.learningPlanDraftVersions(draft.id);
}

function progressStatus(value) {
  if (value >= 100) return 'success';
  if (value >= 70) return 'warning';
  return '';
}

function statusType(status) {
  if (['WAITING_APPROVAL', 'APPROVED', 'ACTIVE'].includes(status)) return 'success';
  if (['WAITING_ADJUSTMENT', 'INSUFFICIENT_DATA'].includes(status)) return 'warning';
  if (status === 'CANCELLED') return 'info';
  return '';
}

function isAdjustable(status) {
  return ['WAITING_APPROVAL', 'WAITING_ADJUSTMENT', 'INSUFFICIENT_DATA'].includes(status);
}

function terminationText(reason) {
  const labels = {
    INSUFFICIENT_DATA: '课程资料不足，未进入确认',
    DETERMINISTIC_FALLBACK: '模型流程已降级为确定性计划',
    REPAIR_LIMIT_REACHED: '修正次数已达上限'
  };
  return labels[reason] || reason;
}

onMounted(loadPlans);
</script>

<template>
  <div>
    <AiPageHeader title="学习计划" description="草案版本、校验审查、用户确认与正式计划"/>
    <div v-loading="loading">
      <div class="section-heading">
        <h2>待处理草案</h2>
        <el-tag effect="plain">{{ drafts.length }}</el-tag>
      </div>
      <el-empty v-if="!drafts.length" description="暂无计划草案"/>
      <el-card v-for="draft in drafts" :key="draft.id" class="plan-card">
        <template #header>
          <div class="plan-title">
            <div>
              <strong>{{ draft.state.goal }}</strong>
              <small>V{{ draft.state.version }} · {{ new Date(draft.createdAt).toLocaleString() }}</small>
            </div>
            <el-tag :type="statusType(draft.state.status)">{{ draft.state.status }}</el-tag>
          </div>
        </template>

        <el-alert v-if="draft.state.terminationReason"
                  :title="terminationText(draft.state.terminationReason)"
                  :type="draft.state.terminationReason === 'INSUFFICIENT_DATA' ? 'warning' : 'info'"
                  show-icon :closable="false"/>
        <el-descriptions :column="4" border class="draft-summary">
          <el-descriptions-item label="每日时间">{{ draft.state.minutesPerDay }} 分钟</el-descriptions-item>
          <el-descriptions-item label="候选课程">{{ draft.state.candidates.length }}</el-descriptions-item>
          <el-descriptions-item label="检索改写">{{ draft.state.searchAttempts }}/1</el-descriptions-item>
          <el-descriptions-item label="草案修正">{{ draft.state.repairAttempts }}/2</el-descriptions-item>
        </el-descriptions>

        <div v-if="draft.state.validationErrors.length" class="issue-list">
          <el-alert v-for="error in draft.state.validationErrors" :key="error"
                    :title="error" type="error" show-icon :closable="false"/>
        </div>
        <div v-if="draft.state.reviewResult" class="review-panel">
          <div class="review-heading">
            <strong>Reviewer</strong>
            <el-tag :type="draft.state.reviewResult.accepted ? 'success' : 'warning'">
              {{ draft.state.reviewResult.accepted ? '通过' : '需调整' }}
            </el-tag>
          </div>
          <p v-if="draft.state.reviewResult.suggestedFocus">
            {{ draft.state.reviewResult.suggestedFocus }}
          </p>
          <ul v-if="draft.state.reviewResult.issues.length">
            <li v-for="issue in draft.state.reviewResult.issues" :key="issue.code + issue.message">
              <code>{{ issue.code }}</code> {{ issue.message }}
            </li>
          </ul>
        </div>

        <el-table :data="draft.state.draft.courses" class="course-table">
          <el-table-column prop="order" label="#" width="60"/>
          <el-table-column prop="courseId" label="课程 ID" width="110"/>
          <el-table-column prop="objective" label="学习目标" min-width="260"/>
        </el-table>

        <div class="draft-actions" :class="{compact: !isAdjustable(draft.state.status)}">
          <el-input v-model="adjustmentForms[draft.id]" maxlength="500"
                    v-if="isAdjustable(draft.state.status)"
                    placeholder="调整目标、课程顺序或每日节奏"/>
          <el-button v-if="isAdjustable(draft.state.status)"
                     :loading="savingId === draft.id" @click="adjustDraft(draft)">调整</el-button>
          <el-button v-if="draft.state.status === 'WAITING_APPROVAL'" type="success"
                     :loading="savingId === draft.id" @click="approveDraft(draft)">确认</el-button>
          <el-button v-if="draft.state.status === 'WAITING_APPROVAL'" type="danger" plain
                     :loading="savingId === draft.id" @click="cancelDraft(draft)">取消</el-button>
          <el-button link type="primary" @click="toggleVersions(draft)">版本记录</el-button>
        </div>

        <el-timeline v-if="versionsByDraft[draft.id]" class="version-list">
          <el-timeline-item v-for="version in versionsByDraft[draft.id]" :key="version.id"
                            :timestamp="new Date(version.createdAt).toLocaleString()">
            V{{ version.state.version }} · {{ version.state.status }}
            <span v-if="version.state.adjustmentRequest"> · {{ version.state.adjustmentRequest }}</span>
          </el-timeline-item>
        </el-timeline>
      </el-card>

      <div class="section-heading">
        <h2>正式计划</h2>
        <el-tag effect="plain">{{ plans.length }}</el-tag>
      </div>
      <el-empty v-if="!plans.length" description="暂无正式学习计划"/>
      <el-card v-for="plan in plans" :key="plan.id" class="plan-card">
        <template #header><div class="plan-title">
          <div><strong>{{ plan.goal }}</strong><small>{{ new Date(plan.createdAt).toLocaleString() }}</small></div>
          <el-tag :type="statusType(plan.status)">{{ plan.status }}</el-tag>
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
          <div v-if="plan.adjustments?.length" class="issue-list">
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
.section-heading { display:flex; align-items:center; gap:10px; margin:18px 0 12px; }
.section-heading h2 { margin:0; font-size:18px; letter-spacing:0; }
.plan-card { margin-bottom:16px; }
.plan-title { display:flex; align-items:center; justify-content:space-between; gap:12px; }
.plan-title small { display:block; margin-top:5px; color:var(--el-text-color-secondary); }
.draft-summary { margin-top:14px; }
.issue-list { display:grid; gap:8px; margin-top:14px; }
.review-panel { margin-top:14px; padding:12px 0; border-block:1px solid var(--el-border-color-lighter); }
.review-heading { display:flex; align-items:center; gap:10px; }
.review-panel p,.review-panel ul { margin:8px 0 0; line-height:1.6; }
.draft-actions { display:grid; grid-template-columns:minmax(220px,1fr) repeat(4,auto); gap:10px; margin-top:14px; }
.draft-actions.compact { display:flex; justify-content:flex-end; }
.version-list { margin-top:18px; }
.progress-panel { display:grid; gap:12px; margin-top:16px; }
.progress-form { display:grid; grid-template-columns:140px minmax(180px,1fr) 110px; gap:10px; }
.course-table { margin-top:16px; }
code { color:var(--el-color-warning-dark-2); }
@media (max-width:900px) {
  .draft-actions,.progress-form { grid-template-columns:1fr; }
  .draft-summary { --el-descriptions-table-border:1px solid var(--el-border-color-lighter); }
}
</style>
