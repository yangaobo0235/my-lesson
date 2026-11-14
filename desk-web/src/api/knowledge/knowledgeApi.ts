import { request, query } from '../http'
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
import { productModel, serviceItem } from '../support/types'

/** 知识上传、索引与发布分别解码，不把传输成功当成可检索。 @author yangaobo */
export function series(value: unknown) {
  const o = record(value)
  return {
    id: identifier(o.id),
    sourceType: choices(
      'OFFICIAL_MANUAL',
      'TROUBLESHOOTING_GUIDE',
      'SANITIZED_CASE',
    )(o.sourceType),
    sourceNamespace: text(o.sourceNamespace),
    sourceCode: text(o.sourceCode),
    sourceOrganization: text(o.sourceOrganization),
    languageCode: text(o.languageCode),
    audience: choices('CUSTOMER', 'SERVICE_STAFF')(o.audience),
  }
}
export interface RuleInput {
  serviceItemId: string
  productModelId: string | null
  osName: string | null
  osVersion: string | null
  softwareName: string | null
  softwareVersion: string | null
}
function rule(value: unknown): RuleInput {
  const o = record(value)
  return {
    serviceItemId: identifier(o.serviceItemId),
    productModelId: nullable(identifier)(o.productModelId),
    osName: nullable(text)(o.osName),
    osVersion: nullable(text)(o.osVersion),
    softwareName: nullable(text)(o.softwareName),
    softwareVersion: nullable(text)(o.softwareVersion),
  }
}
export function document(value: unknown) {
  const o = record(value)
  return {
    id: identifier(o.id),
    seriesId: identifier(o.seriesId),
    clientUploadId: identifier(o.clientUploadId),
    title: text(o.title),
    revisionNumber: integer(o.revisionNumber),
    contentFormat: choices('PDF', 'MARKDOWN')(o.contentFormat),
    originalFileName: text(o.originalFileName),
    fileSizeBytes: decimal(o.fileSizeBytes),
    fileSha256: text(o.fileSha256),
    sourceUrl: nullable(text)(o.sourceUrl),
    sourceReviewNote: nullable(text)(o.sourceReviewNote),
    revisionLabel: nullable(text)(o.revisionLabel),
    issuedOn: nullable(text)(o.issuedOn),
    applicabilityNote: nullable(text)(o.applicabilityNote),
    publicationStatus: choices(
      'DRAFT',
      'PUBLISHED',
      'DISABLED',
      'RETIRED',
      'DELETED',
    )(o.publicationStatus),
    currentJobId: nullable(identifier)(o.currentJobId),
    rowVersion: decimal(o.rowVersion),
    rules: o.rules === null ? [] : array(rule)(o.rules),
  }
}
export function indexJob(value: unknown) {
  const o = record(value)
  return {
    id: identifier(o.id),
    documentId: identifier(o.documentId),
    clientRequestId: identifier(o.clientRequestId),
    submitState: choices('PREPARING', 'COMMITTED', 'ABORTED')(o.submitState),
    cancelState: choices(
      'NONE',
      'PREPARING',
      'COMMITTED',
      'ABORTED',
    )(o.cancelState),
    aiAccepted: nullable(bool)(o.aiAccepted),
    aiStatus: nullable(
      choices('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED'),
    )(o.aiStatus),
    aiStage: nullable(text)(o.aiStage),
    parserState: nullable(text)(o.parserState),
    replicasVerified: nullable(bool)(o.replicasVerified),
    lastErrorCode: nullable(text)(o.lastErrorCode),
  }
}
export type DocumentSeries = ReturnType<typeof series>
export type KnowledgeDocument = ReturnType<typeof document>
export type IndexJob = ReturnType<typeof indexJob>
export interface MetadataInput {
  title: string
  sourceUrl: string | null
  sourceReviewNote: string | null
  revisionLabel: string | null
  issuedOn: string | null
  applicabilityNote: string | null
  rules: RuleInput[]
}
export const blankRule = (): RuleInput => ({
  serviceItemId: '',
  productModelId: null,
  osName: null,
  osVersion: null,
  softwareName: null,
  softwareVersion: null,
})
export const listSeries = (
  pageNum: number,
  csrf: string,
  signal?: AbortSignal,
) =>
  request('admin/document-series/list', page(series), {
    body: { pageNum },
    csrf,
    signal,
  })
