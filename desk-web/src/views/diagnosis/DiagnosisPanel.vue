<script setup lang="ts">
import { computed, onMounted, onScopeDispose, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElInput,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus'
import {
  listConversations,
  createConversation,
  getConversation,
  listTurns,
  listFacts,
  changeFacts,
  createGeneration,
  generationReceipt,
  getGeneration,
  cancelGeneration,
  createDraft,
  draftReceipt,
  putFeedback,
  shareFeedback,
  subscribeGeneration,
  generationCalls,
  type GenerationCalls,
  deleteConversation,
  renameConversation,
  type Conversation,
  type Generation,
  type Fact,
  type Draft,
  type Feedback,
  type GenerationInput,
  type DraftPurpose,
} from '../../api/diagnosis/diagnosisApi'
import { listServices, listDevices } from '../../api/support/supportApi'
import {
  blankContext,
  type EntitledService,
  type Device,
} from '../../api/support/types'
import {
  createTicket,
  creationReceipt,
  act,
  actionReceipt,
} from '../../api/ticket/ticketApi'
import type {
  Ticket,
  TicketCreateForm,
  TicketAction,
} from '../../api/ticket/types'
import { originalLink } from '../../api/knowledge/knowledgeApi'
import { newRequestId } from '../../api/http'
import { useAuthStore } from '../../stores/authStore'
import { useOperation } from '../../composables/useOperation'
import SupportContextFields from '../support/SupportContextFields.vue'

/** 本人私有会话；断流查询原运行，建议必须人工编辑确认后进入正式工单。 @author yangaobo */
const props = defineProps<{ ticket?: Ticket }>()
const emit = defineEmits<{ changed: [] }>()
const calls = ref<GenerationCalls | null>(null)
const auth = useAuthStore(),
  router = useRouter(),
  { run, busy, error, signal } = useOperation()
const mode = computed(() =>
  props.ticket
    ? ('TECHNICIAN_ASSIST' as const)
    : ('CUSTOMER_SELF_SERVICE' as const),
)
const conversations = ref<Conversation[]>([]),
  conversation = ref<Conversation | null>(null),
  turns = ref<Generation[]>([]),
  facts = ref<Fact[]>([]),
  current = ref<Generation | null>(null)
const conversationPage = ref(1),
  conversationTotal = ref('0'),
  turnPage = ref(1),
  turnTotal = ref('0'),
  factPage = ref(1),
  factTotal = ref('0')
const services = ref<EntitledService[]>([]),
  devices = ref<Device[]>([]),
  servicePage = ref(1),
  devicePage = ref(1),
  serviceTotal = ref('0'),
  deviceTotal = ref('0')
const serviceItemId = ref(''),
  deviceId = ref<string | null>(null),
  title = ref(''),
  context = ref(blankContext()),
  question = ref('')
const pending = ref<{ conversationId: string; form: GenerationInput } | null>(
  null,
)
const factAction = ref<'ASSERT' | 'CORRECT' | 'RETRACT'>('ASSERT'),
  factKind = ref<'OBSERVATION' | 'PERFORMED_ACTION'>('OBSERVATION'),
  factId = ref<string | null>(null),
  statement = ref(''),
  confirmed = ref<string[]>([])
const draft = ref<Draft | null>(null),
  draftContent = ref({
    title: '',
    symptom: '',
    attemptedActions: '',
    content: '',
    resolutionType: 'GUIDANCE' as 'RESTORED' | 'GUIDANCE' | 'REFERRED',
    referralTarget: '',
  })
const pendingDraft = ref<{
  conversationId: string
  sourceGenerationId: string
  purpose: DraftPurpose
  clientRequestId: string
  confirmedFactIds: string[]
} | null>(null)
const adoption = ref<
  | { kind: 'CREATE'; form: TicketCreateForm }
  | { kind: 'ACTION'; action: TicketAction }
  | null
>(null)
const feedback = ref<Feedback | null>(null),
  feedbackComment = ref(''),
  notice = ref(''),
  streaming = ref(false),
  newName = ref('')
let subscription: AbortController | undefined,
  selection = 0
const active = computed(
  () => !!current.value && ['QUEUED', 'RUNNING'].includes(current.value.status),
)
const statusNames: Record<Generation['status'], string> = {
  QUEUED: '已持久受理，等待执行',
  RUNNING: '正在执行有界排障',
  SUCCEEDED: '本轮已结束',
  FAILED: '本轮失败',
  CANCELLED: '本轮已取消',
  INTERRUPTED: '执行中断，未自动重跑',
}
const sourceNames = {
  USER_REPORT: '用户反馈',
  STAFF_RECORD: '人工记录',
  TOOL_OBSERVATION: '工具观测',
  ASSISTANT_PROPOSAL: '助手提议',
}
const factKindNames = {
  OBSERVATION: '观察或反馈',
  PERFORMED_ACTION: '用户报告已尝试',
  OPEN_QUESTION: '待确认问题',
  HYPOTHESIS: '待验证假设',
}
watch(
  () => [current.value?.id, current.value?.sourcesHidden],
  () => {
    calls.value = null
  },
)
async function loadList() {
  const value = await listConversations(
    conversationPage.value,
    mode.value,
    props.ticket?.id,
    signal,
  )
  conversations.value = value.records
  conversationTotal.value = value.total
}
async function loadCurrent(version = selection) {
  if (!conversation.value) return
  const id = conversation.value.id
  const [c, t, f] = await Promise.all([
    getConversation(id, signal),
    listTurns(id, turnPage.value, signal),
    listFacts(id, factPage.value, signal),
  ])
  if (version !== selection || signal.aborted) return
  conversation.value = c
  turns.value = t.records
  turnTotal.value = t.total
  facts.value = f.records
  factTotal.value = f.total
}
function select(value: Conversation) {
  subscription?.abort()
  selection++
  const version = selection
  void run(async () => {
    conversation.value = value
    current.value = null
    calls.value = null
    draft.value = null
    feedback.value = null
    confirmed.value = []
    turnPage.value = 1
    factPage.value = 1
    context.value = { ...value.supportContext }
    await loadCurrent(version)
    const latest = turns.value.at(-1)
    if (latest) {
      current.value = latest
      if (['QUEUED', 'RUNNING'].includes(latest.status))
        void follow(latest, version)
    }
  })
}
function create() {
  void run(async () => {
    const value = await createConversation(
      props.ticket
        ? {
            mode: 'TECHNICIAN_ASSIST',
            title: title.value || props.ticket.title,
            ticketId: props.ticket.id,
          }
        : {
            mode: 'CUSTOMER_SELF_SERVICE',
            title: title.value,
            serviceItemId: serviceItemId.value,
            deviceId: deviceId.value,
            supportContext: { ...context.value },
          },
      await auth.token(),
    )
    subscription?.abort()
    selection++
    conversation.value = value
    current.value = null
    draft.value = null
    turns.value = []
    facts.value = []
    turnPage.value = 1
    factPage.value = 1
    await loadList()
  })
}
function loadCalls() {
  void run(async () => {
    const id = current.value?.id,
      version = selection
    if (!id) return
    const value = await generationCalls(id, signal)
    if (version === selection && current.value?.id === id && !signal.aborted)
      calls.value = value
  })
}
async function follow(value: Generation, version: number) {
  subscription?.abort()
  const controller = new AbortController()
  subscription = controller
  streaming.value = true
  const live = () =>
    !controller.signal.aborted && !signal.aborted && version === selection
  try {
    const complete = await subscribeGeneration(
      value.id,
      await auth.token(),
      controller.signal,
      (status) => {
        if (live() && current.value?.id === value.id)
          current.value = { ...current.value, status }
      },
      (tool) => {
        if (live()) notice.value = `只读工具 ${tool.name}：${tool.status}`
      },
    )
    if (!live()) return
    if (!complete)
      notice.value = '订阅已断开，正在核对原运行；没有重新提交问题。'
  } catch (failure) {
    if (live())
      notice.value =
        failure instanceof Error ? failure.message : '订阅已中断，请核对原运行'
  } finally {
    if (live()) {
      streaming.value = false
      try {
        const snapshot = await getGeneration(value.id, signal)
        if (live()) {
          current.value = snapshot
          await loadCurrent(version)
        }
      } catch (failure) {
        if (live()) {
          error.value =
            failure instanceof Error ? failure.message : '原运行核对失败'
          if (current.value)
            current.value = {
              ...current.value,
              answer: null,
              citations: [],
              structuredResult: null,
              sourcesHidden: true,
            }
        }
      }
    }
  }
}
function send() {
  void run(async () => {
    if (!conversation.value) throw new Error('请先创建或选择本人会话')
    pending.value ??= {
      conversationId: conversation.value.id,
      form: {
        question: question.value,
        stateVersion: conversation.value.stateVersion,
        clientRequestId: newRequestId(),
      },
    }
    const value = await createGeneration(
      pending.value.conversationId,
      pending.value.form,
      await auth.token(),
    )
    if (question.value === pending.value.form.question) question.value = ''
    pending.value = null
    current.value = value
    draft.value = null
    feedback.value = null
    await loadCurrent()
    void follow(value, selection)
  })
}
function recover() {
  void run(async () => {
    if (!pending.value) return
    const value = await generationReceipt(
      pending.value.form.clientRequestId,
      signal,
    )
    pending.value = null
    current.value = value
    await loadCurrent()
    if (['QUEUED', 'RUNNING'].includes(value.status))
      void follow(value, selection)
  })
}
function refreshRun() {
  void run(async () => {
    if (current.value)
      current.value = await getGeneration(current.value.id, signal)
    await loadCurrent()
  })
}
function cancel() {
  void run(async () => {
    if (!current.value) return
    current.value = await cancelGeneration(current.value.id, await auth.token())
    subscription?.abort()
    streaming.value = false
    await loadCurrent()
  })
}
function recordFact(environmentOnly = false) {
  void run(async () => {
    if (!conversation.value) return
    conversation.value = await changeFacts(
      conversation.value.id,
      environmentOnly
        ? {
            stateVersion: conversation.value.stateVersion,
            clientActionId: newRequestId(),
            action: 'SET_CONTEXT',
            supportContext: { ...context.value },
          }
        : {
            stateVersion: conversation.value.stateVersion,
            clientActionId: newRequestId(),
            action: factAction.value,
            statement: statement.value,
            factKind: factKind.value,
            factId: factAction.value === 'ASSERT' ? null : factId.value,
          },
      await auth.token(),
    )
    statement.value = ''
    await loadCurrent()
  })
}
function showDraft(value: Draft) {
  draft.value = value
  draftContent.value = {
    ...value.content,
    resolutionType: value.content.resolutionType ?? 'GUIDANCE',
    referralTarget: value.content.referralTarget ?? '',
  }
  adoption.value = null
}
function makeDraft(purpose: DraftPurpose) {
  void run(async () => {
    if (!conversation.value || !current.value) return
    pendingDraft.value ??= {
      conversationId: conversation.value.id,
      sourceGenerationId: current.value.id,
      purpose,
      clientRequestId: newRequestId(),
      confirmedFactIds: [...confirmed.value],
    }
    const { conversationId, ...form } = pendingDraft.value
    showDraft(await createDraft(conversationId, form, await auth.token()))
    pendingDraft.value = null
  })
}
function recoverDraft() {
  void run(async () => {
    if (!pendingDraft.value) return
    showDraft(await draftReceipt(pendingDraft.value.clientRequestId, signal))
    pendingDraft.value = null
  })
}
function adopt() {
  void run(async () => {
    if (!draft.value || !conversation.value) return
    if (!adoption.value) {
      if (draft.value.purpose === 'CREATE_TICKET')
        adoption.value = {
          kind: 'CREATE',
          form: {
            clientRequestId: newRequestId(),
            title: draftContent.value.title,
            symptom: draftContent.value.symptom,
            attemptedActions: draftContent.value.attemptedActions || null,
            serviceItemId: conversation.value.serviceItemId,
            deviceId: conversation.value.deviceId,
            supportContext: { ...conversation.value.supportContext },
            serviceAreaCode: null,
            serviceLocation: null,
            sourceDraftId: draft.value.id,
          },
        }
      else {
        if (!props.ticket || draft.value.ticketVersion === null)
          throw new Error('处理草稿缺少工单版本')
        const base = {
          clientActionId: newRequestId(),
          rowVersion: draft.value.ticketVersion,
          content: draftContent.value.content,
          sourceDraftId: draft.value.id,
        }
        adoption.value = {
          kind: 'ACTION',
          action:
            draft.value.purpose === 'ADD_RECORD'
              ? { name: 'comments', form: { ...base, visibility: 'PUBLIC' } }
              : {
                  name: 'resolve',
                  form: {
                    ...base,
                    resolutionType: draftContent.value.resolutionType,
                    referralTarget: draftContent.value.referralTarget || null,
                  },
                },
        }
      }
    }
    if (adoption.value.kind === 'CREATE') {
      const value = await createTicket(
        adoption.value.form,
        await auth.token(),
        signal,
      )
      adoption.value = null
      await router.push(`/tickets/${value.id}`)
    } else {
      if (!props.ticket) return
      await act(
        props.ticket.id,
        adoption.value.action,
        await auth.token(),
        signal,
      )
      adoption.value = null
      notice.value = '人工确认动作已提交，请以工单时间线为准。'
      emit('changed')
    }
  })
}
function recoverAdoption() {
  void run(async () => {
    if (!adoption.value) return
    if (adoption.value.kind === 'CREATE') {
      const value = await creationReceipt(
        adoption.value.form.clientRequestId,
        signal,
      )
      adoption.value = null
      await router.push(`/tickets/${value.id}`)
    } else if (props.ticket) {
      await actionReceipt(
        props.ticket.id,
        adoption.value.action.form.clientActionId,
        signal,
      )
      adoption.value = null
      notice.value = '已查到原工单动作回执。'
      emit('changed')
    }
  })
}
function rate(helpful: boolean) {
  void run(async () => {
    if (current.value)
      feedback.value = await putFeedback(
        current.value.id,
        {
          rating: helpful ? 'HELPFUL' : 'UNHELPFUL',
          issueCategory: helpful ? null : 'OTHER',
          comment: feedbackComment.value || null,
        },
        await auth.token(),
      )
  })
}
function share() {
  void run(async () => {
    if (current.value && feedback.value)
      feedback.value = await shareFeedback(
        current.value.id,
        feedback.value.rowVersion,
        !feedback.value.shareWithKnowledgeAdmin,
        await auth.token(),
      )
  })
}
function rename() {
  void run(async () => {
    if (conversation.value) {
      conversation.value = await renameConversation(
        conversation.value.id,
        conversation.value.rowVersion,
        newName.value,
        await auth.token(),
      )
      await loadList()
    }
  })
}
function remove() {
  void run(async () => {
    if (!conversation.value) return
    await deleteConversation(
      conversation.value.id,
      conversation.value.rowVersion,
      await auth.token(),
    )
    subscription?.abort()
    selection++
    conversation.value = null
    current.value = null
    facts.value = []
    turns.value = []
    draft.value = null
    await loadList()
  })
}
function moreOptions(kind: 'services' | 'devices') {
  void run(async () => {
    if (kind === 'services') {
      const value = await listServices(undefined, signal, servicePage.value + 1)
      services.value.push(...value.records)
      serviceTotal.value = value.total
      servicePage.value++
    } else {
      const value = await listDevices(undefined, signal, devicePage.value + 1)
      devices.value.push(...value.records)
      deviceTotal.value = value.total
      devicePage.value++
    }
  })
}
onMounted(() => {
  void run(async () => {
    await loadList()
    if (!props.ticket) {
      const [s, d] = await Promise.all([
        listServices(undefined, signal),
        listDevices(undefined, signal),
      ])
      services.value = s.records
      devices.value = d.records
      serviceTotal.value = s.total
      deviceTotal.value = d.total
    }
  })
})
watch(
  () => auth.user?.ownerId,
  () => {
    subscription?.abort()
    selection++
    conversation.value = null
    current.value = null
    turns.value = []
    facts.value = []
    draft.value = null
    pending.value = null
    pendingDraft.value = null
    adoption.value = null
  },
)
onScopeDispose(() => subscription?.abort())

function conversationPagePrevious() {
  conversationPage.value--
  void run(loadList)
}
function conversationPageNext() {
  conversationPage.value++
  void run(loadList)
}
function turnPagePrevious() {
  turnPage.value--
  void run(loadCurrent)
}
function turnPageNext() {
  turnPage.value++
  void run(loadCurrent)
}
function factPagePrevious() {
  factPage.value--
  void run(loadCurrent)
}
function factPageNext() {
  factPage.value++
  void run(loadCurrent)
}
</script>

<template>
  <section class="panel">
    <h2>{{ ticket ? '我的工单私有助手' : '我的自助排障' }}</h2>
    <p>
      会话仅本人可读。助手建议不代表已执行；需要补充信息时本轮会结束。人工支持可以独立使用。
    </p>
    <ElAlert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
    /><ElAlert
      v-if="notice"
      :title="notice"
      type="info"
      :closable="true"
      @close="notice = ''"
    />
    <div class="toolbar">
      <ElSelect
        :model-value="conversation?.id ?? ''"
        :disabled="busy || !!pending || !!adoption"
        aria-label="选择本人会话"
        @update:model-value="
          (value) => {
            const row = conversations.find((item) => item.id === value)
            if (row) select(row)
          }
        "
        ><ElOption
          v-for="row in conversations"
          :key="row.id"
          :value="row.id"
          :label="row.title" /></ElSelect
      ><ElButton
        :disabled="conversationPage === 1 || busy"
        @click="conversationPagePrevious"
        >上一页会话</ElButton
      ><ElButton
        :disabled="
          BigInt(conversationPage * 10) >= BigInt(conversationTotal) || busy
        "
        @click="conversationPageNext"
        >下一页会话</ElButton
      >
    </div>
    <details>
      <summary>新建本人会话</summary>
      <label>会话标题<ElInput v-model="title" maxlength="100" /></label
      ><template v-if="!ticket"
        ><label
          >支持项目<ElSelect v-model="serviceItemId"
            ><ElOption
              v-for="row in services"
              :key="row.item.id"
              :value="row.item.id"
              :label="row.item.serviceName" /></ElSelect></label
        ><label
          >设备（可空）<ElSelect v-model="deviceId" clearable
            ><ElOption
              v-for="row in devices.filter((row) => row.status === 'ACTIVE')"
              :key="row.id"
              :value="row.id"
              :label="row.assetCode" /></ElSelect></label
        ><ElButton
          v-if="BigInt(services.length) < BigInt(serviceTotal)"
          :disabled="busy"
          @click="moreOptions('services')"
          >更多支持项目</ElButton
        ><ElButton
          v-if="BigInt(devices.length) < BigInt(deviceTotal)"
          :disabled="busy"
          @click="moreOptions('devices')"
          >更多设备</ElButton
        ><SupportContextFields v-model="context" /></template
      ><ElButton :disabled="busy || !!pending || !!adoption" @click="create"
        >创建私有会话</ElButton
      >
    </details>
    <template v-if="conversation"
      ><p>
        事实版本 {{ conversation.stateVersion }} · 会话版本
        {{ conversation.rowVersion
        }}<template v-if="current?.ticketVersion">
          · 本轮工单版本 {{ current.ticketVersion }}</template
        >
      </p>
      <ElInput
        v-model="newName"
        aria-label="会话新名称"
        maxlength="100"
        placeholder="会话新名称"
      /><ElButton :disabled="busy || !newName" @click="rename">改名</ElButton
      ><ElButton :disabled="busy" @click="remove"
        >删除本会话并结束活跃轮</ElButton
      >
      <form @submit.prevent="send">
        <label
          >问题或本次反馈<ElInput
            v-model="question"
            type="textarea"
            :rows="3"
            maxlength="4000"
            :disabled="!!pending"
            placeholder="说明现象、已尝试的步骤和反馈。环境变更请在下方明确更新。" /></label
        ><ElButton
          native-type="submit"
          :disabled="busy || active || (!question.trim() && !pending)"
          >{{ pending ? '沿原请求重试' : '提交本轮' }}</ElButton
        ><ElButton v-if="pending" :disabled="busy" @click="recover"
          >核对原运行</ElButton
        ><ElButton v-if="active" :disabled="busy" @click="cancel"
          >取消本轮</ElButton
        ><ElButton :disabled="busy" @click="refreshRun">读取持久状态</ElButton>
      </form>
      <article v-if="current" class="panel" aria-live="polite">
        <h3>{{ statusNames[current.status] }}</h3>
        <p v-if="streaming">正在订阅真实阶段，关闭页面只结束订阅。</p>
        <p>{{ current.question }}</p>
        <ElAlert
          v-if="current.sourcesHidden"
          title="当前来源或上下文已不可用，相关正文、引用和草稿已隐藏。"
          type="warning"
          :closable="false"
        />
        <p v-if="current.answer" class="preserve">{{ current.answer }}</p>
        <p v-if="current.errorCode">诊断：{{ current.errorCode }}</p>
        <p>
          实际输入 Token：{{ current.inputTokens ?? '未知' }} · 输出 Token：{{
            current.outputTokens ?? '未知'
          }}
        </p>
        <ElButton :disabled="busy" @click="loadCalls"
          >查看逐次模型与只读工具调用</ElButton
        >
        <section v-if="calls">
          <p v-for="call in calls.modelCalls" :key="call.number">
            模型 {{ call.number }} · {{ call.requestedModel }} ·
            {{ call.status }} · 输入 {{ call.inputTokens ?? '未知' }} / 输出
            {{ call.outputTokens ?? '未知' }} · 估算费用
            {{ call.estimatedCost ?? '未知' }}
          </p>
          <p v-for="tool in calls.toolCalls" :key="tool.number">
            工具 {{ tool.number }} · {{ tool.name }} · {{ tool.status }} ·
            {{ tool.resultSummary ?? '摘要不可用' }}
          </p>
        </section>
        <ol>
          <li v-for="cite in current.citations" :key="cite.sourceUsageId">
            [{{ cite.number }}] {{ cite.title }} · {{ cite.location }}
            <p class="preserve">{{ cite.excerpt }}</p>
            <a
              v-if="cite.documentId"
              :href="
                originalLink(cite.documentId, current.id, cite.sourceUsageId)
              "
              >读取当前有权原件</a
            >
          </li>
        </ol>
        <template
          v-if="current.status === 'SUCCEEDED' && !current.sourcesHidden"
          ><ElButton
            v-if="!ticket"
            :disabled="busy || confirmed.length === 0"
            @click="makeDraft('CREATE_TICKET')"
            >用本人确认事实形成建单草稿</ElButton
          ><template v-else
            ><ElButton
              :disabled="busy || !current.structuredResult?.proposedRecord"
              @click="makeDraft('ADD_RECORD')"
              >形成处理记录草稿</ElButton
            ><ElButton
              :disabled="busy || !current.structuredResult?.proposedResolution"
              @click="makeDraft('RESOLVE_TICKET')"
              >形成解决建议草稿</ElButton
            ></template
          ><ElButton v-if="pendingDraft" :disabled="busy" @click="recoverDraft"
            >核对原草稿</ElButton
          ><label
            >反馈说明<ElInput
              v-model="feedbackComment"
              maxlength="1000" /></label
          ><ElButton :disabled="busy" @click="rate(true)">有帮助</ElButton
          ><ElButton :disabled="busy" @click="rate(false)">无帮助</ElButton>
          <p v-if="feedback">
            反馈：{{
              feedback.status === 'RECORDED'
                ? '已记录，不共享私有工单上下文'
                : feedback.status
            }}
          </p>
          <ElButton
            v-if="feedback && !ticket"
            :disabled="busy"
            @click="share"
            >{{
              feedback.shareWithKnowledgeAdmin
                ? '撤销本轮反馈共享'
                : '明确共享本轮反馈给知识管理员'
            }}</ElButton
          ></template
        >
      </article>
      <details>
        <summary>历史运行</summary>
        <ul>
          <li v-for="value in turns" :key="value.id">
            <ElButton link @click="current = value"
              >{{ statusNames[value.status] }} · {{ value.question }}</ElButton
            >
          </li>
        </ul>
        <ElButton :disabled="turnPage === 1 || busy" @click="turnPagePrevious"
          >上一页运行</ElButton
        ><ElButton
          :disabled="BigInt(turnPage * 10) >= BigInt(turnTotal) || busy"
          @click="turnPageNext"
          >下一页运行</ElButton
        >
      </details>
      <section class="panel">
        <h3>诊断事实与本人确认</h3>
        <ul>
          <li v-for="value in facts" :key="value.id">
            <ElTag>{{ sourceNames[value.sourceType] }}</ElTag>
            {{ factKindNames[value.factKind] }} ·
            {{ value.operation === 'ASSERT' ? '记录' : '撤回' }}
            <span v-if="!value.active">已被更正或撤回</span>
            <span v-if="value.historicalEnvironment">旧环境待复核</span>
            <p class="preserve">
              {{ value.statement ?? '来源已失效，正文隐藏' }}
            </p>
            <label
              v-if="
                !ticket &&
                value.sourceType === 'USER_REPORT' &&
                value.active &&
                !value.historicalEnvironment &&
                !value.sourcesHidden
              "
              ><input
                v-model="confirmed"
                type="checkbox"
                :value="value.id"
              />确认此反馈可用于交接草稿</label
            >
          </li>
        </ul>
        <ElButton :disabled="factPage === 1 || busy" @click="factPagePrevious"
          >上一页事实</ElButton
        ><ElButton
          :disabled="BigInt(factPage * 10) >= BigInt(factTotal) || busy"
          @click="factPageNext"
          >下一页事实</ElButton
        >
        <ElSelect v-model="factAction" :disabled="active"
          ><ElOption value="ASSERT" label="追加本人反馈" /><ElOption
            value="CORRECT"
            label="更正原事实" /><ElOption
            value="RETRACT"
            label="撤回原事实" /></ElSelect
        ><ElSelect v-model="factKind" :disabled="active"
          ><ElOption value="OBSERVATION" label="本人观察或反馈" /><ElOption
            value="PERFORMED_ACTION"
            label="本人明确报告已执行" /></ElSelect
        ><ElSelect
          v-if="factAction !== 'ASSERT'"
          v-model="factId"
          :disabled="active"
          placeholder="选择同会话前驱事实"
          ><ElOption
            v-for="value in facts.filter(
              (value) => value.operation === 'ASSERT',
            )"
            :key="value.id"
            :value="value.id"
            :label="value.statement ?? '受限事实'" /></ElSelect
        ><ElInput
          v-model="statement"
          :disabled="active"
          type="textarea"
          maxlength="2000"
          placeholder="更正或撤回请写明原因"
        /><ElButton
          :disabled="busy || active || !statement"
          @click="recordFact()"
          >按事实版本追加记录</ElButton
        >
        <template v-if="!ticket"
          ><SupportContextFields
            v-model="context"
            :disabled="active"
          /><ElButton :disabled="busy || active" @click="recordFact(true)"
            >明确更新问题环境</ElButton
          ></template
        >
        <p v-else>
          技术会话的项目、设备和环境以当前工单为准，请通过工单动作修改。
        </p>
      </section>
      <section v-if="draft" class="panel">
        <h3>待人工确认草稿</h3>
        <p>
          有效期至
          {{
            new Date(draft.expiresAt).toLocaleString('zh-CN', {
              timeZone: 'Asia/Shanghai',
            })
          }}。创建草稿未执行任何工单动作。
        </p>
        <template v-if="draft.purpose === 'CREATE_TICKET'"
          ><label
            >工单标题<ElInput
              v-model="draftContent.title"
              maxlength="100"
              :disabled="!!adoption" /></label
          ><label
            >确认现象<ElInput
              v-model="draftContent.symptom"
              type="textarea"
              :disabled="!!adoption" /></label
          ><label
            >本人明确报告的已执行操作<ElInput
              v-model="draftContent.attemptedActions"
              type="textarea"
              :disabled="!!adoption" /></label></template
        ><template v-else
          ><label
            >人工编辑后的记录或结果<ElInput
              v-model="draftContent.content"
              type="textarea"
              :rows="5"
              :disabled="!!adoption" /></label
          ><template v-if="draft.purpose === 'RESOLVE_TICKET'"
            ><ElSelect
              v-model="draftContent.resolutionType"
              :disabled="!!adoption"
              ><ElOption value="RESTORED" label="人工报告恢复" /><ElOption
                value="GUIDANCE"
                label="指导交付" /><ElOption
                value="REFERRED"
                label="转介" /></ElSelect
            ><ElInput
              v-model="draftContent.referralTarget"
              :disabled="!!adoption"
              placeholder="转介去向（转介必填）" /></template></template
        ><ElAlert
          v-if="ticket && draft.ticketVersion !== ticket.rowVersion"
          title="工单版本已改变，请核对新上下文；不会自动套用新版本或重新生成。"
          type="warning"
          :closable="false"
        /><ElButton
          :disabled="
            busy || (!!ticket && draft.ticketVersion !== ticket.rowVersion)
          "
          @click="adopt"
          >{{
            adoption ? '沿原工单动作键重试' : '我已编辑确认，提交正式工单动作'
          }}</ElButton
        ><ElButton v-if="adoption" :disabled="busy" @click="recoverAdoption"
          >核对原工单动作回执</ElButton
        >
      </section>
    </template>
  </section>
</template>
