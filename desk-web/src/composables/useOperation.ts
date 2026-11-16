import { RESULT_CODES } from '../api/resultCodes'
import { onScopeDispose, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError, RequestError } from '../api/http'
import { useAuthStore } from '../stores/authStore'

/** 页面请求拥有取消与错误反馈；旧作用域结果不再更新页面。 @author yangaobo */
export function useOperation() {
  const busy = ref(false)
  const error = ref('')
  const controller = new AbortController()
  const auth = useAuthStore()
  const router = useRouter()
  onScopeDispose(() => controller.abort())
  async function run(action: () => Promise<void>) {
    if (busy.value) return
    busy.value = true
    error.value = ''
    try {
      await action()
    } catch (failure) {
      if (
        controller.signal.aborted ||
        (failure instanceof RequestError && failure.kind === 'cancelled')
      )
        return
      if (
        failure instanceof ApiError &&
        (failure.code === RESULT_CODES.UNAUTHENTICATED ||
          failure.code === RESULT_CODES.SESSION_REVOKED)
      ) {
        auth.clear()
        await router.replace('/login')
        return
      }
      if (
        failure instanceof ApiError &&
        failure.code === RESULT_CODES.PASSWORD_CHANGE_REQUIRED
      ) {
        await router.replace('/password')
        return
      }
      error.value =
        failure instanceof ApiError || failure instanceof RequestError
          ? `${failure.message}${failure.retryAfterMs !== null ? `，请至少等待${Math.ceil(failure.retryAfterMs / 1000)}秒后重试` : ''}${failure.traceId ? `（追踪号 ${failure.traceId}）` : ''}`
          : failure instanceof Error
            ? failure.message
            : '操作失败，请重试'
    } finally {
      if (!controller.signal.aborted) busy.value = false
    }
  }
  return { busy, error, run, signal: controller.signal }
}
