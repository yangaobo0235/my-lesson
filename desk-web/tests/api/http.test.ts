import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, request } from '../../src/api/http'
import { decimal, identifier } from '../../src/api/validation'

/** 验证传输边界，成功状态不能掩盖业务失败或格式错误。 @author yangaobo */
describe('HTTP 与数值合同', () => {
  afterEach(() => vi.unstubAllGlobals())

  function respond(code: number, status = 200, traceId = 'trace') {
    const fetch = vi
      .fn()
      .mockResolvedValue(
        new Response(
          JSON.stringify({ code, message: '安全提示', data: '42', traceId }),
          { status },
        ),
      )
    vi.stubGlobal('fetch', fetch)
    return fetch
  }

  it('只解包 HTTP 成功且 code=0 的响应', async () => {
    respond(0)
    await expect(request('tickets', decimal)).resolves.toBe('42')
  })

  it.each([200, 1, 1004])('拒绝非零业务码 %s', async (code) => {
    respond(code)
    await expect(request('tickets', decimal)).rejects.toMatchObject({ code })
  })

  it('HTTP 失败不能因 code=0 被当成成功', async () => {
    respond(0, 503)
    await expect(request('tickets', decimal)).rejects.toBeInstanceOf(ApiError)
  })

  it('成功响应也必须有追踪号', async () => {
    respond(0, 200, '')
    await expect(request('tickets', decimal)).rejects.toThrow('追踪号')
  })

  it('写入保留同源凭据及原表单，不自动重试', async () => {
    const fetch = respond(1004, 409)
    await expect(
      request('tickets', decimal, {
        body: { clientRequestId: 'original' },
        csrf: 'csrf',
      }),
    ).rejects.toBeInstanceOf(ApiError)
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/tickets',
      expect.objectContaining({
        credentials: 'same-origin',
        method: 'POST',
        body: '{"clientRequestId":"original"}',
      }),
    )
  })

  it('调用方取消传播至真正的 fetch 信号', async () => {
    const fetch = respond(0)
    const controller = new AbortController()
    await request('tickets', decimal, { signal: controller.signal })
    const signal: AbortSignal = fetch.mock.calls[0]?.[1].signal
    controller.abort()
    expect(signal.aborted).toBe(true)
  })

  it('标识和长整数不接受数字或浮点值', () => {
    expect(decimal('9223372036854775807')).toBe('9223372036854775807')
    expect(() => decimal(42)).toThrow()
    expect(() => decimal('1.2')).toThrow()
    expect(() => identifier('wrong-id')).toThrow()
  })
})
