import { integer, record, text, type Decoder } from './validation'
import { RESULT_CODES } from './resultCodes'

/** 请求失败保留类别，写入的任何传输失败都不能证明服务端未执行。 */
export class RequestError extends Error {
  constructor(
    readonly kind: 'network' | 'timeout' | 'cancelled' | 'protocol',
    message: string,
    readonly status: number | null = null,
    readonly traceId: string | null = null,
    readonly retryAfterMs: number | null = null,
  ) {
    super(message)
  }
}

/** HTTP失败保留公开错误码及追踪号，业务拒绝与响应未知分开处理。 @author yangaobo */
export class ApiError extends Error {
  constructor(
    readonly code: number,
    message: string,
    readonly status: number,
    readonly traceId: string | null,
    readonly retryAfterMs: number | null = null,
  ) {
    super(message)
  }
}

/** 服务端等待秒数或HTTP日期均归一为毫秒，非法头不影响原始失败。 */
export function retryDelay(response: Response): number | null {
  const value = response.headers.get('Retry-After')?.trim()
  if (!value) return null
  if (/^\d+$/.test(value)) {
    const milliseconds = Number(value) * 1000
    return Number.isSafeInteger(milliseconds) ? milliseconds : null
  }
  const date = Date.parse(value)
  return Number.isFinite(date) ? Math.max(0, date - Date.now()) : null
}

/** 调用方取消和请求超时分别表达，不向页面暴露浏览器原始错误。 */
function transportFailure(signal: AbortSignal): RequestError {
  if (signal.aborted) {
    const timeout =
      signal.reason instanceof DOMException &&
      signal.reason.name === 'TimeoutError'
    return new RequestError(
      timeout ? 'timeout' : 'cancelled',
      timeout ? '请求超时，请核对原操作结果' : '请求已取消',
    )
  }
  return new RequestError('network', '网络连接失败，请核对原操作结果')
}

/** 原生同源请求；不保存Cookie、不重试写入、不在底层跳转。 */
export async function request<T>(
  path: string,
  parse: Decoder<T>,
  options: {
    body?: unknown
    form?: FormData
    method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
    csrf?: string
    signal?: AbortSignal
    timeoutMs?: number
  } = {},
): Promise<T> {
  if (options.body !== undefined && options.form !== undefined)
    throw new Error('请求不能同时包含 JSON 与文件表单')
  const signal = AbortSignal.any([
    AbortSignal.timeout(options.timeoutMs ?? 15_000),
    ...(options.signal ? [options.signal] : []),
  ])
  let response: Response
  try {
    response = await fetch(`/api/v1/${path}`, {
      method:
        options.method ??
        (options.body === undefined && !options.form ? 'GET' : 'POST'),
      credentials: 'same-origin',
      cache: 'no-store',
      headers: {
        ...(options.body === undefined
          ? {}
          : { 'Content-Type': 'application/json' }),
        ...(options.csrf ? { 'X-CSRF-TOKEN': options.csrf } : {}),
      },
      body:
        options.form ??
        (options.body === undefined ? undefined : JSON.stringify(options.body)),
      signal,
    })
  } catch {
    throw transportFailure(signal)
  }
  const retryAfterMs = retryDelay(response)
  let value: unknown
  try {
    value = await response.json()
  } catch {
    if (signal.aborted) throw transportFailure(signal)
    throw new RequestError(
      'protocol',
      '服务响应格式不匹配，请核对原操作结果',
      response.status,
      response.headers.get('X-Trace-Id'),
      retryAfterMs,
    )
  }
  if (signal.aborted) throw transportFailure(signal)
  try {
    const envelope = record(value)
    const code = integer(envelope.code)
    const message = text(envelope.message)
    const traceId = text(envelope.traceId)
    if (!traceId)
      throw new RequestError(
        'protocol',
        '服务响应缺少追踪号，请稍后重新核对结果',
        response.status,
      )
    if (!response.ok && code === RESULT_CODES.SUCCESS)
      throw new RequestError(
        'protocol',
        '服务响应状态与结果不一致，请核对原操作结果',
        response.status,
        traceId,
        retryAfterMs,
      )
    if (!response.ok || code !== RESULT_CODES.SUCCESS)
      throw new ApiError(code, message, response.status, traceId, retryAfterMs)
    return parse(envelope.data)
  } catch (failure) {
    if (failure instanceof ApiError || failure instanceof RequestError)
      throw failure
    throw new RequestError(
      'protocol',
      '服务响应格式不匹配，请核对原操作结果',
      response.status,
      response.headers.get('X-Trace-Id'),
      retryAfterMs,
    )
  }
}

/** 查询参数仅接受端点明确允许的标量。 */
export function query(
  values: Record<string, string | number | boolean | null | undefined>,
): string {
  const result = new URLSearchParams()
  for (const [name, value] of Object.entries(values))
    if (value !== undefined && value !== null && value !== '')
      result.set(name, String(value))
  return result.size ? `?${result}` : ''
}

/** 32位请求键只标识一次用户确认，响应未知时保留原键。 */
export const newRequestId = () => crypto.randomUUID().replaceAll('-', '')
