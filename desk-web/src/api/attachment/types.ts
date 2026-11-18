import {
  bool,
  choices,
  decimal,
  identifier,
  nullable,
  record,
  text,
} from '../validation'

/** 附件只接收安全投影，原件定位不进入浏览器。 @author yangaobo */
export function attachment(value: unknown) {
  const o = record(value)
  return {
    id: identifier(o.id),
    ticketId: identifier(o.ticketId),
    uploadedBy: identifier(o.uploadedBy),
    clientUploadId: identifier(o.clientUploadId),
    originalFileName: text(o.originalFileName),
    contentFormat: choices('TEXT', 'LOG', 'JSON')(o.contentFormat),
    visibility: choices('PUBLIC', 'INTERNAL')(o.visibility),
    status: choices(
      'UPLOADING',
      'READY',
      'FAILED',
      'UNKNOWN',
      'DELETED',
    )(o.status),
    fileSizeBytes: decimal(o.fileSizeBytes),
    fileSha256: text(o.fileSha256),
    redactionVersion: decimal(o.redactionVersion),
    redactedSha256: nullable(text)(o.redactedSha256),
    aiUseAllowed: bool(o.aiUseAllowed),
    confirmedBy: nullable(identifier)(o.confirmedBy),
    confirmedAt: nullable(text)(o.confirmedAt),
    deletedAt: nullable(text)(o.deletedAt),
    errorCode: nullable(text)(o.errorCode),
    createdAt: text(o.createdAt),
    updatedAt: text(o.updatedAt),
    rowVersion: decimal(o.rowVersion),
  }
}
export type Attachment = ReturnType<typeof attachment>
export type AttachmentUploadForm = {
  clientUploadId: string
  contentFormat: Attachment['contentFormat']
  visibility: Attachment['visibility']
}
export type AttachmentAction =
  | {
      attachmentId: string
      name: 'confirm-redaction'
      form: {
        rowVersion: string
        clientActionId: string
        fileSha256: string
        redactedText: string
        allowAiUse: boolean
      }
    }
  | {
      attachmentId: string
      name: 'delete'
      form: { rowVersion: string; clientActionId: string }
    }
export const attachmentStatusNames: Record<Attachment['status'], string> = {
  UPLOADING: '上传结果待核对',
  READY: '原件已就绪',
  FAILED: '上传失败',
  UNKNOWN: '上传结果未知',
  DELETED: '已撤销访问',
}
