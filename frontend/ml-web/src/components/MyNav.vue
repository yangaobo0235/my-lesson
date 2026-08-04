<script setup>
import router from "../router";

let parent = defineProps({
  /* 导航项
   *
   * icon: 图标
   * label: 文案
   * url: 导航项点击跳转地址
   */
  items: {type: Array, required: true},
});
</script>

<template>
  <el-breadcrumb class="my-nav breadcrumb-nav">
    <el-button class="back-btn"
               size="small"
               link
               icon="Back"
               @click="router.back(-1);">
      返回
    </el-button>
    <el-divider class="divider" direction="vertical"/>
    <el-breadcrumb-item class="my-nav-item"
                        v-for="(item ,i) in items"
                        :key="item"
                        :to="item['url']">
      <!--type: 最后1个按钮为default，其余为info-->
      <el-button class="item-btn"
                 size="small"
                 link
                 :icon="item['icon']"
                 :type="i === items.length - 1 ? 'info' : 'default'">
        {{ item['label'] }}
      </el-button>
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<style scoped lang="scss">
.my-nav {
  * {
    float: left; // 左浮动
  }

  .el-button {
    font-size: 1rem; // 字号
    padding-top: 0; // 上内边距
  }
}
</style>
