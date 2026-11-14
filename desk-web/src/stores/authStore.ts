import { RESULT_CODES } from '../api/resultCodes'
import { ref } from 'vue'
import { defineStore } from 'pinia'
import * as api from '../api/auth/authApi'
import type { Identity, Role } from '../api/auth/types'
import { ApiError } from '../api/http'

/** 身份及令牌仅存当前页面内存，退出后清除；浏览器会话由HttpOnly Cookie持有。 @author yangaobo */
export const useAuthStore = defineStore('auth', () => {
  const user = ref<Identity | null>(null)
  const csrf = ref('')
  function clear() {
    user.value = null
    csrf.value = ''
  }
  function has(role: Role) {
    return user.value?.roles.includes(role) ?? false
  }
  async function refresh() {
    try {
      user.value = await api.getIdentity()
    } catch (error) {
      if (
        error instanceof ApiError &&
        (error.code === RESULT_CODES.UNAUTHENTICATED ||
          error.code === RESULT_CODES.SESSION_REVOKED)
      )
        clear()
      else throw error
    }
  }
  async function token() {
    csrf.value = await api.getCsrf()
    return csrf.value
  }
  async function signIn(username: string, password: string) {
    clear()
    user.value = await api.login(username, password, await token())
    await token()
  }
  async function signOut() {
    await api.logout(await token())
    clear()
  }
  return { user, csrf, has, clear, refresh, token, signIn, signOut }
})
