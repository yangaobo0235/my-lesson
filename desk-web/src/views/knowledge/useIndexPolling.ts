import { onScopeDispose } from 'vue'
import type {
  IndexJob,
  KnowledgeDocument,
} from '../../api/knowledge/knowledgeApi'

const POLL_INTERVAL_MS = 3_000
const POLL_BUDGET_MS = 300_000

/** 索引执行、解析和取消事务同时收敛后才允许建立新索引。 @author yangaobo */
export function isIndexSettled(job: IndexJob) {
  return (
    job.submitState === 'ABORTED' ||
    (['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(job.aiStatus ?? '') &&
      ['NOT_REQUIRED', 'NOT_SUBMITTED', 'SETTLED'].includes(
        job.parserState ?? '',
      ) &&
      job.cancelState !== 'PREPARING')
  )
}

/** 一次资料选择拥有一个轮询作用域，读完后才排下次，切换时一起释放详情查询。 @author yangaobo */
export function useIndexPolling(options: {
  signal: AbortSignal
  readJob: (id: string, signal: AbortSignal) => Promise<IndexJob>
  readDocument: (id: string, signal: AbortSignal) => Promise<KnowledgeDocument>
  updateJob: (job: IndexJob) => void
  updateDocument: (document: KnowledgeDocument) => void
  failed: (failure: unknown) => void
}) {
  let current: AbortController | undefined
  let timer: ReturnType<typeof setTimeout> | undefined

  function stop() {
    current?.abort()
    current = undefined
    if (timer !== undefined) clearTimeout(timer)
    timer = undefined
  }

  /** 固定本次修订和索引身份，每次提交界面变更前复核作用域。 */
  function start(document: Pick<KnowledgeDocument, 'id' | 'currentJobId'>) {
    stop()
    if (!document.currentJobId || options.signal.aborted) return

    const jobId = document.currentJobId
    const controller = new AbortController()
    current = controller
    const signal = AbortSignal.any([controller.signal, options.signal])
    const deadline = Date.now() + POLL_BUDGET_MS
    const documentId = document.id

    async function poll() {
      if (signal.aborted) return
      timer = undefined
      try {
        const job = await options.readJob(jobId, signal)
        if (signal.aborted) return
        if (job.id !== jobId || job.documentId !== documentId) {
          throw new Error('索引响应与当前修订不匹配')
        }
        options.updateJob(job)
        if (!isIndexSettled(job) && Date.now() < deadline) {
          timer = setTimeout(() => void poll(), POLL_INTERVAL_MS)
          return
        }
        const latest = await options.readDocument(documentId, signal)
        if (signal.aborted) return
        if (latest.id !== documentId)
          throw new Error('详情响应与当前修订不匹配')
        options.updateDocument(latest)
      } catch (failure) {
        if (!signal.aborted) options.failed(failure)
      }
    }

    void poll()
  }

  onScopeDispose(stop)
  return { start, stop }
}
