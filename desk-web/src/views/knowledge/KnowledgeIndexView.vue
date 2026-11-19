<script setup lang="ts">
import { ref } from 'vue'
import { ElAlert, ElButton, ElSkeleton } from 'element-plus'
import {
  getDocument,
  getIndex,
  type IndexJob,
  type KnowledgeDocument,
} from '../../api/knowledge/knowledgeApi'
import { useOperation } from '../../composables/useOperation'
import { useAuthStore } from '../../stores/authStore'
import IndexProgressPanel from './IndexProgressPanel.vue'
import KnowledgeRevisionSelect from './KnowledgeRevisionSelect.vue'
import { useKnowledgeRevision } from '../../router/knowledgeRevision'

const auth = useAuthStore()
const { busy, error, run, signal } = useOperation()
const selected = ref<KnowledgeDocument | null>(null)
useKnowledgeRevision(() => selected.value?.id)
const job = ref<IndexJob | null>(null)
const loaded = ref(false)

function refresh() {
  const id = selected.value?.id
  if (!id) return
  void run(async () => {
    const csrf = await auth.token()
    const revision = await getDocument(id, csrf, signal)
    const progress = revision.currentJobId
      ? await getIndex({ id: revision.currentJobId }, csrf, signal)
      : null
    if (progress && progress.documentId !== id)
      throw new Error('索引任务与所选修订不符，请刷新后重试')
    selected.value = revision
    job.value = progress
    loaded.value = true
  })
}

function select(document: KnowledgeDocument | null) {
  selected.value = document
  job.value = null
  loaded.value = false
  if (document) refresh()
}
</script>

<template>
  <main>
    <div class="page-heading">
      <div>
        <h1>索引任务</h1>
        <p>按资料修订核对提交、受理、解析和双索引复核结果。</p>
      </div>
    </div>
    <KnowledgeRevisionSelect :disabled="busy" @select="select" />
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <ElSkeleton v-if="busy && !loaded" :rows="4" animated />
    <div v-if="selected" class="toolbar">
      <strong
        >{{ selected.title }} · 第 {{ selected.revisionNumber }} 版</strong
      >
      <ElButton :disabled="busy" @click="refresh">刷新索引状态</ElButton>
    </div>
    <IndexProgressPanel v-if="job" :job="job" :busy="busy" />
    <p v-else-if="loaded" class="card">该修订暂无索引任务。</p>
  </main>
</template>
