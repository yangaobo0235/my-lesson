import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  answerCheck,
  check,
  createTask,
  listTaskEvents,
  taskReceipt,
} from '../../src/api/diagnosis/taskApi'
import { conversation } from '../../src/api/diagnosis/conversationApi'
import { createRun } from '../../src/api/diagnosis/runApi'
import { draft } from '../../src/api/diagnosis/draftApi'

/** 固定回执与严格值域防止保存反馈时隐式生成新运行。 @author yangaobo */
const id = 'a'.repeat(32),
  taskId = 'b'.repeat(32),
  messageId = 'c'.repeat(32)
const receipt = {
  taskId,
  eventId: id,
  taskStateVersion: '2',
  status: 'ACTIVE',
  inputMessageId: messageId,
  factId: null,
  checkId: id,
}
afterEach(() => vi.unstubAllGlobals())
function respond(data: unknown) {
  const fetch = vi
    .fn()
    .mockResolvedValue(
      new Response(
        JSON.stringify({ code: 0, message: '成功', traceId: 'trace', data }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )
  vi.stubGlobal('fetch', fetch)
  return fetch
}
describe('任务与运行独立合同', () => {
  it('任务事件使用版本游标并尊重服务端来源隐藏标记', async () => {
    const fetch = respond({
      records: [
        {
          id,
          stateVersion: '17',
          actorType: 'WORKER',
          eventType: 'RUN_COMMITTED',
          details: null,
          sourcesHidden: true,
          createdAt: '2026-11-16T00:00:00Z',
        },
      ],
      nextCursor: '17',
      hasMore: false,
    })
    const page = await listTaskEvents(taskId, '16')
    expect(fetch.mock.calls[0]![0]).toContain(
      `/tasks/${taskId}/events?afterStateVersion=16`,
    )
    expect(page.records[0]).toMatchObject({ reason: null, sourcesHidden: true })
    expect(page.nextCursor).toBe('17')
  })
  it('咨询不要求诊断项目或事实版本', () => {
    expect(
      conversation({
        id,
        title: '咨询',
        mode: 'CONSULT',
        customerId: null,
        ticketId: null,
        rowVersion: '0',
      }).mode,
    ).toBe('CONSULT')
  })
  it('创建任务使用原动作键并返回独立消息回执', async () => {
    const fetch = respond(receipt)
    expect(
      (await createTask(id, { clientActionId: id, goal: '检查网络' }, 'csrf'))
        .inputMessageId,
    ).toBe(messageId)
    expect(fetch.mock.calls[0]![0]).toContain(`/conversations/${id}/tasks`)
  })
  it.each([false, 0])(
    '保存答案 %s 不变为空值，也不自动请求运行',
    async (value) => {
      const fetch = respond(receipt)
      const form = {
        clientActionId: id,
        taskStateVersion: '1',
        answer: { value, executionStatus: null, outcome: null },
      }
      expect(await answerCheck(taskId, id, form, 'csrf')).toEqual(receipt)
      expect(fetch).toHaveBeenCalledTimes(1)
      expect(fetch.mock.calls[0]![0]).toContain(
        `/tasks/${taskId}/checks/${id}/answer`,
      )
      expect(JSON.parse(fetch.mock.calls[0]![1].body).answer.value).toBe(value)
    },
  )
  it('显式续行只提交已保存消息身份，不重复发送文字', async () => {
    const fetch = respond({
      id,
      conversationId: id,
      taskId,
      inputMessageId: messageId,
      executionPhase: 'CONTEXT',
      iterationNumber: 0,
      usageUnknown: false,
      question: '已保存反馈',
      status: 'QUEUED',
      answerType: null,
      answer: null,
      structuredResult: null,
      inputStateVersion: '2',
      outputStateVersion: null,
      ticketRowVersion: null,
      sourcesHidden: false,
      stale: false,
      diagnosisContextVersion: '0',
      currentDiagnosisContextVersion: '0',
      citations: [],
      inputTokens: null,
      outputTokens: null,
      errorCode: null,
    })
    await createRun(
      id,
      {
        clientRequestId: id,
        taskId,
        taskStateVersion: '2',
        inputMessageId: messageId,
      },
      'csrf',
    )
    const body = JSON.parse(fetch.mock.calls[0]![1].body)
    expect(body.inputMessageId).toBe(messageId)
    expect(body).not.toHaveProperty('message')
    expect(fetch.mock.calls[0]![0]).toContain(`/conversations/${id}/runs`)
  })
  it('已隐藏检查保留安全状态，不虚构默认答案', () => {
    expect(
      check({
        id,
        proposedRunId: id,
        checkKey: 'network',
        checkKind: 'CHECK',
        attemptNumber: 1,
        instruction: null,
        status: 'ANSWERED',
        contextVersion: '0',
        rowVersion: '1',
        executorType: 'CUSTOMER',
        answerSchema: null,
        normalizedAnswer: null,
        answerMessageId: messageId,
        previousCheckId: null,
        sourcesHidden: true,
        expiresAt: null,
      }).normalizedAnswer,
    ).toBeNull()
  })
  it('事实模板建单草稿无需成功运行，但必须有任务版本', () => {
    const result = draft({
      id,
      draftRootId: id,
      revisionNumber: 1,
      contentSha256: 'd'.repeat(64),
      status: 'PENDING',
      sourceRunId: null,
      purpose: 'CREATE_TICKET',
      ticketId: null,
      ticketRowVersion: null,
      taskId,
      taskStateVersion: '2',
      diagnosisContextVersion: '0',
      expiresAt: '2026-11-16T23:00:00Z',
      content: {
        title: '网络故障',
        symptom: '无法联网',
        attemptedActions: '',
        content: '',
        resolutionType: null,
        referralTarget: null,
      },
    })
    expect(result.sourceRunId).toBeNull()
    expect(result.taskStateVersion).toBe('2')
  })
  it('非法回执不作为已保存反馈接受', () => {
    expect(() =>
      taskReceipt({ ...receipt, inputMessageId: undefined }),
    ).toThrow()
    expect(() => taskReceipt({ ...receipt, taskStateVersion: 2 })).toThrow()
  })
})