export const createSeries = (body: Omit<DocumentSeries, 'id'>, csrf: string) =>
  request('admin/document-series/add', series, { body, csrf })
/** 修订详情按真实系列身份查询，列表分页不影响来源展示。 */
export const getSeries = (id: string, csrf: string, signal?: AbortSignal) =>
  request('admin/document-series/query', series, { body: { id }, csrf, signal })
export const listDocuments = (
  seriesId: string | null,
  pageNum: number,
  csrf: string,
  signal?: AbortSignal,
) =>
  request('admin/document/list', page(document), {
    body: { seriesId, pageNum },
    csrf,
    signal,
  })
export const getDocument = (id: string, csrf: string, signal?: AbortSignal) =>
  request('admin/document/query', document, { body: { id }, csrf, signal })
export const uploadDocument = (
  form: FormData,
  csrf: string,
  signal?: AbortSignal,
) =>
  request('admin/document/upload', document, {
    form,
    csrf,
    signal,
    timeoutMs: 60_000,
  })
export const uploadReceipt = (
  key: string,
  csrf: string,
  signal?: AbortSignal,
) =>
  request('admin/document/query-upload', document, {
    body: { clientUploadId: key },
    csrf,
    signal,
  })
export const updateDocument = (
  id: string,
  rowVersion: string,
  metadata: MetadataInput,
  csrf: string,
) =>
  request('admin/document/update', document, {
    body: { id, rowVersion, ...metadata },
    csrf,
  })
export const changePublication = (
  action: 'publish' | 'replace' | 'disable' | 'restore',
  value: KnowledgeDocument,
  csrf: string,
  replacesDocumentId?: string,
) =>
  request(`admin/document/${action}`, document, {
    body: {
      id: value.id,
      rowVersion: value.rowVersion,
      ...(action === 'publish' || action === 'replace'
        ? { replacesDocumentId: replacesDocumentId ?? null }
        : {}),
    },
    csrf,
  })
export const deleteDocument = (value: KnowledgeDocument, csrf: string) =>
  request(
    'admin/document/delete',
    (v) => {
      if (v !== null) throw new Error('删除回执不匹配')
    },
    { body: { id: value.id, rowVersion: value.rowVersion }, csrf },
  )
export const createIndex = (
  value: KnowledgeDocument,
  key: string,
  csrf: string,
) =>
  request('admin/index-job/add', indexJob, {
    body: {
      id: value.id,
      rowVersion: value.rowVersion,
      clientRequestId: key,
      processingConfig: {},
    },
    csrf,
  })
export const getIndex = (
  body: { id: string } | { clientRequestId: string },
  csrf: string,
  signal?: AbortSignal,
) => request('admin/index-job/query', indexJob, { body, csrf, signal })
export const cancelIndex = (value: KnowledgeDocument, csrf: string) =>
  request('admin/index-job/cancel', indexJob, {
    body: { id: value.currentJobId, rowVersion: value.rowVersion },
    csrf,
  })
export const replayIndex = (id: string, csrf: string) =>
  request('admin/index-job/replay-command', indexJob, { body: { id }, csrf })
export const listRuleItems = (pageNum: number, signal?: AbortSignal) =>
  request(
    `tickets/knowledge-service-items${query({ pageNum })}`,
    page(serviceItem),
    { signal },
  )
export const listRuleModels = (pageNum: number, signal?: AbortSignal) =>
  request(`tickets/product-models${query({ pageNum })}`, page(productModel), {
    signal,
  })
/** 原件链接只由真实文档ID和实际来源引用构造，不从模型正文解析URL。 */
export const originalLink = (
  documentId: string,
  generationId: string,
  sourceUsageId: string,
) =>
  `/api/v1/knowledge/documents/${identifier(documentId)}/file${query({ generationId: identifier(generationId), sourceUsageId: identifier(sourceUsageId) })}`
