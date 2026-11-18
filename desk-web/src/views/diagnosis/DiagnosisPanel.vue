<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
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
  listRuns,
  createRun,
  runReceipt,
  getRun,
  cancelRun,
  createDraft,
  reviseDraft,
  withdrawDraft,
  draftReceipt,
  putFeedback,
  getFeedback,
  runCalls,
  type RunCalls,
  deleteConversation,
  renameConversation,
  type Conversation,
  type Run,
  type Fact,
  type Draft,
  type Feedback,
  type RunInput,
  type ConversationInput,
  type DraftPurpose,
  type SafetyCondition,
  safetyConditionNames,
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
import {
  bookOnsite,
  bookingReceipt,
  ticketSlots,
  type OnsiteSlot,
} from '../../api/onsite/onsiteApi'
import { synchronizeDraft } from '../../api/diagnosis/taskApi'
import { ApiError, newRequestId } from '../../api/http'
import { RESULT_CODES } from '../../api/resultCodes'
import { useAuthStore } from '../../stores/authStore'
import { useOperation } from '../../composables/useOperation'
import { useRunSubscription } from '../../composables/useRunSubscription'
import {
  createTask,
  getTask,
  listTasks,
  listFacts,
  changeFacts,
  changeContext,
  closeTask,
  listMessages,
  type DiagnosisTask,
  type TaskInput,
  type TaskReceipt,
  type FactInput,
  type TaskAction,
} from '../../api/diagnosis/taskApi'
import TaskChecks from './TaskChecks.vue'
import SupportContextFields from '../support/SupportContextFields.vue'

/** 本人私有会话；断流查询原运行，建议必须人工编辑确认后进入正式工单。 @author yangaobo */
const props = defineProps<{ ticket?: Ticket }>()
const emit = defineEmits<{ changed: [] }>()
const calls = ref<RunCalls | null>(null)
const auth = useAuthStore(),
  router = useRouter(),
  { run, busy, error, signal } = useOperation()
const selectedMode = ref<'CUSTOMER_SELF_SERVICE' | 'CONSULT'>(
  'CUSTOMER_SELF_SERVICE',
)
const mode = computed(() =>
  props.ticket ? ('TECHNICIAN_ASSIST' as const) : selectedMode.value,
)
const task = ref<DiagnosisTask | null>(null),
  tasks = ref<DiagnosisTask[]>([]),
  taskPage = ref(1),
  taskTotal = ref('0'),
  goal = ref(''),
  closeReason = ref('')
const savedMessageId = ref<string | null>(null)
const messages = ref<Awaited<ReturnType<typeof listMessages>>['records']>([]),
  messageCursor = ref('0'),
  messagesMore = ref(false)
const pendingCreate = ref<{
  conversation: ConversationInput
  task: TaskInput | null
  conversationId: string | null
} | null>(null)
const pendingTask = ref<{ conversationId: string; form: TaskInput } | null>(
  null,
)
const pendingFact = ref<{ taskId: string; form: FactInput } | null>(null)
const pendingContext = ref<{
  taskId: string
  form: TaskAction & {
    supportContext?: ReturnType<typeof blankContext>
    serviceItemId?: string | null
  }
} | null>(null)
const pendingClose = ref<{
  taskId: string
  action: 'close' | 'cancel' | 'report-outcome'
  form: TaskAction & { reason: string; restored?: true }
} | null>(null)
const pendingCheck = ref(false)
const selectionLocked = computed(
  () =>
    busy.value ||
    !!pending.value ||
    !!pendingCreate.value ||
    !!pendingTask.value ||
    !!pendingFact.value ||
    !!pendingContext.value ||
    !!pendingClose.value ||
    !!pendingRevision.value ||
    !!pendingDraft.value ||
    !!pendingSync.value ||
    !!adoption.value ||
    pendingCheck.value,
)
const conversations = ref<Conversation[]>([]),
  conversation = ref<Conversation | null>(null),
  turns = ref<Run[]>([]),
  facts = ref<Fact[]>([]),
  current = ref<Run | null>(null)
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
const pending = ref<{ conversationId: string; form: RunInput } | null>(null)
const factAction = ref<'ASSERT' | 'CORRECT' | 'RETRACT'>('ASSERT'),
  factKind = ref<'OBSERVATION' | 'ACTION_REPORT'>('OBSERVATION'),
  factId = ref<string | null>(null),
  statement = ref(''),
  reportedConditions = ref<SafetyCondition[]>([]),
  confirmed = ref<string[]>([])
const draft = ref<Draft | null>(null),
  draftContent = ref({
    title: '',
    symptom: '',
    attemptedActions: '',
    content: '',
    resolutionType: 'GUIDANCE' as 'RESTORED' | 'GUIDANCE' | 'REFERRED',
    referralTarget: '',
    followUpAt: '',
    slotId: '',
  })
const draftFollowUp = ref(''),
  selectedSlot = ref('')
const availableSlots = ref<OnsiteSlot[]>([]),
  slotPage = ref(1),
  slotTotal = ref('0')
const pendingSync = ref<{
  id: string
  body: { clientActionId: string; taskStateVersion: string }
} | null>(null)
const adoptedRoot = ref<string | null>(null)
const pendingDraft = ref<
  (Parameters<typeof createDraft>[1] & { taskId: string }) | null
