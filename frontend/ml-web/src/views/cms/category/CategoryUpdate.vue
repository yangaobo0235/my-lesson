<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyForm from "../../../components/MyForm.vue";
import {reactive, ref} from "vue";
import {updateApi} from "../../../api/index.js";
import {RULE} from "../../../const/index.js";
import {ElMessage} from "element-plus";
import router from "../../../router/index.js";

// 获取当前类别记录
let category = JSON.parse(sessionStorage.getItem('row'));
// 路径导航
const navItems = [
  {icon: 'Notebook', label: '课程管理'},
  {icon: 'Management', label: '类别列表', url: '/Category'},
  {icon: 'Edit', label: '修改类别'},
];
// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '标题', prop: 'title', required: true, span: 12},
  {label: '排序', prop: 'idx', type: 'number', required: true, span: 12},
  {label: '描述', prop: 'info', required: true, type: 'textarea'},
]);
let params = reactive(category);
let rules = {title: RULE.TITLE, info: RULE.INFO};

/* ==================== 修改成功后 ==================== */

function updateSuccess(){
  ElMessage.success('修改记录成功！');
  setTimeout(() => router.push('/Category'), 1000);
}
</script>

<template>
  <my-nav :items="navItems"/>
  <el-card class="category-update-card" header="修改课程类别信息">
    <my-form type="update"
             :items="items"
             :rules="rules"
             :params="params"
             :api="updateApi"
             :args="{module: 'category'}"
             :callback="updateSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.category-update-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
