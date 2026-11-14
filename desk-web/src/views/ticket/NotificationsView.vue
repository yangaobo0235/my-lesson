<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElAlert, ElButton } from 'element-plus'
import { listNotifications, readNotification } from '../../api/ticket/ticketApi'
import type { Notification } from '../../api/ticket/types'
import { useOperation } from '../../composables/useOperation'
import { useAuthStore } from '../../stores/authStore'
const rows = ref<Notification[]>([]),
  pageNum = ref(1),
  total = ref('0')
const { busy, error, run, signal } = useOperation(),
  auth = useAuthStore()
async function load() {
  const page = await listNotifications(pageNum.value, signal)
  rows.value = page.records
  total.value = page.total
}
function next(delta: number) {
  void run(async () => {
    pageNum.value += delta
    await load()
  })
}
function read(id: string) {
  void run(async () => {
    await readNotification(id, await auth.token())
    await load()
  })
}
onMounted(() => void run(load))
</script>
<template>
  <main>
    <h1>站内通知</h1>
    <p>只显示当前仍有权访问的工单通知。</p>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <article v-for="row in rows" :key="row.id" class="card page-heading">
      <div>
        <RouterLink :to="`/tickets/${row.ticketId}`">{{ row.title }}</RouterLink
        ><small class="subtle">{{
          new Date(row.createdAt).toLocaleString('zh-CN', {
            timeZone: 'Asia/Shanghai',
          })
        }}</small>
      </div>
      <ElButton :disabled="busy || !!row.readAt" @click="read(row.id)">{{
        row.readAt ? '已读' : '标记已读'
      }}</ElButton>
    </article>
    <p v-if="!rows.length && !busy">暂无通知</p>
    <div class="toolbar">
      <ElButton :disabled="busy || pageNum === 1" @click="next(-1)"
        >上一页</ElButton
      ><span>第 {{ pageNum }} 页 · 共 {{ total }} 条</span
      ><ElButton
        :disabled="busy || BigInt(pageNum * 20) >= BigInt(total)"
        @click="next(1)"
        >下一页</ElButton
      >
    </div>
  </main>
</template>
