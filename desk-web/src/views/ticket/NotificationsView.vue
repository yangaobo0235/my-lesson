<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElAlert, ElButton, ElSkeleton, ElTag } from 'element-plus'
import { listNotifications, readNotification } from '../../api/ticket/ticketApi'
import PageControls from '../../components/PageControls.vue'
import { useOperation } from '../../composables/useOperation'
import { useAuthStore } from '../../stores/authStore'
const result = ref<Awaited<ReturnType<typeof listNotifications>> | null>(null)
const { busy, error, run, signal } = useOperation(),
  auth = useAuthStore()
async function readPage(pageNum: number) {
  result.value = await listNotifications(pageNum, signal)
}
function load(pageNum = 1) {
  void run(() => readPage(pageNum))
}
function read(id: string) {
  void run(async () => {
    await readNotification(id, await auth.token())
    await readPage(result.value?.pageNum ?? 1)
  })
}
onMounted(() => load())
</script>
<template>
  <main>
    <h1>站内通知</h1>
    <p>只显示当前仍有权访问的工单通知。</p>
    <ElAlert v-if="error" :title="error" type="error" :closable="false">
      <ElButton text @click="load(result?.pageNum)">重新加载</ElButton>
    </ElAlert>
    <ElSkeleton v-if="busy && !result" :rows="3" animated />
    <article
      v-for="row in result?.records"
      :key="row.id"
      class="card notice-row"
    >
      <div>
        <RouterLink
          :to="{ name: 'ticket-detail', params: { id: row.ticketId } }"
          >{{ row.title }}</RouterLink
        ><small class="subtle">{{
          new Date(row.createdAt).toLocaleString('zh-CN', {
            timeZone: 'Asia/Shanghai',
          })
        }}</small>
      </div>
      <ElTag v-if="row.readAt" type="info">已读</ElTag>
      <ElButton
        v-else
        plain
        type="primary"
        :disabled="busy"
        @click="read(row.id)"
        >标记已读</ElButton
      >
    </article>
    <p v-if="result && !result.records.length" class="card">暂无通知。</p>
    <PageControls v-if="result" :page="result" :busy="busy" @change="load" />
  </main>
</template>

<style scoped>
.notice-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.notice-row a {
  font-weight: 650;
}
</style>
