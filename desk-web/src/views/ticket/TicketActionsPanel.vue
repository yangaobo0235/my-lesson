<script setup lang="ts">
import { computed, ref, toRef, watch } from 'vue'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElInput,
  ElOption,
  ElSelect,
} from 'element-plus'
import { act, actionReceipt } from '../../api/ticket/ticketApi'
import type { Ticket, TicketAction } from '../../api/ticket/types'
import { blankContext, type EntitledService } from '../../api/support/types'
import { listServices } from '../../api/support/supportApi'
import { newRequestId, ApiError } from '../../api/http'
import { useOperation } from '../../composables/useOperation'
import { useAuthStore } from '../../stores/authStore'
import SupportContextFields from '../support/SupportContextFields.vue'

/** 工单单次动作独立管理表单、提交锁和原键核对。 @author yangaobo */
const props = defineProps<{ ticket: Ticket; refreshing: boolean }>()
const emit = defineEmits<{ changed: [] }>()
const ticket = toRef(props, 'ticket')
const id = props.ticket.id
const auth = useAuthStore()
const { busy, error, run, signal } = useOperation()
const services = ref<EntitledService[]>([])
const servicesLoaded = ref(false)
const servicePage = ref(1)
const serviceTotal = ref('0')
const followUpAt = ref('')
const content = ref(''),
  reason = ref(''),
  assignee = ref(''),
  visibility = ref<'PUBLIC' | 'INTERNAL'>('PUBLIC'),
  resolution = ref<'RESTORED' | 'GUIDANCE' | 'REFERRED'>('GUIDANCE'),
  referral = ref(''),
  priority = ref<'NORMAL' | 'HIGH'>('NORMAL')
const context = ref(blankContext()),
  serviceItemId = ref<string | null>(null),
  serviceAreaCode = ref<string | null>(null),
  serviceLocation = ref<string | null>(null)
const selected = ref<TicketAction['name']>('comments'),
  pending = ref<TicketAction | null>(null)
const actionOpen = ref(false)
const customer = computed(() => auth.has('CUSTOMER_USER'))
const handler = computed(
  () =>
    auth.has('TECHNICIAN') && ticket.value?.assigneeId === auth.user?.ownerId,
)
const editable = computed(() => auth.has('SERVICE_AGENT') || handler.value)
const actions = computed(() => {
  const t = ticket.value
  const result: { name: TicketAction['name']; label: string }[] = []
  if (!t) return result
  if (t.status === 'SUBMITTED' && auth.has('TECHNICIAN'))
    result.push({ name: 'accept', label: '接单' })
  if (auth.has('SERVICE_AGENT')) {
    if (t.status === 'SUBMITTED')
      result.push(
        { name: 'assign', label: '分派' },
        { name: 'priority', label: '调整优先级' },
      )
    if (
      ['PROCESSING', 'WAITING_CUSTOMER', 'WAITING_EXTERNAL'].includes(t.status)
    )
      result.push({ name: 'reassign', label: '转派' })
  }
  if (
    editable.value &&
    [
      'SUBMITTED',
      'PROCESSING',
      'WAITING_CUSTOMER',
      'WAITING_EXTERNAL',
    ].includes(t.status)
  )
    result.push(
      { name: 'context', label: '分类与环境' },
      { name: 'comments', label: '记录处理进展' },
    )
  if (handler.value && t.status === 'PROCESSING')
    result.push(
      { name: 'request-information', label: '请求补充' },
      { name: 'wait-external', label: '等待外部反馈' },
      { name: 'resolve', label: '提交处理结果' },
    )
  if (
    handler.value &&
    ['WAITING_CUSTOMER', 'WAITING_EXTERNAL'].includes(t.status)
  )
    result.push(
      { name: 'end-wait', label: '结束等待' },
      { name: 'follow-up', label: '跟进并续期' },
    )
  if (customer.value && t.status === 'WAITING_CUSTOMER')
    result.push({ name: 'supplements', label: '补充信息' })
  if (customer.value && t.status === 'RESOLVED')
    result.push(
      { name: 'confirm', label: '确认处理结果' },
      { name: 'unresolved', label: '问题仍未解决' },
    )
  if (
    (customer.value || editable.value) &&
    [
      'SUBMITTED',
      'PROCESSING',
      'WAITING_CUSTOMER',
      'WAITING_EXTERNAL',
    ].includes(t.status)
  )
    result.push({ name: 'cancel', label: '取消工单' })
  return result
})
const needsFollowUp = computed(() =>
  ['request-information', 'wait-external', 'follow-up'].includes(
    selected.value,
  ),
)
const needsReason = computed(() =>
  ['assign', 'reassign', 'priority', 'context'].includes(selected.value),
)
const needsContent = computed(() =>
  [
    'comments',
    'request-information',
    'wait-external',
    'follow-up',
    'end-wait',
    'supplements',
    'unresolved',
    'cancel',
    'resolve',
  ].includes(selected.value),
)
const actionLabel = computed(
  () =>
    actions.value.find(({ name }) => name === selected.value)?.label ??
    '处理动作',
)

