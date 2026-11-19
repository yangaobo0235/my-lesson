<script setup lang="ts">
import { ElButton } from 'element-plus'

/** 使用响应分页事实，不假定服务端默认页大小。 @author yangaobo */
defineProps<{
  page: { pageNum: number; pageSize: number; total: string }
  busy?: boolean
  label?: string
}>()
const emit = defineEmits<{ change: [pageNum: number] }>()
</script>

<template>
  <div class="toolbar" role="group" :aria-label="label ?? '列表分页'">
    <span>共 {{ page.total }} 条 · 第 {{ page.pageNum }} 页</span>
    <ElButton
      :disabled="busy || page.pageNum <= 1"
      @click="emit('change', page.pageNum - 1)"
      >上一页</ElButton
    >
    <ElButton
      :disabled="
        busy || BigInt(page.pageNum * page.pageSize) >= BigInt(page.total)
      "
      @click="emit('change', page.pageNum + 1)"
      >下一页</ElButton
    >
  </div>
</template>
