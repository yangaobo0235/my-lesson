/** 校验跨端数据；仅通过字段解码器构造可供页面使用的对象。 @author yangaobo */
export type Decoder<T> = (value: unknown) => T

/** 协议不匹配不作为空列表或业务成功处理。 */
export function invalid(): never {
  throw new Error('服务响应格式不匹配，请刷新后重试')
}
export const text: Decoder<string> = (v) =>
  typeof v === 'string' ? v : invalid()
export const bool: Decoder<boolean> = (v) =>
  typeof v === 'boolean' ? v : invalid()
export const integer: Decoder<number> = (v) =>
  typeof v === 'number' && Number.isSafeInteger(v) && v >= 0 ? v : invalid()
export const decimal: Decoder<string> = (v) =>
  typeof v === 'string' &&
  /^(0|[1-9]\d{0,18})$/.test(v) &&
  BigInt(v) <= 9223372036854775807n
    ? v
    : invalid()
export const identifier: Decoder<string> = (v) =>
  typeof v === 'string' && /^[0-9a-f]{32}$/.test(v) ? v : invalid()
export const nullable =
  <T>(parse: Decoder<T>): Decoder<T | null> =>
  (v) =>
    v === null ? null : parse(v)
export const array =
  <T>(parse: Decoder<T>): Decoder<T[]> =>
  (v) =>
    Array.isArray(v) ? v.map(parse) : invalid()
export const choices =
  <T extends string>(...options: T[]): Decoder<T> =>
  (v) => {
    const result = options.find((option) => option === v)
    return result ?? invalid()
  }
/** JSON对象只在协议边界读取，业务层使用解码后的具体类型。 */
export function record(v: unknown): Record<string, unknown> {
  if (typeof v !== 'object' || v === null || Array.isArray(v)) return invalid()
  return Object.fromEntries(Object.entries(v))
}
export const page =
  <T>(parse: Decoder<T>) =>
  (v: unknown) => {
    const o = record(v)
    const pageNum = integer(o.pageNum)
    const pageSize = integer(o.pageSize)
    if (pageNum < 1 || pageSize < 1 || pageSize > 100) invalid()
    return {
      records: array(parse)(o.records),
      total: decimal(o.total),
      pageNum,
      pageSize,
    }
  }
export const empty: Decoder<void> = (v) => {
  if (v !== null) invalid()
}
