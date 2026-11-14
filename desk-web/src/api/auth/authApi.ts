import { request } from '../http'
import { empty, record, text } from '../validation'
import { identity } from './types'

/** 身份端点始终沿同源Cookie与独立CSRF令牌调用。 @author yangaobo */
export const getIdentity = (signal?: AbortSignal) =>
  request('users/auth/me', identity, { signal })
export const getCsrf = () =>
  request('users/auth/csrf', (v) => {
    const o = record(v)
    if (text(o.headerName) !== 'X-CSRF-TOKEN')
      throw new Error('不支持的安全令牌')
    return text(o.token)
  })
export const login = (username: string, password: string, csrf: string) =>
  request('users/auth/login', identity, { body: { username, password }, csrf })
export const changePassword = (
  currentPassword: string,
  newPassword: string,
  csrf: string,
) =>
  request('users/auth/password', empty, {
    body: { currentPassword, newPassword },
    csrf,
  })
export const logout = (csrf: string) =>
  request('users/auth/logout', empty, { body: {}, csrf })
