/** AI 各入口共享的动作输入合同。 @author yangaobo */
import type { SupportContextForm } from '../support/types'
import { choices } from '../validation'

export const conversationMode = choices(
  'CONSULT',
  'CUSTOMER_SELF_SERVICE',
  'TECHNICIAN_ASSIST',
)
export type ConversationMode = ReturnType<typeof conversationMode>

export const taskStatus = choices(
  'ACTIVE',
  'WAITING_USER',
  'WAITING_APPROVAL',
  'BLOCKED_EXTERNAL',
  'RESOLVED_REPORTED',
  'HANDED_OFF',
  'CLOSED',
  'CANCELLED',
)

export const factKind = choices(
  'OBSERVATION',
  'ACTION_REPORT',
  'SYMPTOM',
  'ENVIRONMENT',
  'OUTCOME',
)
export const safetyCondition = choices(
  'SAFETY_HAZARD_REPORTED',
  'DATA_LOSS_RISK_REPORTED',
  'ADMIN_PRIVILEGE_REQUIRED',
  'OUT_OF_SERVICE_SCOPE',
  'NO_PROGRESS_LIMIT',
  'EVIDENCE_CONFLICT',
)
export type SafetyCondition = ReturnType<typeof safetyCondition>

/** 会话只管理私有范围，诊断目标和环境由独立任务拥有。 */
export type ConversationInput = {
  mode: ConversationMode
  title: string
  clientRequestId: string
  ticketId?: string
}

/** 已保存反馈和新消息二选一；任务与版本必须成对提供。 */
export type RunInput = {
  clientRequestId: string
  taskId?: string
  taskStateVersion?: string
  resumesRunId?: string
} & (
  | { message: string; inputMessageId?: never }
  | { inputMessageId: string; message?: never }
)

export type TaskAction = { clientActionId: string; taskStateVersion: string }
export type TaskInput = {
  clientActionId: string
  goal: string
  serviceItemId?: string | null
  deviceId?: string | null
  supportContext?: SupportContextForm
}
export type FactInput = TaskAction & {
  action: 'ASSERT' | 'CORRECT' | 'RETRACT'
  statement: string
  factKind: ReturnType<typeof factKind>
  predecessorFactId: string | null
  reportedConditions?: SafetyCondition[]
}
export type CheckAnswerInput = TaskAction & {
  answer: {
    value: string | boolean | number
    executionStatus: 'NOT_PERFORMED' | 'PERFORMED' | null
    outcome: 'UNKNOWN' | 'UNCHANGED' | 'IMPROVED' | 'RESTORED_REPORTED' | null
  }
}
