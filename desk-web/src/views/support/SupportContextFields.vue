<script setup lang="ts">
import { ElInput, ElSelect, ElOption } from 'element-plus'
import type { SupportContextForm } from '../../api/support/types'
const props = defineProps<{
  modelValue: SupportContextForm
  disabled?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [value: SupportContextForm] }>()
/** 每次生成独立表单值，避免修改父组件嵌套数据。 */
function update<K extends keyof SupportContextForm>(
  key: K,
  value: SupportContextForm[K],
) {
  emit('update:modelValue', {
    ...props.modelValue,
    [key]: value === '' ? null : value,
  })
}
</script>
<template>
  <fieldset :disabled="disabled">
    <legend>已知环境（未知可留空）</legend>
    <div class="form-grid">
      <label
        >操作系统<ElInput
          :model-value="modelValue.osName ?? ''"
          maxlength="100"
          :disabled="disabled"
          @update:model-value="update('osName', $event)" /></label
      ><label
        >系统版本<ElInput
          :model-value="modelValue.osVersion ?? ''"
          maxlength="100"
          :disabled="disabled"
          @update:model-value="update('osVersion', $event)" /></label
      ><label
        >软件名称<ElInput
          :model-value="modelValue.softwareName ?? ''"
          maxlength="100"
          :disabled="disabled"
          @update:model-value="update('softwareName', $event)" /></label
      ><label
        >软件版本<ElInput
          :model-value="modelValue.softwareVersion ?? ''"
          maxlength="100"
          :disabled="disabled"
          @update:model-value="update('softwareVersion', $event)" /></label
      ><label
        >错误码<ElInput
          :model-value="modelValue.errorCode ?? ''"
          maxlength="100"
          :disabled="disabled"
          @update:model-value="update('errorCode', $event)" /></label
      ><label
        >连接方式<ElSelect
          :model-value="modelValue.connectionType"
          :disabled="disabled"
          @update:model-value="update('connectionType', $event)"
          ><ElOption
            v-for="option in ['UNKNOWN', 'USB', 'LAN', 'WIFI', 'VPN', 'OTHER']"
            :key="option"
            :value="option"
            :label="option === 'UNKNOWN' ? '未知' : option" /></ElSelect></label
      ><label
        >影响范围<ElSelect
          :model-value="modelValue.affectedScope"
          :disabled="disabled"
          @update:model-value="update('affectedScope', $event)"
          ><ElOption value="UNKNOWN" label="未知" /><ElOption
            value="SINGLE_USER"
            label="单人" /><ElOption
            value="MULTIPLE_USERS"
            label="多人" /></ElSelect
      ></label>
    </div>
  </fieldset>
</template>
