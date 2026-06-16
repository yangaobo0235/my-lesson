<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyForm from "../../../components/MyForm.vue";
import {onMounted, reactive, ref} from "vue";
import {simpleListApi, updateApi} from "../../../api/index.js";
import {getResponseData} from "../../../request/index.js";
import {ElMessage} from "element-plus";
import {ORDER_PAY_TYPE_OPTIONS, ORDER_STATE_OPTIONS, RULE} from "../../../const/index.js";
import router from "../../../router/index.js";

// 全部优惠卷下拉菜单选项
let couponsOptions = ref([]);
// 获取当前订单记录
let order = JSON.parse(sessionStorage.getItem('row'));
// 路径导航
const navItems = [
  {icon: 'Files', label: '订单管理'},
  {icon: 'Goods', label: '订单列表', url: '/Order'},
  {icon: 'Edit', label: '修改购物车记录'},
];
// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '下单账号', prop: 'username', disabled: true},
  {label: '订单总金额', prop: 'totalAmount', type: 'number', required: true, span: 12},
  {label: '实际支付金额', prop: 'payAmount', type: 'number', required: true, span: 12},
  {label: '订单状态', prop: 'status', type: 'select', options: ORDER_STATE_OPTIONS, required: true, span: 12},
  {label: '支付方式', prop: 'payType', type: 'select', options: ORDER_PAY_TYPE_OPTIONS, required: true, span: 12},
  {label: '优惠卷', prop: 'fkCouponsId', type: 'select', options: couponsOptions},
  {label: '描述', prop: 'info', required: true, type: 'textarea'},
]);
let params = reactive(order);
let rules = {info: RULE.INFO};

/* ==================== 修改成功后 ==================== */

function updateSuccess() {
  ElMessage.success('修改记录成功！');
  setTimeout(() => router.push('/Order'), 1000);
}

/* ==================== 加载函数 ==================== */

onMounted(async () => {
  // 查询全部优惠卷并添加到下拉菜单选项中
  Object.values(getResponseData(await simpleListApi(null, {module: 'coupons'}))).forEach(coupons => {
    couponsOptions.value.push({label: coupons['title'], value: coupons['id']});
  });
});
</script>

<template>
  <my-nav :items="navItems"/>
  <el-card v-if="couponsOptions.length > 0" class="order-update-card" header="修改订单记录">
    <my-form type="update"
             :items="items"
             :rules="rules"
             :params="params"
             :api="updateApi"
             :args="{module: 'order'}"
             :callback="updateSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.order-update-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
