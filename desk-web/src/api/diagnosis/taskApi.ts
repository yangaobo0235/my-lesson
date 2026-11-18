import { query, request } from '../http'
import {
  array,
  bool,
  choices,
  decimal,
  identifier,
  integer,
  invalid,
  nullable,
  page,
  record,
  text,
} from '../validation'
import { supportContext, type SupportContextForm } from '../support/types'
import {
  factKind,
  safetyCondition,
  taskStatus,
  type CheckAnswerInput,
  type FactInput,
  type TaskAction,
  type TaskInput,
} from './types'

/** 诊断任务与人工检查合同；答复保存不隐式发起模型运行。 @author yangaobo */
const checkStatus = choices(
  'OPEN',
  'ANSWERED',
  'OBSERVED_DONE',
  'FAILED',
  'SKIPPED',
  'WITHDRAWN',
  'EXPIRED',
  'NEEDS_RECHECK',
)
const finiteNumber = (value: unknown): number =>
  typeof value === 'number' && Number.isFinite(value) ? value : invalid()

/** 假设属于任务投影，不与用户事实混合展示。 */
export function task(value: unknown) {
  const o = record(value)
  return {
    id: identifier(o.id),
    conversationId: identifier(o.conversationId),
    serviceItemId: nullable(identifier)(o.serviceItemId),
    deviceId: nullable(identifier)(o.deviceId),
    goal: text(o.goal),
    status: taskStatus(o.status),
    taskStateVersion: decimal(o.taskStateVersion),
    diagnosisContextVersion: decimal(o.diagnosisContextVersion),
    rowVersion: decimal(o.rowVersion),
    supportContext: supportContext(o.supportContext),
    sourcesHidden: bool(o.sourcesHidden),
    hypotheses: array((value) => {
      const h = record(value)
      return {
        key: text(h.hypothesisKey),
        statement: text(h.statement),
        status: choices(
          'PROPOSED',
          'SUPPORTED',
          'REFUTED',
          'INAPPLICABLE',
        )(h.status),
      }
    })(record(o.hypotheses).items),
    outcomeNote: nullable(text)(o.outcomeNote),
    handoverTicketId: nullable(identifier)(o.handoverTicketId),
  }
}
/** 原动作回执保留反馈消息身份，可用于显式续行。 */
export function taskReceipt(value: unknown) {
  const o = record(value)
  return {
    taskId: identifier(o.taskId),
    eventId: identifier(o.eventId),
    taskStateVersion: decimal(o.taskStateVersion),
    status: taskStatus(o.status),
    inputMessageId: nullable(identifier)(o.inputMessageId),
    factId: nullable(identifier)(o.factId),
    checkId: nullable(identifier)(o.checkId),
  }
}

/** 规范答案保留 false 与零，未执行和未知结果均需明确。 */
function answer(value: unknown): CheckAnswerInput['answer'] {
  const o = record(value),
    v = o.value
  if (
    typeof v !== 'string' &&
    typeof v !== 'boolean' &&
    !(typeof v === 'number' && Number.isFinite(v))
  )
    invalid()
  return {
    value: v,
    executionStatus: nullable(choices('NOT_PERFORMED', 'PERFORMED'))(
      o.executionStatus,
    ),
    outcome: nullable(
      choices('UNKNOWN', 'UNCHANGED', 'IMPROVED', 'RESTORED_REPORTED'),
    )(o.outcome),
  }
}
/** 来源撤权时指引和答案合同均可隐藏，不能继续提交旧指引。 */
export function check(value: unknown) {
  const o = record(value)
  return {
    id: identifier(o.id),
    proposedRunId: identifier(o.proposedRunId),
    checkKey: text(o.checkKey),
    checkKind: choices('CLARIFICATION', 'CHECK')(o.checkKind),
    attemptNumber: integer(o.attemptNumber),
    instruction: nullable(text)(o.instruction),
    status: checkStatus(o.status),
    contextVersion: decimal(o.contextVersion),
    rowVersion: decimal(o.rowVersion),
    executorType: choices('CUSTOMER', 'TECHNICIAN', 'TOOL')(o.executorType),
    answerSchema: nullable((value) => {
      const s = record(value)
      return {
        kind: choices('TEXT', 'CHOICE', 'BOOLEAN', 'NUMBER')(s.kind),
        options: array(text)(s.options),
        minimum: nullable(finiteNumber)(s.minimum),
        maximum: nullable(finiteNumber)(s.maximum),
        maxLength: integer(s.maxLength),
      }
    })(o.answerSchema),
    normalizedAnswer: nullable((value) => answer(record(value).answer))(
      o.normalizedAnswer,
    ),
    answerMessageId: nullable(identifier)(o.answerMessageId),
    previousCheckId: nullable(identifier)(o.previousCheckId),
    sourcesHidden: bool(o.sourcesHidden),
    expiresAt: nullable(text)(o.expiresAt),
  }
}
export type DiagnosisTask = ReturnType<typeof task>
export type DiagnosticCheck = ReturnType<typeof check>
export type TaskReceipt = ReturnType<typeof taskReceipt>

