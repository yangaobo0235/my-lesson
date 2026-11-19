<script setup lang="ts">
import { ElButton, ElTag } from 'element-plus'
import type { IndexJob } from '../../api/knowledge/knowledgeApi'

/** 区分知识侧提交、AI 受理、执行状态和当前索引可读性。 @author yangaobo */
defineProps<{ job: IndexJob; busy: boolean; canReplay?: boolean }>()
const emit = defineEmits<{ replay: [] }>()

const submitLabel: Record<IndexJob['submitState'], string> = {
  PREPARING: '准备中',
  COMMITTED: '本地已提交',
  ABORTED: '本次提交已终止',
}
const cancelLabel: Record<IndexJob['cancelState'], string> = {
  ...submitLabel,
  NONE: '未申请取消',
}
const executionLabel: Record<NonNullable<IndexJob['aiStatus']>, string> = {
  QUEUED: '排队中',
  RUNNING: '执行中',
  SUCCEEDED: '执行成功',
  FAILED: '执行失败',
  CANCELLED: '已取消',
}
</script>

<template>
  <section class="panel" aria-labelledby="index-progress">
    <h2 id="index-progress">索引任务进展</h2>
    <dl aria-live="polite">
      <dt>提交指令</dt>
      <dd>{{ submitLabel[job.submitState] }}</dd>
      <dt>取消指令</dt>
      <dd>{{ cancelLabel[job.cancelState] }}</dd>
      <dt>AI 受理</dt>
      <dd>
        {{
          job.aiAccepted === null
            ? '尚未查询'
            : job.aiAccepted
              ? '已持久受理'
              : '本次未查到，指令可能在途'
        }}
      </dd>
      <dt>执行</dt>
      <dd>
        <ElTag>{{ job.aiStatus ? executionLabel[job.aiStatus] : '未知' }}</ElTag
        ><span v-if="job.aiStage"> · {{ job.aiStage }}</span>
      </dd>
      <dt>解析</dt>
      <dd>{{ job.parserState ?? '未知' }}</dd>
      <dt>双索引复核</dt>
      <dd>
        {{
          job.replicasVerified === null
            ? '未验证'
            : job.replicasVerified
              ? '通过'
              : '尚不满足'
        }}
      </dd>
      <dt v-if="job.lastErrorCode">错误标识</dt>
      <dd v-if="job.lastErrorCode">{{ job.lastErrorCode }}</dd>
    </dl>
    <ElButton
      v-if="canReplay"
      :disabled="
        busy ||
        job.submitState !== 'COMMITTED' ||
        job.cancelState === 'PREPARING'
      "
      @click="emit('replay')"
    >
      审计并重放已提交原指令
    </ElButton>
  </section>
</template>
