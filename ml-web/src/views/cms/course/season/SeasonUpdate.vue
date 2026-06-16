<script setup>
import MyNav from "../../../../components/MyNav.vue";
import MyForm from "../../../../components/MyForm.vue";
import {reactive, ref} from "vue";
import {updateApi} from "../../../../api/index.js";
import {RULE} from "../../../../const/index.js";
import {ElMessage} from "element-plus";
import router from "../../../../router/index.js";

// 获取当前课程主键和课程标题
let updatePageParam = JSON.parse(sessionStorage.getItem('updatePageParam'));
let courseId = updatePageParam['courseId'];
// 获取当前季次记录
let season = JSON.parse(sessionStorage.getItem('row'));
let seasonPart = season['title'].split(' / ');
season['courseTitle'] = seasonPart[0];
season['title'] = seasonPart[1];
// 路径导航
const navItems = [
  {icon: 'Notebook', label: '课程管理'},
  {icon: 'Notebook', label: '（' + season['courseTitle'] + '）的季次列表', url: '/Season'},
  {icon: 'Edit', label: '修改季次'},
];
// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '所属课程', prop: 'courseTitle', disabled: true},
  {label: '标题', prop: 'title', required: true, span: 12},
  {label: '排序', prop: 'idx', type: 'number', min: 1, required: true, span: 12},
  {label: '描述', prop: 'info', required: true, type: 'textarea'},
]);
let params = reactive(season);
let rules = {title: RULE.TITLE, info: RULE.INFO};

/* ==================== 修改成功后 ==================== */

/**
 * 修改成功后调用
 *
 * 1. 路由到 Season 页面
 */
function updateSuccess() {
  ElMessage.success('修改记录成功！');
  setTimeout(() => router.push('/Season'), 1000);
}
</script>

<template>
  <my-nav :items="navItems"/>
  <el-card class="season-update-card" header="修改季次信息">
    <my-form type="update"
             :items="items"
             :rules="rules"
             :params="params"
             :api="updateApi"
             :args="{module: 'season'}"
             :callback="updateSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.season-update-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
