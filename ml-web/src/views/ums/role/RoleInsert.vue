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
  {icon: 'UserFilled', label: '角色列表', url: '/Role'},
  {icon: 'Plus', label: '添加新角色'},
]
// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '标题', prop: 'title', required: true, span: 12},
  {hidden: true, span: 12},
  {label: '序号', prop: 'idx', type: 'number', required: true, span: 12},
  {hidden: true, span: 12},
  {label: '描述', prop: 'info', required: true, type: 'textarea'},
]);
let params = reactive({});
let rules = {title: RULE.TITLE, info: RULE.INFO};

/* ==================== 添加成功后 ==================== */

function insertSuccess() {
  ElMessage.success('添加成功！');
  setTimeout(() => router.push('/Role'), 1000);
}
</script>

<template>
  <my-nav :items="navItems"/>
  <el-card class="role-insert-card" header="添加新角色">
    <my-form type="insert"
             :items="items"
             :params="params"
             :rules="rules"
             :api="insertApi"
             :args="{module: 'role'}"
             :callback="insertSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.role-insert-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
