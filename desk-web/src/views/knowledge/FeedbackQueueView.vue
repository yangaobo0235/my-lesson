<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElAlert, ElButton, ElInput, ElOption, ElSelect } from 'element-plus'
import {
  getSharedFeedback,
  handleSharedFeedback,
  listSharedFeedback,
  type FeedbackQueueItem,
  type SharedFeedbackCase,
} from '../../api/diagnosis/diagnosisApi'
import { useAuthStore } from '../../stores/authStore'
import { useOperation } from '../../composables/useOperation'

/** 知识管理员反馈处置；共享内容再次授权后才展开。 @author yangaobo */
const auth = useAuthStore()
const { run, busy, error, signal } = useOperation()
const rows = ref<FeedbackQueueItem[]>([])
const selected = ref<SharedFeedbackCase | null>(null)
const status = ref<'PENDING' | 'RESOLVED' | 'DISMISSED' | ''>('PENDING')
const pageNum = ref(1)
const total = ref('0')
const note = ref('')

function load() {
  void run(async () => {
    const result = await listSharedFeedback(
      pageNum.value,
      status.value || undefined,
      signal,
    )
    rows.value = result.records
    total.value = result.total
    selected.value = null
    note.value = ''
  })
}

function open(row: FeedbackQueueItem) {
  void run(async () => {
    selected.value = await getSharedFeedback(row.id, signal)
    note.value = ''
  })
}

function handle(action: 'RESOLVE' | 'DISMISS') {
  void run(async () => {
    if (!selected.value) return
    if (!note.value.trim()) throw new Error('请填写处置说明')
    const result = await handleSharedFeedback(
      selected.value.feedback.id,
      {
        rowVersion: selected.value.feedback.rowVersion,
        action,
        handlingNote: note.value,
      },
      await auth.token(),
    )
    rows.value = rows.value.map((row) => (row.id === result.id ? result : row))
    selected.value = null
    note.value = ''
  })
}

function previous() {
  pageNum.value--
  load()
}

function next() {
  pageNum.value++
  load()
}

function changeStatus() {
  pageNum.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <main class="page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">知识质量处置</p>
        <h1>共享反馈</h1>
      </div>
      <ElSelect v-model="status" :disabled="busy" @change="changeStatus">
        <ElOption value="PENDING" label="待处理" />
        <ElOption value="RESOLVED" label="已解决" />
        <ElOption value="DISMISSED" label="已驳回" />
        <ElOption value="" label="全部" />
      </ElSelect>
    </div>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <div class="card table-scroll">
      <table>
        <thead>
          <tr>
            <th>反馈分类</th>
            <th>状态</th>
            <th>提交时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td>{{ row.issueCategory }}</td>
            <td>
              {{
                row.status === 'PENDING'
                  ? '待处理'
                  : row.status === 'RESOLVED'
                    ? '已解决'
                    : '已驳回'
              }}
            </td>
            <td>
              {{
                new Date(row.createdAt).toLocaleString('zh-CN', {
                  timeZone: 'Asia/Shanghai',
                })
              }}
            </td>
            <td>
              <ElButton link :disabled="busy" @click="open(row)"
                >核验并展开</ElButton
              >
            </td>
          </tr>
          <tr v-if="!rows.length && !busy">
            <td colspan="4">暂无共享反馈</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="toolbar">
      <span>共 {{ total }} 条 · 第 {{ pageNum }} 页</span>
      <ElButton :disabled="busy || pageNum === 1" @click="previous"
        >上一页</ElButton
      >
      <ElButton
        :disabled="busy || BigInt(pageNum * 10) >= BigInt(total)"
        @click="next"
        >下一页</ElButton
      >
    </div>
    <section v-if="selected" class="panel">
      <h2>已核验的共享内容</h2>
      <p>问题：{{ selected.question }}</p>
      <p>回答：{{ selected.answer }}</p>
      <p v-if="selected.comment">用户补充：{{ selected.comment }}</p>
      <h3>实际来源</h3>
      <ul>
        <li
          v-for="source in selected.sources"
          :key="`${source.title}-${source.excerpt}`"
        >
          {{ source.title }} · {{ source.excerpt }}
        </li>
      </ul>
      <template v-if="selected.feedback.status === 'PENDING'">
        <ElInput
          v-model="note"
          type="textarea"
          :rows="4"
          maxlength="2000"
          placeholder="填写处置说明"
        />
        <div class="toolbar">
          <ElButton :disabled="busy" @click="handle('RESOLVE')"
            >记录已处理</ElButton
          >
          <ElButton :disabled="busy" @click="handle('DISMISS')"
            >驳回反馈</ElButton
          >
        </div>
      </template>
    </section>
  </main>
</template>
