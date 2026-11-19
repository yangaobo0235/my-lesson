<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElInput,
  ElOption,
  ElSelect,
  ElTabPane,
  ElTabs,
} from 'element-plus'
import * as api from '../../api/administration/identityApi'
import {
  type Account,
  type AccountCreateForm,
  type Organization,
} from '../../api/administration/types'
import { ROLE_LABELS, type Role } from '../../api/auth/types'
import { useAuthStore } from '../../stores/authStore'
import { useOperation } from '../../composables/useOperation'
import { STAFF_ROLES } from '../../router/workspaceAccess'
const auth = useAuthStore(),
  { busy, error, run, signal } = useOperation()
const tab = ref('organizations'),
  organizations = ref<Organization[]>([]),
  accounts = ref<Account[]>([])
const orgPage = ref(1),
  accountPage = ref(1),
  orgTotal = ref('0'),
  accountTotal = ref('0'),
  accountPageSize = ref(0),
  keyword = ref('')
const orgCode = ref(''),
  orgName = ref(''),
  orgStatus = ref<'ACTIVE' | 'DISABLED'>('ACTIVE'),
  selectedOrg = ref<Organization | null>(null),
  reason = ref('')
const newAccount = (): AccountCreateForm => ({
  organizationId: '',
  username: '',
  displayName: '',
  initialPassword: '',
  roles: [],
})
const accountForm = ref(newAccount()),
  selectedAccount = ref<Account | null>(null),
  accountAction = ref<'update' | 'roles' | 'scopes' | 'status' | 'password'>(
    'update',
  ),
  customerIds = ref<string[]>([]),
  newPassword = ref('')
