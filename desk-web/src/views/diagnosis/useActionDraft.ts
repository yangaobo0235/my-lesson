import { computed, ref, type Ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  createDraft,
  draftReceipt,
  reviseDraft,
  synchronizeDraft,
  withdrawDraft,
  type Draft,
  type DraftPurpose,
} from '../../api/diagnosis/draftApi'
import { getTask, type DiagnosisTask } from '../../api/diagnosis/taskApi'
import type { Run } from '../../api/diagnosis/runApi'
import { ApiError, newRequestId } from '../../api/http'
import {
  bookOnsite,
  bookingReceipt,
  ticketSlots,
  type OnsiteSlot,
} from '../../api/onsite/onsiteApi'
import { RESULT_CODES } from '../../api/resultCodes'
import {
  act,
  actionReceipt,
  createTicket,
  creationReceipt,
} from '../../api/ticket/ticketApi'
import type {
  Ticket,
  TicketAction,
  TicketCreateForm,
} from '../../api/ticket/types'

/** 人工确认时冻结目标、版本和原请求键，供提交或回执核对复用。 */
type PendingAdoption =
  | { kind: 'CREATE'; form: TicketCreateForm }
  | { kind: 'ACTION'; ticketId: string; action: TicketAction }
  | { kind: 'BOOK'; ticketId: string; form: Parameters<typeof bookOnsite>[1] }

