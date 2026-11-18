/** 读取与修订人工确认草稿，并按原身份核对正式动作回执。 @author yangaobo */
import { request } from '../http'
import {
  choices,
  decimal,
  identifier,
  integer,
  nullable,
  record,
  text,
} from '../validation'
import { taskReceipt } from './taskApi'
import type { TaskAction } from './types'

export const draftPurpose = choices(
  'CREATE_TICKET',
  'ADD_RECORD',
  'RESOLVE_TICKET',
  'REQUEST_INFORMATION',
  'BOOK_ONSITE',
)
export function draft(value: unknown) {
  const o = record(value),
    c = record(o.content)
  return {
    id: identifier(o.id),
    draftRootId: identifier(o.draftRootId),
    revisionNumber: integer(o.revisionNumber),
    contentSha256: text(o.contentSha256),
    status: choices('PENDING', 'SUPERSEDED', 'WITHDRAWN', 'EXPIRED')(o.status),
    sourceRunId: nullable(identifier)(o.sourceRunId),
    purpose: draftPurpose(o.purpose),
    ticketId: nullable(identifier)(o.ticketId),
    ticketRowVersion: nullable(decimal)(o.ticketRowVersion),
    taskId: identifier(o.taskId),
    taskStateVersion: decimal(o.taskStateVersion),
    diagnosisContextVersion: decimal(o.diagnosisContextVersion),
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
      ...(c.followUpAt === undefined ? {} : { followUpAt: text(c.followUpAt) }),
      ...(c.slotId === undefined ? {} : { slotId: identifier(c.slotId) }),
    },
  }
}

export type Draft = ReturnType<typeof draft>
export type DraftPurpose = Draft['purpose']

export const createDraft = (
  id: string,
  body: {
    sourceRunId: string | null
    purpose: DraftPurpose
    clientRequestId: string
    confirmedFactIds: string[]
    followUpAt?: string
    slotId?: string
    waitForConfirmation?: boolean
  },
  csrf: string,
) => request(`ai/tasks/${id}/action-drafts`, draft, { body, csrf })
export const draftReceipt = (key: string, signal?: AbortSignal) =>
  request(`ai/action-drafts/by-request/${key}`, draft, { signal })

/** 用户编辑先保存不可变修订，原键重试不覆盖旧内容。 */
export const reviseDraft = (
  id: string,
  body: {
    clientRequestId: string
    contentSha256: string
    content: Draft['content']
  },
  csrf: string,
) => request(`ai/action-drafts/${id}/revisions`, draft, { body, csrf })
/** 撤回只阻止后续校验，正式结果需查询Java回执。 */
export const withdrawDraft = (
  id: string,
  contentSha256: string,
  csrf: string,
) =>
  request(`ai/action-drafts/${id}/withdraw`, draft, {
    body: { contentSha256 },
    csrf,
  })

/** 正式动作提交后显式核对Java回执，不从浏览器成功提示推断任务终态。 */
export const synchronizeDraft = (id: string, body: TaskAction, csrf: string) =>
  request(`ai/action-drafts/${id}/synchronize`, taskReceipt, { body, csrf })