function chooseAction(name: TicketAction['name']) {
  if (props.refreshing || busy.value || pending.value) return
  if (selected.value !== name) {
    content.value = ''
    reason.value = ''
    followUpAt.value = ''
  }
  selected.value = name
  actionOpen.value = true
  if (name === 'context' && !servicesLoaded.value) loadServiceChoices()
}
watch(
  () => ticket.value.rowVersion,
  () => {
    if (pending.value || actionOpen.value) return
    context.value = { ...ticket.value.supportContext }
    serviceItemId.value = ticket.value.serviceItemId
    serviceAreaCode.value = ticket.value.serviceAreaCode
    serviceLocation.value = ticket.value.serviceLocation
    priority.value = ticket.value.priority
    selected.value = actions.value[0]?.name ?? 'comments'
  },
  { immediate: true },
)

function loadServiceChoices() {
  void run(async () => {
    const page = await listServices(ticket.value.customerId, signal)
    services.value = page.records
    servicePage.value = page.pageNum
    serviceTotal.value = page.total
    servicesLoaded.value = true
  })
}
/** 当前客户的服务项目逐页补充，不覆盖正在编辑的分类。 */
function moreServices() {
  void run(async () => {
    if (!ticket.value) return
    const page = await listServices(
      ticket.value.customerId,
      signal,
      servicePage.value + 1,
    )
    services.value.push(...page.records)
    servicePage.value++
    serviceTotal.value = page.total
  })
}
/** 按动作构造字段白名单，不把完整工单回传。 */
function buildAction(): TicketAction {
  if (!ticket.value) throw new Error('请先加载工单')
  const base = {
    clientActionId: newRequestId(),
    rowVersion: ticket.value.rowVersion,
  }
  const name = selected.value
  if (needsContent.value && !content.value.trim())
    throw new Error('请填写处理说明')
  if (needsReason.value && !reason.value.trim())
    throw new Error('请填写变更原因')
  switch (name) {
    case 'accept':
    case 'confirm':
      return { name, form: base }
    case 'assign':
    case 'reassign':
      if (!/^[0-9a-f]{32}$/.test(assignee.value.trim()))
        throw new Error('请填写有效的技术人员账号标识')
      return {
        name,
        form: {
          ...base,
          assigneeId: assignee.value.trim(),
          reason: reason.value,
        },
      }
    case 'priority':
      return {
        name,
        form: { ...base, priority: priority.value, reason: reason.value },
      }
    case 'comments':
      return {
        name,
        form: {
          ...base,
          content: content.value,
          visibility: visibility.value,
          sourceDraftId: null,
          sourceDraftRootId: null,
          contentSha256: null,
        },
      }
    case 'resolve':
      return {
        name,
        form: {
          ...base,
          content: content.value,
          resolutionType: resolution.value,
          referralTarget: referral.value || null,
          sourceDraftId: null,
          sourceDraftRootId: null,
          contentSha256: null,
        },
      }
    case 'request-information':
    case 'wait-external':
    case 'follow-up': {
      if (
        !followUpAt.value ||
        !Number.isFinite(new Date(followUpAt.value).getTime())
      )
        throw new Error('请选择下次跟进时间')
      if (content.value.length > 1000)
        throw new Error('等待说明不能超过1000字符')
      return {
        name,
        form: {
          ...base,
          content: content.value,
          followUpAt: new Date(followUpAt.value).toISOString(),
        },
      }
    }
    case 'context':
      return {
        name,
        form: {
          ...base,
          serviceItemId: serviceItemId.value,
          supportContext: { ...context.value },
          serviceAreaCode: serviceAreaCode.value,
          serviceLocation: serviceLocation.value,
          reason: reason.value,
        },
      }
    default:
      return { name, form: { ...base, content: content.value } }
  }
}
function submit() {
  void run(async () => {
    pending.value ??= buildAction()
    try {
      await act(id, pending.value, await auth.token(), signal)
    } catch (failure) {
      if (
        failure instanceof ApiError &&
        failure.status < 500 &&
        failure.status !== 409
      )
        pending.value = null
      throw failure
    }
    pending.value = null
    content.value = ''
    reason.value = ''
    actionOpen.value = false
    emit('changed')
  })
}
function recover() {
  void run(async () => {
    if (!pending.value) return
    await actionReceipt(id, pending.value.form.clientActionId, signal)
    pending.value = null
    content.value = ''
    reason.value = ''
    actionOpen.value = false
    emit('changed')
  })
}
/** 版本冲突后放弃旧动作必须由用户主动选择，不自动套用新版本提交。 */
function reload() {
  void run(async () => {
    pending.value = null
    actionOpen.value = false
    emit('changed')
  })
}
</script>

