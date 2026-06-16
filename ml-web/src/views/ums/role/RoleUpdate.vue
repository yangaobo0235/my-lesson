<script setup>
import MyForm from "../../../components/MyForm.vue";
import MyNav from "../../../components/MyNav.vue";
import {reactive, ref} from "vue";
import {updateApi} from "../../../api/index.js";
import {RULE} from "../../../const";
import {ElMessage} from "element-plus";
import router from "../../../router";

// 角色记录
let role = JSON.parse(sessionStorage.getItem('row'));
// 路径导航
const navItems = [
  {icon: 'Avatar', label: '用户管理'},
  {icon: 'UserFilled', label: '角色列表', url: '/Role'},
  {icon: 'Edit', label: '修改角色'},
];
// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '标题', prop: 'title', required: true, span: 12},
  {hidden: true, span: 12},
  {label: '序号', prop: 'idx', type: 'number', required: true, span: 12},
  {hidden: true, span: 12},
  {label: '描述', prop: 'info', required: true, type: 'textarea'},
]);
let params = reactive(role);
let rules = {title: RULE.TITLE, info: RULE.INFO};

/* ==================== 修改成功后 ==================== */

function updateSuccess() {
  ElMessage.success('修改记录成功！');
  setTimeout(() => router.push('/Role'), 1000);
}
</script>

<template>
  <my-nav :items="navItems"/>
  <el-card class="role-update-card" header="修改角色信息">
    <my-form type="update"
             :items="items"
             :params="params"
             :rules="rules"
             :api="updateApi"
             :args="{module: 'role'}"
             :callback="updateSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.role-update-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
