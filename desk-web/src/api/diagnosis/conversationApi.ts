/** 创建和管理本人私有会话；诊断目标由独立任务管理。 @author yangaobo */
import { query, request } from '../http'
import {
  array,
  bool,
  choices,
  decimal,
  identifier,
  nullable,
  page,
  record,
  text,
} from '../validation'
import {
  conversationMode,
  type ConversationInput,
  type ConversationMode,
} from './types'

export function conversation(value: unknown) {
  const o = record(value)
  return {
    id: identifier(o.id),
    title: text(o.title),
    mode: conversationMode(o.mode),
    customerId: nullable(identifier)(o.customerId),
    ticketId: nullable(identifier)(o.ticketId),
    rowVersion: decimal(o.rowVersion),
  }
}

export type Conversation = ReturnType<typeof conversation>

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

/** 独立消息按服务端游标读取，不从运行次数推算序号。 */
export function messagePage(value: unknown) {
  const o = record(value)
  return {
    records: array((value) => {
      const m = record(value)
      return {
        id: identifier(m.id),
        taskId: nullable(identifier)(m.taskId),
        sequenceNumber: decimal(m.sequenceNumber),
        role: choices('USER', 'ASSISTANT', 'SYSTEM')(m.role),
        content: nullable(text)(m.content),
        runId: nullable(identifier)(m.runId),
        sourcesHidden: bool(m.sourcesHidden),
      }
    })(o.records),
    nextCursor: decimal(o.nextCursor),
    hasMore: bool(o.hasMore),
  }
}
export const listMessages = (
  id: string,
  afterSequence: string,
  signal?: AbortSignal,
) =>
  request(
    `ai/conversations/${id}/messages${query({ afterSequence })}`,
    messagePage,
    { signal },
  )
