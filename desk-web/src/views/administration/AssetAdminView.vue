<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElAlert, ElButton, ElInput, ElOption, ElSelect } from 'element-plus'
import * as api from '../../api/administration/supportApi'
import type { Device, ProductModel } from '../../api/support/types'
import { useAuthStore } from '../../stores/authStore'
import { useOperation } from '../../composables/useOperation'
const auth = useAuthStore(),
  { busy, error, run, signal } = useOperation()
const models = ref<ProductModel[]>([]),
  devices = ref<Device[]>([]),
  modelPage = ref(1),
  devicePage = ref(1),
  modelTotal = ref('0'),
  deviceTotal = ref('0'),
  devicePageSize = ref(0),
  customerId = ref<string | null>(null)
const model = ref<api.ModelForm>({
    category: 'COMPUTER',
    brand: '',
    modelName: '',
    supportedSystems: null,
  }),
  selectedModel = ref<ProductModel | null>(null)
const device = ref<api.DeviceForm>({
    customerId: '',
    productModelId: '',
    assetCode: '',
    serialNumber: null,
    location: null,
    serviceAreaCode: null,
  }),
  selectedDevice = ref<Device | null>(null)
async function load() {
  if (!auth.has('SERVICE_AGENT')) return
  const csrf = await auth.token()
  const [m, d] = await Promise.all([
    api.listModels(modelPage.value, csrf, signal),
    api.listDevices(customerId.value, devicePage.value, csrf, signal),
  ])
  models.value = m.records
  modelTotal.value = m.total
  devices.value = d.records
  deviceTotal.value = d.total
  devicePageSize.value = d.pageSize
}
onMounted(() => void run(load))
function editModel(row: ProductModel) {
  if (row.category !== 'COMPUTER' && row.category !== 'PRINTER') return
  selectedModel.value = row
  model.value = {
    category: row.category,
    brand: row.brand,
    modelName: row.modelName,
    supportedSystems: row.supportedSystems,
  }
}
function editDevice(row: Device) {
  selectedDevice.value = row
  device.value = {
    customerId: row.customerId,
    productModelId: row.productModelId,
    assetCode: row.assetCode,
    serialNumber: row.serialNumber,
    location: row.location,
    serviceAreaCode: row.serviceAreaCode,
  }
}
function clearModel() {
  selectedModel.value = null
  model.value = {
    category: 'COMPUTER',
    brand: '',
    modelName: '',
    supportedSystems: null,
  }
}
function clearDevice() {
  selectedDevice.value = null
  device.value = {
    customerId: customerId.value ?? '',
    productModelId: '',
    assetCode: '',
    serialNumber: null,
    location: null,
    serviceAreaCode: null,
  }
}
function saveModel() {
  void run(async () => {
    const csrf = await auth.token()
    if (selectedModel.value)
      await api.updateModel(
        {
          id: selectedModel.value.id,
          rowVersion: selectedModel.value.rowVersion,
          brand: model.value.brand,
          modelName: model.value.modelName,
          supportedSystems: model.value.supportedSystems || null,
        },
        csrf,
      )
    else await api.createModel({ ...model.value }, csrf)
    clearModel()
    await load()
  })
}
function disableModel() {
  void run(async () => {
    if (!selectedModel.value) return
    await api.disableModel(
      selectedModel.value.id,
      selectedModel.value.rowVersion,
      await auth.token(),
    )
    clearModel()
    await load()
  })
}
function saveDevice() {
  void run(async () => {
    const csrf = await auth.token()
    if (selectedDevice.value)
      await api.updateDevice(
        {
          id: selectedDevice.value.id,
          rowVersion: selectedDevice.value.rowVersion,
          assetCode: device.value.assetCode,
          serialNumber: device.value.serialNumber || null,
          location: device.value.location || null,
          serviceAreaCode: device.value.serviceAreaCode || null,
        },
        csrf,
      )
    else await api.createDevice({ ...device.value }, csrf)
    clearDevice()
    await load()
  })
}
function retireDevice() {
  void run(async () => {
    if (!selectedDevice.value) return
    await api.retireDevice(
      selectedDevice.value.id,
      selectedDevice.value.rowVersion,
      await auth.token(),
    )
    clearDevice()
    await load()
  })
}
function next(kind: 'model' | 'device', delta: number) {
  if (kind === 'model') modelPage.value += delta
  else devicePage.value += delta
  void run(load)
}
</script>
<template>
  <main>
    <h1>设备与型号</h1>
    <p v-if="!auth.has('SERVICE_AGENT')">
      型号与设备维护需要客服角色及相应客户范围。
    </p>
    <template v-else
      ><ElAlert v-if="error" :title="error" type="error" :closable="false" />
      <div class="toolbar">
        <ElButton :loading="busy" @click="run(load)">刷新</ElButton>
      </div>
      <h2>型号目录</h2>
      <div class="card table-scroll">
        <table>
          <thead>
            <tr>
              <th>品牌 / 型号</th>
              <th>类别</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in models" :key="row.id">
              <td>{{ row.brand }} / {{ row.modelName }}</td>
              <td>{{ row.category }}</td>
              <td>{{ row.status }}</td>
              <td><ElButton @click="editModel(row)">编辑</ElButton></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="toolbar">
        <ElButton :disabled="busy || modelPage === 1" @click="next('model', -1)"
          >上一页</ElButton
        ><span>{{ modelTotal }} 条</span
        ><ElButton
          :disabled="busy || BigInt(modelPage * 100) >= BigInt(modelTotal)"
          @click="next('model', 1)"
          >下一页</ElButton
        >
      </div>
      <form class="card" @submit.prevent="saveModel">
        <h3>{{ selectedModel ? '更新型号' : '新增型号' }}</h3>
        <div class="form-grid">
          <label
            >类别<ElSelect v-model="model.category" :disabled="!!selectedModel"
              ><ElOption value="COMPUTER" label="终端" /><ElOption
                value="PRINTER"
                label="打印机" /></ElSelect></label
          ><label
            >品牌<ElInput
              v-model="model.brand"
              required
              maxlength="100" /></label
          ><label
            >型号<ElInput
              v-model="model.modelName"
              required
              maxlength="100" /></label
          ><label
            >适用系统说明<ElInput
              v-model="model.supportedSystems"
              maxlength="500"
          /></label>
        </div>
        <div class="toolbar">
          <ElButton type="primary" native-type="submit" :loading="busy"
            >确认保存</ElButton
          ><ElButton
            v-if="selectedModel?.status === 'ACTIVE'"
            :disabled="busy"
            type="danger"
            @click="disableModel"
            >确认停用</ElButton
          ><ElButton @click="clearModel">新建 / 清空</ElButton>
        </div>
      </form>
      <h2>客户设备</h2>
      <label
        >筛选客户<ElSelect
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
              <th>资产编号</th>
              <th>客户</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in devices" :key="row.id">
              <td>{{ row.assetCode }}</td>
              <td>{{ row.customerId }}</td>
              <td>{{ row.status === 'ACTIVE' ? '在用' : '退役' }}</td>
              <td><ElButton @click="editDevice(row)">编辑</ElButton></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="toolbar">
        <ElButton
          :disabled="busy || devicePage === 1"
          @click="next('device', -1)"
          >上一页</ElButton
        ><span>{{ deviceTotal }} 条</span
        ><ElButton
          :disabled="
            busy || BigInt(devicePage * devicePageSize) >= BigInt(deviceTotal)
          "
          @click="next('device', 1)"
          >下一页</ElButton
        >
      </div>
      <form class="card" @submit.prevent="saveDevice">
        <h3>{{ selectedDevice ? '更新设备' : '新增设备' }}</h3>
        <div class="form-grid">
          <label
            >所属客户<ElSelect
              v-model="device.customerId"
              :disabled="!!selectedDevice"
              ><ElOption
                v-for="id in auth.user?.customerIds"
                :key="id"
                :value="id"
                :label="id" /></ElSelect></label
          ><label
            >型号<ElSelect
              v-model="device.productModelId"
              :disabled="!!selectedDevice"
              ><ElOption
                v-for="row in models"
                :key="row.id"
                :value="row.id"
                :label="`${row.brand} ${row.modelName}`" /></ElSelect></label
          ><label
            >资产编号<ElInput
              v-model="device.assetCode"
              required
              maxlength="100" /></label
          ><label
            >序列号<ElInput
              v-model="device.serialNumber"
              maxlength="100" /></label
          ><label
            >地点<ElInput v-model="device.location" maxlength="300" /></label
          ><label
            >服务区域代码<ElInput
              v-model="device.serviceAreaCode"
              maxlength="32"
          /></label>
        </div>
        <div class="toolbar">
          <ElButton type="primary" native-type="submit" :loading="busy"
            >确认保存</ElButton
          ><ElButton
            v-if="selectedDevice?.status === 'ACTIVE'"
            :disabled="busy"
            type="danger"
            @click="retireDevice"
            >确认退役</ElButton
          ><ElButton @click="clearDevice">新建 / 清空</ElButton>
        </div>
      </form></template
    >
  </main>
</template>
