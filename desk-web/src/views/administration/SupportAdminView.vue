<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElInput,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTabPane,
  ElTabs,
} from 'element-plus'
import * as api from '../../api/administration/supportApi'
import {
  categoryNames,
  type Category,
  type Entitlement,
  type ServiceItem,
} from '../../api/support/types'
import { newRequestId } from '../../api/http'
import { useAuthStore } from '../../stores/authStore'
import { useOperation } from '../../composables/useOperation'
const auth = useAuthStore(),
  { busy, error, run, signal } = useOperation()
const tab = ref(auth.has('SYSTEM_ADMIN') ? 'items' : 'entitlements')
const items = ref<ServiceItem[]>([]),
  entitlements = ref<Entitlement[]>([]),
  events = ref<api.ServiceEvent[]>([])
const itemPage = ref(1),
  entitlementPage = ref(1),
  eventPage = ref(1),
  itemTotal = ref('0'),
  entitlementTotal = ref('0'),
  entitlementPageSize = ref(0),
  eventTotal = ref('0'),
  eventPageSize = ref(0),
  customerId = ref<string | null>(null)
const serviceCode = ref(''),
  category = ref<Category>('TERMINAL'),
  serviceName = ref(''),
  supportScope = ref(''),
  onsite = ref(false),
  reason = ref(''),
  selectedItem = ref<ServiceItem | null>(null)
const selectedEntitlement = ref<Entitlement | null>(null),
  serviceItemId = ref(''),
  startsOn = ref(''),
  endsOn = ref('')
const eventTarget = ref<{
  resource: 'service-item' | 'service-entitlement'
  id: string
} | null>(null)
type Pending =
  | { kind: 'create'; body: api.ServiceCreateForm }
  | { kind: 'update'; body: api.ServiceUpdateForm }
  | { kind: 'disable'; body: api.StatusForm }
  | { kind: 'grant'; body: api.GrantForm }
  | { kind: 'dates'; body: api.EntitlementUpdateForm }
  | { kind: 'suspend' | 'resume'; body: api.StatusForm }
