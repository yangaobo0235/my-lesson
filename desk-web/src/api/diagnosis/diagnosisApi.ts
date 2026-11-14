import { ApiError, request, query } from '../http'
import {
  array,
  bool,
  choices,
  decimal,
  identifier,
  integer,
  nullable,
  page,
  record,
  text,
} from '../validation'
import {
  affectedScope,
  connectionType,
  type SupportContextForm,
} from '../support/types'

/** AI合同在边界解码，私有正文不写浏览器持久存储。 @author yangaobo */
const mode = choices('CUSTOMER_SELF_SERVICE', 'TECHNICIAN_ASSIST')
const runStatus = choices(
  'QUEUED',
  'RUNNING',
  'SUCCEEDED',
  'FAILED',
  'CANCELLED',
  'INTERRUPTED',
)
export function supportContext(value: unknown): SupportContextForm {
  const o = record(value)
  return {
    osName: nullable(text)(o.osName),
    osVersion: nullable(text)(o.osVersion),
    softwareName: nullable(text)(o.softwareName),
    softwareVersion: nullable(text)(o.softwareVersion),
    connectionType: connectionType(o.connectionType),
    affectedScope: affectedScope(o.affectedScope),
    errorCode: nullable(text)(o.errorCode),
  }
}
export function conversation(value: unknown) {
  const o = record(value)
  return {
    id: identifier(o.id),
    title: text(o.title),
    mode: mode(o.mode),
    customerId: identifier(o.customerId),
    serviceItemId: nullable(identifier)(o.serviceItemId),
    deviceId: nullable(identifier)(o.deviceId),
    ticketId: nullable(identifier)(o.ticketId),
    supportContext: supportContext(o.supportContext),
    stateVersion: decimal(o.stateVersion),
    rowVersion: decimal(o.rowVersion),
  }
}
function location(value: unknown): string {
  const o = record(value)
  switch (o.kind) {
    case 'PDF':
      return array((item) => {
        const p = record(item)
        return `物理第 ${integer(p.page_number)} 页`
      })(o.pages).join('、')
    case 'MARKDOWN':
      return array((item) => {
        const p = record(item)
        return `#${text(p.anchor)} · 第 ${integer(p.line_start)}–${integer(p.line_end)} 行`
      })(o.segments).join('、')
    case 'TICKET_EVENT':
      return `工单事件 ${integer(o.sequence)}`
    case 'DIAGNOSTIC_FACT':
      return `诊断事实 ${identifier(o.fact_id)}`
    default:
      throw new Error('来源定位格式不匹配')
  }
}
export function citation(value: unknown) {
  const o = record(value)
  return {
    number: integer(o.number),
    sourceUsageId: identifier(o.sourceUsageId),
    title: text(o.title),
    location: location(o.locator),
    excerpt: text(o.excerpt),
    documentId: nullable(identifier)(o.documentId),
  }
}
function structured(value: unknown) {
  const o = record(value)
  return {
    openQuestions: array(text)(o.openQuestions),
    hypotheses: array(text)(o.hypotheses),
    proposedRecord: nullable(text)(o.proposedRecord),
    proposedResolution: nullable(text)(o.proposedResolution),
    resolutionType: nullable(choices('RESTORED', 'GUIDANCE', 'REFERRED'))(
      o.resolutionType,
    ),
    referralTarget: nullable(text)(o.referralTarget),
  }
}
export function generation(value: unknown) {
  const o = record(value)
  return {
    id: identifier(o.id),
    conversationId: identifier(o.conversationId),
    question: text(o.question),
    status: runStatus(o.status),
    answerType: nullable(
      choices('ANSWERED', 'CLARIFICATION', 'INSUFFICIENT_EVIDENCE'),
    )(o.answerType),
    answer: nullable(text)(o.answer),
    structuredResult: nullable(structured)(o.structuredResult),
    inputStateVersion: decimal(o.inputStateVersion),
    outputStateVersion: nullable(decimal)(o.outputStateVersion),
    ticketVersion: nullable(decimal)(o.ticketVersion),
    sourcesHidden: bool(o.sourcesHidden),
    citations: array(citation)(o.citations),
    inputTokens: nullable(decimal)(o.inputTokens),
    outputTokens: nullable(decimal)(o.outputTokens),
    errorCode: nullable(text)(o.errorCode),
  }
}
export function fact(value: unknown) {
  const o = record(value)
  return {
    id: identifier(o.id),
    stateVersion: decimal(o.stateVersion),
    factKind: choices(
      'OBSERVATION',
      'PERFORMED_ACTION',
      'OPEN_QUESTION',
      'HYPOTHESIS',
    )(o.factKind),
    operation: choices('ASSERT', 'RETRACT')(o.operation),
    active: bool(o.active),
    statement: nullable(text)(o.statement),
    sourceType: choices(
      'USER_REPORT',
      'STAFF_RECORD',
      'TOOL_OBSERVATION',
      'ASSISTANT_PROPOSAL',
    )(o.sourceType),
    supersedesFactId: nullable(identifier)(o.supersedesFactId),
    sourcesHidden: bool(o.sourcesHidden),
    historicalEnvironment: bool(o.historicalEnvironment),
  }
}
export const draftPurpose = choices(
  'CREATE_TICKET',
  'ADD_RECORD',
  'RESOLVE_TICKET',
)
export function draft(value: unknown) {
  const o = record(value),
    c = record(o.content)
  return {
    id: identifier(o.id),
    sourceGenerationId: identifier(o.sourceGenerationId),
    purpose: draftPurpose(o.purpose),
    ticketId: nullable(identifier)(o.ticketId),
    ticketVersion: nullable(decimal)(o.ticketVersion),
    stateVersion: decimal(o.stateVersion),
    expiresAt: text(o.expiresAt),
    content: {
      title: text(c.title),
      symptom: text(c.symptom),
      attemptedActions: text(c.attemptedActions),
      content: text(c.content),
      resolutionType: nullable(choices('RESTORED', 'GUIDANCE', 'REFERRED'))(
        c.resolutionType,
      ),
      referralTarget: nullable(text)(c.referralTarget),
    },
  }
}
function feedback(value: unknown) {
  const o = record(value)
  return {
    id: identifier(o.id),
    rating: choices('HELPFUL', 'UNHELPFUL')(o.rating),
    issueCategory: nullable(text)(o.issueCategory),
    comment: nullable(text)(o.comment),
    status: choices(
      'NOT_REQUIRED',
      'RECORDED',
      'PENDING',
      'RESOLVED',
      'DISMISSED',
    )(o.status),
    shareWithKnowledgeAdmin: bool(o.shareWithKnowledgeAdmin),
    rowVersion: decimal(o.rowVersion),
  }
}
export type Conversation = ReturnType<typeof conversation>
export type Generation = ReturnType<typeof generation>
export type Fact = ReturnType<typeof fact>
export type Draft = ReturnType<typeof draft>
export type Feedback = ReturnType<typeof feedback>
function feedbackQueueItem(value: unknown) {
  const o = record(value)
  return {
    id: identifier(o.id),
    generationId: identifier(o.generationId),
    rating: choices('UNHELPFUL')(o.rating),
    issueCategory: text(o.issueCategory),
    status: choices('PENDING', 'RESOLVED', 'DISMISSED')(o.status),
    rowVersion: decimal(o.rowVersion),
    createdAt: text(o.createdAt),
  }
}
function sharedFeedbackCase(value: unknown) {
  const o = record(value),
    item = feedbackQueueItem(o.feedback)
  return {
    feedback: item,
    comment: nullable(text)(o.comment),
    question: text(o.question),
    answer: text(o.answer),
    sources: array((source) => {
      const s = record(source)
      return {
        title: text(s.title),
        locator: record(s.locator),
        excerpt: text(s.excerpt),
      }
    })(o.sources),
  }
}
export type FeedbackQueueItem = ReturnType<typeof feedbackQueueItem>
export type SharedFeedbackCase = ReturnType<typeof sharedFeedbackCase>
export type ConversationMode = Conversation['mode']
export type DraftPurpose = Draft['purpose']
export function toolCall(value: unknown) {
  const o = record(value)
  return {
    number: integer(o.number),
    name: choices(
      'search_knowledge',
      'read_knowledge',
      'read_ticket_events',
    )(o.name),
    status: choices(
      'STARTED',
      'SUCCEEDED',
      'FAILED',
      'UNKNOWN',
      'CANCELLED',
    )(o.status),
    resultSummary: nullable(text)(o.resultSummary),
    sourceUsageIds: array(identifier)(o.sourceUsageIds),
  }
}
function calls(value: unknown) {
  const o = record(value)
  return {
    toolCalls: array(toolCall)(o.toolCalls),
    modelCalls: array((value) => {
      const c = record(value)
      return {
        number: integer(c.number),
        purpose: text(c.purpose),
        status: text(c.status),
        requestedModel: text(c.requestedModel),
        responseModel: nullable(text)(c.responseModel),
        inputTokens: nullable(decimal)(c.inputTokens),
        outputTokens: nullable(decimal)(c.outputTokens),
        estimatedCost: nullable(text)(c.estimatedCost),
      }
    })(o.modelCalls),
  }
}
export const generationCalls = (id: string, signal?: AbortSignal) =>
  request(`ai/generations/${id}/calls`, calls, { signal })
