<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElButton, ElInput, ElOption, ElSelect } from 'element-plus'
import {
  blankRule,
  listRuleItems,
  listRuleModels,
  type RuleInput,
} from '../../api/knowledge/knowledgeApi'
import type { ProductModel, ServiceItem } from '../../api/support/types'
import { useOperation } from '../../composables/useOperation'

/** 规则只表达项目与精确环境，一条内AND、多条间OR；不把未知当通配。 @author yangaobo */
const props = defineProps<{ modelValue: RuleInput[]; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [RuleInput[]] }>()
const items = ref<ServiceItem[]>([]),
  models = ref<ProductModel[]>([]),
  itemPage = ref(1),
  modelPage = ref(1),
  itemTotal = ref('0'),
  modelTotal = ref('0')
const { run, error, signal, busy } = useOperation()
const fields = [
  { key: 'osName', label: '系统名称' },
  { key: 'osVersion', label: '系统精确版本' },
  { key: 'softwareName', label: '软件名称' },
  { key: 'softwareVersion', label: '软件精确版本' },
] as const
function change<K extends keyof RuleInput>(
  index: number,
  key: K,
  value: RuleInput[K],
) {
  emit(
    'update:modelValue',
    props.modelValue.map((row, position) =>
      position === index
        ? { ...row, [key]: value === '' ? null : value }
        : { ...row },
    ),
  )
}
function more(kind: 'items' | 'models') {
  void run(async () => {
    if (kind === 'items') {
      const page = await listRuleItems(itemPage.value + 1, signal)
      items.value.push(...page.records)
      itemPage.value++
      itemTotal.value = page.total
    } else {
      const page = await listRuleModels(modelPage.value + 1, signal)
      models.value.push(...page.records)
      modelPage.value++
      modelTotal.value = page.total
    }
  })
}
onMounted(() => {
  void run(async () => {
    const [i, m] = await Promise.all([
      listRuleItems(1, signal),
      listRuleModels(1, signal),
    ])
    items.value = i.records
    models.value = m.records
    itemTotal.value = i.total
    modelTotal.value = m.total
  })
})
</script>

<template>
  <fieldset :disabled="disabled">
    <legend>适用规则（至少一条，最多50条）</legend>
    <p>
      留空表示该规则不限制该字段；问题环境未知时不会自动匹配有版本限制的资料。
    </p>
    <p v-if="error" role="alert">{{ error }}</p>
    <div
      v-for="(rule, index) in modelValue"
      :key="index"
      class="panel form-grid"
    >
      <label
        >支持项目<ElSelect
          :model-value="rule.serviceItemId"
          :disabled="disabled"
          @update:model-value="change(index, 'serviceItemId', $event)"
          ><ElOption
            v-for="item in items"
            :key="item.id"
            :value="item.id"
            :label="`${item.serviceName}${item.status === 'DISABLED' ? '（已停用）' : ''}`" /></ElSelect
      ></label>
      <label
        >限定型号（可空）<ElSelect
          :model-value="rule.productModelId ?? ''"
          clearable
          :disabled="disabled"
          @update:model-value="change(index, 'productModelId', $event)"
          ><ElOption
            v-for="model in models"
            :key="model.id"
            :value="model.id"
            :label="`${model.brand} ${model.modelName}`" /></ElSelect
      ></label>
      <label v-for="field in fields" :key="field.key"
        >{{ field.label
        }}<ElInput
          :model-value="rule[field.key] ?? ''"
          :disabled="disabled"
          maxlength="100"
          @update:model-value="change(index, field.key, $event)"
      /></label>
      <ElButton
        :disabled="disabled || modelValue.length < 2"
        @click="
          emit(
            'update:modelValue',
            modelValue
              .filter((_, position) => position !== index)
              .map((row) => ({ ...row })),
          )
        "
        >移除此规则</ElButton
      >
    </div>
    <ElButton
      :disabled="disabled || modelValue.length >= 50"
      @click="
        emit('update:modelValue', [
          ...modelValue.map((row) => ({ ...row })),
          blankRule(),
        ])
      "
      >增加适用规则</ElButton
    >
    <ElButton
      v-if="BigInt(items.length) < BigInt(itemTotal)"
      :disabled="busy"
      @click="more('items')"
      >加载更多项目</ElButton
    >
    <ElButton
      v-if="BigInt(models.length) < BigInt(modelTotal)"
      :disabled="busy"
      @click="more('models')"
      >加载更多型号</ElButton
    >
  </fieldset>
</template>
