<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElAlert, ElButton, ElSkeleton } from 'element-plus'
import { listAudit } from '../../api/administration/identityApi'
import PageControls from '../../components/PageControls.vue'
import { useOperation } from '../../composables/useOperation'
import { useAuthStore } from '../../stores/authStore'

const auth = useAuthStore()
const result = ref<Awaited<ReturnType<typeof listAudit>> | null>(null)
const { busy, error, run, signal } = useOperation()

function load(page = 1) {
  void run(async () => {
    result.value = await listAudit(page, await auth.token(), signal)
  })
}

onMounted(() => load())
</script>

<template>
  <main>
    <h1>身份变更审计</h1>
    <p>查看组织、账号、角色与客户授权的变更记录。</p>
    <ElAlert v-if="error" :title="error" type="error" :closable="false">
      <ElButton text @click="load(result?.pageNum)">刷新记录</ElButton>
    </ElAlert>
    <ElSkeleton v-if="busy && !result" :rows="4" animated />
    <template v-if="result">
      <p v-if="!result.records.length" class="card">暂无身份变更记录。</p>
      <article v-for="event in result.records" :key="event.id" class="card">
        <strong>{{ event.eventType }}</strong>
        <p class="subtle">
          {{ event.createdAt }} · 操作者 {{ event.actorUserId || '系统' }}
        </p>
        <details>
          <summary>变更内容</summary>
          <pre>{{ event.details }}</pre>
        </details>
      </article>
      <PageControls :page="result" :busy="busy" @change="load" />
    </template>
  </main>
</template>
