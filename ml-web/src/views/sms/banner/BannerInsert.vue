<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyForm from "../../../components/MyForm.vue";
import {reactive, ref} from "vue";
import {insertApi} from "../../../api/index.js";
import {RULE} from "../../../const/index.js";
import router from "../../../router/index.js";
import {ElMessage} from "element-plus";

// 路径导航
const navItems = [
  {icon: 'Goods', label: '营销管理'},
  {icon: 'Picture', label: '横幅列表', url: '/Banner'},
  {icon: 'Plus', label: '添加横幅'},
];
// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '横幅排序', prop: 'idx', type: 'number', min: 1, required: true, span: 12},
  {label: '横幅描述', prop: 'info', required: true, type: 'textarea'},
]);
let params = reactive({});
let rules = {info: RULE.INFO};

/* ==================== 添加成功后 ==================== */

function insertSuccess() {
  ElMessage.success('添加成功！');
  setTimeout(() => router.push('/Banner'), 1000);
}
</script>

<template>
  <my-nav :items="navItems"/>
  <el-card class="banner-insert-card" header="添加横幅">
    <my-form type="insert"
             :items="items"
             :params="params"
             :rules="rules"
             :api="insertApi"
             :args="{module: 'banner'}"
             :callback="insertSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.banner-insert-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
