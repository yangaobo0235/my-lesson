import { onScopeDispose, ref } from 'vue'
import { getRun, subscribeRun, type Run } from '../api/diagnosis/diagnosisApi'

/** 订阅与核对共用同一生命周期，迟到状态不能覆盖新会话。 @author yangaobo */
export function useRunSubscription(options: {
  signal: AbortSignal
  csrf: () => Promise<string>
  status: (id: string, status: Run['status']) => void
  notice: (message: string) => void
  snapshot: (value: Run) => Promise<void>
  failed: (failure: unknown) => void
}) {
  const streaming = ref(false)
  let controller: AbortController | undefined
  let epoch = 0

  /** 停止拥有的订阅和最后核对；不把取消等待当成取消后台运行。 */
  function stop() {
    epoch++
    controller?.abort()
    streaming.value = false
  }

  /** 断流或正常结束均核对原运行，绝不自动提交新问题。 */
  async function follow(id: string, current: () => boolean) {
    stop()
    const version = epoch
    const owned = new AbortController()
    controller = owned
    const signal = AbortSignal.any([owned.signal, options.signal])
    const live = () => !signal.aborted && version === epoch && current()
    streaming.value = true
    try {
      const csrf = await options.csrf()
      if (!live()) return
      const complete = await subscribeRun(
        id,
        csrf,
        signal,
        (status) => {
          if (live()) options.status(id, status)
        },
        (tool) => {
          if (live()) options.notice(`只读工具 ${tool.name}：${tool.status}`)
        },
      )
      if (live() && !complete)
        options.notice('订阅已断开，正在核对原运行；没有重新提交问题。')
    } catch {
      if (live())
        options.notice(
          '实时连接已中断，正在查询本轮结果；如仍在执行，请点击“读取持久状态”。',
        )
    } finally {
      if (live()) {
        streaming.value = false
        try {
          const value = await getRun(id, signal)
          if (live()) await options.snapshot(value)
        } catch (failure) {
          if (live()) options.failed(failure)
        }
      }
    }
  }

  onScopeDispose(stop)
  return { streaming, follow, stop }
}
