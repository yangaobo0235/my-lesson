<script setup>
import {onMounted, ref} from 'vue';
import {ElMessage} from 'element-plus';
import AiPageHeader from './AiPageHeader.vue';
import {aiApi} from '../../api/ai.js';
const rows = ref([]);
const loading = ref(false);
async function load() {
  loading.value = true;
  try { rows.value = await aiApi.approvals(); } finally { loading.value = false; }
}
async function decide(row, approved) {
  await (approved ? aiApi.approve(row.id) : aiApi.reject(row.id));
  ElMessage.success(approved ? '操作已批准' : '操作已拒绝');
  await load();
}
onMounted(load);
</script>

<template>
  <div>
    <AiPageHeader title="待确认操作" description="写操作会在这里等待用户明确批准"/>
    <el-card><el-table :data="rows" v-loading="loading">
      <el-table-column prop="actionType" label="操作" width="220"/>
      <el-table-column prop="reason" label="原因" min-width="260"/>
      <el-table-column prop="status" label="状态" width="120"/>
      <el-table-column label="过期时间" min-width="190"><template #default="{row}">{{ new Date(row.expiresAt).toLocaleString() }}</template></el-table-column>
      <el-table-column label="参数快照" min-width="260"><template #default="{row}"><code>{{ JSON.stringify(row.actionPayload) }}</code></template></el-table-column>
      <el-table-column label="操作" width="160" fixed="right"><template #default="{row}">
        <template v-if="row.status === 'PENDING'">
          <el-button link type="success" @click="decide(row, true)">批准</el-button>
          <el-button link type="danger" @click="decide(row, false)">拒绝</el-button>
        </template><span v-else>-</span>
      </template></el-table-column>
    </el-table></el-card>
  </div>
</template>