/** 任务创建和查询始终受会话私有范围约束。 */
export const createTask = (id: string, body: TaskInput, csrf: string) =>
  request(`ai/conversations/${id}/tasks`, taskReceipt, { body, csrf })
export const listTasks = (id: string, pageNum: number, signal?: AbortSignal) =>
  request(`ai/conversations/${id}/tasks${query({ pageNum })}`, page(task), {
    signal,
  })
export const getTask = (id: string, signal?: AbortSignal) =>
  request(`ai/tasks/${id}`, task, { signal })
export const listFacts = (id: string, pageNum: number, signal?: AbortSignal) =>
  request(`ai/tasks/${id}/facts${query({ pageNum })}`, page(fact), { signal })
export const changeFacts = (id: string, body: FactInput, csrf: string) =>
  request(`ai/tasks/${id}/facts`, taskReceipt, { body, csrf })
export const changeContext = (
  id: string,
  body: TaskAction & {
    supportContext?: SupportContextForm
    serviceItemId?: string | null
  },
  csrf: string,
) => request(`ai/tasks/${id}/context`, taskReceipt, { body, csrf })
export const listChecks = (id: string, pageNum: number, signal?: AbortSignal) =>
  request(`ai/tasks/${id}/checks${query({ pageNum })}`, page(check), { signal })
export const answerCheck = (
  id: string,
  checkId: string,
  body: CheckAnswerInput,
  csrf: string,
) =>
  request(`ai/tasks/${id}/checks/${checkId}/answer`, taskReceipt, {
    body,
    csrf,
  })
/** 结束或人工报告恢复只改变诊断任务，不关闭正式工单。 */
export const closeTask = (
  id: string,
  action: 'close' | 'cancel' | 'report-outcome',
  body: TaskAction & { reason: string; restored?: true },
  csrf: string,
) => request(`ai/tasks/${id}/${action}`, taskReceipt, { body, csrf })

/** 事实来源与更正链由后端提供，前端不推断执行结果。 */
export function fact(value: unknown) {
  const o = record(value)
  return {
    id: identifier(o.id),
    stateVersion: decimal(o.stateVersion),
    factKind: factKind(o.factKind),
    operation: choices('ASSERT', 'CORRECT', 'RETRACT')(o.operation),
    active: bool(o.active),
    statement: nullable(text)(o.statement),
    reportedConditions: array(safetyCondition)(o.reportedConditions),
    sourceType: choices(
      'USER_REPORT',
      'HUMAN_RECORD',
      'TOOL_OBSERVATION',
    )(o.sourceType),
    supersedesFactId: nullable(identifier)(o.supersedesFactId),
    sourcesHidden: bool(o.sourcesHidden),
    historicalEnvironment: bool(o.historicalEnvironment),
  }
}
export type Fact = ReturnType<typeof fact>
