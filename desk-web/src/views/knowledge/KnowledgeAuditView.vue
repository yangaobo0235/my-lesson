<script setup lang="ts">
import { ref } from 'vue'
import { ElAlert, ElButton, ElSkeleton } from 'element-plus'
import {
  listDocumentEvents,
  type DocumentEventType,
} from '../../api/knowledge/documentAuditApi'
import type { KnowledgeDocument } from '../../api/knowledge/knowledgeApi'
import PageControls from '../../components/PageControls.vue'
import { useOperation } from '../../composables/useOperation'
import { useAuthStore } from '../../stores/authStore'
import KnowledgeRevisionSelect from './KnowledgeRevisionSelect.vue'
import { useKnowledgeRevision } from '../../router/knowledgeRevision'

const names: Record<DocumentEventType, string> = {
  UPLOADED: '上传原件',
  METADATA_CHANGED: '更新适用规则与资料信息',
  INDEX_REQUESTED: '申请索引',
  INDEX_CANCEL_REQUESTED: '申请取消索引',
  INDEX_REQUEUED: '重放索引指令',
  PUBLISHED: '发布修订',
  DISABLED: '停用修订',
  RESTORED: '恢复修订',
  RETIRED: '被新修订替换',
  WITHDRAWN: '风险撤回',
  ACCESS_REVOKED: '撤销访问',
  ACCESS_RESTORED: '恢复访问',
  DELETED: '删除未发布草稿',
}
const auth = useAuthStore()
const { busy, error, run, signal } = useOperation()
const selectedId = ref<string>()
useKnowledgeRevision(() => selectedId.value)
const result = ref<Awaited<ReturnType<typeof listDocumentEvents>> | null>(null)

async function loadEvents(id: string, page: number, csrf: string) {
  result.value = await listDocumentEvents(id, page, csrf, signal)
}

function select(document: KnowledgeDocument | null) {
  selectedId.value = document?.id
  result.value = null
  if (document) turn(1)
}

function turn(page: number) {
  const id = selectedId.value
  if (id) void run(async () => loadEvents(id, page, await auth.token()))
}
</script>

<template>
  <main>
    <div class="page-heading">
      <div>
        <h1>知识发布审计</h1>
        <p>按资料修订核对发布、索引和访问权限变更。</p>
      </div>
    </div>
    <KnowledgeRevisionSelect :disabled="busy" @select="select" />
    <ElAlert v-if="error" :title="error" type="error" :closable="false">
      <ElButton text @click="turn(result?.pageNum ?? 1)">重试</ElButton>
    </ElAlert>
    <ElSkeleton v-if="busy && !result" :rows="3" animated />
    <p v-else-if="!error && !selectedId" class="card">
      请选择一个修订查看审计记录。
    </p>
    <template v-if="result">
      <p v-if="!result.records.length" class="card">该修订暂无审计记录。</p>
      <article v-for="value in result.records" :key="value.id" class="card">
        <strong>{{ names[value.eventType] }}</strong>
        <p class="subtle">{{ value.createdAt }} · 操作者 {{ value.actorId }}</p>
        <details>
          <summary>变更详情</summary>
          <pre>{{ JSON.stringify(value.details, null, 2) }}</pre>
        </details>
      </article>
      <PageControls :page="result" :busy="busy" @change="turn" />
    </template>
  </main>
</template>
