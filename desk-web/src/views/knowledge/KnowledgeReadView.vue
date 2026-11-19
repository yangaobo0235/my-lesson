<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElInput,
  ElOption,
  ElSelect,
  ElSkeleton,
  ElTag,
} from 'element-plus'
import {
  listReadableDocuments,
  readableOriginalLink,
  type ReadContext,
} from '../../api/knowledge/knowledgeReadApi'
import { listDevices, listServices } from '../../api/support/supportApi'
import PageControls from '../../components/PageControls.vue'
import { useOperation } from '../../composables/useOperation'

const services = ref<Awaited<ReturnType<typeof listServices>>['records']>([])
const devices = ref<Awaited<ReturnType<typeof listDevices>>['records']>([])
const activeDevices = computed(() =>
  devices.value.filter((row) => row.status === 'ACTIVE'),
)
const servicePage = ref<Awaited<ReturnType<typeof listServices>> | null>(null)
const devicePage = ref<Awaited<ReturnType<typeof listDevices>> | null>(null)
const serviceId = ref<string>()
const deviceId = ref<string>()
const environment = reactive({
  osName: '',
  osVersion: '',
  softwareName: '',
  softwareVersion: '',
})
const environmentFields = [
  ['osName', '操作系统'],
  ['osVersion', '系统版本'],
  ['softwareName', '软件名称'],
  ['softwareVersion', '软件版本'],
] as const
const result = ref<Awaited<ReturnType<typeof listReadableDocuments>> | null>(
  null,
)
const applied = ref<ReadContext | null>(null)
const { busy, error, run, signal } = useOperation()

async function load() {
  await run(async () => {
    const [serviceResult, deviceResult] = await Promise.all([
      listServices(undefined, signal),
      listDevices(undefined, signal),
    ])
    services.value = serviceResult.records
    devices.value = deviceResult.records
    servicePage.value = serviceResult
    devicePage.value = deviceResult
  })
}

function search(pageNum: number, criteria = applied.value) {
  if (!criteria) return
  void run(async () => {
    if (criteria !== applied.value) {
      result.value = null
      applied.value = null
    }
    result.value = await listReadableDocuments(criteria, pageNum, signal)
    applied.value = criteria
  })
}

function submit() {
  if (!serviceId.value) return
  search(1, {
    serviceItemId: serviceId.value,
    deviceId: deviceId.value || undefined,
    osName: environment.osName.trim(),
    osVersion: environment.osVersion.trim(),
    softwareName: environment.softwareName.trim(),
    softwareVersion: environment.softwareVersion.trim(),
  })
}

function more(kind: 'service' | 'device') {
  void run(async () => {
    if (kind === 'service' && servicePage.value) {
      const next = await listServices(
        undefined,
        signal,
        servicePage.value.pageNum + 1,
      )
      services.value.push(...next.records)
      servicePage.value = next
    }
    if (kind === 'device' && devicePage.value) {
      const next = await listDevices(
        undefined,
        signal,
        devicePage.value.pageNum + 1,
      )
      devices.value.push(...next.records)
      devicePage.value = next
    }
  })
}

onMounted(load)
</script>

<template>
  <main>
    <h1>帮助知识</h1>
    <p>选择已开通的服务项目，查看当前可读的已发布指南。</p>
    <form class="toolbar query-bar" @submit.prevent="submit">
      <ElSelect
        v-model="serviceId"
        :disabled="busy"
        clearable
        placeholder="选择服务项目"
        aria-label="服务项目"
      >
        <ElOption
          v-for="row in services"
          :key="row.item.id"
          :value="row.item.id"
          :label="row.item.serviceName"
        />
      </ElSelect>
      <ElSelect
        v-model="deviceId"
        :disabled="busy"
        clearable
        placeholder="选择可选设备"
        aria-label="设备"
      >
        <ElOption
          v-for="row in activeDevices"
          :key="row.id"
          :value="row.id"
          :label="row.assetCode"
        />
      </ElSelect>
      <ElButton
        native-type="submit"
        type="primary"
        :disabled="!serviceId"
        :loading="busy"
        >查找资料</ElButton
      >
      <details class="knowledge-environment">
        <summary>按系统或软件筛选</summary>
        <div class="form-grid">
          <label v-for="[field, label] in environmentFields" :key="field"
            >{{ label }}
            <ElInput
              v-model="environment[field]"
              :disabled="busy"
              maxlength="100"
            />
          </label>
        </div>
      </details>
      <ElButton
        v-if="
          servicePage && BigInt(services.length) < BigInt(servicePage.total)
        "
        :disabled="busy"
        @click="more('service')"
        >更多服务项目</ElButton
      >
      <ElButton
        v-if="devicePage && BigInt(devices.length) < BigInt(devicePage.total)"
        :disabled="busy"
        @click="more('device')"
        >更多设备</ElButton
      >
    </form>
    <ElAlert v-if="error" :title="error" type="error" :closable="false">
      <ElButton v-if="!servicePage" text @click="load">重新加载</ElButton>
    </ElAlert>
    <ElSkeleton v-if="busy && !result" :rows="4" animated />
    <p v-else-if="!error && servicePage && !services.length" class="card">
      当前没有已开通的服务项目。
    </p>
    <template v-if="result && applied">
      <p v-if="!result.records.length" class="card">当前条件下暂无可读资料。</p>
      <div v-else class="service-grid">
        <article v-for="row in result.records" :key="row.id" class="card">
          <ElTag>{{ row.contentFormat === 'PDF' ? 'PDF' : 'Markdown' }}</ElTag>
          <h2>{{ row.title }}</h2>
          <p class="subtle">
            第 {{ row.revisionNumber }} 版<template v-if="row.revisionLabel">
              · {{ row.revisionLabel }}</template
            >
          </p>
          <p v-if="row.applicabilityNote" class="preserve">
            {{ row.applicabilityNote }}
          </p>
          <a
            :href="readableOriginalLink(row.id, applied)"
            target="_blank"
            rel="noopener noreferrer"
            >查看原件</a
          >
        </article>
      </div>
      <PageControls :page="result" :busy="busy" @change="search" />
    </template>
  </main>
</template>

<style scoped>
.knowledge-environment {
  flex-basis: 100%;
}
</style>
