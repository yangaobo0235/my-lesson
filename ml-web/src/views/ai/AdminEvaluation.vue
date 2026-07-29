<script setup>
import {onMounted, reactive, ref} from 'vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import AiPageHeader from './AiPageHeader.vue';
import {aiApi} from '../../api/ai.js';

const knowledge = ref({});
const summary = ref({});
const results = ref([]);
const sources = ref([]);
const toolCalls = ref([]);
const loading = ref(false);
const sourceLoading = ref(false);
const toolLoading = ref(false);
const activeTab = ref('knowledge');
const evaluationMode = ref('deterministic');
const evaluationReport = ref(null);
const evaluationRunning = ref(false);

const sourceFilters = reactive({
  sourceType: '',
  status: '',
  limit: 100
});
const toolFilters = reactive({
  userId: '',
  toolName: '',
  toolSource: '',
  accessType: '',
  status: '',
  limit: 100
});

async function loadOverview() {
  [knowledge.value, summary.value, results.value] = await Promise.all([
    aiApi.knowledgeStatus(),
    aiApi.evaluationSummary(),
    aiApi.evaluationResults()
  ]);
}

function compactParams(filters) {
  return Object.fromEntries(Object.entries(filters)
      .filter(([, value]) => value !== '' && value !== null && value !== undefined));
}

async function loadSources() {
  sourceLoading.value = true;
  try {
    sources.value = await aiApi.knowledgeSources(compactParams(sourceFilters));
  } finally {
    sourceLoading.value = false;
  }
}

async function loadToolCalls() {
  toolLoading.value = true;
  try {
    toolCalls.value = await aiApi.toolCalls(compactParams(toolFilters));
  } finally {
    toolLoading.value = false;
  }
}

async function load() {
  loading.value = true;
  try {
    await Promise.all([loadOverview(), loadSources(), loadToolCalls()]);
  } catch (error) {
    ElMessage.error(error.response?.status === 403 ? '当前账号没有管理员权限' : '加载 AI 管理数据失败');
  } finally {
    loading.value = false;
  }
}

async function retrySource(row) {
  try {
    await ElMessageBox.confirm(`确认重新索引 ${row.sourceType}:${row.sourceId}？`, '知识源重试', {
      type: 'warning',
      confirmButtonText: '重试',
      cancelButtonText: '取消'
    });
  } catch {
    return;
  }
  sourceLoading.value = true;
  try {
    await aiApi.retryKnowledgeSource(row.sourceType, row.sourceId);
    ElMessage.success('已完成重试');
    await Promise.all([loadOverview(), loadSources()]);
  } finally {
    sourceLoading.value = false;
  }
}

async function runEvaluation() {
  evaluationRunning.value = true;
  try {
    evaluationReport.value = await aiApi.runEvaluation(evaluationMode.value);
    ElMessage.success(`评测完成：${evaluationReport.value.passedCount}/${evaluationReport.value.totalCount}`);
    await loadOverview();
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '评测执行失败');
  } finally {
    evaluationRunning.value = false;
  }
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : '-';
}

function statusType(value) {
  if (['SUCCEEDED', 'INDEXED'].includes(value)) return 'success';
  if (['FAILED', 'TIMED_OUT'].includes(value)) return 'danger';
  if (['STARTED', 'PROCESSING', 'PENDING'].includes(value)) return 'warning';
  return 'info';
}

function jsonPreview(value) {
  if (!value) return '-';
  return value.length > 120 ? `${value.slice(0, 120)}...` : value;
}

onMounted(load);
</script>

