import { effectScope } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type {
  IndexJob,
  KnowledgeDocument,
} from '../../../src/api/knowledge/knowledgeApi'
import { useIndexPolling } from '../../../src/views/knowledge/useIndexPolling'

const oldId = 'a'.repeat(32)
const newId = 'b'.repeat(32)
const oldJobId = 'c'.repeat(32)
const newJobId = 'd'.repeat(32)

/** 构造同一修订的真实索引状态组合。 */
function job(id: string, documentId: string, settled = false): IndexJob {
  return {
    id,
    documentId,
    clientRequestId: id,
    submitState: 'COMMITTED',
    cancelState: 'NONE',
    aiAccepted: true,
    aiStatus: settled ? 'SUCCEEDED' : 'RUNNING',
    aiStage: null,
    parserState: settled ? 'SETTLED' : 'PARSING',
    replicasVerified: settled,
    lastErrorCode: null,
  }
}

/** 留住请求的交付时机，模拟取消后仍到达应用层的响应。 */
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

function setup() {
  const scope = effectScope()
  const options = {
    signal: new AbortController().signal,
    readJob: vi.fn<(id: string, signal: AbortSignal) => Promise<IndexJob>>(),
    readDocument:
      vi.fn<(id: string, signal: AbortSignal) => Promise<KnowledgeDocument>>(),
    updateJob: vi.fn(),
    updateDocument: vi.fn(),
    failed: vi.fn(),
  }
  const polling = scope.run(() => useIndexPolling(options))!
  return { scope, options, ...polling }
}

/** 索引轮询不重叠执行，迟到响应不能覆盖新修订。 @author yangaobo */
describe('知识修订索引轮询', () => {
  afterEach(() => vi.useRealTimers())

  it('切换修订中止已收敛旧任务的详情刷新', async () => {
    vi.useFakeTimers()
    const { scope, options, start } = setup()
    const detail = deferred<KnowledgeDocument>()
    options.readJob
      .mockResolvedValueOnce(job(oldJobId, oldId, true))
      .mockResolvedValueOnce(job(newJobId, newId))
    options.readDocument.mockReturnValueOnce(detail.promise)

    start({ id: oldId, currentJobId: oldJobId })
    await Promise.resolve()
    const oldSignal = options.readDocument.mock.calls[0]![1]
    start({ id: newId, currentJobId: newJobId })
    expect(oldSignal.aborted).toBe(true)
    detail.resolve({ id: oldId } as KnowledgeDocument)
    await Promise.resolve()

    expect(options.updateDocument).not.toHaveBeenCalled()
    expect(options.updateJob).toHaveBeenLastCalledWith(job(newJobId, newId))
    expect(options.failed).not.toHaveBeenCalled()
    scope.stop()
  })

  it('完成当前读取后才安排下一次轮询', async () => {
    vi.useFakeTimers()
    const { scope, options, start } = setup()
    const pending = deferred<IndexJob>()
    options.readJob.mockReturnValue(pending.promise)

    start({ id: oldId, currentJobId: oldJobId })
    await vi.advanceTimersByTimeAsync(6_000)
    expect(options.readJob).toHaveBeenCalledTimes(1)
    pending.resolve(job(oldJobId, oldId))
    await Promise.resolve()
    await vi.advanceTimersByTimeAsync(2_999)
    expect(options.readJob).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(options.readJob).toHaveBeenCalledTimes(2)
    scope.stop()
  })
})
