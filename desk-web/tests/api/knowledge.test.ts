import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  changeDocumentSafety,
  document,
} from '../../src/api/knowledge/knowledgeApi'
import { procedureCheck } from '../../src/api/knowledge/procedures'
import {
  listReadableDocuments,
  readableOriginalLink,
} from '../../src/api/knowledge/knowledgeReadApi'
import { listDocumentEvents } from '../../src/api/knowledge/documentAuditApi'

/** 资料安全状态与原动作重试必须完整通过跨端协议。 @author yangaobo */
describe('知识修订安全合同', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('前置答案要求完整保留，旧定义缺省不虚构条件', () => {
    const check = {
      checkKey: 'recover',
      roles: ['CUSTOMER'],
      instruction: '仅调整本人当前选择',
      answerSchema: {
        schemaVersion: 1,
        kind: 'CHOICE',
        options: ['已完成', '无法完成'],
        maxLength: 2000,
      },
      requiredFields: [],
      prerequisiteCheckKeys: ['observe'],
      riskLevel: 'LOW',
      stopConditions: [],
      sourceLocator: {
        kind: 'MARKDOWN',
        anchor: 'recover',
        lineStart: 2,
        lineEnd: 4,
      },
    }
    const guard = [{ checkKey: 'observe', allowedValues: ['允许自行调整'] }]
    expect(
      procedureCheck({ ...check, prerequisiteAnswers: guard })
        .prerequisiteAnswers,
    ).toEqual(guard)
    expect(procedureCheck(check).prerequisiteAnswers).toEqual([])
    expect(() =>
      procedureCheck({ ...check, prerequisiteAnswers: null }),
    ).toThrow()
  })
  const revision = {
    id: 'a'.repeat(32),
    seriesId: 'b'.repeat(32),
    clientUploadId: 'c'.repeat(32),
    title: '网络打印规程',
    revisionNumber: 1,
    contentFormat: 'MARKDOWN',
    originalFileName: 'printer.md',
    fileSizeBytes: '128',
    fileSha256: 'd'.repeat(64),
    sourceUrl: null,
    sourceReviewNote: null,
    revisionLabel: null,
    issuedOn: null,
    applicabilityNote: null,
    procedureDefinition: null,
    procedureSha256: null,
    procedureValidatedSha256: null,
    procedureValidatedAt: null,
    procedureValidatedBy: null,
    publicationStatus: 'WITHDRAWN',
    accessStatus: 'ALLOWED',
    accessReason: '恢复访问但保留风险撤回',
    withdrawalReason: '原指导存在风险',
    currentJobId: 'e'.repeat(32),
    rowVersion: '7',
    rules: [],
  }
  it('恢复访问后仍保留风险撤回状态及依据', () => {
    const value = document(revision)
    expect(value.publicationStatus).toBe('WITHDRAWN')
    expect(value.accessStatus).toBe('ALLOWED')
    expect(value.withdrawalReason).toBe('原指导存在风险')
  })
  it('缺少访问状态的响应不能默认为允许', () => {
    expect(() => document({ ...revision, accessStatus: undefined })).toThrow()
  })
  it('知识列表和原件沿同一个适用上下文请求', async () => {
    const fetch = vi.fn(
      async () =>
        new Response(
          JSON.stringify({
            code: 0,
            message: '成功',
            traceId: 'trace',
            data: {
              records: [{ ...revision, publicationStatus: 'PUBLISHED' }],
              total: '1',
              pageNum: 1,
              pageSize: 10,
            },
          }),
          { headers: { 'Content-Type': 'application/json' } },
        ),
    )
    vi.stubGlobal('fetch', fetch)
    const context = { serviceItemId: revision.seriesId, osName: 'Windows 11' }
    expect((await listReadableDocuments(context, 1)).records).toHaveLength(1)
    expect(fetch).toHaveBeenCalledWith(
      `/api/v1/knowledge/documents?serviceItemId=${revision.seriesId}&environment.osName=Windows+11&pageNum=1`,
      expect.objectContaining({ method: 'GET' }),
    )
    expect(readableOriginalLink(revision.id, context)).toBe(
      `/api/v1/knowledge/documents/${revision.id}/file?serviceItemId=${revision.seriesId}&environment.osName=Windows+11`,
    )
  })
  it('发布审计使用修订筛选和受保护的管理查询', async () => {
    const fetch = vi.fn(
      async () =>
        new Response(
          JSON.stringify({
            code: 0,
            message: '成功',
            traceId: 'trace',
            data: {
              records: [
                {
                  id: revision.clientUploadId,
                  documentId: revision.id,
                  actorId: revision.seriesId,
                  eventType: 'PUBLISHED',
                  relatedDocumentId: null,
                  details: { schema_version: 1 },
                  createdAt: '2026-11-18T00:00:00Z',
                },
              ],
              total: '1',
              pageNum: 1,
              pageSize: 10,
            },
          }),
          { headers: { 'Content-Type': 'application/json' } },
        ),
    )
    vi.stubGlobal('fetch', fetch)
    expect(
      (await listDocumentEvents(revision.id, 1, 'csrf')).records[0]?.eventType,
    ).toBe('PUBLISHED')
    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/admin/document-event/list',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ documentId: revision.id, pageNum: 1 }),
        headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf' }),
      }),
    )
  })
  it('重试发送原动作键、预期版本与原因，不使用最新资料快照覆盖', async () => {
    const fetch = vi.fn().mockImplementation(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({
            code: 0,
            message: '成功',
            data: revision,
            traceId: 'trace',
          }),
          { headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    )
    vi.stubGlobal('fetch', fetch)
    const input = {
      id: revision.id,
      rowVersion: '4',
      clientActionId: 'f'.repeat(32),
      reason: '原指导存在风险',
    }
    await changeDocumentSafety('withdraw', input, 'csrf')
    await changeDocumentSafety('withdraw', input, 'csrf')
    for (const call of fetch.mock.calls) {
      expect(call[0]).toContain('/admin/document/withdraw')
      expect(JSON.parse(call[1].body)).toEqual(input)
    }
  })
})
