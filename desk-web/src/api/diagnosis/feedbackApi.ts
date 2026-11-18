/** 查询和保存当前用户本人的运行评价。 @author yangaobo */
import { request } from '../http'
import {
  choices,
  decimal,
  identifier,
  nullable,
  record,
  text,
} from '../validation'

function feedback(value: unknown) {
  const o = record(value)
  return {
    id: identifier(o.id),
    runId: identifier(o.runId),
    rating: choices('HELPFUL', 'UNHELPFUL')(o.rating),
    adoption: choices(
      'NOT_USED',
      'ACCEPTED',
      'MODIFIED',
      'REJECTED',
    )(o.adoption),
    issueCategory: nullable(
      choices(
        'INCORRECT',
        'IRRELEVANT',
        'MISSING_CONTEXT',
        'UNSUPPORTED',
        'OTHER',
      ),
    )(o.issueCategory),
    comment: nullable(text)(o.comment),
    rowVersion: decimal(o.rowVersion),
  }
}

export type Feedback = ReturnType<typeof feedback>

export const putFeedback = (
  id: string,
  body: {
    rating: 'HELPFUL' | 'UNHELPFUL'
    adoption: Feedback['adoption']
    issueCategory: Feedback['issueCategory']
    comment: string | null
    rowVersion?: string
  },
  csrf: string,
) =>
  request(`ai/runs/${id}/feedback`, feedback, {
    method: 'PUT',
    body,
    csrf,
  })
/** 读取本人已保存的运行评价，尚未评价返回null。 @author yangaobo */
export const getFeedback = (id: string, signal?: AbortSignal) =>
  request(`ai/runs/${id}/feedback`, nullable(feedback), { signal })
