<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElAlert, ElButton, ElInput, ElOption, ElSelect } from 'element-plus'
import {
  answerCheck,
  listChecks,
  type CheckAnswerInput,
  type DiagnosticCheck,
  type DiagnosisTask,
  type TaskReceipt,
} from '../../api/diagnosis/taskApi'
import { ApiError, newRequestId } from '../../api/http'
import { useOperation } from '../../composables/useOperation'
import { useAuthStore } from '../../stores/authStore'

/** 检查反馈独立持久化；保存成功后由父页面提供显式续行入口。 @author yangaobo */
const props = defineProps<{ task: DiagnosisTask; active: boolean }>()
const emit = defineEmits<{
  saved: [receipt: TaskReceipt]
  resume: [messageId: string]
  pending: [value: boolean]
}>()
const auth = useAuthStore(),
  { run, busy, error, signal } = useOperation()
const checks = ref<DiagnosticCheck[]>([]),
  pageNum = ref(1),
  total = ref('0')
const selected = ref<DiagnosticCheck | null>(null),
  value = ref('')
const execution = ref<'NOT_PERFORMED' | 'PERFORMED'>('NOT_PERFORMED')
const outcome = ref<'UNKNOWN' | 'UNCHANGED' | 'IMPROVED' | 'RESTORED_REPORTED'>(
  'UNKNOWN',
)
const pending = ref<{
  taskId: string
  checkId: string
  body: CheckAnswerInput
} | null>(null)
const statusNames: Record<DiagnosticCheck['status'], string> = {
  OPEN: '待答复',
  ANSWERED: '已收到人工反馈',
  OBSERVED_DONE: '工具已观测',
  FAILED: '检查失败',
  SKIPPED: '已跳过',
  WITHDRAWN: '已撤回',
  EXPIRED: '已过期',
  NEEDS_RECHECK: '环境变化，需复查',
}
watch(
  () => busy.value || !!pending.value,
  (value) => emit('pending', value),
  { flush: 'sync' },
)
let epoch = 0
async function load() {
  const id = props.task.id,
    version = epoch
  const result = await listChecks(id, pageNum.value, signal)
  if (signal.aborted || id !== props.task.id || version !== epoch) return
  checks.value = result.records
  total.value = result.total
  if (selected.value)
    selected.value =
      result.records.find((c) => c.id === selected.value?.id) ?? null
}
watch(
  () => [props.task.id, props.task.taskStateVersion],
  (_, previous) => {
    epoch++
    if (previous?.[0] !== props.task.id) {
      pageNum.value = 1
      selected.value = null
      pending.value = null
    }
    checks.value = []
    void run(load)
  },
  { immediate: true },
)
function edit(check: DiagnosticCheck) {
  if (pending.value) return
  selected.value = check
  value.value = ''
  execution.value = 'NOT_PERFORMED'
  outcome.value = 'UNKNOWN'
}
function save() {
  void run(async () => {
    if (!pending.value) {
      const check = selected.value
      if (
        !check?.answerSchema ||
        check.status !== 'OPEN' ||
        check.sourcesHidden
      )
        return
      const schema = check.answerSchema
      let parsed: string | boolean | number = value.value
      if (!value.value.trim()) throw new Error('请明确填写答复')
      if (schema.kind === 'BOOLEAN') {
        if (!['true', 'false'].includes(value.value))
          throw new Error('请选择是或否')
        parsed = value.value === 'true'
      }
      if (schema.kind === 'NUMBER') {
        parsed = Number(value.value)
        if (!Number.isFinite(parsed)) throw new Error('请填写有效数值')
      }
      pending.value = {
        taskId: props.task.id,
        checkId: check.id,
        body: {
          clientActionId: newRequestId(),
          taskStateVersion: props.task.taskStateVersion,
          answer: {
            value: parsed,
            executionStatus:
              check.checkKind === 'CHECK' ? execution.value : null,
            outcome:
              check.checkKind === 'CHECK'
                ? execution.value === 'NOT_PERFORMED'
                  ? 'UNKNOWN'
                  : outcome.value
                : null,
          },
        },
      }
    }
    const submission = pending.value
    let receipt: TaskReceipt
    try {
      receipt = await answerCheck(
        submission.taskId,
        submission.checkId,
        submission.body,
        await auth.token(),
      )
    } catch (failure) {
      if (
        failure instanceof ApiError &&
        [1001, 1004, 5103].includes(failure.code)
      ) {
        pending.value = null
        selected.value = null
        await load()
      }
      throw failure
    }
    if (props.task.id !== submission.taskId || signal.aborted) return
    pending.value = null
    selected.value = null
    emit('saved', receipt)
    await load()
  })
}
function turnPage(offset: number) {
  pageNum.value += offset
  void run(load)
}
</script>

