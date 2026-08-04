<script setup>
import MyNav from "../../../../components/MyNav.vue";
import MyForm from "../../../../components/MyForm.vue";
import {reactive, ref} from "vue";
import {insertApi} from "../../../../api/index.js";
import {RULE} from "../../../../const/index.js";
import {ElMessage} from "element-plus";
import router from "../../../../router/index.js";

// 获取当前课程主键和课程标题
let insertPageParam = JSON.parse(sessionStorage.getItem('insertPageParam'));
let courseId = insertPageParam['courseId'];
let courseTitle = insertPageParam['courseTitle'];
// 路径导航
const navItems = [
  {icon: 'Notebook', label: '课程管理'},
  {icon: 'Notebook', label: '（' + courseTitle + '）的季次列表', url: '/Season'},
  {icon: 'Plus', label: '添加季次'},
];
// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '所属课程', prop: 'courseTitle', disabled: true},
  {label: '标题', prop: 'title', required: true, span: 12},
  {label: '排序', prop: 'idx', type: 'number', min: 1, required: true, span: 12},
  {label: '描述', prop: 'info', required: true, type: 'textarea'},
]);
let params = reactive({courseTitle, 'fkCourseId': courseId});
let rules = {title: RULE.TITLE, info: RULE.INFO};

/* ==================== 添加成功后 ==================== */

function insertSuccess() {
  ElMessage.success('添加记录成功！');
  setTimeout(() => router.push('/Season'), 1000);
}
</script>

<template>
  <my-nav :items="navItems"/>
  <el-card class="season-insert-card" header="添加季次">
    <my-form type="insert"
             :items="items"
             :rules="rules"
             :params="params"
             :api="insertApi"
             :args="{module: 'season'}"
             :callback="insertSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.season-insert-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
