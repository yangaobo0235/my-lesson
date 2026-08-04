<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyForm from "../../../components/MyForm.vue";
import {reactive, ref} from "vue";
import {updateApi} from "../../../api/index.js";
import {RULE} from "../../../const/index.js";
import {ElMessage} from "element-plus";
import router from "../../../router/index.js";

// 获取当前通知记录
let notice = JSON.parse(sessionStorage.getItem('row'));
// 路径导航
const navItems = [
  {icon: 'Goods', label: '营销管理'},
  {icon: 'Opportunity', label: '通知列表', url: '/Notice'},
  {icon: 'Edit', label: '修改通知'},
];
// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '通知排序', prop: 'idx', type: 'number', min: 1, required: true, span: 12},
  {label: '通知内容', prop: 'content', required: true, type: 'textarea'},
]);
let params = reactive(notice);
let rules = {content: RULE.CONTENT};

/* ==================== 修改成功后 ==================== */

function updateSuccess() {
  ElMessage.success('修改记录成功！');
  setTimeout(() => router.push('/Notice'), 1000);
}
</script>

<template>
  <my-nav :items="navItems"/>
  <el-card class="notice-update-card" header="修改通知信息">
    <my-form type="update"
             :items="items"
             :rules="rules"
             :params="params"
             :api="updateApi"
             :args="{module: 'notice'}"
             :callback="updateSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.notice-update-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
