import { afterEach, describe, expect, it, vi } from 'vitest'
import { subscribeRun } from '../../src/api/diagnosis/diagnosisApi'

/** 断流只触发原运行查询；分块边界不能改变事件身份和UTF-8正文。 @author yangaobo */
describe('持久运行SSE订阅', () => {
  afterEach(() => vi.unstubAllGlobals())
  const id = 'a'.repeat(32)
  function response(parts: Uint8Array[]) {
    let index = 0
    const stream = new ReadableStream<Uint8Array>({
      pull(controller) {
        if (index === parts.length) controller.close()
        else controller.enqueue(parts[index++]!)
      },
    })
    const fetch = vi.fn().mockResolvedValue(
      new Response(stream, {
        headers: { 'Content-Type': 'text/event-stream' },
      }),
    )
    vi.stubGlobal('fetch', fetch)
    return fetch
  }
  function frame(name: string, sequence: number, data: unknown, runId = id) {
    return `event: ${name}\r\ndata: ${JSON.stringify({ runId, sequence, data })}\r\n\r\n`
  }
  it('跨字节拆分中文及CRLF仍只交付有效有序事件', async () => {
    const bytes = new TextEncoder().encode(
      frame('stage', 1, { status: 'RUNNING' }) +
        frame('answer', 2, {
          answer: '中文反馈',
          answerType: 'CLARIFICATION',
        }) +
        frame('done', 3, { status: 'SUCCEEDED' }),
    )
    const fetch = response(Array.from(bytes, (byte) => Uint8Array.of(byte)))
    const stage = vi.fn()
    await expect(
      subscribeRun(id, 'csrf', new AbortController().signal, stage),
    ).resolves.toBe(true)
    expect(stage).toHaveBeenCalledExactlyOnceWith('RUNNING')
    expect(fetch).toHaveBeenCalledTimes(1)
  })
  it('没有done的连接结束不判完成，也不重发付费请求', async () => {
    const fetch = response([
      new TextEncoder().encode(frame('stage', 1, { status: 'RUNNING' })),
    ])
    await expect(
      subscribeRun(id, 'csrf', new AbortController().signal, vi.fn()),
    ).resolves.toBe(false)
    expect(fetch).toHaveBeenCalledTimes(1)
  })
  it.each([
    frame('stage', 1, { status: 'RUNNING' }, 'b'.repeat(32)),
    frame('stage', 1, { status: 'RUNNING' }) +
      frame('done', 1, { status: 'SUCCEEDED' }),
  ])('拒绝错运行或重复序号', async (body) => {
    response([new TextEncoder().encode(body)])
    await expect(
      subscribeRun(id, 'csrf', new AbortController().signal, vi.fn()),
    ).rejects.toThrow('身份或顺序')
  })
})