>(null)
const pendingRevision = ref<{
  id: string
  body: Parameters<typeof reviseDraft>[1]
} | null>(null)
/** 显示控件中的默认选择仅用于编辑，对无关用途保持规范空值。 */
function editedContent(): Draft['content'] {
  return {
    title: draftContent.value.title,
    symptom: draftContent.value.symptom,
    attemptedActions: draftContent.value.attemptedActions,
    content: draftContent.value.content,
    resolutionType:
      draft.value?.purpose === 'RESOLVE_TICKET'
        ? draftContent.value.resolutionType
        : null,
    referralTarget:
      draft.value?.purpose === 'RESOLVE_TICKET'
        ? draftContent.value.referralTarget || null
        : null,
    ...(draft.value?.purpose === 'REQUEST_INFORMATION'
      ? { followUpAt: draftContent.value.followUpAt }
      : {}),
    ...(draft.value?.purpose === 'BOOK_ONSITE'
      ? { slotId: draftContent.value.slotId }
      : {}),
  }
}
const draftChanged = computed(
  () =>
    !!draft.value &&
    JSON.stringify(editedContent()) !== JSON.stringify(draft.value.content),
)
function saveDraftRevision() {
  void run(async () => {
    if (!draft.value || adoption.value) return
    pendingRevision.value ??= {
      id: draft.value.id,
      body: {
        clientRequestId: newRequestId(),
        contentSha256: draft.value.contentSha256,
        content: editedContent(),
      },
    }
    const value = await reviseDraft(
      pendingRevision.value.id,
      pendingRevision.value.body,
      await auth.token(),
    )
    pendingRevision.value = null
    showDraft(value)
  })
}
function recoverRevision() {
  void run(async () => {
    if (!pendingRevision.value) return
    const value = await draftReceipt(
      pendingRevision.value.body.clientRequestId,
      signal,
    )
    pendingRevision.value = null
    showDraft(value)
  })
}
function withdrawCurrentDraft() {
  void run(async () => {
    if (!draft.value || adoption.value || pendingRevision.value) return
    showDraft(
      await withdrawDraft(
        draft.value.id,
        draft.value.contentSha256,
        await auth.token(),
      ),
    )
    notice.value = '已停止新的草稿校验；已在途的工单请求仍需查询正式动作回执。'
  })
}
const adoption = ref<
  | { kind: 'CREATE'; form: TicketCreateForm }
  | { kind: 'ACTION'; action: TicketAction }
  | { kind: 'BOOK'; ticketId: string; form: Parameters<typeof bookOnsite>[1] }
  | null
>(null)
const feedback = ref<Feedback | null>(null),
  feedbackComment = ref(''),
  notice = ref(''),
  newName = ref('')
const feedbackAdoption = ref<Feedback['adoption']>('NOT_USED')
const feedbackCategory = ref<NonNullable<Feedback['issueCategory']>>('OTHER')
const feedbackReady = ref(false)
const pendingFeedback = ref<{
  runId: string
  body: Parameters<typeof putFeedback>[1]
} | null>(null)
let selection = 0
const {
  streaming,
  follow: followRun,
  stop: stopSubscription,
} = useRunSubscription({
  signal,
  csrf: () => auth.token(),
  status: (id, status) => {
    if (current.value?.id === id) current.value = { ...current.value, status }
  },
  notice: (message) => {
    notice.value = message
  },
  snapshot: async (snapshot) => {
    current.value = snapshot
    await loadCurrent()
  },
  failed: (failure) => {
    error.value = failure instanceof Error ? failure.message : '原运行核对失败'
    if (current.value)
      current.value = {
        ...current.value,
        answer: null,
        citations: [],
        structuredResult: null,
        sourcesHidden: true,
      }
  },
})
const active = computed(
  () => !!current.value && ['QUEUED', 'RUNNING'].includes(current.value.status),
)
const statusNames: Record<Run['status'], string> = {
  QUEUED: '已持久受理，等待执行',
  RUNNING: '正在执行有界排障',
  SUCCEEDED: '本轮已结束',
  FAILED: '本轮失败',
  CANCELLED: '本轮已取消',
  INTERRUPTED: '执行中断，未自动重跑',
}

