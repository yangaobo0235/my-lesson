<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElAlert, ElButton, ElSkeleton, ElTag } from 'element-plus'
import { listDevices, listServices } from '../../api/support/supportApi'
import { categoryNames } from '../../api/support/types'
import { useOperation } from '../../composables/useOperation'

const services = ref<Awaited<ReturnType<typeof listServices>> | null>(null)
const devices = ref<Awaited<ReturnType<typeof listDevices>> | null>(null)
const { busy, error, run, signal } = useOperation()

async function load() {
  await run(async () => {
    const [serviceResult, deviceResult] = await Promise.all([
      listServices(undefined, signal),
      listDevices(undefined, signal),
    ])
    services.value = serviceResult
    devices.value = deviceResult
  })
}

function turn(kind: 'service' | 'device', delta: number) {
  void run(async () => {
    if (kind === 'service' && services.value)
      services.value = await listServices(
        undefined,
        signal,
        services.value.pageNum + delta,
      )
    if (kind === 'device' && devices.value)
      devices.value = await listDevices(
        undefined,
        signal,
        devices.value.pageNum + delta,
      )
  })
}

onMounted(load)
</script>

<template>
  <main>
    <div class="page-heading">
      <div>
        <h1>服务范围</h1>
        <p>查看当前组织已开通的服务项目和可关联的设备。</p>
      </div>
      <RouterLink class="button-link" :to="{ name: 'report' }"
        >直接报修</RouterLink
      >
    </div>
    <ElAlert v-if="error" :title="error" type="error" :closable="false"
      ><ElButton text @click="load">重新加载</ElButton></ElAlert
    >
    <ElSkeleton v-if="busy && !services" :rows="4" animated />
    <section v-if="services" aria-labelledby="eligible-services">
      <h2 id="eligible-services">已开通服务</h2>
      <div v-if="services.records.length" class="service-grid">
        <article
          v-for="row in services.records"
          :key="row.item.id"
          class="card"
        >
          <ElTag>{{ categoryNames[row.item.category] }}</ElTag>
          <h3>{{ row.item.serviceName }}</h3>
          <p>{{ row.item.supportScope }}</p>
          <small class="subtle"
            >有效期 {{ row.startsOn }} 至 {{ row.endsOn
            }}<template v-if="row.item.onsiteSupported">
              · 支持现场服务</template
            ></small
          >
        </article>
      </div>
      <p v-else>当前没有已开通的服务。</p>
      <div
        v-if="BigInt(services.total) > BigInt(services.pageSize)"
        class="toolbar"
      >
        <ElButton
          :disabled="busy || services.pageNum === 1"
          @click="turn('service', -1)"
          >上一页服务</ElButton
        >
        <span>第 {{ services.pageNum }} 页</span>
        <ElButton
          :disabled="
            busy ||
            BigInt(services.pageNum * services.pageSize) >=
              BigInt(services.total)
          "
          @click="turn('service', 1)"
          >下一页服务</ElButton
        >
      </div>
    </section>
    <section v-if="devices" aria-labelledby="customer-devices">
      <h2 id="customer-devices">组织设备</h2>
      <div class="card table-scroll">
        <table v-if="devices.records.length">
          <thead>
            <tr>
              <th scope="col">资产编号</th>
              <th scope="col">位置</th>
              <th scope="col">状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in devices.records" :key="row.id">
              <td>{{ row.assetCode }}</td>
              <td>{{ row.location || '未填写' }}</td>
              <td>
                <ElTag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{
                  row.status === 'ACTIVE' ? '在用' : '已退役'
                }}</ElTag>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else>当前没有登记设备，报修时可以不关联设备。</p>
      </div>
      <div
        v-if="BigInt(devices.total) > BigInt(devices.pageSize)"
        class="toolbar"
      >
        <ElButton
          :disabled="busy || devices.pageNum === 1"
          @click="turn('device', -1)"
          >上一页设备</ElButton
        >
        <span>第 {{ devices.pageNum }} 页</span>
        <ElButton
          :disabled="
            busy ||
            BigInt(devices.pageNum * devices.pageSize) >= BigInt(devices.total)
          "
          @click="turn('device', 1)"
          >下一页设备</ElButton
        >
      </div>
    </section>
  </main>
</template>