const selectedType = computed(
  () =>
    organizations.value.find(
      (org) => org.id === accountForm.value.organizationId,
    )?.organizationType,
)
const allowedRoles = computed(() =>
  Object.entries(ROLE_LABELS).filter(([role]) =>
    selectedType.value === 'CUSTOMER'
      ? role === 'CUSTOMER_USER'
      : role !== 'CUSTOMER_USER',
  ),
)
const customers = computed(() =>
  organizations.value.filter((org) => org.organizationType === 'CUSTOMER'),
)
async function load() {
  const csrf = await auth.token()
  const [orgs, users] = await Promise.all([
    api.listOrganizations(orgPage.value, keyword.value, csrf, signal),
    api.listAccounts(accountPage.value, keyword.value, csrf, signal),
  ])
  organizations.value = orgs.records
  orgTotal.value = orgs.total
  accounts.value = users.records
  accountTotal.value = users.total
  accountPageSize.value = users.pageSize
}
onMounted(() => void run(load))
function search() {
  orgPage.value = 1
  accountPage.value = 1
  void run(load)
}
function editOrg(org: Organization) {
  selectedOrg.value = org
  orgCode.value = org.organizationCode
  orgName.value = org.organizationName
  orgStatus.value = org.status
  reason.value = ''
}
function clearOrg() {
  selectedOrg.value = null
  orgCode.value = ''
  orgName.value = ''
  reason.value = ''
}
function saveOrg() {
  void run(async () => {
    const csrf = await auth.token()
    if (selectedOrg.value) {
      if (!reason.value.trim()) throw new Error('请填写变更原因')
      await api.updateOrganization(
        {
          id: selectedOrg.value.id,
          rowVersion: selectedOrg.value.rowVersion,
          organizationName: orgName.value,
          status: orgStatus.value,
          reason: reason.value,
        },
        csrf,
      )
    } else
      await api.createOrganization(
        { organizationCode: orgCode.value, organizationName: orgName.value },
        csrf,
      )
    clearOrg()
    await load()
  })
}
function editAccount(row: Account) {
  selectedAccount.value = row
  accountForm.value = {
    organizationId: row.organizationId,
    username: row.username,
    displayName: row.displayName,
    initialPassword: '',
    roles: [...row.roles],
  }
  customerIds.value = [...row.customerIds]
  reason.value = ''
  newPassword.value = ''
}
function clearAccount() {
  selectedAccount.value = null
  accountForm.value = newAccount()
  customerIds.value = []
  reason.value = ''
  newPassword.value = ''
}
/** 各管理动作单独提交当前版本，冲突保留草稿让管理员重新核对。 */
function saveAccount() {
  void run(async () => {
    const csrf = await auth.token(),
      row = selectedAccount.value
    if (!row)
      await api.createAccount(
        { ...accountForm.value, roles: [...accountForm.value.roles] },
        csrf,
      )
    else {
      if (!reason.value.trim()) throw new Error('请填写变更原因')
      const base = {
        id: row.id,
        rowVersion: row.rowVersion,
        reason: reason.value,
      }
      switch (accountAction.value) {
        case 'update':
          await api.updateAccount(
            {
              ...base,
              organizationId: accountForm.value.organizationId,
              displayName: accountForm.value.displayName,
            },
            csrf,
          )
          break
        case 'roles':
          await api.setRoles({ ...base, roles: accountForm.value.roles }, csrf)
          break
        case 'scopes':
          await api.setScopes({ ...base, customerIds: customerIds.value }, csrf)
          break
        case 'status':
          await api.setAccountEnabled(base, row.status !== 'ACTIVE', csrf)
          break
        case 'password':
          await api.resetPassword(
            { ...base, newPassword: newPassword.value },
            csrf,
          )
          break
      }
    }
    clearAccount()
    await load()
  })
}
function changeOrganization() {
  accountForm.value.roles =
    selectedType.value === 'CUSTOMER' ? ['CUSTOMER_USER'] : []
}
function next(kind: 'org' | 'account', delta: number) {
  if (kind === 'org') orgPage.value += delta
  else accountPage.value += delta
  void run(load)
}
function roleLabel(roles: Role[]) {
  return roles.map((value) => ROLE_LABELS[value]).join('、')
}
</script>
<template>
  <main>
    <h1>组织、账号与人员范围</h1>
    <p>
      角色与客户范围由后端即时核验，变更会使旧会话失效。最后一名有效管理员不能被停用或移除。
    </p>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <form class="toolbar" @submit.prevent="search">
      <ElInput v-model="keyword" placeholder="名称或账号关键字" /><ElButton
        native-type="submit"
        :loading="busy"
        >查询 / 刷新</ElButton
      >
    </form>
    <ElTabs v-model="tab"
      ><ElTabPane label="组织" name="organizations"
        ><div class="card table-scroll">
          <table>
            <thead>
              <tr>
                <th>名称</th>
                <th>代码</th>
                <th>类型 / 状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="org in organizations" :key="org.id">
                <td>
                  {{ org.organizationName
                  }}<small class="subtle">{{ org.id }}</small>
                </td>
                <td>{{ org.organizationCode }}</td>
                <td>
                  {{ org.organizationType === 'CUSTOMER' ? '客户' : '服务商' }}
                  / {{ org.status === 'ACTIVE' ? '启用' : '停用' }}
                </td>
                <td>
                  <ElButton :disabled="busy" @click="editOrg(org)"
                    >编辑</ElButton
                  >
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="toolbar">
          <ElButton :disabled="busy || orgPage === 1" @click="next('org', -1)"
            >上一页</ElButton
          ><span>{{ orgTotal }} 条 · 第 {{ orgPage }} 页</span
          ><ElButton
            :disabled="busy || BigInt(orgPage * 100) >= BigInt(orgTotal)"
            @click="next('org', 1)"
            >下一页</ElButton
          >
        </div>
        <form class="card" @submit.prevent="saveOrg">
          <h2>{{ selectedOrg ? '更新组织' : '新增客户组织' }}</h2>
          <label
            >组织代码<ElInput
              v-model="orgCode"
              :disabled="!!selectedOrg"
              required
              maxlength="64" /></label
          ><label
            >组织名称<ElInput
              v-model="orgName"
              required
              maxlength="100" /></label
          ><label v-if="selectedOrg"
            >状态<ElSelect v-model="orgStatus"
              ><ElOption value="ACTIVE" label="启用" /><ElOption
                value="DISABLED"
                label="停用" /></ElSelect></label
          ><label v-if="selectedOrg"
            >原因<ElInput v-model="reason" required maxlength="500"
          /></label>
          <div class="toolbar">
            <ElButton type="primary" native-type="submit" :loading="busy"
              >确认保存</ElButton
            ><ElButton @click="clearOrg">新建 / 清空</ElButton>
          </div>
        </form></ElTabPane
      ><ElTabPane label="账号与范围" name="accounts"
        ><div class="card table-scroll">
          <table>
            <thead>
              <tr>
                <th>账号</th>
                <th>姓名</th>
                <th>角色</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in accounts" :key="row.id">
                <td>
                  {{ row.username }}<small class="subtle">{{ row.id }}</small>
                </td>
                <td>{{ row.displayName }}</td>
                <td>{{ roleLabel(row.roles) }}</td>
                <td>
                  {{ row.status === 'ACTIVE' ? '启用' : '停用'
                  }}{{ row.mustChangePassword ? ' · 须改密' : '' }}
                </td>
                <td>
                  <ElButton :disabled="busy" @click="editAccount(row)"
                    >管理</ElButton
                  >
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="toolbar">
          <ElButton
            :disabled="busy || accountPage === 1"
            @click="next('account', -1)"
            >上一页</ElButton
          ><span>{{ accountTotal }} 条 · 第 {{ accountPage }} 页</span
          ><ElButton
            :disabled="
              busy ||
              BigInt(accountPage * accountPageSize) >= BigInt(accountTotal)
            "
            @click="next('account', 1)"
            >下一页</ElButton
          >
        </div>
        <form class="card" @submit.prevent="saveAccount">
          <h2>
            {{
              selectedAccount ? `管理 ${selectedAccount.username}` : '新增账号'
            }}
          </h2>
          <label v-if="selectedAccount"
            >管理动作<ElSelect v-model="accountAction"
              ><ElOption value="update" label="姓名与组织调动" /><ElOption
                value="roles"
                label="设置角色" /><ElOption
                value="scopes"
                label="设置客户范围" /><ElOption
                value="status"
                :label="
                  selectedAccount.status === 'ACTIVE' ? '停用账号' : '启用账号'
                " /><ElOption
                value="password"
                label="重置密码" /></ElSelect></label
          ><template v-if="!selectedAccount || accountAction === 'update'"
            ><label
              >归属组织<ElSelect
                v-model="accountForm.organizationId"
                @change="changeOrganization"
                ><ElOption
                  v-for="org in organizations"
                  :key="org.id"
                  :value="org.id"
                  :label="org.organizationName" /></ElSelect></label
            ><label v-if="!selectedAccount"
              >登录账号<ElInput
                v-model="accountForm.username"
                maxlength="64"
                autocomplete="off"
                required /></label
            ><label
              >姓名<ElInput
                v-model="accountForm.displayName"
                maxlength="100"
                required /></label></template
          ><label v-if="!selectedAccount"
            >初始密码<ElInput
              v-model="accountForm.initialPassword"
              type="password"
              autocomplete="new-password"
              required /></label
          ><label v-if="!selectedAccount || accountAction === 'roles'"
            >角色<ElSelect v-model="accountForm.roles" multiple
              ><ElOption
                v-for="[value, label] in allowedRoles"
                :key="value"
                :value="value"
                :label="label" /></ElSelect></label
          ><ElButton
            v-if="
              selectedType === 'SERVICE_PROVIDER' &&
              (!selectedAccount || accountAction === 'roles')
            "
            :disabled="busy"
            @click="accountForm.roles = [...STAFF_ROLES]"
            >选中全部服务商角色</ElButton
          ><label v-if="selectedAccount && accountAction === 'scopes'"
            >客户范围<ElSelect
              v-model="customerIds"
              multiple
              filterable
              allow-create
              placeholder="选择客户，或输入32位客户组织标识"
              ><ElOption
                v-for="org in customers"
                :key="org.id"
                :value="org.id"
                :label="org.organizationName" /></ElSelect></label
          ><label v-if="selectedAccount && accountAction === 'password'"
            >新密码<ElInput
              v-model="newPassword"
              type="password"
              autocomplete="new-password"
              required /></label
          ><label v-if="selectedAccount"
            >变更原因<ElInput v-model="reason" required maxlength="500"
          /></label>
          <p v-if="selectedAccount && accountAction === 'status'">
            将{{
              selectedAccount.status === 'ACTIVE' ? '停用' : '启用'
            }}此账号，请确认业务影响。
          </p>
          <div class="toolbar">
            <ElButton type="primary" native-type="submit" :loading="busy"
              >确认执行</ElButton
            ><ElButton @click="clearAccount">新建 / 清空</ElButton>
          </div>
        </form></ElTabPane
      ></ElTabs
    >
  </main>
</template>
