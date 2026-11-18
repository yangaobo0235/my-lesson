import { afterEach, describe, expect, it, vi } from 'vitest'
import { getFeedback, putFeedback } from '../../src/api/diagnosis/feedbackApi'

/** 私有评价不继承旧共享协议，采纳与版本按原输入提交。 @author yangaobo */
describe('私有运行反馈', () => {
  afterEach(() => vi.unstubAllGlobals())
  const saved = {
    id: 'a'.repeat(32),
    runId: 'b'.repeat(32),
    rating: 'UNHELPFUL',
    adoption: 'NOT_USED',
    issueCategory: 'MISSING_CONTEXT',
    comment: '缺少当前系统版本',
    rowVersion: '0',
  }
  function respond(data: unknown) {
    const fetch = vi.fn().mockImplementation(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({
            code: 0,
            message: '成功',
            data,
            traceId: 'trace',
          }),
          { headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    )
    vi.stubGlobal('fetch', fetch)
    return fetch
  }
  it('未评价明确返回null', async () => {
    respond(null)
    expect(await getFeedback(saved.runId)).toBeNull()
  })
  it('旧共享协议缺少运行或采纳信息时拒绝响应', async () => {
    respond({ ...saved, adoption: undefined, status: 'RECORDED' })
    await expect(getFeedback(saved.runId)).rejects.toThrow()
  })
  it('沿原评价内容和行版本重试', async () => {
    const fetch = respond(saved)
    const body = {
      rating: 'UNHELPFUL' as const,
      adoption: 'NOT_USED' as const,
      issueCategory: 'MISSING_CONTEXT' as const,
      comment: saved.comment,
      rowVersion: '0',
    }
    await putFeedback(saved.runId, body, 'csrf')
    await putFeedback(saved.runId, body, 'csrf')
    for (const [url, options] of fetch.mock.calls) {
      expect(url).toContain(`/ai/runs/${saved.runId}/feedback`)
      expect(options.method).toBe('PUT')
      expect(JSON.parse(options.body)).toEqual(body)
    }
  })
})