<template>
  <div>
    <AiPageHeader title="评测与知识库" description="查看 RAG 评测、知识同步状态和 AI Tool 调用审计"/>
    <div v-loading="loading">
      <el-row :gutter="16">
        <el-col :span="6"><el-card><el-statistic title="启用评测用例" :value="summary.caseCount || 0"/></el-card></el-col>
        <el-col :span="6"><el-card><el-statistic title="已执行结果" :value="summary.resultCount || 0"/></el-card></el-col>
        <el-col :span="6"><el-card><el-statistic title="通过率" :value="(summary.passRate || 0) * 100" suffix="%" :precision="1"/></el-card></el-col>
        <el-col :span="6"><el-card><el-statistic title="已索引分块" :value="knowledge.indexedChunks || 0"/></el-card></el-col>
      </el-row>

      <el-card class="status-card">
        <el-descriptions title="知识库状态" :column="4" border>
          <el-descriptions-item label="状态">{{ knowledge.state || '-' }}</el-descriptions-item>
          <el-descriptions-item label="运行中">{{ knowledge.running ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="已索引来源">{{ knowledge.indexedSources || 0 }}</el-descriptions-item>
          <el-descriptions-item label="失败来源">{{ knowledge.failedSources || 0 }}</el-descriptions-item>
          <el-descriptions-item label="说明" :span="4">{{ knowledge.message || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-tabs v-model="activeTab" class="admin-tabs">
        <el-tab-pane label="知识源状态" name="knowledge">
          <el-card>
            <div class="toolbar">
              <el-select v-model="sourceFilters.sourceType" clearable placeholder="来源类型" style="width: 170px">
                <el-option label="课程" value="COURSE"/>
                <el-option label="课程分集" value="COURSE_EPISODES"/>
                <el-option label="文章" value="ARTICLE"/>
                <el-option label="公告" value="NOTICE"/>
              </el-select>
              <el-select v-model="sourceFilters.status" clearable placeholder="索引状态" style="width: 150px">
                <el-option label="已索引" value="ACTIVE"/>
                <el-option label="已删除" value="DELETED"/>
              </el-select>
              <el-button type="primary" @click="loadSources">刷新</el-button>
            </div>
            <el-table v-loading="sourceLoading" :data="sources">
              <el-table-column prop="sourceType" label="类型" width="130"/>
              <el-table-column prop="sourceId" label="业务 ID" width="120"/>
              <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip/>
              <el-table-column label="状态" width="110">
                <template #default="{row}">
                  <el-tag :type="statusType(row.indexStatus)">{{ row.indexStatus }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="chunkCount" label="分块" width="80"/>
              <el-table-column label="最近事件" width="110">
                <template #default="{row}">
                  <el-tag v-if="row.lastEventStatus" :type="statusType(row.lastEventStatus)">{{ row.lastEventStatus }}</el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column prop="lastError" label="失败原因" min-width="180" show-overflow-tooltip/>
              <el-table-column label="更新时间" width="180">
                <template #default="{row}">{{ formatTime(row.updatedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{row}">
                  <el-button link type="primary" @click="retrySource(row)">重试</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-tab-pane>

        <el-tab-pane label="Tool 审计" name="tools">
          <el-card>
            <div class="toolbar">
              <el-input v-model="toolFilters.userId" clearable placeholder="用户 ID" style="width: 130px"/>
              <el-input v-model="toolFilters.toolName" clearable placeholder="Tool 名称" style="width: 180px"/>
              <el-select v-model="toolFilters.toolSource" clearable placeholder="来源" style="width: 120px">
                <el-option label="LOCAL" value="LOCAL"/>
                <el-option label="MCP" value="MCP"/>
              </el-select>
              <el-select v-model="toolFilters.accessType" clearable placeholder="读写类型" style="width: 130px">
                <el-option label="READ" value="READ"/>
                <el-option label="WRITE" value="WRITE"/>
              </el-select>
              <el-select v-model="toolFilters.status" clearable placeholder="状态" style="width: 150px">
                <el-option label="STARTED" value="STARTED"/>
                <el-option label="SUCCEEDED" value="SUCCEEDED"/>
                <el-option label="FAILED" value="FAILED"/>
                <el-option label="TIMED_OUT" value="TIMED_OUT"/>
              </el-select>
              <el-button type="primary" @click="loadToolCalls">刷新</el-button>
            </div>
            <el-table v-loading="toolLoading" :data="toolCalls">
              <el-table-column prop="toolName" label="Tool" min-width="170"/>
              <el-table-column label="来源" width="100">
                <template #default="{row}">
                  <el-tag :type="row.toolSource === 'MCP' ? 'success' : 'info'">{{ row.toolSource || 'LOCAL' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="外部工具" min-width="170" show-overflow-tooltip>
                <template #default="{row}">
                  {{ row.toolSource === 'MCP' ? (row.externalToolName || '-') : '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="mcpServerName" label="MCP Server" min-width="140" show-overflow-tooltip/>
              <el-table-column prop="userId" label="用户" width="100"/>
              <el-table-column label="类型" width="90">
                <template #default="{row}">
                  <el-tag :type="row.accessType === 'WRITE' ? 'warning' : 'info'">{{ row.accessType }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="{row}">
                  <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="latencyMs" label="耗时(ms)" width="100"/>
              <el-table-column prop="errorCode" label="错误码" width="130"/>
              <el-table-column prop="requestHash" label="输入 Hash" min-width="180" show-overflow-tooltip/>
              <el-table-column prop="responseHash" label="输出 Hash" min-width="180" show-overflow-tooltip/>
              <el-table-column label="输入摘要" min-width="220" show-overflow-tooltip>
                <template #default="{row}">{{ jsonPreview(row.requestJson) }}</template>
              </el-table-column>
              <el-table-column label="输出摘要" min-width="220" show-overflow-tooltip>
                <template #default="{row}">{{ jsonPreview(row.responseJson) }}</template>
              </el-table-column>
              <el-table-column label="创建时间" width="180">
                <template #default="{row}">{{ formatTime(row.createdAt) }}</template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-tab-pane>

        <el-tab-pane label="RAG 评测" name="evaluation">
          <el-card>
            <template #header>
              <div class="evaluation-header">
                <strong>可执行评测</strong>
                <div class="evaluation-actions">
                  <el-segmented v-model="evaluationMode" :options="[
                    {label: 'Deterministic', value: 'deterministic'},
                    {label: 'External', value: 'external'}
                  ]"/>
                  <el-button type="primary" :loading="evaluationRunning" @click="runEvaluation">运行</el-button>
                </div>
              </div>
            </template>
            <el-alert v-if="evaluationMode === 'external'" title="External 模式将调用真实模型和业务依赖"
                      type="warning" show-icon :closable="false"/>
            <el-descriptions v-if="evaluationReport" :column="4" border class="report-summary">
              <el-descriptions-item label="报告 ID" :span="2">{{ evaluationReport.id }}</el-descriptions-item>
              <el-descriptions-item label="数据集">{{ evaluationReport.datasetVersion }}</el-descriptions-item>
              <el-descriptions-item label="模型">{{ evaluationReport.modelVersion }}</el-descriptions-item>
              <el-descriptions-item label="通过">{{ evaluationReport.passedCount }}/{{ evaluationReport.totalCount }}</el-descriptions-item>
              <el-descriptions-item label="失败">{{ evaluationReport.failedCount }}</el-descriptions-item>
              <el-descriptions-item label="P95">{{ evaluationReport.p95LatencyMs }} ms</el-descriptions-item>
              <el-descriptions-item label="门禁">
                <el-tag :type="evaluationReport.gatePassed ? 'success' : evaluationReport.thresholdGateActive ? 'danger' : 'info'">
                  {{ evaluationReport.thresholdGateActive ? (evaluationReport.gatePassed ? '通过' : '失败') : '仅基线' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="指标" :span="4"><code>{{ JSON.stringify(evaluationReport.metrics) }}</code></el-descriptions-item>
            </el-descriptions>
            <el-table v-if="evaluationReport?.failures?.length" :data="evaluationReport.failures" class="failure-table">
              <el-table-column prop="caseId" label="失败用例" width="180"/>
              <el-table-column prop="caseType" label="类型" width="110"/>
              <el-table-column prop="failureReason" label="失败原因" min-width="280" show-overflow-tooltip/>
              <el-table-column prop="latencyMs" label="耗时(ms)" width="100"/>
            </el-table>
            <el-divider/>
            <strong>最近评测结果</strong>
            <el-table :data="results">
              <el-table-column prop="caseType" label="类型" width="110"/>
              <el-table-column prop="question" label="问题" min-width="260"/>
              <el-table-column prop="modelName" label="模型" width="150"/>
              <el-table-column label="结果" width="90">
                <template #default="{row}">
                  <el-tag :type="row.passed ? 'success' : 'danger'">{{ row.passed ? '通过' : '失败' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="指标" min-width="260">
                <template #default="{row}"><code>{{ JSON.stringify(row.metrics) }}</code></template>
              </el-table-column>
              <el-table-column label="时间" width="190">
                <template #default="{row}">{{ formatTime(row.createdAt) }}</template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<style scoped>
.status-card { margin:16px 0; }
.admin-tabs { margin-top:16px; }
.toolbar {
  display:flex;
  flex-wrap:wrap;
  gap:10px;
  margin-bottom:14px;
}
.evaluation-header,.evaluation-actions {
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:12px;
}
.report-summary,.failure-table { margin-top:16px; }
code {
  white-space:pre-wrap;
  word-break:break-word;
}
</style>