export type GenerationCalls = ReturnType<typeof calls>
export type ConversationInput =
  | {
      mode: 'CUSTOMER_SELF_SERVICE'
      title: string
      serviceItemId: string
      deviceId: string | null
      supportContext: SupportContextForm
    }
  | { mode: 'TECHNICIAN_ASSIST'; title: string; ticketId: string }
export type GenerationInput = {
  question: string
  stateVersion: string
  clientRequestId: string
}
export type FactInput =
  | {
      stateVersion: string
      clientActionId: string
      action: 'ASSERT' | 'CORRECT' | 'RETRACT'
      statement: string
      factKind: 'OBSERVATION' | 'PERFORMED_ACTION'
      factId: string | null
    }
  | {
      stateVersion: string
      clientActionId: string
      action: 'SET_CONTEXT'
      supportContext: SupportContextForm
    }
export const listConversations = (
  pageNum: number,
  mode: ConversationMode,
  ticketId?: string,
  signal?: AbortSignal,
) =>
  request(
    `ai/conversations${query({ pageNum, mode, ticketId })}`,
    page(conversation),
    { signal },
  )
export const createConversation = (body: ConversationInput, csrf: string) =>
  request('ai/conversations', conversation, { body, csrf })
export const getConversation = (id: string, signal?: AbortSignal) =>
  request(`ai/conversations/${id}`, conversation, { signal })
