import { query, request } from '../http'
import { page, identifier } from '../validation'
import { receipt } from '../ticket/types'
import {
  attachment,
  type AttachmentAction,
  type AttachmentUploadForm,
} from './types'

/** 同一上传保留元信息、文件及请求键；底层不重试外写。 @author yangaobo */
export function uploadAttachment(
  ticketId: string,
  metadata: AttachmentUploadForm,
  file: File,
  csrf: string,
  signal?: AbortSignal,
) {
  const form = new FormData()
  form.append(
    'metadata',
    new Blob([JSON.stringify(metadata)], { type: 'application/json' }),
  )
  form.append('file', file)
  return request(`tickets/${identifier(ticketId)}/attachments`, attachment, {
    form,
    csrf,
    signal,
    timeoutMs: 30_000,
  })
}
/** 查询原上传意图，服务端仅对登记原对象做读取核验。 @author yangaobo */
export const attachmentByRequest = (key: string, signal?: AbortSignal) =>
  request(`attachments/by-request/${identifier(key)}`, attachment, { signal })
/** 附件列表由服务端按当前权限分页。 @author yangaobo */
export const listAttachments = (
  ticketId: string,
  pageNum: number,
  signal?: AbortSignal,
) =>
  request(
    `tickets/${identifier(ticketId)}/attachments${query({ pageNum })}`,
    page(attachment),
    { signal },
  )
/** 人工确认及撤销沿统一动作回执核对，不重传新的动作键。 @author yangaobo */
export const changeAttachment = (
  ticketId: string,
  action: AttachmentAction,
  csrf: string,
  signal?: AbortSignal,
) =>
  request(
    `tickets/${identifier(ticketId)}/attachments/${identifier(action.attachmentId)}/${action.name}`,
    receipt,
    { body: action.form, csrf, signal },
  )
/** 原件仅使用后端同源下载入口，不从内容推导地址。 @author yangaobo */
export const attachmentFileLink = (ticketId: string, attachmentId: string) =>
  `/api/v1/tickets/${identifier(ticketId)}/attachments/${identifier(attachmentId)}/file`
