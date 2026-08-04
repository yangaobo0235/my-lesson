<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyForm from "../../../components/MyForm.vue";
import {onMounted, reactive, ref} from "vue";
import {insertApi, simpleListApi} from "../../../api/index.js";
import {getResponseData} from "../../../request/index.js";
import {RULE} from "../../../const/index.js";
import {ElMessage} from "element-plus";
import router from "../../../router/index.js";

// 所在课程类别下拉菜单选项
let categoryOptions = ref([]);
// 路径导航
const navItems = [
  {icon: 'Notebook', label: '课程管理'},
  {icon: 'Notebook', label: '课程列表', url: '/Course'},
  {icon: 'Plus', label: '添加课程'},
];
// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '标题', prop: 'title', required: true},
  {label: '排序', prop: 'idx', type: 'number', min: 1, required: true, span: 12},
  {label: '作者', prop: 'author', required: true, span: 12},
  {label: '类别', prop: 'fkCategoryId', required: true, span: 12, type: 'select', options: categoryOptions, placeholder: '请选择课程类别'},
  {label: '价格', prop: 'price', type: 'number', min: 1, required: true, span: 12},
  {label: '描述', prop: 'info', required: true, type: 'textarea'},
]);
let params = reactive({});
let rules = {title: RULE.TITLE, author: RULE.AUTHOR, info: RULE.INFO};

/* ==================== 添加成功后 ==================== */

function insertSuccess() {
  ElMessage.success('添加记录成功！');
  setTimeout(() => router.push('/Course'), 1000);
}

/* ==================== 加载函数 ==================== */

onMounted(async () => {
  // 查询全部课程类别并添加到下拉菜单选项中
  Object.values(getResponseData(await simpleListApi(null, {module: 'category'}))).forEach(category => {
    categoryOptions.value.push({label: category['title'], value: category['id']});
  });
});

</script>

<template>
  <my-nav :items="navItems"/>
  <el-card v-if="categoryOptions.length > 0" class="course-insert-card" header="添加课程">
    <my-form type="insert"
             :items="items"
             :params="params"
             :rules="rules"
             :api="insertApi"
             :args="{module: 'course'}"
             :callback="insertSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.course-insert-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