<template>
  <section class="panel">
    <h3>待答问题与检查</h3>
    <p>答复保存后不会自动调用模型；是否执行、是否恢复请按实际填写。</p>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <article v-for="check in checks" :key="check.id">
      <p>
        {{ statusNames[check.status] }} · 第 {{ check.attemptNumber }} 次 ·
        环境版本 {{ check.contextVersion }}
      </p>
      <p class="preserve">
        {{ check.instruction ?? '来源已失效，指引已隐藏' }}
      </p>
      <p v-if="check.previousCheckId">这是一次复查，原答复保留在历史中。</p>
      <p v-if="check.normalizedAnswer">
        反馈：{{ check.normalizedAnswer.value }} · 执行情况：{{
          check.normalizedAnswer.executionStatus ?? '澄清问题'
        }}
        · 结果：{{ check.normalizedAnswer.outcome ?? '不适用' }}
      </p>
      <ElButton
        v-if="
          check.status === 'OPEN' &&
          !check.sourcesHidden &&
          check.executorType !== 'TOOL'
        "
        :disabled="busy || active || !!pending"
        @click="edit(check)"
        >填写答复</ElButton
      >
      <ElButton
        v-if="check.answerMessageId"
        :disabled="busy || active"
        @click="emit('resume', check.answerMessageId)"
        >选择此反馈继续</ElButton
      >
    </article>
    <ElButton
      :disabled="busy || pageNum === 1 || !!pending"
      @click="turnPage(-1)"
      >上一页检查</ElButton
    >
    <ElButton
      :disabled="busy || BigInt(pageNum * 10) >= BigInt(total) || !!pending"
      @click="turnPage(1)"
      >下一页检查</ElButton
    >
    <form v-if="selected?.answerSchema" @submit.prevent="save">
      <label
        >答复
        <ElSelect
          v-if="selected.answerSchema.kind === 'CHOICE'"
          v-model="value"
          :disabled="!!pending"
          ><ElOption
            v-for="option in selected.answerSchema.options"
            :key="option"
            :label="option"
            :value="option"
        /></ElSelect>
        <ElSelect
          v-else-if="selected.answerSchema.kind === 'BOOLEAN'"
          v-model="value"
          :disabled="!!pending"
          ><ElOption label="是" value="true" /><ElOption
            label="否"
            value="false"
        /></ElSelect>
        <ElInput
          v-else
          v-model="value"
          :maxlength="selected.answerSchema.maxLength"
          :disabled="!!pending"
          :type="
            selected.answerSchema.kind === 'NUMBER' ? 'number' : 'textarea'
          "
        />
      </label>
      <template v-if="selected.checkKind === 'CHECK'">
        <label
          >是否执行<ElSelect v-model="execution" :disabled="!!pending"
            ><ElOption label="尚未执行" value="NOT_PERFORMED" /><ElOption
              label="本人报告已执行"
              value="PERFORMED" /></ElSelect
        ></label>
        <label v-if="execution === 'PERFORMED'"
          >执行结果<ElSelect v-model="outcome" :disabled="!!pending"
            ><ElOption label="尚不清楚" value="UNKNOWN" /><ElOption
              label="没有变化"
              value="UNCHANGED" /><ElOption
              label="有所改善"
              value="IMPROVED" /><ElOption
              label="本人报告已恢复"
              value="RESTORED_REPORTED" /></ElSelect
        ></label>
      </template>
      <ElButton native-type="submit" :disabled="busy || active">{{
        pending ? '按原答复重试并核对' : '保存答复'
      }}</ElButton>
    </form>
  </section>
</template>