/** 编辑草稿修订、确认正式动作及沿原键核对；使用诊断面板的请求作用域。 @author yangaobo */
export function useActionDraft(options: {
  task: Ref<DiagnosisTask | null>
  current: Readonly<Ref<Run | null>>
  confirmed: Readonly<Ref<string[]>>
  pendingCheck: Readonly<Ref<boolean>>
  notice: Ref<string>
  ticket: Readonly<Ref<Ticket | undefined>>
  hasConversation: () => boolean
  selection: () => number
  signal: AbortSignal
  run: (action: () => Promise<void>) => Promise<void>
  token: () => Promise<string>
  reload: () => Promise<void>
  changed: () => void
}) {
  const { task, current, confirmed, pendingCheck, notice, signal, run } =
    options
  const router = useRouter()
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
  const adoption = ref<PendingAdoption | null>(null)

  /** 旧任务的迟到结果不能更新新诊断面板。 */
  function selectionGuard() {
    const version = options.selection()
    const taskId = task.value?.id
    return () =>
      !signal.aborted &&
      version === options.selection() &&
      taskId === task.value?.id
  }

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
      if (!draft.value || adoption.value || pendingSync.value) return
      pendingRevision.value ??= {
        id: draft.value.id,
        body: {
          clientRequestId: newRequestId(),
          contentSha256: draft.value.contentSha256,
          content: editedContent(),
        },
      }
      const request = pendingRevision.value
      const csrf = await options.token()
      if (request !== pendingRevision.value || signal.aborted) return
      const value = await reviseDraft(request.id, request.body, csrf)
      if (request !== pendingRevision.value || signal.aborted) return
      pendingRevision.value = null
      showDraft(value)
    })
  }
  function recoverRevision() {
    void run(async () => {
      const request = pendingRevision.value
      if (!request) return
      const value = await draftReceipt(request.body.clientRequestId, signal)
      if (request !== pendingRevision.value || signal.aborted) return
      pendingRevision.value = null
      showDraft(value)
    })
  }
  function withdrawCurrentDraft() {
    void run(async () => {
      const selected = draft.value
      if (
        !selected ||
        adoption.value ||
        pendingRevision.value ||
        pendingSync.value
      )
        return
      const csrf = await options.token()
      if (draft.value !== selected || signal.aborted) return
      const value = await withdrawDraft(
        selected.id,
        selected.contentSha256,
        csrf,
      )
      if (draft.value !== selected || signal.aborted) return
      showDraft(value)
      notice.value =
        '已停止新的草稿校验；已在途的工单请求仍需查询正式动作回执。'
    })
  }

  /** 任务或会话切换后清理已展示的选择，不丢弃任何未决提交身份。 */
  function clearSelection() {
    draft.value = null
    adoptedRoot.value = null
    selectedSlot.value = ''
    draftFollowUp.value = ''
    availableSlots.value = []
    slotPage.value = 1
    slotTotal.value = '0'
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
      if (
        pendingRevision.value ||
        adoption.value ||
        pendingSync.value ||
        pendingCheck.value
      )
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
      const request = pendingDraft.value
      const { taskId, ...form } = request
      const csrf = await options.token()
      if (request !== pendingDraft.value || signal.aborted) return
      const value = await createDraft(taskId, form, csrf)
      if (request !== pendingDraft.value || signal.aborted) return
      showDraft(value)
      pendingDraft.value = null
    })
  }

  /** 预约时段按已交接工单分页读取，选择不会预占容量。 */
  function readSlots(page = 1) {
    void run(async () => {
      if (!task.value?.handoverTicketId) return
      const taskId = task.value.id,
        ticketId = task.value.handoverTicketId,
        version = options.selection()
      const value = await ticketSlots(ticketId, page, signal)
      if (
        signal.aborted ||
        version !== options.selection() ||
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
    const selected = draft.value
    const currentTask = task.value
    if (!selected || !currentTask) return
    const stillSelected = selectionGuard()
    pendingSync.value ??= {
      id: selected.id,
      body: {
        clientActionId: newRequestId(),
        taskStateVersion: currentTask.taskStateVersion,
      },
    }
    const request = pendingSync.value
    try {
      const csrf = await options.token()
      if (!stillSelected()) return
      await synchronizeDraft(request.id, request.body, csrf)
    } catch (failure) {
      if (!stillSelected()) return
      if (
        failure instanceof ApiError &&
        failure.code === RESULT_CODES.CONFLICT
      ) {
        const latest = await getTask(currentTask.id, signal)
        if (!stillSelected()) return
        task.value = latest
        pendingSync.value = {
          id: selected.id,
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
    if (!stillSelected()) return
    adoptedRoot.value = selected.draftRootId
    pendingSync.value = null
    await options.reload()
  }
  function recoverDraft() {
    void run(async () => {
      const request = pendingDraft.value
      if (!request) return
      const value = await draftReceipt(request.clientRequestId, signal)
      if (request !== pendingDraft.value || signal.aborted) return
      showDraft(value)
      pendingDraft.value = null
    })
  }
  function adopt() {
    void run(async () => {
      if (!draft.value || !options.hasConversation() || !task.value) return
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
          if (!options.ticket.value || draft.value.ticketRowVersion === null)
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
            ticketId: options.ticket.value.id,
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
                        referralTarget:
                          draftContent.value.referralTarget || null,
                      },
                    },
          }
        }
      }
      await submitAdoption(false)
    })
  }

  /** 已有原键时选择提交或查询；正式回执确认后统一同步诊断任务。 */
  async function submitAdoption(checkReceipt: boolean) {
    const request = adoption.value
    if (!request) return
    const stillSelected = selectionGuard()
    const csrf = checkReceipt ? '' : await options.token()
    if (!stillSelected()) return

    let afterSync: () => void | Promise<void>
    if (request.kind === 'CREATE') {
      const result = checkReceipt
        ? await creationReceipt(request.form.clientRequestId, signal)
        : await createTicket(request.form, csrf, signal)
      afterSync = async () => {
        await router.push(`/tickets/${result.id}`)
      }
    } else if (request.kind === 'BOOK') {
      const result = checkReceipt
        ? await bookingReceipt(
            request.ticketId,
            request.form.clientRequestId,
            signal,
          )
        : await bookOnsite(request.ticketId, request.form, csrf)
      afterSync = () => {
        notice.value = checkReceipt
          ? `已核对预约结果：${result.status}`
          : result.status === 'CONFIRMED'
            ? '上门预约已确认。'
            : `预约未成功：${result.resultCode}，请重新选择时段。`
      }
    } else {
      if (checkReceipt) {
        await actionReceipt(
          request.ticketId,
          request.action.form.clientActionId,
          signal,
        )
      } else {
        await act(request.ticketId, request.action, csrf, signal)
      }
      afterSync = () => {
        notice.value = checkReceipt
          ? '已查到原工单动作回执。'
          : '人工确认动作已提交，请以工单时间线为准。'
        options.changed()
      }
    }

    if (!stillSelected()) return
    adoption.value = null
    await syncAdoption()
    if (stillSelected()) await afterSync()
  }

  function recoverAdoption() {
    void run(() => submitAdoption(true))
  }

  /** 所属工单或身份改变时释放原作用域的草稿与待核对请求。 */
  function reset() {
    clearSelection()
    pendingDraft.value = null
    pendingRevision.value = null
    pendingSync.value = null
    adoption.value = null
  }

  return {
    draft,
    draftContent,
    draftChanged,
    draftFollowUp,
    selectedSlot,
    availableSlots,
    slotPage,
    slotTotal,
    pendingSync,
    adoptedRoot,
    pendingDraft,
    pendingRevision,
    adoption,
    clearSelection,
    reset,
    saveDraftRevision,
    recoverRevision,
    withdrawCurrentDraft,
    makeDraft,
    readSlots,
    syncAdoption,
    recoverDraft,
    adopt,
    recoverAdoption,
  }
}
