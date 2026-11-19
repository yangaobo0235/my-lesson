<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElAlert, ElButton, ElSkeleton } from 'element-plus'
import { listTaskEvents } from '../../api/diagnosis/taskApi'
import { useOperation } from '../../composables/useOperation'

/** 展示所选私有任务的版本历史，切换任务时由父级按任务 ID 重建。 @author yangaobo */
const props = defineProps<{ taskId: string }>()
const entries = ref<Awaited<ReturnType<typeof listTaskEvents>>['records']>([])
const cursor = ref('0')
const more = ref(false)
const loaded = ref(false)
const { busy, error, run, signal } = useOperation()
const actorLabels = { USER: '人工操作', WORKER: '诊断执行', SYSTEM: '系统记录' }

function load(reset: boolean) {
  void run(async () => {
    const after = reset ? '0' : cursor.value
    const response = await listTaskEvents(props.taskId, after, signal).catch(
      (failure: unknown) => {
        entries.value = []
        cursor.value = '0'
        more.value = false
        loaded.value = false
        throw failure
      },
    )
    if (response.hasMore && BigInt(response.nextCursor) <= BigInt(after))
      throw new Error('事件暂时无法继续分页，请刷新后重试')
    entries.value = reset
      ? response.records
      : [...entries.value, ...response.records]
    cursor.value = response.nextCursor
    more.value = response.hasMore
    loaded.value = true
  })
}

onMounted(() => load(true))
</script>

<template>
  <div class="card">
    <div class="page-heading">
      <strong>诊断任务历史</strong>
      <ElButton text :disabled="busy" @click="load(true)">刷新记录</ElButton>
    </div>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <ElSkeleton v-if="busy && !loaded" :rows="3" animated />
    <p v-if="loaded && !entries.length">暂无任务事件。</p>
    <ol v-if="entries.length" class="timeline">
      <li v-for="value in entries" :key="value.id">
        <strong
          >{{ actorLabels[value.actorType] }} · 任务版本
          {{ value.stateVersion }}</strong
        >
        <time :datetime="value.createdAt">{{
          new Date(value.createdAt).toLocaleString('zh-CN', {
            timeZone: 'Asia/Shanghai',
          })
        }}</time>
        <p v-if="value.sourcesHidden">来源已不可访问，相关内容已隐藏。</p>
        <p v-else class="preserve">{{ value.reason ?? value.eventType }}</p>
      </li>
    </ol>
    <ElButton v-if="more" :disabled="busy" @click="load(false)"
      >加载更多事件</ElButton
    >
  </div>
</template>
