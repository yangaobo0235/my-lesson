<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElAlert, ElButton, ElInput, ElOption, ElSelect } from 'element-plus'
import { listServices, listDevices } from '../../api/support/supportApi'
import {
  blankContext,
  categoryNames,
  type Device,
  type EntitledService,
} from '../../api/support/types'
import { createTicket, creationReceipt } from '../../api/ticket/ticketApi'
import type { TicketCreateForm } from '../../api/ticket/types'
import { newRequestId, ApiError } from '../../api/http'
import { useAuthStore } from '../../stores/authStore'
import { useOperation } from '../../composables/useOperation'
import SupportContextFields from './SupportContextFields.vue'
const services = ref<EntitledService[]>([]),
  devices = ref<Device[]>([])
const servicePage = ref(1),
  devicePage = ref(1),
  serviceTotal = ref('0'),
  deviceTotal = ref('0')
const form = ref<TicketCreateForm>({
  clientRequestId: newRequestId(),
  serviceItemId: null,
  deviceId: null,
  title: '',
  symptom: '',
  attemptedActions: null,
  supportContext: blankContext(),
  serviceAreaCode: null,
  serviceLocation: null,
  sourceDraftId: null,
})
const pending = ref<TicketCreateForm | null>(null)
const auth = useAuthStore(),
  router = useRouter()
const { busy, error, run, signal } = useOperation()
onMounted(
  () =>
    void run(async () => {
      const [s, d] = await Promise.all([
        listServices(undefined, signal),
        listDevices(undefined, signal),
      ])
      serviceTotal.value = s.total
      deviceTotal.value = d.total
      services.value = s.records
      devices.value = d.records.filter((row) => row.status === 'ACTIVE')
    }),
)
/** 可选项按页追加，保留当前表单，不能静默只展示前100个资源。 */
function more(kind: 'services' | 'devices') {
  void run(async () => {
    if (kind === 'services') {
      const page = await listServices(undefined, signal, servicePage.value + 1)
      services.value.push(...page.records)
      servicePage.value++
      serviceTotal.value = page.total
    } else {
      const page = await listDevices(undefined, signal, devicePage.value + 1)
      devices.value.push(
        ...page.records.filter((row) => row.status === 'ACTIVE'),
      )
      devicePage.value++
      deviceTotal.value = page.total
    }
  })
}
/** 一经发送冻结本次内容，未知结果只能查询或用原键原内容重试。 */
function submit() {
  void run(async () => {
    pending.value ??= {
      ...form.value,
      supportContext: { ...form.value.supportContext },
    }
    try {
      const result = await createTicket(
        pending.value,
        await auth.token(),
        signal,
      )
      await router.push(`/tickets/${result.id}`)
    } catch (failure) {
      if (
        failure instanceof ApiError &&
        failure.status < 500 &&
        failure.status !== 409
      )
        pending.value = null
      throw failure
    }
  })
}
function recover() {
  void run(async () => {
    if (!pending.value) return
    const result = await creationReceipt(pending.value.clientRequestId, signal)
    await router.push(`/tickets/${result.id}`)
  })
}
</script>
<template>
  <main>
    <p class="eyebrow">客户支持</p>
    <h1>需要什么帮助？</h1>
    <p>
      有效服务范围内可直接提交问题。类别不确定时，我们先协助分流；设备可以不选。
    </p>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <div class="service-grid">
      <article v-for="service in services" :key="service.item.id" class="card">
        <p class="eyebrow">{{ categoryNames[service.item.category] }}</p>
        <h2>{{ service.item.serviceName }}</h2>
        <p>{{ service.item.supportScope }}</p>
        <small>有效期 {{ service.startsOn }} 至 {{ service.endsOn }}</small>
      </article>
    </div>
    <ElButton
      v-if="BigInt(servicePage * 100) < BigInt(serviceTotal)"
      :disabled="busy"
      @click="more('services')"
      >加载更多服务项目</ElButton
    >
    <ElButton
      v-if="BigInt(devicePage * 100) < BigInt(deviceTotal)"
      :disabled="busy"
      @click="more('devices')"
      >加载更多可选设备</ElButton
    >
    <p v-if="!busy && !error && !services.length">
      当前没有可用服务，请联系服务团队确认开通范围。
    </p>
    <form v-if="services.length" class="card" @submit.prevent="submit">
      <h2>描述问题</h2>
      <label
        >服务项目<ElSelect
          :model-value="form.serviceItemId ?? ''"
          :disabled="!!pending"
          @update:model-value="form.serviceItemId = $event || null"
          ><ElOption value="" label="不确定，先协助分流" /><ElOption
            v-for="service in services"
            :key="service.item.id"
            :value="service.item.id"
            :label="service.item.serviceName" /></ElSelect></label
      ><label
        >相关设备（可选）<ElSelect
          v-model="form.deviceId"
          clearable
          :disabled="!!pending"
          @clear="form.deviceId = null"
          ><ElOption
            v-for="item in devices"
            :key="item.id"
            :value="item.id"
            :label="item.assetCode" /></ElSelect></label
      ><label
        >问题标题<ElInput
          v-model="form.title"
          maxlength="200"
          required
          :disabled="!!pending" /></label
      ><label
        >故障现象<ElInput
          v-model="form.symptom"
          type="textarea"
          :rows="4"
          maxlength="5000"
          required
          :disabled="!!pending" /></label
      ><label
        >已尝试的操作<ElInput
          v-model="form.attemptedActions"
          type="textarea"
          maxlength="5000"
          :disabled="!!pending" /></label
      ><SupportContextFields
        v-model="form.supportContext"
        :disabled="!!pending"
      /><ElAlert
        v-if="pending"
        title="本次请求已保留。失败或结果未知时，请查询回执或用原内容重试，避免重复提单。"
        type="info"
        :closable="false"
      />
      <div class="toolbar">
        <ElButton type="primary" native-type="submit" :loading="busy">{{
          pending ? '原请求重试' : '确认提交'
        }}</ElButton
        ><ElButton v-if="pending" :disabled="busy" @click="recover"
          >查询原请求结果</ElButton
        >
      </div>
    </form>
  </main>
</template>
