import { effectScope } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useGenerationSubscription } from '../../src/composables/useGenerationSubscription'
import {
  getGeneration,
  subscribeGeneration,
  type Generation,
} from '../../src/api/diagnosis/diagnosisApi'

vi.mock('../../src/api/diagnosis/diagnosisApi', () => ({
  getGeneration: vi.fn(),
  subscribeGeneration: vi.fn(),
}))

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
    csrf: vi.fn().mockResolvedValue('csrf'),
    status: vi.fn(),
    notice: vi.fn(),
    snapshot: vi.fn().mockResolvedValue(undefined),
    failed: vi.fn(),
  }
  const subscription = scope.run(() => useGenerationSubscription(options))!
  return { scope, options, ...subscription }
}

beforeEach(() => vi.resetAllMocks())

describe('生成订阅的资源生命周期', () => {
  it('断流只核对原运行并交付快照', async () => {
    const fixture = setup()
    const snapshot = { id: 'original', status: 'SUCCEEDED' } as Generation
    vi.mocked(subscribeGeneration).mockResolvedValue(false)
    vi.mocked(getGeneration).mockResolvedValue(snapshot)
    await fixture.follow('original', () => true)
    expect(getGeneration).toHaveBeenCalledWith(
      'original',
      expect.any(AbortSignal),
    )
    expect(fixture.options.snapshot).toHaveBeenCalledWith(snapshot)
    expect(fixture.streaming.value).toBe(false)
    fixture.scope.stop()
  })

  it('新订阅开始后，旧请求的finally和迟到事件不能覆盖新状态', async () => {
    const fixture = setup()
    const first = deferred<boolean>(),
      second = deferred<boolean>()
    vi.mocked(subscribeGeneration)
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)
    const old = fixture.follow('old', () => true)
    await Promise.resolve()
    const latest = fixture.follow('new', () => true)
    await Promise.resolve()
    const oldCall = vi.mocked(subscribeGeneration).mock.calls[0]!
    oldCall[3]('SUCCEEDED')
    first.resolve(false)
    await old
    expect(fixture.options.status).not.toHaveBeenCalled()
    expect(getGeneration).not.toHaveBeenCalled()
    expect(fixture.streaming.value).toBe(true)
    fixture.stop()
    second.resolve(false)
    await latest
    fixture.scope.stop()
  })

  it('账号或资源切换中止最后核对，迟到快照不再交付', async () => {
    const fixture = setup()
    const pending = deferred<Generation>()
    vi.mocked(subscribeGeneration).mockResolvedValue(true)
    vi.mocked(getGeneration).mockReturnValue(pending.promise)
    const operation = fixture.follow('old', () => true)
    await Promise.resolve()
    await Promise.resolve()
    fixture.stop()
    pending.resolve({ id: 'old' } as Generation)
    await operation
    expect(fixture.options.snapshot).not.toHaveBeenCalled()
    expect(fixture.options.failed).not.toHaveBeenCalled()
    expect(fixture.streaming.value).toBe(false)
    fixture.scope.stop()
  })

  it('作用域结束时取消所属订阅', async () => {
    const fixture = setup()
    const pending = deferred<boolean>()
    vi.mocked(subscribeGeneration).mockReturnValue(pending.promise)
    const operation = fixture.follow('run', () => true)
    await Promise.resolve()
    const signal = vi.mocked(subscribeGeneration).mock.calls[0]![2]
    fixture.scope.stop()
    expect(signal.aborted).toBe(true)
    pending.resolve(false)
    await operation
    expect(getGeneration).not.toHaveBeenCalled()
  })
})