export const renameConversation = (
  id: string,
  rowVersion: string,
  title: string,
  csrf: string,
) =>
  request(`ai/conversations/${id}`, conversation, {
    method: 'PATCH',
    body: { rowVersion, title },
    csrf,
  })
export const deleteConversation = (
  id: string,
  rowVersion: string,
  csrf: string,
) =>
  request(
    `ai/conversations/${id}`,
    (value) => {
      if (value !== null) throw new Error('删除回执不合法')
    },
    { method: 'DELETE', body: { rowVersion }, csrf },
  )
export const listTurns = (id: string, pageNum: number, signal?: AbortSignal) =>
  request(
    `ai/conversations/${id}/turns${query({ pageNum })}`,
    page(generation),
    { signal },
  )
export const listFacts = (id: string, pageNum: number, signal?: AbortSignal) =>
  request(`ai/conversations/${id}/facts${query({ pageNum })}`, page(fact), {
    signal,
  })
export const changeFacts = (id: string, body: FactInput, csrf: string) =>
  request(`ai/conversations/${id}/facts`, conversation, { body, csrf })
export const createGeneration = (
  id: string,
  body: GenerationInput,
  csrf: string,
) => request(`ai/conversations/${id}/generations`, generation, { body, csrf })
export const generationReceipt = (key: string, signal?: AbortSignal) =>
  request(`ai/generations/by-request/${key}`, generation, { signal })
export const getGeneration = (id: string, signal?: AbortSignal) =>
  request(`ai/generations/${id}`, generation, { signal })
export const cancelGeneration = (id: string, csrf: string) =>
  request(`ai/generations/${id}/cancel`, generation, { body: {}, csrf })
export const createDraft = (
  id: string,
  body: {
    sourceGenerationId: string
    purpose: DraftPurpose
    clientRequestId: string
    confirmedFactIds: string[]
  },
  csrf: string,
) => request(`ai/conversations/${id}/action-drafts`, draft, { body, csrf })
export const draftReceipt = (key: string, signal?: AbortSignal) =>
  request(`ai/action-drafts/by-request/${key}`, draft, { signal })
