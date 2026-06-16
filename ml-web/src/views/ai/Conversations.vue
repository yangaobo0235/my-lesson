<script setup>
import {onMounted, ref} from 'vue';
import {useRouter} from 'vue-router';
import {ElMessage, ElMessageBox} from 'element-plus';
import AiPageHeader from './AiPageHeader.vue';
import {aiApi} from '../../api/ai.js';

const router = useRouter();
const rows = ref([]);
const loading = ref(false);
async function load() {
  loading.value = true;
  try { rows.value = await aiApi.conversations(); } finally { loading.value = false; }
}
const open = row => router.push({path: '/ai/chat', query: {conversationId: row.id}});
async function remove(row) {
  await ElMessageBox.confirm(`确认删除会话“${row.title || '新对话'}”？`, '删除会话');
  await aiApi.deleteConversation(row.id);
  ElMessage.success('会话已删除');
  await load();
}
onMounted(load);
</script>

<template>
  <div>
    <AiPageHeader title="历史会话" description="仅显示当前登录用户的未删除会话"/>
    <el-card>
      <el-table :data="rows" v-loading="loading">
        <el-table-column prop="title" label="标题" min-width="220"/>
        <el-table-column prop="status" label="状态" width="120"/>
        <el-table-column label="更新时间" min-width="190"><template #default="{row}">{{ new Date(row.updatedAt).toLocaleString() }}</template></el-table-column>
        <el-table-column label="操作" width="180"><template #default="{row}">
          <el-button link type="primary" @click="open(row)">继续对话</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template></el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
