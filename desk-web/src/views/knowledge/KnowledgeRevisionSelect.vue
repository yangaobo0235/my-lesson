<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElAlert, ElButton, ElOption, ElSelect } from 'element-plus'
import {
  getDocument,
  listDocuments,
  type KnowledgeDocument,
} from '../../api/knowledge/knowledgeApi'
import { useOperation } from '../../composables/useOperation'
import { useAuthStore } from '../../stores/authStore'

/** 索引和审计均按有权资料修订选择。支持分页和从修订详情进入。 @author yangaobo */
defineProps<{ disabled?: boolean }>()
const emit = defineEmits<{ select: [document: KnowledgeDocument | null] }>()
const route = useRoute()
const auth = useAuthStore()
const { busy, error, run, signal } = useOperation()
const documents = ref<KnowledgeDocument[]>([])
const result = ref<Awaited<ReturnType<typeof listDocuments>> | null>(null)
const selectedId = ref<string>()

function select(id: string) {
  const selected = documents.value.find((row) => row.id === id) ?? null
  selectedId.value = selected?.id
  emit('select', selected)
}

function load(page = 1) {
  void run(async () => {
    const csrf = await auth.token()
    const response = await listDocuments(null, page, csrf, signal)
    result.value = response
    const existing = page === 1 ? [] : documents.value
    documents.value = [
      ...existing,
      ...response.records.filter(
        (row) => !existing.some((item) => item.id === row.id),
      ),
    ]
    const initial = route.query.document
    if (
      page === 1 &&
      typeof initial === 'string' &&
      /^[0-9a-f]{32}$/.test(initial)
    ) {
      if (!documents.value.some((item) => item.id === initial))
        documents.value.unshift(await getDocument(initial, csrf, signal))
      select(initial)
    }
  })
}

onMounted(() => load())
</script>

<template>
  <div class="toolbar query-bar">
    <ElSelect
      :model-value="selectedId"
      clearable
      :disabled="busy || disabled"
      placeholder="选择资料修订"
      aria-label="资料修订"
      @update:model-value="select"
    >
      <ElOption
        v-for="item in documents"
        :key="item.id"
        :value="item.id"
        :label="`${item.title} · 第 ${item.revisionNumber} 版`"
      />
    </ElSelect>
    <ElButton
      v-if="
        result &&
        BigInt(result.pageNum * result.pageSize) < BigInt(result.total)
      "
      :disabled="busy || disabled"
      @click="load(result.pageNum + 1)"
      >加载更多修订</ElButton
    >
    <span v-if="busy" class="subtle" role="status">正在读取可选修订</span>
    <span v-else-if="result && !documents.length" class="subtle"
      >暂无资料修订。</span
    >
  </div>
  <ElAlert v-if="error" :title="error" type="error" :closable="false">
    <ElButton text @click="load()">重试读取修订</ElButton>
  </ElAlert>
</template>
