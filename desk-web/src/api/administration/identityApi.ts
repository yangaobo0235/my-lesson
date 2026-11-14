import { request } from '../http'
import { empty, page } from '../validation'
import {
  account,
  audit,
  organization,
  type AccountCreateForm,
  type AccountMutationForm,
  type AccountUpdateForm,
  type OrganizationCreateForm,
  type OrganizationUpdateForm,
} from './types'
import type { Role } from '../auth/types'

/** 管理查询依约使用POST，并沿同源安全令牌执行。 @author yangaobo */
export const listOrganizations = (
  pageNum: number,
  keyword: string,
  csrf: string,
  signal?: AbortSignal,
) =>
  request('admin/organization/list', page(organization), {
    body: { pageNum, pageSize: 100, keyword },
    csrf,
    signal,
  })
export const createOrganization = (
  body: OrganizationCreateForm,
  csrf: string,
) => request('admin/organization/add', organization, { body, csrf })
export const updateOrganization = (
  body: OrganizationUpdateForm,
  csrf: string,
) => request('admin/organization/update', organization, { body, csrf })
export const listAccounts = (
  pageNum: number,
  keyword: string,
  csrf: string,
  signal?: AbortSignal,
) =>
  request('admin/account/list', page(account), {
    body: { pageNum, keyword },
    csrf,
    signal,
  })
export const createAccount = (body: AccountCreateForm, csrf: string) =>
  request('admin/account/add', account, { body, csrf })
export const updateAccount = (body: AccountUpdateForm, csrf: string) =>
  request('admin/account/update', account, { body, csrf })
export const setRoles = (
  body: AccountMutationForm & { roles: Role[] },
  csrf: string,
) => request('admin/account/set-roles', account, { body, csrf })
export const setScopes = (
  body: AccountMutationForm & { customerIds: string[] },
  csrf: string,
) => request('admin/account/set-customer-scopes', account, { body, csrf })
export const setAccountEnabled = (
  body: AccountMutationForm,
  enabled: boolean,
  csrf: string,
) =>
  request(`admin/account/${enabled ? 'enable' : 'disable'}`, account, {
    body,
    csrf,
  })
export const resetPassword = (
  body: AccountMutationForm & { newPassword: string },
  csrf: string,
) => request('admin/account/reset-password', empty, { body, csrf })
export const listAudit = (
  pageNum: number,
  csrf: string,
  signal?: AbortSignal,
) =>
  request('admin/audit-event/list', page(audit), {
    body: { pageNum },
    csrf,
    signal,
  })