export const putFeedback = (
  id: string,
  body: {
    rating: 'HELPFUL' | 'UNHELPFUL'
    issueCategory: 'OTHER' | null
    comment: string | null
  },
  csrf: string,
) =>
  request(`ai/generations/${id}/feedback`, feedback, {
    method: 'PUT',
    body,
    csrf,
  })
export const shareFeedback = (
  id: string,
  rowVersion: string,
  share: boolean,
  csrf: string,
) =>
  request(`ai/generations/${id}/feedback/share`, feedback, {
    method: 'PATCH',
    body: { rowVersion, share },
    csrf,
  })

export const listSharedFeedback = (
  pageNum: number,
  status?: 'PENDING' | 'RESOLVED' | 'DISMISSED',
  signal?: AbortSignal,
) =>
  request(
    `admin/feedback${query({ pageNum, status })}`,
    page(feedbackQueueItem),
    { signal },
  )
export const getSharedFeedback = (id: string, signal?: AbortSignal) =>
  request(`admin/feedback/${id}`, sharedFeedbackCase, { signal })
export const handleSharedFeedback = (
  id: string,
  body: {
    rowVersion: string
    action: 'RESOLVE' | 'DISMISS'
    handlingNote: string
  },
  csrf: string,
) =>
  request(`admin/feedback/${id}/handle`, feedbackQueueItem, {
    body,
    csrf,
  })

/** SSE只订阅原运行，逐块解码UTF-8；完成事件缺失时由调用者查询原状态。 */
export async function subscribeGeneration(
  id: string,
  csrf: string,
  signal: AbortSignal,
  onStage: (status: Generation['status']) => void,
  onTool?: (tool: ReturnType<typeof toolCall>) => void,
): Promise<boolean> {
  const response = await fetch(`/api/v1/ai/generations/${id}/stream`, {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'X-CSRF-TOKEN': csrf },
    signal: AbortSignal.any([signal, AbortSignal.timeout(185_000)]),
  })
  if (!response.ok) {
    const envelope = record((await response.json()) as unknown)
    throw new ApiError(
      integer(envelope.code),
      text(envelope.message),
      response.status,
      text(envelope.traceId),
    )
  }
  if (
    !response.headers.get('Content-Type')?.startsWith('text/event-stream') ||
    !response.body
  )
    throw new Error('订阅协议不匹配')
  const reader = response.body.getReader(),
    decoder = new TextDecoder('utf-8', { fatal: true })
  let buffer = '',
    completed = false,
    sequence = 0
  try {
    while (true) {
      const part = await reader.read()
      buffer = (
        buffer + decoder.decode(part.value, { stream: !part.done })
      ).replace(/\r\n/g, '\n')
      if (buffer.length > 1_000_000) throw new Error('订阅事件超过预算')
      let boundary: number
      while ((boundary = buffer.indexOf('\n\n')) >= 0) {
        const frame = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)
        const lines = frame.split('\n'),
          name = lines
            .find((line) => line.startsWith('event:'))
            ?.slice(6)
            .trim()
        if (
          !name ||
          !['stage', 'answer', 'citations', 'tool', 'done', 'error'].includes(
            name,
          )
        )
          continue
        const payload: unknown = JSON.parse(
          lines
            .filter((line) => line.startsWith('data:'))
            .map((line) => line.slice(5).trimStart())
            .join('\n'),
        )
        const event = record(payload),
          next = integer(event.sequence)
        if (identifier(event.generationId) !== id || next <= sequence)
          throw new Error('订阅事件身份或顺序不匹配')
        sequence = next
        if (name === 'stage') onStage(runStatus(record(event.data).status))
        if (name === 'tool') {
          const tool = toolCall(event.data)
          onTool?.(tool)
        }
        if (name === 'answer') {
          const data = record(event.data)
          text(data.answer)
          choices(
            'ANSWERED',
            'CLARIFICATION',
            'INSUFFICIENT_EVIDENCE',
          )(data.answerType)
        }
        if (name === 'citations')
          array((value) => {
            const c = record(value)
            identifier(c.sourceUsageId)
            text(c.title)
          })(event.data)
        if (name === 'done') {
          runStatus(record(event.data).status)
          completed = true
          return completed
        }
        if (name === 'error') throw new Error(text(record(event.data).message))
      }
      if (part.done) return completed
    }
  } finally {
    await reader.cancel().catch(() => undefined)
    reader.releaseLock()
  }
}
