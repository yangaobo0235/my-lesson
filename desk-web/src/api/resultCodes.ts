/** 仅集中维护前端实际分支使用的响应码，完整登记由接口约定负责。 @author yangaobo */
export const RESULT_CODES = {
  SUCCESS: 0,
  CONFLICT: 1004,
  UNAUTHENTICATED: 2001,
  SESSION_REVOKED: 2002,
  PASSWORD_CHANGE_REQUIRED: 2004,
} as const
