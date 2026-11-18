<script setup lang="ts">
import { computed, onMounted, onScopeDispose, ref } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElCheckbox,
  ElInput,
  ElOption,
  ElSelect,
} from 'element-plus'
import {
  attachmentByRequest,
  attachmentFileLink,
  changeAttachment,
  listAttachments,
  uploadAttachment,
} from '../../api/attachment/attachmentApi'
import {
  attachmentStatusNames,
  type Attachment,
  type AttachmentAction,
  type AttachmentUploadForm,
} from '../../api/attachment/types'
import { ApiError, newRequestId } from '../../api/http'
import { actionReceipt } from '../../api/ticket/ticketApi'
import type { Ticket } from '../../api/ticket/types'
import { useOperation } from '../../composables/useOperation'
import { useAuthStore } from '../../stores/authStore'

/** 工单附件上传、人工脱敏确认及原键恢复，未决操作保留输入。 @author yangaobo */
const props = defineProps<{ ticket: Ticket }>()
const emit = defineEmits<{ changed: [] }>()
const auth = useAuthStore()
const { run, busy, error, signal } = useOperation()
const rows = ref<Attachment[]>([]),
  pageNum = ref(1),
  total = ref('0'),
  notice = ref('')
const file = ref<File | null>(null),
  format = ref<Attachment['contentFormat']>('LOG'),
  visibility = ref<Attachment['visibility']>('PUBLIC')
const pendingUpload = ref<{
  file: File
  metadata: AttachmentUploadForm
} | null>(null)
const pendingAction = ref<AttachmentAction | null>(null)
const rejected = ref(false)
const selected = ref<Attachment | null>(null),
  redactedText = ref(''),
  allowAiUse = ref(false)
const unresolved = computed(
  () => pendingUpload.value !== null || pendingAction.value !== null,
)
const customer = computed(() => auth.has('CUSTOMER_USER'))
const canManage = computed(
  () =>
    auth.has('SERVICE_AGENT') ||
    (auth.has('TECHNICIAN') && auth.user?.ownerId === props.ticket.assigneeId),
)