/** 将本轮持久诊断转换为可操作提示，不把失败、未知调用或预算停止描述为已恢复。 */
const failureExplanation = computed(() => {
  const code = current.value?.errorCode
  if (!code) return ''
  switch (code) {
    case 'TASK_BUDGET_EXHAUSTED':
      return '本任务的累计用量已达到限额，或存在尚未核实的调用用量。请核对调用记录并转人工处理，继续提问不会重置任务额度。'
    case 'EXECUTION_BUDGET_EXCEEDED':
      return '本轮已达到执行时间或用量限制，或上一调用用量尚未确认。请先查看调用记录，再缩小问题范围或转人工处理。'
    case 'CONTEXT_CHANGED':
      return '任务环境、权限或执行状态已变化，本轮结果未提交。请刷新并核对最新任务状态后再决定是否继续。'
    case 'MODEL_PROVIDER_UNAVAILABLE':
      return '模型服务暂不可用，本轮已停止，已保存的反馈仍然保留。请联系服务管理员或转人工处理；部分调用用量可能尚未确认，系统不会自动重新执行。'
    case 'DEPENDENCY_UNAVAILABLE':
      return '身份或业务依赖暂不可用，本轮已停止，已保存的反馈仍然保留。请稍后核对原运行和调用记录，再决定是否继续；这不代表需要重新登录。'
    case 'QUEUE_DEADLINE_EXCEEDED':
      return '本轮等待时间过长，尚未开始执行。可稍后重新发起。'
    case 'EXECUTION_RIGHT_EXPIRED':
    case 'WORKER_STOPPED':
      return '处理进程中断，本轮已停止。部分调用的结果或用量可能尚未确认，请先核对调用记录；系统不会自动重新执行。'
    default:
      return '本轮未能生成通过校验的回答。请核对已保存的任务和调用记录，必要时转人工处理。'
  }
})
const sourceNames = {
  USER_REPORT: '用户反馈',
  HUMAN_RECORD: '人工记录',
  TOOL_OBSERVATION: '工具观测',
}
const factKindNames = {
  OBSERVATION: '观察或反馈',
  ACTION_REPORT: '用户报告已尝试',
  SYMPTOM: '故障现象',
  ENVIRONMENT: '运行环境',
  OUTCOME: '用户报告结果',
}
watch(
  () => [current.value?.id, current.value?.sourcesHidden],
  () => {
    calls.value = null
  },
)
watch(
  () => current.value?.id,
  () => {
    feedback.value = null
    feedbackComment.value = ''
    feedbackAdoption.value = 'NOT_USED'
    feedbackCategory.value = 'OTHER'
    feedbackReady.value = false
    pendingFeedback.value = null
  },
)
/** 明确读取原评价后编辑，迟到响应不得覆盖其他运行。 */
async function readFeedback() {
  const id = current.value?.id,
    version = selection
  if (!id) return
  const value = await getFeedback(id, signal)
  if (signal.aborted || current.value?.id !== id || version !== selection)
    return
  feedback.value = value
  feedbackComment.value = value?.comment ?? ''
  feedbackAdoption.value = value?.adoption ?? 'NOT_USED'
  feedbackCategory.value = value?.issueCategory ?? 'OTHER'
  pendingFeedback.value = null
  feedbackReady.value = true
}
async function loadList() {
  const version = selection,
    owner = auth.user?.ownerId
  const value = await listConversations(
    conversationPage.value,
    mode.value,
    props.ticket?.id,
    signal,
  )
  if (signal.aborted || version !== selection || owner !== auth.user?.ownerId)
    return
  conversations.value = value.records
  conversationTotal.value = value.total
}
/** 每次读取重核消息和任务来源；游标续读不发起模型请求。 */
async function loadMessages(append = false) {
  if (!conversation.value) return
  const id = conversation.value.id,
    version = selection
  const result = await listMessages(
    id,
    append ? messageCursor.value : '0',
    signal,
  )
  if (version !== selection || signal.aborted || conversation.value?.id !== id)
    return
  messages.value = append
    ? [...messages.value, ...result.records]
    : result.records
  messageCursor.value = result.nextCursor
  messagesMore.value = result.hasMore
}
async function loadTask() {
  if (!task.value) {
    facts.value = []
    factTotal.value = '0'
    return
  }
  const id = task.value.id,
    version = selection
  const [value, f] = await Promise.all([
    getTask(id, signal),
    listFacts(id, factPage.value, signal),
  ])
  if (version !== selection || signal.aborted || task.value?.id !== id) return
  task.value = value
  serviceItemId.value = value.serviceItemId ?? ''
  facts.value = f.records
  factTotal.value = f.total
  confirmed.value = confirmed.value.filter((id) =>
    facts.value.some(
      (f) =>
        f.id === id && f.active && !f.historicalEnvironment && !f.sourcesHidden,
    ),
  )
}
async function loadCurrent(version = selection) {
  if (!conversation.value) return
  const id = conversation.value.id
  const [c, t, taskList] = await Promise.all([
    getConversation(id, signal),
    listRuns(id, turnPage.value, signal),
    conversation.value.mode === 'CONSULT'
      ? Promise.resolve(null)
      : listTasks(id, taskPage.value, signal),
  ])
  if (version !== selection || signal.aborted || conversation.value?.id !== id)
    return
  conversation.value = c
  turns.value = t.records
  turnTotal.value = t.total
  tasks.value = taskList?.records ?? []
  taskTotal.value = taskList?.total ?? '0'
  if (!task.value) task.value = tasks.value[0] ?? null
  await Promise.all([loadTask(), loadMessages()])
}
function syncTaskContext() {
  if (task.value) context.value = { ...task.value.supportContext }
}
/** 任务或会话切换后清理已展示的选择，不丢弃任何未决提交身份。 */
function clearDraftSelection() {
  draft.value = null
  adoptedRoot.value = null
  selectedSlot.value = ''
  draftFollowUp.value = ''
  availableSlots.value = []
  slotPage.value = 1
  slotTotal.value = '0'
}
function selectTask(value: DiagnosisTask) {
  if (selectionLocked.value || active.value) return
  task.value = value
  current.value = turns.value.find((item) => item.taskId === value.id) ?? null
  calls.value = null
  feedback.value = null
  feedbackReady.value = false
  factId.value = null
  statement.value = ''
  reportedConditions.value = []
  closeReason.value = ''
  factPage.value = 1
  confirmed.value = []
  savedMessageId.value = null
  clearDraftSelection()
  context.value = { ...value.supportContext }
  void run(loadTask)
}
function taskPageChange(offset: number) {
  taskPage.value += offset
  void run(loadCurrent)
}
async function feedbackSaved(receipt: TaskReceipt) {
  savedMessageId.value = receipt.inputMessageId
  notice.value = '反馈已保存。请完成待答项后，明确点击继续诊断。'
  await run(loadCurrent)
}
/** 仅已确认的参数/版本拒绝允许调整；传输或服务故障保留原请求。 */
async function taskMutation<T>(
  operation: () => Promise<T>,
  rejected: () => void,
): Promise<T> {
  try {
    return await operation()
  } catch (failure) {
    if (
      failure instanceof ApiError &&
      [1001, 1004, 5103].includes(failure.code)
    ) {
      rejected()
      notice.value = '本次操作已被拒绝，请刷新任务状态、调整输入后重新提交。'
    }
    throw failure
  }
}
function finishTask(action: 'close' | 'cancel' | 'report-outcome') {
  void run(async () => {
    if (!task.value) return
    if (!closeReason.value.trim() && !pendingClose.value)
      throw new Error('请填写结束原因或实际结果')
    pendingClose.value ??= {
      taskId: task.value.id,
      action,
      form: {
        clientActionId: newRequestId(),
        taskStateVersion: task.value.taskStateVersion,
        reason: closeReason.value,
        ...(action === 'report-outcome' ? { restored: true } : {}),
      },
    }
    const submission = pendingClose.value
    await taskMutation(
      async () =>
        closeTask(
          submission.taskId,
          submission.action,
          submission.form,
          await auth.token(),
        ),
      () => {
        pendingClose.value = null
      },
    )
    pendingClose.value = null
    await loadCurrent()
  })
}
function newTask() {
  void run(async () => {
    if (!conversation.value || conversation.value.mode === 'CONSULT') return
    pendingTask.value ??= {
      conversationId: conversation.value.id,
      form: {
        clientActionId: newRequestId(),
        goal: goal.value,
        ...(props.ticket
          ? {}
          : {
              serviceItemId: serviceItemId.value || null,
              deviceId: deviceId.value || null,
              supportContext: { ...context.value },
            }),
      },
    }
    const receipt = await createTask(
      pendingTask.value.conversationId,
      pendingTask.value.form,
      await auth.token(),
    )
    pendingTask.value = null
    task.value = await getTask(receipt.taskId, signal)
    current.value = null
    calls.value = null
    savedMessageId.value = null
    confirmed.value = []
    clearDraftSelection()
    await loadCurrent()
  })
}
function select(value: Conversation) {
  if (selectionLocked.value) return
  stopSubscription()
  selection++
  const version = selection
  void run(async () => {
    conversation.value = value
    current.value = null
    calls.value = null
    clearDraftSelection()
    feedback.value = null
    confirmed.value = []
    turnPage.value = 1
    factPage.value = 1
    task.value = null
    tasks.value = []
    taskPage.value = 1
    savedMessageId.value = null
    messages.value = []
    await loadCurrent(version)
    if (version !== selection || signal.aborted) return
    syncTaskContext()
    const latest = turns.value[0]
    if (latest) {
      current.value = latest
      if (['QUEUED', 'RUNNING'].includes(latest.status))
        void follow(latest, version)
    }
  })
}
function create() {
  void run(async () => {
    pendingCreate.value ??= {
      conversation: {
        mode: mode.value,
        title: title.value || props.ticket?.title || '我的咨询',
        clientRequestId: newRequestId(),
        ...(props.ticket ? { ticketId: props.ticket.id } : {}),
      },
      task:
        mode.value === 'CONSULT'
          ? null
          : {
              clientActionId: newRequestId(),
              goal:
                goal.value ||
                title.value ||
                props.ticket?.title ||
                '诊断当前问题',
              ...(props.ticket
                ? {}
                : {
                    serviceItemId: serviceItemId.value || null,
                    deviceId: deviceId.value || null,
                    supportContext: { ...context.value },
                  }),
            },
      conversationId: null,
    }
    const submission = pendingCreate.value
    const value = await createConversation(
      submission.conversation,
      await auth.token(),
    )
    submission.conversationId = value.id
    let createdTask: DiagnosisTask | null = null
    if (submission.task) {
      const receipt = await createTask(
        value.id,
        submission.task,
        await auth.token(),
      )
      createdTask = await getTask(receipt.taskId, signal)
    }
    pendingCreate.value = null
    stopSubscription()
    selection++
    conversation.value = value
    task.value = createdTask
    savedMessageId.value = null
    current.value = null
    clearDraftSelection()
    turns.value = []
    facts.value = []
    messages.value = []
    turnPage.value = 1
    factPage.value = 1
    taskPage.value = 1
    await loadCurrent()
    await loadList()
  })
}
function loadCalls() {
  void run(async () => {
    const id = current.value?.id,
      version = selection
    if (!id) return
    const value = await runCalls(id, signal)
    if (version === selection && current.value?.id === id && !signal.aborted)
      calls.value = value
  })
}
async function follow(value: Run, version: number) {
  await followRun(value.id, () => version === selection)
}

