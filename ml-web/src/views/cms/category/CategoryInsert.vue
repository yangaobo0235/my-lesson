<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyForm from "../../../components/MyForm.vue";
import {reactive, ref} from "vue";
import {insertApi} from "../../../api/index.js";
import {RULE} from "../../../const/index.js";
import {ElMessage} from "element-plus";
import router from "../../../router/index.js";

// 路径导航
const navItems = [
  {icon: 'Notebook', label: '课程管理'},
  {icon: 'Management', label: '类别列表', url: '/Category'},
  {icon: 'Plus', label: '添加类别'},
];
// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '标题', prop: 'title', required: true, span: 12},
  {label: '排序', prop: 'idx', type: 'number', min: 1, required: true, span: 12},
  {label: '描述', prop: 'info', required: true, type: 'textarea'},
]);
let params = reactive({});
let rules = {title: RULE.TITLE, info: RULE.INFO};

/* ==================== 添加成功后 ==================== */

function insertSuccess() {
  ElMessage.success('添加记录成功！');
  setTimeout(() => router.push('/Category'), 1000);
}
</script>

<template>
  <my-nav :items="navItems"/>
  <el-card class="category-insert-card" header="添加课程类别">
    <my-form type="insert"
             :items="items"
             :rules="rules"
             :params="params"
             :api="insertApi"
             :args="{module: 'category'}"
             :callback="insertSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.category-insert-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