async function load() {
  const result = await listAttachments(props.ticket.id, pageNum.value, signal)
  rows.value = result.records
  total.value = result.total
}
function selectFile(event: Event) {
  if (event.target instanceof HTMLInputElement)
    file.value = event.target.files?.[0] ?? null
}
function upload() {
  void run(async () => {
    if (!pendingUpload.value) {
      if (!file.value || file.value.size === 0 || file.value.size > 1_048_576)
        throw new Error('请选择不超过 1MiB 的 UTF-8 文本')
      pendingUpload.value = {
        file: file.value,
        metadata: {
          clientUploadId: newRequestId(),
          contentFormat: format.value,
          visibility: visibility.value,
        },
      }
    }
    const original = pendingUpload.value
    const csrf = await auth.token()
    const result = await confirmedRequest(() =>
      uploadAttachment(
        props.ticket.id,
        original.metadata,
        original.file,
        csrf,
        signal,
      ),
    )
    finishUpload(result)
    await load()
  })
}
function finishUpload(value: Attachment) {
  notice.value = `${value.originalFileName}：${attachmentStatusNames[value.status]}`
  if (['READY', 'FAILED', 'DELETED'].includes(value.status)) {
    pendingUpload.value = null
    file.value = null
  }
}
function recoverUpload() {
  void run(async () => {
    if (!pendingUpload.value) return
    finishUpload(
      await attachmentByRequest(
        pendingUpload.value.metadata.clientUploadId,
        signal,
      ),
    )
    await load()
  })
}
function reconcile(value: Attachment) {
  void run(async () => {
    const result = await attachmentByRequest(value.clientUploadId, signal)
    notice.value = `${value.originalFileName}：${attachmentStatusNames[result.status]}`
    await load()
  })
}
function edit(value: Attachment) {
  selected.value = value
  redactedText.value = ''
  allowAiUse.value = false
}
function confirm() {
  void run(async () => {
    if (!selected.value || !redactedText.value.trim())
      throw new Error('请先核对原件，填写并预览完整脱敏文本')
    pendingAction.value = {
      attachmentId: selected.value.id,
      name: 'confirm-redaction',
      form: {
        rowVersion: selected.value.rowVersion,
        clientActionId: newRequestId(),
        fileSha256: selected.value.fileSha256,
        redactedText: redactedText.value,
        allowAiUse: allowAiUse.value,
      },
    }
    await submitAction()
  })
}
function remove(value: Attachment) {
  if (!window.confirm('撤销后所有人和 AI 均不能继续读取此附件。确认撤销访问？'))
    return
  pendingAction.value = {
    attachmentId: value.id,
    name: 'delete',
    form: { rowVersion: value.rowVersion, clientActionId: newRequestId() },
  }
  void run(submitAction)
}
async function submitAction() {
  if (!pendingAction.value) return
  const action = pendingAction.value
  const csrf = await auth.token()
  const result = await confirmedRequest(() =>
    changeAttachment(props.ticket.id, action, csrf, signal),
  )
  finishAction(result.actionType)
  await load()
}
/** 明确业务拒绝才允许返回编辑；网络、服务异常和未知响应保留原键。 */
async function confirmedRequest<T>(operation: () => Promise<T>): Promise<T> {
  rejected.value = false
  try {
    return await operation()
  } catch (failure) {
    rejected.value =
      failure instanceof ApiError &&
      [400, 403, 404, 409, 413, 415, 422].includes(failure.status)
    throw failure
  }
}
function reviseRejected() {
  if (!rejected.value) return
  pendingAction.value = null
  pendingUpload.value = null
  rejected.value = false
  notice.value = '上次请求被明确拒绝。输入已保留，请刷新资源并核对后再提交。'
}
function finishAction(type: string) {
  notice.value =
    type === 'DELETE_ATTACHMENT'
      ? '附件访问已撤销'
      : '脱敏版本与 AI 使用授权已保存'
  pendingAction.value = null
  selected.value = null
  redactedText.value = ''
  emit('changed')
}
function recoverAction() {
  void run(async () => {
    if (!pendingAction.value) return
    const result = await actionReceipt(
      props.ticket.id,
      pendingAction.value.form.clientActionId,
      signal,
    )
    finishAction(result.actionType)
    await load()
  })
}
function navigate(delta: number) {
  pageNum.value += delta
  void run(load)
}
function guard() {
  if (!unresolved.value) return true
  error.value = '仍有操作待核对，请先查询原回执或原键重试。'
  return false
}
function beforeUnload(event: BeforeUnloadEvent) {
  if (unresolved.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}
onBeforeRouteLeave(guard)
onBeforeRouteUpdate(guard)
onMounted(() => {
  window.addEventListener('beforeunload', beforeUnload)
  void run(load)
})
onScopeDispose(() => window.removeEventListener('beforeunload', beforeUnload))
</script>

<template>
  <section class="card">
    <h2>诊断附件</h2>
    <p>
      支持 UTF-8 的文本、日志及 JSON，单个文件最多
      1MiB。原件仅供有权人员读取；AI 只能使用你明确确认的脱敏文本。
    </p>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <ElAlert v-if="notice" :title="notice" type="info" :closable="false" />
    <ElButton v-if="rejected" :disabled="busy" @click="reviseRejected"
      >请求被拒绝，返回编辑</ElButton
    >
    <form v-if="!pendingAction" @submit.prevent="upload">
      <fieldset :disabled="busy || !!pendingUpload">
        <label
          >选择原件
          <input type="file" accept=".txt,.log,.json" @change="selectFile"
        /></label>
        <label
          >内容格式
          <ElSelect v-model="format" :disabled="busy || !!pendingUpload"
            ><ElOption label="日志" value="LOG" /><ElOption
              label="文本"
              value="TEXT" /><ElOption label="JSON" value="JSON" /></ElSelect
        ></label>
        <label v-if="!customer"
          >可见范围
          <ElSelect v-model="visibility" :disabled="busy || !!pendingUpload"
            ><ElOption label="客户及服务团队" value="PUBLIC" /><ElOption
              label="仅服务团队"
              value="INTERNAL" /></ElSelect
        ></label>
      </fieldset>
      <ElButton
        native-type="submit"
        :disabled="busy || (!file && !pendingUpload)"
        >{{ pendingUpload ? '原上传重试' : '上传原件' }}</ElButton
      >
      <ElButton v-if="pendingUpload" :disabled="busy" @click="recoverUpload"
        >查询原上传结果</ElButton
      >
      <p v-if="pendingUpload">
        原请求键：{{
          pendingUpload.metadata.clientUploadId
        }}。结果未明前请保留本页；原键重试不会重复写入已登记对象。
      </p>
    </form>
    <ul>
      <li v-for="value in rows" :key="value.id">
        <strong>{{ value.originalFileName }}</strong> ·
        {{ attachmentStatusNames[value.status] }} ·
        {{ value.visibility === 'INTERNAL' ? '内部' : '公开给客户' }}
        <p>
          {{
            value.aiUseAllowed
              ? `已允许 AI 使用脱敏版本 ${value.redactionVersion}`
              : '未允许 AI 使用'
          }}
        </p>
        <a
          v-if="value.status === 'READY'"
          :href="attachmentFileLink(ticket.id, value.id)"
          target="_blank"
          rel="noopener noreferrer"
          >下载原件并人工核对</a
        >
        <ElButton
          v-if="
            value.uploadedBy === auth.user?.ownerId &&
            ['UPLOADING', 'UNKNOWN'].includes(value.status)
          "
          :disabled="busy || unresolved"
          @click="reconcile(value)"
          >核对存储结果</ElButton
        >
        <ElButton
          v-if="
            value.status === 'READY' && value.uploadedBy === auth.user?.ownerId
          "
          :disabled="busy || unresolved"
          @click="edit(value)"
          >确认或修改脱敏版本</ElButton
        >
        <ElButton
          v-if="value.uploadedBy === auth.user?.ownerId || canManage"
          :disabled="busy || unresolved"
          @click="remove(value)"
          >撤销附件访问</ElButton
        >
      </li>
    </ul>
    <div class="toolbar">
      <ElButton
        :disabled="busy || pageNum === 1 || unresolved"
        @click="navigate(-1)"
        >上一页</ElButton
      ><span>第 {{ pageNum }} 页 · 共 {{ total }} 条</span>
      <ElButton
        :disabled="busy || BigInt(pageNum * 10) >= BigInt(total) || unresolved"
        @click="navigate(1)"
        >下一页</ElButton
      >
      <ElButton :disabled="busy" @click="run(load)">刷新列表</ElButton>
    </div>
    <form v-if="selected && !pendingAction" @submit.prevent="confirm">
      <h3>确认 {{ selected.originalFileName }} 的脱敏内容</h3>
      <p>
        请移除密码、令牌、个人资料及无关信息。下方文本将成为新版本；保存时会使旧版本失效。JSON
        文件仍需保持有效 JSON。
      </p>
      <label
        >完整脱敏文本
        <ElInput
          v-model="redactedText"
          type="textarea"
          :rows="8"
          :disabled="busy || unresolved"
      /></label>
      <ElCheckbox v-model="allowAiUse" :disabled="busy || unresolved"
        >我已核对上述文本，允许 AI 使用这个脱敏版本</ElCheckbox
      >
      <p>不勾选可撤销 AI 使用授权；原件的人工读取权限不受此选项影响。</p>
      <ElButton
        native-type="submit"
        :disabled="busy || unresolved || !redactedText.trim()"
        >确认保存新版本</ElButton
      >
    </form>
    <div v-if="pendingAction">
      <p>
        操作结果待核对，已保留原输入与请求键：{{
          pendingAction.form.clientActionId
        }}
      </p>
      <ElButton :disabled="busy" @click="recoverAction"
        >查询原动作回执</ElButton
      >
      <ElButton :disabled="busy" @click="run(submitAction)"
        >原动作重试</ElButton
      >
    </div>
  </section>
</template>
