<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyForm from "../../../components/MyForm.vue";
import {onMounted, reactive, ref} from "vue";
import {insertApi, simpleListApi} from "../../../api/index.js";
import {getResponseData} from "../../../request/index.js";
import {ORDER_PAY_TYPE_OPTIONS, ORDER_STATE_OPTIONS, RULE} from "../../../const/index.js";
import {ElMessage} from "element-plus";
import router from "../../../router/index.js";

// 全部用户下拉菜单选项
let userOptions = ref([]);
// 全部优惠卷下拉菜单选项
let couponsOptions = ref([]);
// 路径导航
const navItems = [
  {icon: 'Files', label: '订单管理'},
  {icon: 'Goods', label: '订单列表', url: '/Order'},
  {icon: 'Plus', label: '添加订单'},
];
// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '下单账号', prop: 'fkUserId', type: 'select', options: userOptions, required: true, span: 12},
  {label: '订单总金额', prop: 'totalAmount', type: 'number', required: true, span: 12},
  {label: '实际支付金额', prop: 'payAmount', type: 'number', required: true, span: 12},
  {label: '订单状态', prop: 'status', type: 'select', options: ORDER_STATE_OPTIONS, required: true, span: 12},
  {label: '支付方式', prop: 'payType', type: 'select', options: ORDER_PAY_TYPE_OPTIONS, required: true, span: 12},
  {label: '优惠卷', prop: 'fkCouponsId', type: 'select', options: couponsOptions, span: 12},
  {label: '描述', prop: 'info', required: true, type: 'textarea'},
]);
let params = reactive({});
let rules = {info: RULE.INFO};

/* ==================== 添加成功后 ==================== */

function insertSuccess() {
  ElMessage.success('添加成功！');
  setTimeout(() => router.push('/Order'), 1000);
}

/* ==================== 加载函数 ==================== */

onMounted(async () => {
  // 查询全部用户并添加到下拉菜单选项中
  Object.values(getResponseData(await simpleListApi(null, {module: 'user'}))).forEach(user => {
    userOptions.value.push({label: user['username'], value: user['id']});
  });
  // 查询全部优惠卷并添加到下拉菜单选项中
  Object.values(getResponseData(await simpleListApi(null, {module: 'coupons'}))).forEach(coupons => {
    couponsOptions.value.push({label: coupons['title'], value: coupons['id']});
  });
});
</script>

<template>
  <my-nav :items="navItems"/>
  <el-card v-if="userOptions.length > 0 && couponsOptions.length > 0" class="order-insert-card" header="添加订单">
    <my-form type="insert"
             :items="items"
             :rules="rules"
             :params="params"
             :api="insertApi"
             :args="{module: 'order'}"
             :callback="insertSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.order-insert-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
