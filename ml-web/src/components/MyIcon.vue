<script setup>
let parent = defineProps({
  // 图标名称，必填
  icon: {type: String, required: true},
  // 标题文案
  label: {type: String, required: false},
  // 图标和标题的共同尺寸，默认14px
  size: {type: Number, required: false, default: 14},
});
</script>

<template>
  <el-icon class="icon" :size="size">
    <component :is="icon"/>
  </el-icon>
  <span class="text" :style="{'font-size' : size + 'px'}">
    {{ label }}
  </span>
</template>
