import { integer, record, text, type Decoder } from './validation'
import { RESULT_CODES } from './resultCodes'

/** HTTP失败保留公开错误码及追踪号，业务拒绝与响应未知分开处理。 @author yangaobo */
export class ApiError extends Error {
  constructor(
    readonly code: number,
    message: string,
    readonly status: number,
    readonly traceId: string | null,
  ) {
    super(message)
  }
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
  const response = await fetch(`/api/v1/${path}`, {
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
    signal: AbortSignal.any([
      AbortSignal.timeout(options.timeoutMs ?? 15_000),
      ...(options.signal ? [options.signal] : []),
    ]),
  })
  const value: unknown = await response.json()
  const envelope = record(value)
  const code = integer(envelope.code)
  const message = text(envelope.message)
  const traceId = text(envelope.traceId)
  if (!traceId) throw new Error('服务响应缺少追踪号，请稍后重新核对结果')
  if (!response.ok || code !== RESULT_CODES.SUCCESS)
    throw new ApiError(code, message, response.status, traceId)
  return parse(envelope.data)
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
