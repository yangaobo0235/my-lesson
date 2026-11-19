import { request } from '../http'
import {
  choices,
  identifier,
  nullable,
  page,
  record,
  text,
} from '../validation'

const eventType = choices(
  'UPLOADED',
  'METADATA_CHANGED',
  'INDEX_REQUESTED',
  'INDEX_CANCEL_REQUESTED',
  'INDEX_REQUEUED',
  'PUBLISHED',
  'DISABLED',
  'RESTORED',
  'RETIRED',
  'WITHDRAWN',
  'ACCESS_REVOKED',
  'ACCESS_RESTORED',
  'DELETED',
)

export type DocumentEventType = ReturnType<typeof eventType>

function event(value: unknown) {
  const o = record(value)
  return {
    id: identifier(o.id),
    documentId: identifier(o.documentId),
    actorId: identifier(o.actorId),
    eventType: eventType(o.eventType),
    relatedDocumentId: nullable(identifier)(o.relatedDocumentId),
    details: record(o.details),
    createdAt: text(o.createdAt),
  }
}

/** 按指定修订读取不可变审计，POST 查询遵守管理接口 CSRF 合同。 @author yangaobo */
export const listDocumentEvents = (
  documentId: string,
  pageNum: number,
  csrf: string,
  signal?: AbortSignal,
) =>
  request('admin/document-event/list', page(event), {
    body: { documentId: identifier(documentId), pageNum },
    csrf,
    signal,
  })
