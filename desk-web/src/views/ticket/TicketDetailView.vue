<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElInput,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus'
import {
  act,
  actionReceipt,
  getTicket,
  listEvents,
} from '../../api/ticket/ticketApi'
import {
  statusNames,
  type Ticket,
  type TicketAction,
  type TicketEvent,
} from '../../api/ticket/types'
import { blankContext, type EntitledService } from '../../api/support/types'
import { listServices } from '../../api/support/supportApi'
import { newRequestId, ApiError } from '../../api/http'
import { useOperation } from '../../composables/useOperation'
import { useAuthStore } from '../../stores/authStore'
import SupportContextFields from '../support/SupportContextFields.vue'
import OnsitePanel from './OnsitePanel.vue'
import DiagnosisPanel from '../diagnosis/DiagnosisPanel.vue'
const route = useRoute(),
  auth = useAuthStore()
const id = String(route.params.id)
const ticket = ref<Ticket | null>(null),
  events = ref<TicketEvent[]>([]),
  services = ref<EntitledService[]>([])
const servicePage = ref(1),
  serviceTotal = ref('0')
const eventPage = ref(1),
  eventTotal = ref('0')
const { busy, error, run, signal } = useOperation()
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
    if (['PROCESSING', 'WAITING_CUSTOMER'].includes(t.status))
      result.push({ name: 'reassign', label: '转派' })
  }
  if (
    editable.value &&
    ['SUBMITTED', 'PROCESSING', 'WAITING_CUSTOMER'].includes(t.status)
  )
    result.push(
      { name: 'context', label: '分类与环境' },
      { name: 'comments', label: '记录处理进展' },
    )
  if (handler.value && t.status === 'PROCESSING')
    result.push(
      { name: 'request-information', label: '请求补充' },
      { name: 'resolve', label: '提交处理结果' },
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
    ['SUBMITTED', 'PROCESSING', 'WAITING_CUSTOMER'].includes(t.status)
  )
    result.push({ name: 'cancel', label: '取消工单' })
  return result
})
const needsReason = computed(() =>
  ['assign', 'reassign', 'priority', 'context'].includes(selected.value),
)
const needsContent = computed(() =>
  [
    'comments',
    'request-information',
    'supplements',
    'unresolved',
    'cancel',
    'resolve',
  ].includes(selected.value),
)
async function load() {
  const [data, timeline] = await Promise.all([
    getTicket(id, signal),
    listEvents(id, eventPage.value, signal),
  ])
  ticket.value = data.ticket
  events.value = timeline.records
  eventTotal.value = timeline.total
  if (!pending.value) {
    context.value = { ...data.ticket.supportContext }
    serviceItemId.value = data.ticket.serviceItemId
    serviceAreaCode.value = data.ticket.serviceAreaCode
    serviceLocation.value = data.ticket.serviceLocation
    priority.value = data.ticket.priority
    selected.value = actions.value[0]?.name ?? 'comments'
  }
}
onMounted(
  () =>
    void run(async () => {
      await load()
      if (editable.value && ticket.value) {
        const page = await listServices(ticket.value.customerId, signal)
        services.value = page.records
        serviceTotal.value = page.total
      }
    }),
)
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
      return {
        name,
        form: { ...base, assigneeId: assignee.value, reason: reason.value },
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
        },
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
    await load()
  })
}
function recover() {
  void run(async () => {
    if (!pending.value) return
    await actionReceipt(id, pending.value.form.clientActionId, signal)
    pending.value = null
    await load()
  })
}
/** 版本冲突后放弃旧动作必须由用户主动选择，不自动套用新版本提交。 */
function reload() {
  void run(async () => {
    pending.value = null
    await load()
  })
}
function next(delta: number) {
  void run(async () => {
    eventPage.value += delta
    const page = await listEvents(id, eventPage.value, signal)
    events.value = page.records
    eventTotal.value = page.total
  })
}
const eventNames: Record<string, string> = {
  CREATED: '已提交',
  ACCEPTED: '已接单',
  ASSIGNED: '已分派',
  REASSIGNED: '已转派',
  CONTEXT_UPDATED: '环境已更新',
  PRIORITY_CHANGED: '优先级已更新',
  NEED_INFO: '请求补充',
  SUPPLEMENTED: '已补充',
  COMMENTED: '处理记录',
  RESOLVED: '处理结果',
  CLOSED: '已结单',
  UNRESOLVED: '客户反馈未解决',
  CANCELLED: '已取消',
  ACCEPTANCE_OVERDUE: '受理超期',
}
</script>
<template>
  <main>
    <ElAlert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
    /><template v-if="ticket"
      ><p class="eyebrow">{{ ticket.ticketNo }}</p>
      <h1>{{ ticket.title }}</h1>
      <div class="toolbar">
        <ElTag>{{ statusNames[ticket.status] }}</ElTag
        ><span>{{
          ticket.serviceItemId ? '已分类' : '待分类 · 仅承诺分流'
        }}</span
        ><span>版本 {{ ticket.rowVersion }}</span
        ><ElButton :disabled="busy || !!pending" @click="reload">刷新</ElButton>
      </div>
      <p v-if="ticket.closureReason">
        结单原因：{{
          ticket.closureReason === 'CUSTOMER_CONFIRMED'
            ? '客户确认处理结果'
            : ticket.closureReason === 'CONFIRMATION_TIMEOUT'
              ? '72小时未反馈自动结单，不代表已修复'
              : '历史未知'
        }}
      </p>
      <p v-if="ticket.confirmationDueAt">
        反馈期限：{{
          new Date(ticket.confirmationDueAt).toLocaleString('zh-CN', {
            timeZone: 'Asia/Shanghai',
          })
        }}
      </p>
      <article class="card">
        <h2>问题与环境</h2>
        <p class="preserve">{{ ticket.symptom }}</p>
        <h3>已尝试</h3>
        <p class="preserve">{{ ticket.attemptedActions || '未提供' }}</p>
        <dl>
          <dt>系统</dt>
          <dd>
            {{ ticket.supportContext.osName || '未知' }}
            {{ ticket.supportContext.osVersion }}
          </dd>
          <dt>软件</dt>
          <dd>
            {{ ticket.supportContext.softwareName || '未知' }}
            {{ ticket.supportContext.softwareVersion }}
          </dd>
          <dt>错误码</dt>
          <dd>{{ ticket.supportContext.errorCode || '未知' }}</dd>
          <dt>连接</dt>
          <dd>{{ ticket.supportContext.connectionType }}</dd>
          <dt>影响范围</dt>
          <dd>{{ ticket.supportContext.affectedScope }}</dd>
        </dl>
      </article>
      <form
        v-if="actions.length || pending"
        class="card"
        @submit.prevent="submit"
      >
        <h2>处理动作</h2>
        <fieldset :disabled="busy || !!pending">
          <label
            >操作<ElSelect v-model="selected" :disabled="busy || !!pending"
              ><ElOption
                v-for="action in actions"
                :key="action.name"
                :value="action.name"
                :label="action.label" /></ElSelect></label
          ><label v-if="['assign', 'reassign'].includes(selected)"
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
          ><label v-if="needsContent"
            >说明<ElInput
              v-model="content"
              type="textarea"
              :rows="4"
              maxlength="5000"
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
            pending ? '原动作重试' : '确认执行'
          }}</ElButton
          ><ElButton v-if="pending" :disabled="busy" @click="recover"
            >查询回执</ElButton
          ><ElButton v-if="pending" :disabled="busy" @click="reload"
            >放弃旧动作并重新读取</ElButton
          >
        </div>
      </form>
      <section class="card">
        <h2>处理时间线</h2>
        <ol class="timeline">
          <li v-for="event in events" :key="event.id">
            <div>
              <strong>{{
                eventNames[event.eventType] || event.eventType
              }}</strong
              ><ElTag v-if="event.visibility === 'INTERNAL'" type="warning"
                >内部</ElTag
              ><time>{{
                new Date(event.createdAt).toLocaleString('zh-CN', {
                  timeZone: 'Asia/Shanghai',
                })
              }}</time>
            </div>
            <p class="preserve">{{ event.content }}</p>
          </li>
        </ol>
        <div class="toolbar">
          <ElButton :disabled="busy || eventPage === 1" @click="next(-1)"
            >上一页</ElButton
          ><span>第 {{ eventPage }} 页 · 共 {{ eventTotal }} 条</span
          ><ElButton
            :disabled="busy || BigInt(eventPage * 50) >= BigInt(eventTotal)"
            @click="next(1)"
            >下一页</ElButton
          >
        </div>
      </section>
      <OnsitePanel
        :key="ticket.rowVersion"
        :ticket="ticket"
        @changed="run(load)"
      />
      <DiagnosisPanel
        v-if="auth.has('TECHNICIAN')"
        :key="ticket.id"
        :ticket="ticket"
        @changed="run(load)"
      />
    </template>
  </main>
</template>
