<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyForm from "../../../components/MyForm.vue";
import {reactive, ref} from "vue";
import {insertApi} from "../../../api/index.js";
import {RULE} from "../../../const/index.js";
import {ElMessage} from "element-plus";
import router from "../../../router/index.js";

// 路径导航
const navItems = [
  {icon: 'Goods', label: '营销管理'},
  {icon: 'Opportunity', label: '优惠卷列表', url: '/Coupons'},
  {icon: 'Plus', label: '添加优惠卷'},
];
// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '兑换口令', prop: 'code', required: true},
  {label: '标题', prop: 'title', required: true, span: 12},
  {label: '优惠价格', prop: 'cpPrice', type: 'number', min: 1, required: true, span: 12},
  {label: '生效时间', prop: 'startTime', type: 'datetime', required: true, span: 12},
  {label: '失效时间', prop: 'endTime', type: 'datetime', required: true, span: 12},
  {label: '描述', prop: 'info', required: true, type: 'textarea'},
]);
let params = reactive({});
let rules = {code: RULE.CODE, title: RULE.TITLE, content: RULE.INFO,};

/* ==================== 添加成功后 ==================== */

function insertSuccess() {
  ElMessage.success('添加成功！');
  setTimeout(() => router.push('/Coupons'), 1000);
}
</script>

<template>
  <my-nav :items="navItems"/>
  <el-card class="coupons-insert-card" header="添加优惠卷">
    <my-form type="insert"
             :items="items"
             :rules="rules"
             :params="params"
             :api="insertApi"
             :args="{module: 'coupons'}"
             :callback="insertSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.coupons-insert-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
