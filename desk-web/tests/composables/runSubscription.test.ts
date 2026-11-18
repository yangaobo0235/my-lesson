import { effectScope } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useRunSubscription } from '../../src/composables/useRunSubscription'
import {
  getRun,
  subscribeRun,
  type Run,
} from '../../src/api/diagnosis/diagnosisApi'

vi.mock('../../src/api/diagnosis/diagnosisApi', () => ({
  getRun: vi.fn(),
  subscribeRun: vi.fn(),
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
  const subscription = scope.run(() => useRunSubscription(options))!
  return { scope, options, ...subscription }
}

beforeEach(() => vi.resetAllMocks())

describe('生成订阅的资源生命周期', () => {
  it('网络异常不展示浏览器原始错误，仍查询原运行', async () => {
    const fixture = setup()
    vi.mocked(subscribeRun).mockRejectedValue(new TypeError('Failed to fetch'))
    vi.mocked(getRun).mockResolvedValue({
      id: 'original',
      status: 'SUCCEEDED',
    } as Run)
    await fixture.follow('original', () => true)
    expect(fixture.options.notice).toHaveBeenCalledWith(
      expect.stringContaining('实时连接已中断'),
    )
    expect(getRun).toHaveBeenCalledWith('original', expect.any(AbortSignal))
    expect(fixture.options.failed).not.toHaveBeenCalled()
    fixture.scope.stop()
  })
  it('断流只核对原运行并交付快照', async () => {
    const fixture = setup()
    const snapshot = { id: 'original', status: 'SUCCEEDED' } as Run
    vi.mocked(subscribeRun).mockResolvedValue(false)
    vi.mocked(getRun).mockResolvedValue(snapshot)
    await fixture.follow('original', () => true)
    expect(getRun).toHaveBeenCalledWith('original', expect.any(AbortSignal))
    expect(fixture.options.snapshot).toHaveBeenCalledWith(snapshot)
    expect(fixture.streaming.value).toBe(false)
    fixture.scope.stop()
  })

  it('新订阅开始后，旧请求的finally和迟到事件不能覆盖新状态', async () => {
    const fixture = setup()
    const first = deferred<boolean>(),
      second = deferred<boolean>()
    vi.mocked(subscribeRun)
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)
    const old = fixture.follow('old', () => true)
    await Promise.resolve()
    const latest = fixture.follow('new', () => true)
    await Promise.resolve()
    const oldCall = vi.mocked(subscribeRun).mock.calls[0]!
    oldCall[3]('SUCCEEDED')
    first.resolve(false)
    await old
    expect(fixture.options.status).not.toHaveBeenCalled()
    expect(getRun).not.toHaveBeenCalled()
    expect(fixture.streaming.value).toBe(true)
    fixture.stop()
    second.resolve(false)
    await latest
    fixture.scope.stop()
  })

  it('账号或资源切换中止最后核对，迟到快照不再交付', async () => {
    const fixture = setup()
    const pending = deferred<Run>()
    vi.mocked(subscribeRun).mockResolvedValue(true)
    vi.mocked(getRun).mockReturnValue(pending.promise)
    const operation = fixture.follow('old', () => true)
    await Promise.resolve()
    await Promise.resolve()
    fixture.stop()
    pending.resolve({ id: 'old' } as Run)
    await operation
    expect(fixture.options.snapshot).not.toHaveBeenCalled()
    expect(fixture.options.failed).not.toHaveBeenCalled()
    expect(fixture.streaming.value).toBe(false)
    fixture.scope.stop()
  })

  it('作用域结束时取消所属订阅', async () => {
    const fixture = setup()
    const pending = deferred<boolean>()
    vi.mocked(subscribeRun).mockReturnValue(pending.promise)
    const operation = fixture.follow('run', () => true)
    await Promise.resolve()
    const signal = vi.mocked(subscribeRun).mock.calls[0]![2]
    fixture.scope.stop()
    expect(signal.aborted).toBe(true)
    pending.resolve(false)
    await operation
    expect(getRun).not.toHaveBeenCalled()
  })
})
