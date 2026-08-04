<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyForm from "../../../components/MyForm.vue";
import {reactive, ref} from "vue";
import {insertApi} from "../../../api/index.js";
import {RULE} from "../../../const/index.js";
import router from "../../../router/index.js";
import {ElMessage} from "element-plus";

// 路径导航
const navItems = [
  {icon: 'User', label: '用户管理'},
  {icon: 'User', label: '用户列表', url: '/User'},
  {icon: 'Plus', label: '添加用户'},
];

/* ==================== 添加表单 ==================== */

// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '账号', prop: 'username', required: true, span: 12},
  {label: '密码', prop: 'password', required: true, type: "password", span: 12},
  {label: '姓名', prop: 'realname', required: true, span: 12},
  {label: '邮箱', prop: 'email', required: true, span: 12},
  {label: '手机号码', prop: 'phone', required: true, span: 12},
  {label: '身份证号', prop: 'idcard', required: true, span: 12},
  {label: '描述', prop: 'info', required: true, type: 'textarea'},
]);
let params = reactive({info: '暂无描述'});
let rules = {
  username: RULE.USERNAME,
  password: RULE.PASSWORD,
  realname: RULE.REALNAME,
  idcard: RULE.IDCARD,
  phone: RULE.PHONE,
  email: RULE.EMAIL,
  info: RULE.INFO
};

/* ==================== 添加成功后 ==================== */

function insertSuccess() {
  ElMessage.success('添加记录成功！');
  setTimeout(() => router.push('/User'), 1000);
}

</script>

<template>
  <my-nav :items="navItems"/>
  <el-card class="user-insert-card" header="添加用户">
    <my-form type="insert"
             :items="items"
             :params="params"
             :rules="rules"
             :api="insertApi"
             :args="{module: 'user'}"
             :callback="insertSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.user-insert-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
