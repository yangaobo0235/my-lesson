import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  attachmentByRequest,
  changeAttachment,
  uploadAttachment,
} from '../../src/api/attachment/attachmentApi'
import { attachment } from '../../src/api/attachment/types'

/** 附件协议不把就绪等同授权，重试保留原键、原版本及确认正文。 @author yangaobo */
describe('附件确认与上传回执', () => {
  afterEach(() => vi.unstubAllGlobals())
  const value = {
    id: 'a'.repeat(32),
    ticketId: 'b'.repeat(32),
    uploadedBy: 'c'.repeat(32),
    clientUploadId: 'd'.repeat(32),
    originalFileName: 'printer.log',
    contentFormat: 'LOG',
    visibility: 'PUBLIC',
    status: 'READY',
    fileSizeBytes: '24',
    fileSha256: 'e'.repeat(64),
    redactionVersion: '0',
    redactedSha256: null,
    aiUseAllowed: false,
    confirmedBy: null,
    confirmedAt: null,
    deletedAt: null,
    errorCode: null,
    createdAt: '2026-11-16T08:00:00Z',
    updatedAt: '2026-11-16T08:00:01Z',
    rowVersion: '1',
  }
  function respond(data: unknown) {
    return new Response(
      JSON.stringify({ code: 0, message: '成功', data, traceId: 'trace' }),
      { headers: { 'Content-Type': 'application/json' } },
    )
  }
  it('就绪原件仍须人工确认，缺失授权字段不能默认允许', () => {
    expect(attachment(value).aiUseAllowed).toBe(false)
    expect(() => attachment({ ...value, aiUseAllowed: undefined })).toThrow()
    expect(() => attachment({ ...value, status: 'SUCCESS' })).toThrow()
  })
  it('上传用 multipart 元信息；查询未知结果不重发文件', async () => {
    const fetch = vi
      .fn()
      .mockImplementation(() => Promise.resolve(respond(value)))
    vi.stubGlobal('fetch', fetch)
    const metadata = {
      clientUploadId: value.clientUploadId,
      contentFormat: 'LOG' as const,
      visibility: 'PUBLIC' as const,
    }
    await uploadAttachment(
      value.ticketId,
      metadata,
      new File(['text'], 'printer.log'),
      'csrf',
    )
    const options = fetch.mock.calls[0]?.[1]
    expect(options.body).toBeInstanceOf(FormData)
    expect(JSON.parse(await options.body.get('metadata').text())).toEqual(
      metadata,
    )
    expect(options.headers['Content-Type']).toBeUndefined()
    await attachmentByRequest(value.clientUploadId)
    expect(fetch.mock.calls[1]?.[0]).toBe(
      `/api/v1/attachments/by-request/${value.clientUploadId}`,
    )
    expect(fetch.mock.calls[1]?.[1].method).toBe('GET')
    expect(fetch.mock.calls[1]?.[1].body).toBeUndefined()
  })
  it('脱敏确认绑定原摘要及完整正文，不用当前资源版本覆盖原请求', async () => {
    const result = {
      receiptId: '1'.repeat(32),
      eventId: '2'.repeat(32),
      actionType: 'CONFIRM_REDACTION',
      clientActionId: '3'.repeat(32),
      ticketId: value.ticketId,
      ticketRowVersion: '4',
      sequenceNumber: '5',
      toStatus: 'PROCESSING',
      bookingId: null,
      bookingStatus: null,
      bookingRowVersion: null,
      sourceDraftId: null,
      sourceDraftRootId: null,
      attachmentId: value.id,
      attachmentStatus: 'READY',
      attachmentRowVersion: '2',
      redactionVersion: '1',
      aiUseAllowed: true,
      resultCode: 'APPLIED',
    }
    const fetch = vi
      .fn()
      .mockImplementation(() => Promise.resolve(respond(result)))
    vi.stubGlobal('fetch', fetch)
    const action = {
      attachmentId: value.id,
      name: 'confirm-redaction' as const,
      form: {
        clientActionId: result.clientActionId,
        rowVersion: '1',
        fileSha256: value.fileSha256,
        redactedText: '脱敏后连接失败',
        allowAiUse: true,
      },
    }
    await changeAttachment(value.ticketId, action, 'csrf')
    await changeAttachment(value.ticketId, action, 'csrf')
    for (const call of fetch.mock.calls)
      expect(JSON.parse(call[1].body)).toEqual(action.form)
  })
})
