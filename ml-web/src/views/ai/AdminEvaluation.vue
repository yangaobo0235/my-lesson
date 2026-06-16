<script setup>
import {onMounted, ref} from 'vue';
import {ElMessage} from 'element-plus';
import AiPageHeader from './AiPageHeader.vue';
import {aiApi} from '../../api/ai.js';
const knowledge = ref({});
const summary = ref({});
const results = ref([]);
const loading = ref(false);
async function load() {
  loading.value = true;
  try {
    [knowledge.value, summary.value, results.value] = await Promise.all([
      aiApi.knowledgeStatus(), aiApi.evaluationSummary(), aiApi.evaluationResults()
    ]);
  } catch (error) {
    ElMessage.error(error.response?.status === 403 ? '当前账号没有管理员权限' : '加载评测数据失败');
  } finally { loading.value = false; }
}
onMounted(load);
</script>

<template>
  <div>
    <AiPageHeader title="评测与知识库" description="只读查看离线评测结果和知识索引状态"/>
    <div v-loading="loading">
      <el-row :gutter="16">
        <el-col :span="6"><el-card><el-statistic title="启用评测用例" :value="summary.caseCount || 0"/></el-card></el-col>
        <el-col :span="6"><el-card><el-statistic title="已执行结果" :value="summary.resultCount || 0"/></el-card></el-col>
        <el-col :span="6"><el-card><el-statistic title="通过率" :value="(summary.passRate || 0) * 100" suffix="%" :precision="1"/></el-card></el-col>
        <el-col :span="6"><el-card><el-statistic title="已索引分块" :value="knowledge.indexedChunks || 0"/></el-card></el-col>
      </el-row>
      <el-card class="status-card"><el-descriptions title="知识库状态" :column="4" border>
        <el-descriptions-item label="状态">{{ knowledge.state || '-' }}</el-descriptions-item>
        <el-descriptions-item label="运行中">{{ knowledge.running ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="已索引来源">{{ knowledge.indexedSources || 0 }}</el-descriptions-item>
        <el-descriptions-item label="失败来源">{{ knowledge.failedSources || 0 }}</el-descriptions-item>
        <el-descriptions-item label="说明" :span="4">{{ knowledge.message || '-' }}</el-descriptions-item>
      </el-descriptions></el-card>
      <el-card><template #header><strong>最近评测结果</strong></template>
        <el-table :data="results">
          <el-table-column prop="caseType" label="类型" width="110"/>
          <el-table-column prop="question" label="问题" min-width="260"/>
          <el-table-column prop="modelName" label="模型" width="150"/>
          <el-table-column label="结果" width="90"><template #default="{row}"><el-tag :type="row.passed ? 'success' : 'danger'">{{ row.passed ? '通过' : '失败' }}</el-tag></template></el-table-column>
          <el-table-column label="指标" min-width="260"><template #default="{row}"><code>{{ JSON.stringify(row.metrics) }}</code></template></el-table-column>
          <el-table-column label="时间" width="190"><template #default="{row}">{{ new Date(row.createdAt).toLocaleString() }}</template></el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>
<style scoped>.status-card { margin:16px 0; }</style>
