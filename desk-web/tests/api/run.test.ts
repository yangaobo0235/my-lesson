import { afterEach, describe, expect, it, vi } from 'vitest'
import { citation, subscribeRun } from '../../src/api/diagnosis/runApi'

/** 断流只触发原运行查询；分块边界不能改变事件身份和UTF-8正文。 @author yangaobo */
describe('持久运行SSE订阅', () => {
  afterEach(() => vi.unstubAllGlobals())
  const id = 'a'.repeat(32)
  const running = {
    status: 'RUNNING',
    executionPhase: 'MODEL',
    iterationNumber: 1,
  }
  const done = { status: 'SUCCEEDED', sourcesHidden: false }
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
      frame('stage', 1, running) +
        frame('answer', 2, {
          answer: '中文反馈',
          answerType: 'CLARIFICATION',
        }) +
        frame('done', 3, done),
    )
    const fetch = response(Array.from(bytes, (byte) => Uint8Array.of(byte)))
    const stage = vi.fn()
    await expect(
      subscribeRun(id, 'csrf', new AbortController().signal, stage),
    ).resolves.toBe(true)
    expect(stage).toHaveBeenCalledExactlyOnceWith(running)
    expect(fetch).toHaveBeenCalledTimes(1)
  })
  it('没有done的连接结束不判完成，也不重发付费请求', async () => {
    const fetch = response([
      new TextEncoder().encode(frame('stage', 1, running)),
    ])
    await expect(
      subscribeRun(id, 'csrf', new AbortController().signal, vi.fn()),
    ).resolves.toBe(false)
    expect(fetch).toHaveBeenCalledTimes(1)
  })
  it('取证工具返回受控拒绝后仍可收到运行终态', async () => {
    const tool = {
      number: 1,
      name: 'read_task_context',
      status: 'INVALID_ARGUMENT',
      resultSummary: null,
      sourceUsageIds: [],
    }
    const observed = vi.fn()
    response([
      new TextEncoder().encode(frame('tool', 1, tool) + frame('done', 2, done)),
    ])
    await expect(
      subscribeRun(id, 'csrf', new AbortController().signal, vi.fn(), observed),
    ).resolves.toBe(true)
    expect(observed).toHaveBeenCalledExactlyOnceWith(tool)
  })
  it('在运行状态不变时交付真实的执行阶段与迭代', async () => {
    const later = { ...running, executionPhase: 'TOOL', iterationNumber: 2 }
    const stage = vi.fn()
    response([
      new TextEncoder().encode(
        frame('stage', 1, running) +
          frame('stage', 2, later) +
          frame('task', 3, {
            taskId: id,
            status: 'WAITING_USER',
            taskStateVersion: '5',
            pendingCheckIds: [id],
          }) +
          frame('done', 4, done),
      ),
    ])
    await expect(
      subscribeRun(id, 'csrf', new AbortController().signal, stage),
    ).resolves.toBe(true)
    expect(stage.mock.calls).toEqual([[running], [later]])
  })
  it('拒绝伪装成完成事件的运行中状态', async () => {
    response([
      new TextEncoder().encode(
        frame('done', 1, { status: 'RUNNING', sourcesHidden: false }),
      ),
    ])
    await expect(
      subscribeRun(id, 'csrf', new AbortController().signal, vi.fn()),
    ).rejects.toThrow()
  })
  it.each([
    frame('stage', 1, running, 'b'.repeat(32)),
    frame('stage', 1, running) + frame('done', 1, done),
  ])('拒绝错运行或重复序号', async (body) => {
    response([new TextEncoder().encode(body)])
    await expect(
      subscribeRun(id, 'csrf', new AbortController().signal, vi.fn()),
    ).rejects.toThrow('身份或顺序')
  })
})

describe('运行来源定位', () => {
  const source = {
    number: 1,
    sourceUsageId: 'a'.repeat(32),
    title: '诊断资料',
    excerpt: '实际读取内容',
    documentId: null,
  }

  it.each([
    [{ kind: 'PDF', page_number: 3 }, '物理第 3 页'],
    [
      { kind: 'MARKDOWN', anchor: 'check', line_start: 7, line_end: 8 },
      '#check · 第 7–8 行',
    ],
    [
      {
        kind: 'ATTACHMENT',
        attachment_id: 'b'.repeat(32),
        redaction_version: '1',
        offset: 0,
        end_offset: 200,
      },
      '脱敏附件 · 字符区间 0–200',
    ],
  ])('解码审核规程及脱敏附件的真实定位', (locator, expected) => {
    expect(citation({ ...source, locator }).location).toBe(expected)
  })
})
