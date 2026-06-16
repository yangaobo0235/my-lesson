<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyForm from "../../../components/MyForm.vue";
import {onMounted, reactive, ref} from "vue";
import {insertApi, simpleListApi} from "../../../api/index.js";
import {getResponseData} from "../../../request/index.js";
import {ElMessage} from "element-plus";
import router from "../../../router/index.js";

// 全部用户下拉菜单选项
let userOptions = ref([]);
// 全部课程下拉菜单选项
let courseOptions = ref([]);
// 路径导航
const navItems = [
  {icon: 'Files', label: '订单管理'},
  {icon: 'ShoppingCart', label: '购物车列表', url: '/Cart'},
  {icon: 'Plus', label: '添加购物车记录'},
];
// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '用户', prop: 'fkUserId', type: 'select', required: true, options: userOptions, placeholder: '请选择用户'},
  {label: '课程', prop: 'fkCourseId', type: 'select', required: true, options: courseOptions, placeholder: '请选择课程'},
]);
let params = reactive({});
let rules = {};

/* ==================== 添加成功后 ==================== */

function insertSuccess() {
  ElMessage.success('添加成功！');
  setTimeout(() => router.push('/Cart'), 1000);
}

/* ==================== 加载函数 ==================== */

onMounted(async () => {
  // 查询全部用户并添加到下拉菜单选项中
  Object.values(getResponseData(await simpleListApi(null, {module: 'user'}))).forEach(user => {
    userOptions.value.push({label: user['username'], value: user['id']});
  });
  // 查询全部课程并添加到下拉菜单选项中
  Object.values(getResponseData(await simpleListApi(null, {module: 'course'}))).forEach(course => {
    courseOptions.value.push({label: course['title'], value: course['id']});
  });
});
</script>

<template>
  <my-nav :items="navItems"/>
  <el-card v-if="userOptions.length > 0 && courseOptions.length > 0" class="cart-insert-card" header="添加购物车记录">
    <my-form type="insert"
             :items="items"
             :rules="rules"
             :params="params"
             :api="insertApi"
             :args="{module: 'cart'}"
             :callback="insertSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.cart-insert-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
