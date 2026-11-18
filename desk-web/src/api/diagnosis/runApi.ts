/** 读取诊断运行、调用账目、来源及原运行订阅。 @author yangaobo */
import { ApiError, query, request } from '../http'
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
import { taskStatus, type RunInput } from './types'

const runStatus = choices(
  'QUEUED',
  'RUNNING',
  'SUCCEEDED',
  'FAILED',
  'CANCELLED',
  'INTERRUPTED',
)
const executionPhase = choices('CONTEXT', 'MODEL', 'TOOL', 'COMMIT')
const terminalStatus = choices(
  'SUCCEEDED',
  'FAILED',
  'CANCELLED',
  'INTERRUPTED',
)

/** 已持久的运行状态、执行阶段及迭代号。 */
function stage(value: unknown) {
  const data = record(value)
  return {
    status: runStatus(data.status),
    executionPhase: executionPhase(data.executionPhase),
    iterationNumber: integer(data.iterationNumber),
  }
}

export type RunStage = ReturnType<typeof stage>

/** 格式化服务端提供的 Markdown 锚点及真实行范围。 */
function markdownLocation(o: Record<string, unknown>): string {
  return `#${text(o.anchor)} · 第 ${integer(o.line_start)}–${integer(o.line_end)} 行`
}
function location(value: unknown): string {
  const o = record(value)
  switch (o.kind) {
    case 'PDF':
      if ('page_number' in o) return `物理第 ${integer(o.page_number)} 页`
      return array((item) => {
        const p = record(item)
        return `物理第 ${integer(p.page_number)} 页`
      })(o.pages).join('、')
    case 'MARKDOWN':
      if ('line_start' in o) return markdownLocation(o)
      return array((item) => {
        const p = record(item)
        return markdownLocation(p)
      })(o.segments).join('、')
    case 'TICKET_EVENT':
      return `工单事件 ${integer(o.sequence)}`
    case 'MESSAGE':
      return `私有消息 ${identifier(o.message_id)}`
    case 'ATTACHMENT':
      identifier(o.attachment_id)
      return `脱敏附件 · 字符区间 ${integer(o.offset)}–${integer(o.end_offset)}`
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
    checks: array((v) => {
      const c = record(v)
      return {
        checkKey: text(c.checkKey),
        instruction: text(c.instruction),
        checkKind: choices('CLARIFICATION', 'CHECK')(c.checkKind),
      }
    })(o.checks),
    hypotheses: array((v) => {
      const h = record(v)
      return {
        hypothesisKey: text(h.hypothesisKey),
        statement: text(h.statement),
        status: choices(
          'PROPOSED',
          'SUPPORTED',
          'REFUTED',
          'INAPPLICABLE',
        )(h.status),
      }
    })(o.hypotheses),
    proposedRecord: nullable(text)(o.proposedRecord),
    proposedResolution: nullable(text)(o.proposedResolution),
    resolutionType: nullable(choices('RESTORED', 'GUIDANCE', 'REFERRED'))(
      o.resolutionType,
    ),
    referralTarget: nullable(text)(o.referralTarget),
  }
}
export function run(value: unknown) {
  const o = record(value)
  return {
    id: identifier(o.id),
    conversationId: identifier(o.conversationId),
    taskId: nullable(identifier)(o.taskId),
    inputMessageId: identifier(o.inputMessageId),
    ...stage(o),
    usageUnknown: bool(o.usageUnknown),
    question: text(o.question),
    answerType: nullable(
      choices('ANSWERED', 'CLARIFICATION', 'INSUFFICIENT_EVIDENCE', 'HANDOVER'),
    )(o.answerType),
    answer: nullable(text)(o.answer),
    structuredResult: nullable(structured)(o.structuredResult),
    inputStateVersion: nullable(decimal)(o.inputStateVersion),
    outputStateVersion: nullable(decimal)(o.outputStateVersion),
    ticketRowVersion: nullable(decimal)(o.ticketRowVersion),
    sourcesHidden: bool(o.sourcesHidden),
    stale: bool(o.stale),
    diagnosisContextVersion: nullable(decimal)(o.diagnosisContextVersion),
    currentDiagnosisContextVersion: nullable(decimal)(
      o.currentDiagnosisContextVersion,
    ),
    citations: array(citation)(o.citations),
    inputTokens: nullable(decimal)(o.inputTokens),
    outputTokens: nullable(decimal)(o.outputTokens),
    errorCode: nullable(text)(o.errorCode),
  }
}

export type Run = ReturnType<typeof run>

export function toolCall(value: unknown) {
  const o = record(value)
  return {
    number: integer(o.number),
    name: choices(
      'search_knowledge',
      'read_evidence',
      'load_procedure',
      'read_ticket_events',
      'read_task_context',
      'list_diagnostic_attachments',
      'inspect_diagnostic_text',
    )(o.name),
    status: choices(
      'PREPARED',
      'RUNNING',
      'OK',
      'EMPTY',
      'INVALID_ARGUMENT',
      'FORBIDDEN',
      'STALE_CONTEXT',
      'DEPENDENCY_FAILED',
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
export const runCalls = (id: string, signal?: AbortSignal) =>
  request(`ai/runs/${id}/calls`, calls, { signal })
export type RunCalls = ReturnType<typeof calls>

export const listRuns = (id: string, pageNum: number, signal?: AbortSignal) =>
  request(`ai/conversations/${id}/runs${query({ pageNum })}`, page(run), {
    signal,
  })
export const createRun = (id: string, body: RunInput, csrf: string) =>
  request(`ai/conversations/${id}/runs`, run, { body, csrf })
export const runReceipt = (key: string, signal?: AbortSignal) =>
  request(`ai/runs/by-request/${key}`, run, { signal })
export const getRun = (id: string, signal?: AbortSignal) =>
  request(`ai/runs/${id}`, run, { signal })
export const cancelRun = (id: string, csrf: string) =>
  request(`ai/runs/${id}/cancel`, run, { body: {}, csrf })

/** SSE只订阅原运行，逐块解码UTF-8；完成事件缺失时由调用者查询原状态。 */
export async function subscribeRun(
  id: string,
  csrf: string,
  signal: AbortSignal,
  onStage: (value: RunStage) => void,
  onTool?: (tool: ReturnType<typeof toolCall>) => void,
): Promise<boolean> {
  const response = await fetch(`/api/v1/ai/runs/${id}/stream`, {
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
          ![
            'stage',
            'answer',
            'citations',
            'tool',
            'task',
            'done',
            'error',
          ].includes(name)
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
        if (identifier(event.runId) !== id || next <= sequence)
          throw new Error('订阅事件身份或顺序不匹配')
        sequence = next
        if (name === 'stage') onStage(stage(event.data))
        if (name === 'tool') {
          const tool = toolCall(event.data)
          onTool?.(tool)
        }
        if (name === 'task') {
          const data = record(event.data)
          identifier(data.taskId)
          taskStatus(data.status)
          decimal(data.taskStateVersion)
          array(identifier)(data.pendingCheckIds)
        }
        if (name === 'answer') {
          const data = record(event.data)
          text(data.answer)
          choices(
            'ANSWERED',
            'CLARIFICATION',
            'INSUFFICIENT_EVIDENCE',
            'HANDOVER',
          )(data.answerType)
        }
        if (name === 'citations')
          array((value) => {
            const c = record(value)
            identifier(c.sourceUsageId)
            text(c.title)
          })(event.data)
        if (name === 'done') {
          const data = record(event.data)
          terminalStatus(data.status)
          bool(data.sourcesHidden)
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