function send(continueSaved = false) {
  void run(async () => {
    if (!conversation.value) throw new Error('请先创建或选择本人会话')
    pending.value ??= {
      conversationId: conversation.value.id,
      form: {
        ...(continueSaved && savedMessageId.value
          ? { inputMessageId: savedMessageId.value }
          : { message: question.value }),
        ...(task.value
          ? {
              taskId: task.value.id,
              taskStateVersion: task.value.taskStateVersion,
            }
          : {}),
        clientRequestId: newRequestId(),
      },
    }
    const value = await createRun(
      pending.value.conversationId,
      pending.value.form,
      await auth.token(),
    )
    if (question.value === pending.value.form.message) question.value = ''
    pending.value = null
    savedMessageId.value = null
    current.value = value
    clearDraftSelection()
    feedback.value = null
    await loadCurrent()
    void follow(value, selection)
  })
}
function recover() {
  void run(async () => {
    if (!pending.value) return
    const value = await runReceipt(pending.value.form.clientRequestId, signal)
    pending.value = null
    savedMessageId.value = null
    current.value = value
    await loadCurrent()
    if (['QUEUED', 'RUNNING'].includes(value.status))
      void follow(value, selection)
  })
}
function refreshRun() {
  void run(async () => {
    if (current.value) current.value = await getRun(current.value.id, signal)
    await loadCurrent()
  })
}
function cancel() {
  void run(async () => {
    if (!current.value) return
    current.value = await cancelRun(current.value.id, await auth.token())
    stopSubscription()
    streaming.value = false
    await loadCurrent()
  })
}
function recordFact(environmentOnly = false) {
  void run(async () => {
    if (!task.value) return
    if (environmentOnly) {
      pendingContext.value ??= {
        taskId: task.value.id,
        form: {
          taskStateVersion: task.value.taskStateVersion,
          clientActionId: newRequestId(),
          ...(props.ticket ? {} : { supportContext: { ...context.value } }),
          ...(!props.ticket && !task.value.serviceItemId
            ? { serviceItemId: serviceItemId.value || null }
            : {}),
        },
      }
      const submission = pendingContext.value
      await taskMutation(
        async () =>
          changeContext(submission.taskId, submission.form, await auth.token()),
        () => {
          pendingContext.value = null
        },
      )
      pendingContext.value = null
    } else {
      pendingFact.value ??= {
        taskId: task.value.id,
        form: {
          taskStateVersion: task.value.taskStateVersion,
          clientActionId: newRequestId(),
          action: factAction.value,
          statement: statement.value,
          factKind: factKind.value,
          reportedConditions:
            factKind.value === 'OBSERVATION' && factAction.value !== 'RETRACT'
              ? [...reportedConditions.value]
              : [],
          predecessorFactId:
            factAction.value === 'ASSERT' ? null : factId.value,
        },
      }
      const submission = pendingFact.value
      await taskMutation(
        async () =>
          changeFacts(submission.taskId, submission.form, await auth.token()),
        () => {
          pendingFact.value = null
        },
      )
      pendingFact.value = null
      statement.value = ''
      reportedConditions.value = []
    }
    await loadCurrent()
  })
}
function showDraft(value: Draft) {
  if (draft.value?.draftRootId !== value.draftRootId) adoptedRoot.value = null
  draft.value = value
  draftContent.value = {
    ...value.content,
    resolutionType: value.content.resolutionType ?? 'GUIDANCE',
    referralTarget: value.content.referralTarget ?? '',
    followUpAt: value.content.followUpAt ?? '',
    slotId: value.content.slotId ?? '',
  }
  adoption.value = null
}
function makeDraft(purpose: DraftPurpose) {
  void run(async () => {
    if (
      !task.value ||
      (!['CREATE_TICKET', 'BOOK_ONSITE'].includes(purpose) && !current.value)
    )
      return
    if (pendingRevision.value || adoption.value || pendingCheck.value)
      throw new Error('请先查询未确认结果的原请求')
    pendingDraft.value ??= {
      taskId: task.value.id,
      sourceRunId: ['CREATE_TICKET', 'BOOK_ONSITE'].includes(purpose)
        ? null
        : (current.value?.id ?? null),
      purpose,
      clientRequestId: newRequestId(),
      confirmedFactIds: [...confirmed.value],
      ...(purpose === 'REQUEST_INFORMATION'
        ? { followUpAt: new Date(draftFollowUp.value).toISOString() }
        : {}),
      ...(purpose === 'BOOK_ONSITE' ? { slotId: selectedSlot.value } : {}),
    }
    const { taskId, ...form } = pendingDraft.value
    showDraft(await createDraft(taskId, form, await auth.token()))
    pendingDraft.value = null
  })
}