<template>
  <div
    v-if="actions.length && !pending"
    class="ticket-actions"
    aria-label="可执行的工单操作"
  >
    <ElButton
      v-for="(action, index) in actions"
      :key="action.name"
      :type="index === 0 ? 'primary' : 'default'"
      :disabled="busy || refreshing"
      @click="chooseAction(action.name)"
      >{{ action.label }}</ElButton
    >
  </div>
  <ElButton v-if="pending" type="warning" plain @click="actionOpen = true"
    >继续核对上次操作</ElButton
  >
  <ElDialog
    v-model="actionOpen"
    :title="actionLabel"
    width="min(560px, calc(100% - 32px))"
    :close-on-click-modal="false"
  >
    <ElAlert
      v-if="error && actionOpen"
      :title="error"
      type="error"
      :closable="false"
    >
      <ElButton
        v-if="selected === 'context' && !servicesLoaded"
        :disabled="busy"
        text
        @click="loadServiceChoices"
        >重试读取服务项目</ElButton
      >
    </ElAlert>
    <form @submit.prevent="submit">
      <fieldset class="action-fields" :disabled="busy || !!pending">
        <label v-if="['assign', 'reassign'].includes(selected)"
          >技术人员账号标识<ElInput
            v-model="assignee"
            maxlength="32"
            placeholder="填写已开通本客户范围的技术人员标识"
            :disabled="!!pending" /></label
        ><label v-if="selected === 'priority'"
          >优先级<ElSelect v-model="priority" :disabled="!!pending"
            ><ElOption value="NORMAL" label="普通（4小时受理）" /><ElOption
              value="HIGH"
              label="高（1小时受理）" /></ElSelect></label
        ><template v-if="selected === 'context'"
          ><label
            >服务项目<ElSelect v-model="serviceItemId" :disabled="!!pending"
              ><ElOption
                v-if="
                  ticket.serviceItemId &&
                  !services.some((s) => s.item.id === ticket?.serviceItemId)
                "
                :value="ticket.serviceItemId"
                label="保留原受理项目" /><ElOption
                v-for="service in services"
                :key="service.item.id"
                :value="service.item.id"
                :label="service.item.serviceName" /></ElSelect></label
          ><ElButton
            v-if="BigInt(servicePage * 100) < BigInt(serviceTotal)"
            :disabled="busy"
            @click="moreServices"
            >加载更多服务项目</ElButton
          ><SupportContextFields
            v-model="context"
            :disabled="!!pending" /><label
            >现场区域<ElInput
              v-model="serviceAreaCode"
              :disabled="!!pending" /></label
          ><label
            >现场地点<ElInput
              v-model="serviceLocation"
              :disabled="!!pending" /></label></template
        ><label v-if="needsReason"
          >原因<ElInput
            v-model="reason"
            maxlength="500"
            required
            :disabled="!!pending" /></label
        ><label v-if="needsFollowUp"
          >下次跟进时间（当前设备时区）
          <input
            v-model="followUpAt"
            type="datetime-local"
            required
            :disabled="!!pending"
          /> </label
        ><label v-if="needsContent"
          >说明<ElInput
            v-model="content"
            type="textarea"
            :rows="4"
            :maxlength="needsFollowUp ? 1000 : 5000"
            required
            :disabled="!!pending" /></label
        ><label v-if="selected === 'comments'"
          >可见范围<ElSelect v-model="visibility" :disabled="!!pending"
            ><ElOption value="PUBLIC" label="公开给客户" /><ElOption
              value="INTERNAL"
              label="仅服务团队" /></ElSelect></label
        ><template v-if="selected === 'resolve'"
          ><label
            >处理结果<ElSelect v-model="resolution" :disabled="!!pending"
              ><ElOption value="RESTORED" label="恢复" /><ElOption
                value="GUIDANCE"
                label="指导" /><ElOption
                value="REFERRED"
                label="转介" /></ElSelect></label
          ><label v-if="resolution === 'REFERRED'"
            >转介去向<ElInput
              v-model="referral"
              maxlength="300"
              required
              :disabled="!!pending"
          /></label>
          <p v-if="!ticket.serviceItemId">
            待分类工单仅可转介；如需恢复或指导结案，请先分类。
          </p></template
        >
      </fieldset>
      <ElAlert
        v-if="pending"
        title="已保留原动作及输入。请查询回执或原键重试；版本冲突时重新读取工单后再决定。"
        type="info"
        :closable="false"
      />
      <div class="toolbar">
        <ElButton type="primary" native-type="submit" :loading="busy">{{
          pending ? '原动作重试' : `确认${actionLabel}`
        }}</ElButton
        ><ElButton v-if="pending" :disabled="busy" @click="recover"
          >查询回执</ElButton
        ><ElButton v-if="pending" :disabled="busy" @click="reload"
          >放弃旧动作并重新读取</ElButton
        >
      </div>
    </form>
  </ElDialog>
</template>

<style scoped>
.ticket-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.ticket-actions :deep(.el-button) {
  margin: 0;
}
.action-fields {
  border: 0;
  padding: 0;
  margin: 0;
}
</style>