const pending = ref<Pending | null>(null)
const canGrant = computed(() => auth.has('SERVICE_AGENT'))
async function load() {
  const csrf = await auth.token()
  const jobs = [
    api.listItems(itemPage.value, csrf, signal).then((data) => {
      items.value = data.records
      itemTotal.value = data.total
    }),
  ]
  if (canGrant.value)
    jobs.push(
      api
        .listEntitlements(customerId.value, entitlementPage.value, csrf, signal)
        .then((data) => {
          entitlements.value = data.records
          entitlementTotal.value = data.total
          entitlementPageSize.value = data.pageSize
        }),
    )
  await Promise.all(jobs)
}
onMounted(() => void run(load))
function editItem(row: ServiceItem) {
  if (pending.value) return
  selectedItem.value = row
  serviceCode.value = row.serviceCode
  category.value = row.category
  serviceName.value = row.serviceName
  supportScope.value = row.supportScope
  onsite.value = row.onsiteSupported
  reason.value = ''
}
function editEntitlement(row: Entitlement) {
  if (pending.value) return
  selectedEntitlement.value = row
  customerId.value = row.customerId
  serviceItemId.value = row.serviceItemId
  startsOn.value = row.startsOn
  endsOn.value = row.endsOn
  reason.value = ''
}
function clear() {
  if (pending.value) return
  selectedItem.value = null
  selectedEntitlement.value = null
  serviceCode.value = ''
  serviceName.value = ''
  supportScope.value = ''
  reason.value = ''
  startsOn.value = ''
  endsOn.value = ''
  serviceItemId.value = ''
}
async function execute() {
  if (!pending.value) return
  const p = pending.value,
    csrf = await auth.token()
  switch (p.kind) {
    case 'create':
      await api.createItem(p.body, csrf)
      break
    case 'update':
      await api.updateItem(p.body, csrf)
      break
    case 'disable':
      await api.disableItem(p.body, csrf)
      break
    case 'grant':
      await api.grant(p.body, csrf)
      break
    case 'dates':
      await api.updateEntitlement(p.body, csrf)
      break
    case 'suspend':
    case 'resume':
      await api.suspend(p.body, p.kind === 'suspend', csrf)
      break
  }
  pending.value = null
  clear()
  await load()
}
function save(kind: Pending['kind']) {
  void run(async () => {
    if (!pending.value) {
      if (!reason.value.trim()) throw new Error('请填写变更原因')
      const base = { clientActionId: newRequestId(), reason: reason.value }
      if (kind === 'create')
        pending.value = {
          kind,
          body: {
            ...base,
            serviceCode: serviceCode.value,
            category: category.value,
            serviceName: serviceName.value,
            supportScope: supportScope.value,
            onsiteSupported: onsite.value,
          },
        }
      else if (kind === 'update' || kind === 'disable') {
        const row = selectedItem.value
        if (!row) return
        const body = { ...base, id: row.id, rowVersion: row.rowVersion }
        pending.value =
          kind === 'disable'
            ? { kind, body }
            : {
                kind,
                body: {
                  ...body,
                  serviceName: serviceName.value,
                  supportScope: supportScope.value,
                  onsiteSupported: onsite.value,
                },
              }
      } else if (kind === 'grant') {
        if (!customerId.value) throw new Error('请选择客户范围')
        pending.value = {
          kind,
          body: {
            ...base,
            customerId: customerId.value,
            serviceItemId: serviceItemId.value,
            startsOn: startsOn.value,
            endsOn: endsOn.value,
          },
        }
      } else {
        const row = selectedEntitlement.value
        if (!row) return
        const body = { ...base, id: row.id, rowVersion: row.rowVersion }
        pending.value =
          kind === 'dates'
            ? {
                kind,
                body: {
                  ...body,
                  startsOn: startsOn.value,
                  endsOn: endsOn.value,
                },
              }
            : { kind, body }
      }
    }
    await execute()
  })
}
function reload() {
  void run(async () => {
    pending.value = null
    clear()
    await load()
  })
}
function showEvents(
  resource: 'service-item' | 'service-entitlement',
  id: string,
) {
  eventTarget.value = { resource, id }
  eventPage.value = 1
  eventTotal.value = '0'
  events.value = []
  void run(loadEvents)
}
async function loadEvents() {
  if (!eventTarget.value) return
  const data = await api.listServiceEvents(
    eventTarget.value.resource,
    eventTarget.value.id,
    eventPage.value,
    await auth.token(),
    signal,
  )
  events.value = data.records
  eventTotal.value = data.total
  eventPageSize.value = data.pageSize
}
function next(kind: 'item' | 'entitlement' | 'event', delta: number) {
  void run(async () => {
    if (kind === 'item') itemPage.value += delta
    else if (kind === 'entitlement') entitlementPage.value += delta
    else eventPage.value += delta
    if (kind === 'event') await loadEvents()
    else await load()
  })
}
</script>
<template>
  <main>
    <h1>服务目录与资格</h1>
    <p>
      服务目录由系统管理员维护，客户资格由当前范围内客服维护。暂停或到期影响新受理，历史工单保留。
    </p>
    <ElAlert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
    /><ElAlert
      v-if="pending"
      title="已保留原动作；请原键重试，或核对变更历史后放弃旧动作并刷新。"
      type="info"
      :closable="false"
    />
    <div class="toolbar">
      <ElButton :disabled="busy" @click="reload">{{
        pending ? '放弃旧动作并刷新' : '刷新'
      }}</ElButton
      ><ElButton v-if="pending" :loading="busy" @click="run(execute)"
        >原动作重试</ElButton
      >
    </div>
    <ElTabs v-model="tab"
      ><ElTabPane label="服务目录" name="items"
        ><div class="service-grid">
          <article v-for="row in items" :key="row.id" class="card">
            <p class="eyebrow">
              {{ categoryNames[row.category] }} ·
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </p>
            <h2>{{ row.serviceName }}</h2>
            <p>{{ row.supportScope }}</p>
            <small>{{ row.serviceCode }}</small>
            <div v-if="auth.has('SYSTEM_ADMIN')" class="toolbar">
              <ElButton :disabled="busy || !!pending" @click="editItem(row)"
                >编辑</ElButton
              ><ElButton
                :disabled="busy"
                @click="showEvents('service-item', row.id)"
                >历史</ElButton
              >
            </div>
          </article>
        </div>
        <div class="toolbar">
          <ElButton :disabled="busy || itemPage === 1" @click="next('item', -1)"
            >上一页</ElButton
          ><span>{{ itemTotal }} 条 · 第 {{ itemPage }} 页</span
          ><ElButton
            :disabled="busy || BigInt(itemPage * 100) >= BigInt(itemTotal)"
            @click="next('item', 1)"
            >下一页</ElButton
          >
        </div>
        <form
          v-if="auth.has('SYSTEM_ADMIN')"
          class="card"
          @submit.prevent="save(selectedItem ? 'update' : 'create')"
        >
          <h2>{{ selectedItem ? '更新服务项目' : '新增服务项目' }}</h2>
          <fieldset :disabled="!!pending">
            <label
              >服务代码<ElInput
                v-model="serviceCode"
                :disabled="!!selectedItem || !!pending"
                required
                maxlength="64" /></label
            ><label
              >类别<ElSelect
                v-model="category"
                :disabled="!!selectedItem || !!pending"
                ><ElOption
                  v-for="(label, value) in categoryNames"
                  :key="value"
                  :value="value"
                  :label="label" /></ElSelect></label
            ><label
              >名称<ElInput
                v-model="serviceName"
                :disabled="!!pending"
                required
                maxlength="100" /></label
            ><label
              >支持条件<ElInput
                v-model="supportScope"
                :disabled="!!pending"
                type="textarea"
                required
                maxlength="2000" /></label
            ><label
              >支持现场服务<ElSwitch
                v-model="onsite"
                :disabled="!!pending" /></label
            ><label
              >原因<ElInput
                v-model="reason"
                :disabled="!!pending"
                required
                maxlength="500"
            /></label>
          </fieldset>
          <div class="toolbar">
            <ElButton
              type="primary"
              native-type="submit"
              :loading="busy"
              :disabled="!!pending"
              >确认保存</ElButton
            ><ElButton
              v-if="selectedItem?.status === 'ACTIVE'"
              type="danger"
              :disabled="busy || !!pending"
              @click="save('disable')"
              >确认停用</ElButton
            ><ElButton :disabled="!!pending" @click="clear"
              >新建 / 清空</ElButton
            >
          </div>
        </form></ElTabPane
      ><ElTabPane v-if="canGrant" label="客户资格" name="entitlements"
        ><label
          >客户范围<ElSelect
            v-model="customerId"
            clearable
            @clear="customerId = null"
            @change="run(load)"
            ><ElOption
              v-for="id in auth.user?.customerIds"
              :key="id"
              :value="id"
              :label="id" /></ElSelect
        ></label>
        <div class="card table-scroll">
          <table>
            <thead>
              <tr>
                <th>客户</th>
                <th>项目</th>
                <th>有效期</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in entitlements" :key="row.id">
                <td>{{ row.customerId }}</td>
                <td>
                  {{
                    items.find((item) => item.id === row.serviceItemId)
                      ?.serviceName || row.serviceItemId
                  }}
                </td>
                <td>{{ row.startsOn }} 至 {{ row.endsOn }}</td>
                <td>{{ row.status === 'ACTIVE' ? '有效' : '暂停' }}</td>
                <td>
                  <ElButton
                    :disabled="busy || !!pending"
                    @click="editEntitlement(row)"
                    >编辑</ElButton
                  ><ElButton
                    :disabled="busy"
                    @click="showEvents('service-entitlement', row.id)"
                    >历史</ElButton
                  >
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="toolbar">
          <ElButton
            :disabled="busy || entitlementPage === 1"
            @click="next('entitlement', -1)"
            >上一页</ElButton
          ><span>{{ entitlementTotal }} 条 · 第 {{ entitlementPage }} 页</span
          ><ElButton
            :disabled="
              busy ||
              BigInt(entitlementPage * entitlementPageSize) >=
                BigInt(entitlementTotal)
            "
            @click="next('entitlement', 1)"
            >下一页</ElButton
          >
        </div>
        <form
          class="card"
          @submit.prevent="save(selectedEntitlement ? 'dates' : 'grant')"
        >
          <h2>{{ selectedEntitlement ? '更新客户资格' : '开通客户资格' }}</h2>
          <p>客户：{{ customerId || '请先选择客户范围' }}</p>
          <label
            >服务项目<ElSelect
              v-model="serviceItemId"
              :disabled="!!selectedEntitlement || !!pending"
              ><ElOption
                v-for="row in items"
                :key="row.id"
                :value="row.id"
                :label="row.serviceName" /></ElSelect></label
          ><label
            >开始日期<input
              v-model="startsOn"
              type="date"
              required
              :disabled="!!pending" /></label
          ><label
            >结束日期<input
              v-model="endsOn"
              type="date"
              required
              :disabled="!!pending" /></label
          ><label
            >原因<ElInput
              v-model="reason"
              required
              maxlength="500"
              :disabled="!!pending"
          /></label>
          <div class="toolbar">
            <ElButton
              type="primary"
              native-type="submit"
              :loading="busy"
              :disabled="!!pending"
              >确认保存</ElButton
            ><ElButton
              v-if="selectedEntitlement"
              :disabled="busy || !!pending"
              @click="
                save(
                  selectedEntitlement.status === 'ACTIVE'
                    ? 'suspend'
                    : 'resume',
                )
              "
              >{{
                selectedEntitlement.status === 'ACTIVE'
                  ? '确认暂停'
                  : '确认恢复'
              }}</ElButton
            ><ElButton :disabled="!!pending" @click="clear"
              >新建 / 清空</ElButton
            >
          </div>
        </form></ElTabPane
      ></ElTabs
    >
    <section v-if="eventTarget" class="card">
      <h2>不可变变更历史</h2>
      <article v-for="event in events" :key="event.id">
        <strong>{{ event.eventType }}</strong>
        <p>
          {{
            new Date(event.createdAt).toLocaleString('zh-CN', {
              timeZone: 'Asia/Shanghai',
            })
          }}
          · {{ event.actorId }}
        </p>
        <details>
          <summary>查看前后值及原因</summary>
          <pre>{{ event.details }}</pre>
        </details>
      </article>
      <div class="toolbar">
        <ElButton :disabled="busy || eventPage === 1" @click="next('event', -1)"
          >上一页</ElButton
        ><span>{{ eventTotal }} 条 · 第 {{ eventPage }} 页</span
        ><ElButton
          :disabled="
            busy || BigInt(eventPage * eventPageSize) >= BigInt(eventTotal)
          "
          @click="next('event', 1)"
          >下一页</ElButton
        >
      </div>
    </section>
  </main>
</template>
