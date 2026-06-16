<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyForm from "../../../components/MyForm.vue";
import {reactive, ref} from "vue";
import {updateApi} from "../../../api/index.js";
import {RULE} from "../../../const/index.js";
import {ElMessage} from "element-plus";
import router from "../../../router/index.js";

// 获取当前新闻记录
let article = JSON.parse(sessionStorage.getItem('row'));
// 路径导航
const navItems = [
  {icon: 'Goods', label: '营销管理'},
  {icon: 'WindPower', label: '新闻列表', url: '/Article'},
  {icon: 'Edit', label: '修改新闻'},
];
// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '排序', prop: 'idx', type: 'number', min: 1, required: true, span: 12},
  {hidden: true, span: 12},
  {label: '标题', prop: 'title', required: true, type: 'textarea', rows: 3},
  {label: '内容', prop: 'content', required: true, type: 'textarea'},
]);
let params = reactive(article);
let rules = {content: RULE.INFO};

/* ==================== 修改成功后 ==================== */

function updateSuccess() {
  ElMessage.success('修改记录成功！');
  setTimeout(() => router.push('/Article'), 1000);
}
</script>

<template>
  <my-nav :items="navItems"/>
  <el-card class="article-update-card" header="修改新闻信息">
    <my-form type="update"
             :items="items"
             :params="params"
             :rules="rules"
             :api="updateApi"
             :args="{module: 'article'}"
             :callback="updateSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.article-update-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
