<script setup>
import MyForm from "../../../components/MyForm.vue";
import MyNav from "../../../components/MyNav.vue";
import {reactive, ref} from "vue";
import {insertApi} from "../../../api/index.js";
import {RULE} from "../../../const";
import {ElMessage} from "element-plus";
import router from "../../../router";

// 路径导航
const navItems = [
  {icon: 'Avatar', label: '用户管理'},
  {icon: 'Menu', label: '菜单列表（父菜单）', url: '/Menu'},
  {icon: 'Plus', label: '添加新菜单（父菜单）'},
]
// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '名称', prop: 'title', required: true, span: 12},
  {label: '序号', prop: 'idx', type: 'number', required: true, span: 12},
  {label: '图标', prop: 'icon', required: true, type: 'icon'},
  {label: '描述', prop: 'info', required: true, type: 'textarea'},
]);
let params = reactive({pid: 0, url: '/'});
let rules = {title: RULE.TITLE, info: RULE.INFO};

/* ==================== 添加成功后 ==================== */

function insertSuccess() {
  ElMessage.success('添加记录成功！');
  setTimeout(() => router.push('/Menu'), 1000);
}
</script>

<template>
  <my-nav :items="navItems"/>
  <el-card class="menu-insert-card" header="添加新菜单（父菜单）">
    <my-form type="insert"
             :items="items"
             :params="params"
             :rules="rules"
             :api="insertApi"
             :args="{module: 'menu'}"
             :callback="insertSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.menu-insert-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