/** 预约时段按已交接工单分页读取，选择不会预占容量。 */
function readSlots(page = 1) {
  void run(async () => {
    if (!task.value?.handoverTicketId) return
    const taskId = task.value.id,
      ticketId = task.value.handoverTicketId,
      version = selection
    const value = await ticketSlots(ticketId, page, signal)
    if (
      signal.aborted ||
      version !== selection ||
      task.value?.id !== taskId ||
      task.value.handoverTicketId !== ticketId
    )
      return
    availableSlots.value = value.records
    slotTotal.value = value.total
    slotPage.value = page
  })
}
/** 未知结果保留原同步键；明确版本冲突后刷新版本，下一次点击仅重试同步。 */
async function syncAdoption() {
  if (!draft.value || !task.value) return
  pendingSync.value ??= {
    id: draft.value.id,
    body: {
      clientActionId: newRequestId(),
      taskStateVersion: task.value.taskStateVersion,
    },
  }
  try {
    await synchronizeDraft(
      pendingSync.value.id,
      pendingSync.value.body,
      await auth.token(),
    )
  } catch (failure) {
    if (failure instanceof ApiError && failure.code === RESULT_CODES.CONFLICT) {
      const currentTaskId = task.value.id,
        currentDraftId = draft.value.id,
        version = selection
      const latest = await getTask(currentTaskId, signal)
      if (
        signal.aborted ||
        version !== selection ||
        task.value?.id !== currentTaskId ||
        draft.value?.id !== currentDraftId
      )
        return
      task.value = latest
      pendingSync.value = {
        id: currentDraftId,
        body: {
          clientActionId: newRequestId(),
          taskStateVersion: latest.taskStateVersion,
        },
      }
      throw new Error(
        '正式动作已提交，任务版本已刷新。请再次点击核对同步；不会重复执行正式动作。',
        { cause: failure },
      )
    }
    throw failure
  }
  adoptedRoot.value = draft.value.draftRootId
  pendingSync.value = null
  await loadCurrent()
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
    if (!draft.value || !conversation.value || !task.value) return
    if (pendingSync.value || adoptedRoot.value === draft.value.draftRootId)
      throw new Error('正式动作已提交，请核对任务同步结果')
    if (
      !adoption.value &&
      (draftChanged.value ||
        pendingRevision.value ||
        draft.value.status !== 'PENDING')
    )
      throw new Error('请先保存并核对当前草稿修订，再确认采用')
    if (!adoption.value) {
      if (draft.value.purpose === 'CREATE_TICKET')
        adoption.value = {
          kind: 'CREATE',
          form: {
            clientRequestId: newRequestId(),
            title: draftContent.value.title,
            symptom: draftContent.value.symptom,
            attemptedActions: draftContent.value.attemptedActions || null,
            serviceItemId: task.value.serviceItemId,
            deviceId: task.value.deviceId,
            supportContext: { ...task.value.supportContext },
            serviceAreaCode: null,
            serviceLocation: null,
            sourceDraftId: draft.value.id,
            sourceDraftRootId: draft.value.draftRootId,
            contentSha256: draft.value.contentSha256,
          },
        }
      else if (draft.value.purpose === 'BOOK_ONSITE') {
        if (!draft.value.ticketId || draft.value.ticketRowVersion === null)
          throw new Error('预约草稿缺少工单版本')
        adoption.value = {
          kind: 'BOOK',
          ticketId: draft.value.ticketId,
          form: {
            clientRequestId: newRequestId(),
            rowVersion: draft.value.ticketRowVersion,
            slotId: draftContent.value.slotId,
            sourceDraftId: draft.value.id,
            sourceDraftRootId: draft.value.draftRootId,
            contentSha256: draft.value.contentSha256,
          },
        }
      } else {
        if (!props.ticket || draft.value.ticketRowVersion === null)
          throw new Error('处理草稿缺少工单版本')
        const base = {
          clientActionId: newRequestId(),
          rowVersion: draft.value.ticketRowVersion,
          content: draftContent.value.content,
          sourceDraftId: draft.value.id,
          sourceDraftRootId: draft.value.draftRootId,
          contentSha256: draft.value.contentSha256,
        }
        adoption.value = {
          kind: 'ACTION',
          action:
            draft.value.purpose === 'ADD_RECORD'
              ? { name: 'comments', form: { ...base, visibility: 'PUBLIC' } }
              : draft.value.purpose === 'REQUEST_INFORMATION'
                ? {
                    name: 'request-information',
                    form: {
                      ...base,
                      followUpAt: draftContent.value.followUpAt,
                    },
                  }
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
      await syncAdoption()
      await router.push(`/tickets/${value.id}`)
    } else if (adoption.value.kind === 'BOOK') {
      const value = await bookOnsite(
        adoption.value.ticketId,
        adoption.value.form,
        await auth.token(),
      )
      adoption.value = null
      await syncAdoption()
      notice.value =
        value.status === 'CONFIRMED'
          ? '上门预约已确认。'
          : `预约未成功：${value.resultCode}，请重新选择时段。`
    } else {
      if (!props.ticket) return
      await act(
        props.ticket.id,
        adoption.value.action,
        await auth.token(),
        signal,
      )
      adoption.value = null
      await syncAdoption()
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
      await syncAdoption()
      await router.push(`/tickets/${value.id}`)
    } else if (adoption.value.kind === 'BOOK') {
      const value = await bookingReceipt(
        adoption.value.ticketId,
        adoption.value.form.clientRequestId,
        signal,
      )
      adoption.value = null
      await syncAdoption()
      notice.value = `已核对预约结果：${value.status}`
    } else if (props.ticket) {
      await actionReceipt(
        props.ticket.id,
        adoption.value.action.form.clientActionId,
        signal,
      )
      adoption.value = null
      await syncAdoption()
      notice.value = '已查到原工单动作回执。'
      emit('changed')
    }
  })
}
function rate(helpful: boolean) {
  void run(async () => {
    if (!current.value || !feedbackReady.value) return
    pendingFeedback.value ??= {
      runId: current.value.id,
      body: {
        rating: helpful ? 'HELPFUL' : 'UNHELPFUL',
        adoption: feedbackAdoption.value,
        issueCategory: helpful ? null : feedbackCategory.value,
        comment: feedbackComment.value || null,
        ...(feedback.value ? { rowVersion: feedback.value.rowVersion } : {}),
      },
    }
    const submission = pendingFeedback.value,
      version = selection
    try {
      const saved = await putFeedback(
        submission.runId,
        submission.body,
        await auth.token(),
      )
      if (current.value?.id !== submission.runId || version !== selection)
        return
      feedback.value = saved
      pendingFeedback.value = null
    } catch (failure) {
      if (
        current.value?.id === submission.runId &&
        version === selection &&
        failure instanceof ApiError &&
        failure.status >= 400 &&
        failure.status < 500
      ) {
        pendingFeedback.value = null
        if (failure.status === 409) feedbackReady.value = false
      }
      throw failure
    }
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
    if (pendingRevision.value || adoption.value || pendingCheck.value)
      throw new Error('请先查询未确认结果的原请求')
    if (!conversation.value) return
    await deleteConversation(
      conversation.value.id,
      conversation.value.rowVersion,
      await auth.token(),
    )
    stopSubscription()
    selection++
    conversation.value = null
    task.value = null
    tasks.value = []
    messages.value = []
    savedMessageId.value = null
    current.value = null
    facts.value = []
    turns.value = []
    clearDraftSelection()
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
    stopSubscription()
    selection++
    conversation.value = null
    task.value = null
    tasks.value = []
    messages.value = []
    savedMessageId.value = null
    current.value = null
    turns.value = []
    facts.value = []
    clearDraftSelection()
    pending.value = null
    pendingDraft.value = null
    pendingRevision.value = null
    adoption.value = null
    pendingCheck.value = false
    pendingCreate.value = null
    pendingTask.value = null
    pendingFact.value = null
    pendingContext.value = null
    pendingClose.value = null
  },
)

watch(selectedMode, () => {
  stopSubscription()
  selection++
  conversation.value = null
  task.value = null
  current.value = null
  tasks.value = []
  facts.value = []
  turns.value = []
  messages.value = []
  conversations.value = []
  savedMessageId.value = null
  clearDraftSelection()
  conversationPage.value = 1
  void run(loadList)
})

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
    <label v-if="!ticket"
      >会话用途<ElSelect v-model="selectedMode" :disabled="selectionLocked"
        ><ElOption
          value="CUSTOMER_SELF_SERVICE"
          label="办公 IT 自助排障" /><ElOption
          value="CONSULT"
          label="普通咨询（不访问业务资料）" /></ElSelect
    ></label>
    <div class="toolbar">
      <ElSelect
        :model-value="conversation?.id ?? ''"
        :disabled="selectionLocked"
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
      ><template v-if="!ticket && mode !== 'CONSULT'"
        ><label
          >支持项目（可留空待分类）<ElSelect v-model="serviceItemId" clearable
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
      ><label v-if="mode !== 'CONSULT'"
        >诊断目标<ElInput v-model="goal" maxlength="1000" /></label
      ><ElButton
        :disabled="
          busy || !!pending || !!adoption || !!pendingRevision || pendingCheck
        "
        @click="create"
        >{{ pendingCreate ? '按原请求完成创建' : '创建私有会话' }}</ElButton
      >
    </details>
    <template v-if="conversation"
      ><p>
        任务版本 {{ task?.taskStateVersion ?? '咨询无诊断任务' }} · 会话版本
        {{ conversation.rowVersion
        }}<template v-if="current?.ticketRowVersion">
          · 本轮工单写版本 {{ current.ticketRowVersion }} · 诊断环境版本
          {{ current.diagnosisContextVersion ?? '未记录' }}</template
        >
      </p>
      <ElInput
        v-model="newName"
        aria-label="会话新名称"
        maxlength="100"
        placeholder="会话新名称"
      /><ElButton :disabled="busy || !newName" @click="rename">改名</ElButton
      ><ElButton :disabled="selectionLocked" @click="remove"
        >删除本会话并结束活跃轮</ElButton
      >
      <section v-if="conversation.mode !== 'CONSULT'" class="panel">
        <h3>诊断任务</h3>
        <ElSelect
          :model-value="task?.id ?? ''"
          :disabled="selectionLocked || active"
          aria-label="诊断目标"
          @update:model-value="
            (id) => {
              const t = tasks.find((t) => t.id === id)
              if (t) selectTask(t)
            }
          "
          ><ElOption
            v-for="t in tasks"
            :key="t.id"
            :label="t.goal"
            :value="t.id"
        /></ElSelect>
        <ElButton :disabled="busy || taskPage === 1" @click="taskPageChange(-1)"
          >上一页任务</ElButton
        ><ElButton
          :disabled="busy || BigInt(taskPage * 10) >= BigInt(taskTotal)"
          @click="taskPageChange(1)"
          >下一页任务</ElButton
        >
        <p v-if="task">
          目标：{{ task.goal }} · 状态：{{ task.status }} · 环境版本
          {{ task.diagnosisContextVersion }}
        </p>
        <ul v-if="task">
          <li v-for="h in task.hypotheses" :key="h.key">
            待验证假设：{{ h.statement }}（{{ h.status }}）
          </li>
        </ul>
        <label
          >新目标<ElInput
            v-model="goal"
            maxlength="1000"
            :disabled="!!pendingTask" /></label
        ><ElButton
          :disabled="
            busy ||
            active ||
            !!pending ||
            !!adoption ||
            !!pendingRevision ||
            pendingCheck ||
            !!pendingFact ||
            !!pendingContext ||
            !!pendingClose
          "
          @click="newTask"
          >{{ pendingTask ? '核对并重试原任务创建' : '新建诊断任务' }}</ElButton
        >
        <template v-if="task"
          ><label
            >实际结果或结束原因<ElInput
              v-model="closeReason"
              maxlength="2000"
              :disabled="!!pendingClose"
          /></label>
          <ElButton
            :disabled="busy || active"
            @click="finishTask('report-outcome')"
            >本人报告已恢复</ElButton
          ><ElButton :disabled="busy || active" @click="finishTask('close')"
            >结束诊断</ElButton
          ><ElButton :disabled="busy || active" @click="finishTask('cancel')"
            >取消任务</ElButton
          >
          <p v-if="task.outcomeNote">{{ task.outcomeNote }}</p>
        </template>
      </section>
      <TaskChecks
        v-if="task"
        :key="task.id"
        :task="task"
        :active="active"
        @saved="feedbackSaved"
        @resume="savedMessageId = $event"
        @pending="pendingCheck = $event"
      />
      <p v-if="savedMessageId">
        已选择持久保存的反馈。<ElButton
          :disabled="busy || active"
          @click="send(true)"
          >继续诊断</ElButton
        >
      </p>
      <details>
        <summary>完整聊天记录（含已保存反馈）</summary>
        <p v-for="message in messages" :key="message.id" class="preserve">
          {{
            message.role === 'USER'
              ? '我'
              : message.role === 'ASSISTANT'
                ? '助手'
                : '系统'
          }}：{{ message.content ?? '来源已失效，正文已隐藏' }}
        </p>
        <ElButton
          v-if="messagesMore"
          :disabled="busy"
          @click="run(() => loadMessages(true))"
          >继续读取消息</ElButton
        >
      </details>
      <form @submit.prevent="send()">
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
          v-if="current.stale && !current.sourcesHidden"
          title="诊断环境已变化或旧运行缺少环境版本。以下内容仅供历史审阅，请核对当前环境后显式开始新一轮。"
          type="warning"
          :closable="false"
        />
        <ElAlert
          v-if="current.sourcesHidden"
          title="当前来源或上下文已不可用，相关正文、引用和草稿已隐藏。"
          type="warning"
          :closable="false"
        />
        <p v-if="current.answer" class="preserve">{{ current.answer }}</p>
        <ElAlert
          v-if="failureExplanation"
          :title="failureExplanation"
          type="warning"
          :closable="false"
        />
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
          v-if="
            current.status === 'SUCCEEDED' &&
            !current.sourcesHidden &&
            !current.stale
          "
          ><template v-if="ticket && task"
            ><ElButton
              :disabled="busy || !current.structuredResult?.proposedRecord"
              @click="makeDraft('ADD_RECORD')"
              >形成处理记录草稿</ElButton
            ><ElButton
              :disabled="busy || !current.structuredResult?.proposedResolution"
              @click="makeDraft('RESOLVE_TICKET')"
              >形成解决建议草稿</ElButton
            ><label
              >下次跟进时间<ElInput
                v-model="draftFollowUp"
                type="datetime-local"
                :disabled="busy || !!pendingDraft" /></label
            ><ElButton
              :disabled="
                busy ||
                !draftFollowUp ||
                !current.structuredResult?.checks.some(
                  (item) => item.checkKind === 'CLARIFICATION',
                )
              "
              @click="makeDraft('REQUEST_INFORMATION')"
              >形成客户补充请求草稿</ElButton
            ></template
          ><ElButton v-if="pendingDraft" :disabled="busy" @click="recoverDraft"
            >核对原草稿</ElButton
          ></template
        >
        <section v-if="!active">
          <h4>本人的运行评价</h4>
          <p>评价仅本人可见；采纳情况请按实际填写。</p>
          <ElButton :disabled="busy" @click="run(readFeedback)"
            >重新读取已保存评价</ElButton
          >
          <label
            >采纳情况<ElSelect
              v-model="feedbackAdoption"
              :disabled="!feedbackReady || !!pendingFeedback"
            >
              <ElOption value="NOT_USED" label="尚未使用" />
              <ElOption value="ACCEPTED" label="直接采纳" />
              <ElOption value="MODIFIED" label="修改后采纳" />
              <ElOption value="REJECTED" label="未采纳" /> </ElSelect
          ></label>
          <label
            >无帮助原因<ElSelect
              v-model="feedbackCategory"
              :disabled="!feedbackReady || !!pendingFeedback"
            >
              <ElOption value="INCORRECT" label="内容错误" />
              <ElOption value="IRRELEVANT" label="与问题无关" />
              <ElOption value="MISSING_CONTEXT" label="缺少上下文" />
              <ElOption value="UNSUPPORTED" label="缺少证据支持" />
              <ElOption value="OTHER" label="其他" /> </ElSelect
          ></label>
          <label
            >反馈说明<ElInput
              v-model="feedbackComment"
              maxlength="2000"
              :disabled="!feedbackReady || !!pendingFeedback"
          /></label>
          <ElButton
            v-if="pendingFeedback"
            :disabled="busy"
            @click="rate(pendingFeedback.body.rating === 'HELPFUL')"
            >按原评价重试</ElButton
          >
          <template v-else>
            <ElButton :disabled="busy || !feedbackReady" @click="rate(true)"
              >有帮助</ElButton
            >
            <ElButton :disabled="busy || !feedbackReady" @click="rate(false)"
              >无帮助</ElButton
            >
          </template>
          <p v-if="feedback">
            已保存：{{ feedback.rating === 'HELPFUL' ? '有帮助' : '无帮助' }} ·
            版本 {{ feedback.rowVersion }}
          </p>
        </section>
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
      <section v-if="task" class="panel">
        <section
          v-if="
            !ticket && task.status === 'HANDED_OFF' && task.handoverTicketId
          "
        >
          <h3>为已交接工单预约上门</h3>
          <p>工单须已受理并填写服务地点，确认时重新核验时段和容量。</p>
          <ElButton :disabled="busy" @click="readSlots()"
            >读取可预约时段</ElButton
          >
          <ElSelect
            v-model="selectedSlot"
            :disabled="busy || !!pendingDraft"
            placeholder="选择上门时段"
          >
            <ElOption
              v-for="slot in availableSlots"
              :key="slot.id"
              :value="slot.id"
              :label="`${slot.serviceAreaCode} · ${new Date(slot.startsAt).toLocaleString()} · 余量 ${slot.capacity - slot.reservedCount}`"
            />
          </ElSelect>
          <ElButton
            :disabled="busy || slotPage === 1"
            @click="readSlots(slotPage - 1)"
            >上一页时段</ElButton
          >
          <ElButton
            :disabled="busy || BigInt(slotPage * 10) >= BigInt(slotTotal)"
            @click="readSlots(slotPage + 1)"
            >下一页时段</ElButton
          >
          <ElButton
            :disabled="busy || !selectedSlot"
            @click="makeDraft('BOOK_ONSITE')"
            >形成预约草稿</ElButton
          >
        </section>
        <h3>诊断事实与本人确认</h3>
        <ElButton
          v-if="!ticket"
          :disabled="
            busy ||
            active ||
            confirmed.length === 0 ||
            task.status === 'HANDED_OFF'
          "
          @click="makeDraft('CREATE_TICKET')"
          >用本人确认事实形成建单草稿</ElButton
        >
        <ElButton v-if="pendingDraft" :disabled="busy" @click="recoverDraft"
          >核对原草稿</ElButton
        >
        <ul>
          <li v-for="value in facts" :key="value.id">
            <ElTag>{{ sourceNames[value.sourceType] }}</ElTag>
            {{ factKindNames[value.factKind] }} ·
            {{
              { ASSERT: '记录', CORRECT: '更正', RETRACT: '撤回' }[
                value.operation
              ]
            }}
            <span v-if="!value.active">已被更正或撤回</span>
            <span v-if="value.historicalEnvironment">旧环境待复核</span>
            <p class="preserve">
              {{ value.statement ?? '来源已失效，正文隐藏' }}
            </p>
            <ElTag
              v-for="condition in value.reportedConditions"
              :key="condition"
              type="warning"
            >
              {{ safetyConditionNames[condition] }}
            </ElTag>
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
            value="ACTION_REPORT"
            label="本人明确报告已执行" /></ElSelect
        ><ElSelect
          v-if="factAction !== 'ASSERT'"
          v-model="factId"
          :disabled="active"
          placeholder="选择同会话前驱事实"
          ><ElOption
            v-for="value in facts.filter(
              (value) => value.active && value.factKind === factKind,
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
        />
        <label v-if="factKind === 'OBSERVATION' && factAction !== 'RETRACT'">
          明确需要停止排查的情况（仅勾选实际遇到的情况）
          <ElSelect
            v-model="reportedConditions"
            multiple
            :disabled="active || !!pendingFact"
          >
            <ElOption
              v-for="(label, code) in safetyConditionNames"
              :key="code"
              :value="code"
              :label="label"
            />
          </ElSelect>
        </label>
        <ElButton :disabled="busy || active || !statement" @click="recordFact()"
          >按事实版本追加记录</ElButton
        >
        <template v-if="!ticket && mode !== 'CONSULT'">
          <label v-if="!task.serviceItemId"
            >确认支持项目
            <ElSelect
              v-model="serviceItemId"
              clearable
              :disabled="active || !!pendingContext"
            >
              <ElOption
                v-for="row in services"
                :key="row.item.id"
                :label="row.item.serviceName"
                :value="row.item.id"
              />
            </ElSelect>
          </label>
          <SupportContextFields v-model="context" :disabled="active" /><ElButton
            :disabled="busy || active"
            @click="recordFact(true)"
            >明确更新问题环境</ElButton
          ></template
        >
        <p v-else>
          技术会话的项目、设备和环境以当前工单为准，请通过工单动作修改。
        </p>
      </section>
      <section v-if="draft" class="panel">
        <h3>
          待人工确认草稿 · 修订 {{ draft.revisionNumber }} · {{ draft.status }}
        </h3>
        <ElButton
          :disabled="
            busy ||
            !!adoption ||
            draft.status !== 'PENDING' ||
            (!draftChanged && !pendingRevision)
          "
          @click="saveDraftRevision"
          >{{
            pendingRevision ? '按原修订请求重试' : '保存编辑为新修订'
          }}</ElButton
        >
        <ElButton
          v-if="pendingRevision"
          :disabled="busy"
          @click="recoverRevision"
          >查询原修订请求</ElButton
        >
        <ElButton
          :disabled="
            busy ||
            !!adoption ||
            !!pendingRevision ||
            draft.status !== 'PENDING'
          "
          @click="withdrawCurrentDraft"
          >撤回当前修订</ElButton
        >
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
              :disabled="
                !!adoption || !!pendingRevision || draft?.status !== 'PENDING'
              " /></label
          ><label
            >确认现象<ElInput
              v-model="draftContent.symptom"
              type="textarea"
              :disabled="
                !!adoption || !!pendingRevision || draft?.status !== 'PENDING'
              " /></label
          ><label
            >本人明确报告的已执行操作<ElInput
              v-model="draftContent.attemptedActions"
              type="textarea"
              :disabled="
                !!adoption || !!pendingRevision || draft?.status !== 'PENDING'
              " /></label></template
        ><template v-else-if="draft.purpose === 'BOOK_ONSITE'">
          <p>
            工单：{{ draft.ticketId }}；时段：{{
              draft.content.slotId
            }}。确认后以实际预约回执为准。
          </p> </template
        ><template v-else
          ><label
            >人工编辑后的记录或结果<ElInput
              v-model="draftContent.content"
              type="textarea"
              :rows="5"
              :disabled="
                !!adoption || !!pendingRevision || draft?.status !== 'PENDING'
              " /></label
          ><label v-if="draft.purpose === 'REQUEST_INFORMATION'"
            >下次跟进时间（含时区）
            <ElInput
              v-model="draftContent.followUpAt"
              :disabled="
                !!adoption || !!pendingRevision || draft.status !== 'PENDING'
              "
            /> </label
          ><template v-if="draft.purpose === 'RESOLVE_TICKET'"
            ><ElSelect
              v-model="draftContent.resolutionType"
              :disabled="
                !!adoption || !!pendingRevision || draft?.status !== 'PENDING'
              "
              ><ElOption value="RESTORED" label="人工报告恢复" /><ElOption
                value="GUIDANCE"
                label="指导交付" /><ElOption
                value="REFERRED"
                label="转介" /></ElSelect
            ><ElInput
              v-model="draftContent.referralTarget"
              :disabled="
                !!adoption || !!pendingRevision || draft?.status !== 'PENDING'
              "
              placeholder="转介去向（转介必填）" /></template></template
        ><ElAlert
          v-if="ticket && draft.ticketRowVersion !== ticket.rowVersion"
          title="工单版本已改变，请核对新上下文；不会自动套用新版本或重新生成。"
          type="warning"
          :closable="false"
        /><ElButton
          :disabled="
            busy ||
            !!pendingSync ||
            adoptedRoot === draft.draftRootId ||
            (!!ticket && draft.ticketRowVersion !== ticket.rowVersion)
          "
          @click="adopt"
          >{{
            adoption ? '沿原工单动作键重试' : '确认当前已保存修订，提交正式动作'
          }}</ElButton
        ><ElButton v-if="adoption" :disabled="busy" @click="recoverAdoption"
          >核对原工单动作回执</ElButton
        >
        <ElButton :disabled="busy" @click="run(syncAdoption)"
          >核对正式回执并同步任务</ElButton
        >
        <p v-if="pendingSync">
          正式动作已提交，任务同步待核对；请勿重复创建动作。
        </p>
        <p v-if="adoptedRoot === draft.draftRootId">已核对正式回执。</p>
      </section>
    </template>
  </section>
</template>
