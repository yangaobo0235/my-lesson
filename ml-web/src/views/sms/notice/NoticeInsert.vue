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
  {icon: 'Goods', label: '营销管理'},
  {icon: 'Opportunity', label: '通知列表', url: '/Notice'},
  {icon: 'Plus', label: '添加通知'},
];

/* ==================== 添加表单 ==================== */

// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '排序', prop: 'idx', type: 'number', min: 1, required: true, span: 12},
  {label: '内容', prop: 'content', required: true, type: 'textarea'},
]);
let params = reactive({});
let rules = {content: RULE.CONTENT};

/* ==================== 添加成功后 ==================== */

function insertSuccess() {
  ElMessage.success('添加记录成功！');
  setTimeout(() => router.push('/Notice'), 1000);
}

</script>

<template>
  <my-nav :items="navItems"/>
  <el-card class="notice-insert-card" header="添加通知">
    <my-form type="insert"
             :items="items"
             :rules="rules"
             :params="params"
             :api="insertApi"
             :args="{module: 'notice'}"
             :callback="insertSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.notice-insert-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
