import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createDraft,
  draftReceipt,
  type Draft,
} from '../../../src/api/diagnosis/draftApi'
import type { DiagnosisTask } from '../../../src/api/diagnosis/taskApi'
import { blankContext } from '../../../src/api/support/types'
import { useActionDraft } from '../../../src/views/diagnosis/useActionDraft'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('../../../src/api/diagnosis/draftApi', () => ({
  createDraft: vi.fn(),
  draftReceipt: vi.fn(),
}))

const id = 'a'.repeat(32)
const task: DiagnosisTask = {
  id,
  conversationId: id,
  serviceItemId: id,
  deviceId: null,
  goal: '检查网络',
  status: 'ACTIVE',
  taskStateVersion: '2',
  diagnosisContextVersion: '1',
  rowVersion: '1',
  supportContext: blankContext(),
  sourcesHidden: false,
  hypotheses: [],
  outcomeNote: null,
  handoverTicketId: null,
}
const revision: Draft = {
  id,
  draftRootId: id,
  revisionNumber: 1,
  contentSha256: 'a'.repeat(64),
  status: 'PENDING',
  sourceRunId: null,
  purpose: 'CREATE_TICKET',
  ticketId: null,
  ticketRowVersion: null,
  taskId: id,
  taskStateVersion: '2',
  diagnosisContextVersion: '1',
  expiresAt: '2026-11-18T00:00:00Z',
  content: {
    title: '网络断开',
    symptom: '已确认网络断开',
    attemptedActions: '',
    content: '',
    resolutionType: null,
    referralTarget: null,
  },
}

/** 记录当前请求完成时间，并让异常保留给原键恢复场景。 */
function setup() {
  let completed = Promise.resolve()
  const failure = vi.fn()
  const scope = useActionDraft({
    task: ref(task),
    current: ref(null),
    confirmed: ref([]),
    pendingCheck: ref(false),
    notice: ref(''),
    ticket: ref(undefined),
    hasConversation: () => true,
    selection: () => 0,
    signal: new AbortController().signal,
    run(action) {
      completed = action().catch(failure)
      return completed
    },
    token: async () => 'csrf',
    reload: async () => {},
    changed: vi.fn(),
  })
  return { ...scope, completed: () => completed, failure }
}

/** 未知提交沿原键核对，切换工单或身份后隔离迟到结果。 @author yangaobo */
describe('诊断草稿操作', () => {
  beforeEach(() => vi.resetAllMocks())

  it('创建回执未知时只查询原键', async () => {
    const flow = setup()
    vi.mocked(createDraft).mockRejectedValueOnce(new Error('响应中断'))
    vi.mocked(draftReceipt).mockResolvedValueOnce(revision)

    flow.makeDraft('CREATE_TICKET')
    await flow.completed()
    const original = vi.mocked(createDraft).mock.calls[0]![1].clientRequestId
    expect(flow.pendingDraft.value?.clientRequestId).toBe(original)

    flow.recoverDraft()
    await flow.completed()
    expect(createDraft).toHaveBeenCalledTimes(1)
    expect(draftReceipt).toHaveBeenCalledExactlyOnceWith(
      original,
      expect.any(AbortSignal),
    )
    expect(flow.draft.value?.id).toBe(revision.id)
    expect(flow.pendingDraft.value).toBeNull()
  })

  it('作用域重置后丢弃迟到草稿回执', async () => {
    const flow = setup()
    let reply!: (value: Draft) => void
    vi.mocked(createDraft).mockReturnValueOnce(
      new Promise((resolve) => {
        reply = resolve
      }),
    )
    flow.makeDraft('CREATE_TICKET')
    await vi.waitFor(() => expect(createDraft).toHaveBeenCalledTimes(1))

    flow.reset()
    reply(revision)
    await flow.completed()
    expect(flow.draft.value).toBeNull()
    expect(flow.pendingDraft.value).toBeNull()
  })
})
