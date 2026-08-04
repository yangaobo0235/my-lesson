<script setup>
import MyNav from "../../../../components/MyNav.vue";
import MyForm from "../../../../components/MyForm.vue";
import {onMounted, reactive, ref} from "vue";
import {simpleListApi, updateApi} from "../../../../api/index.js";
import {getResponseData} from "../../../../request/index.js";
import {RULE} from "../../../../const/index.js";
import {ElMessage} from "element-plus";
import router from "../../../../router/index.js";

// 获取当前订单详情记录
let orderDetail = JSON.parse(sessionStorage.getItem('row'));
// 所属订单ID和所属订单编号
let orderId = sessionStorage.getItem('orderId');
let orderSn = sessionStorage.getItem('orderSn');
// 全部课程下拉菜单选项
let courseOptions = ref([]);
// 路径导航
const navItems = [
  {icon: 'Files', label: '订单管理'},
  {icon: 'Goods', label: '订单列表', url: '/Order'},
  {icon: 'Stopwatch', label: `《${orderSn}》订单详情`, url: '/OrderDetail'},
  {icon: 'Edit', label: '修改订单详情'},
];

/* ==================== 修改表单 ==================== */

// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '所属订单', prop: 'sn', disabled: true},
  {label: '课程', prop: 'fkCourseId', required: true, type: 'select', options: courseOptions, placeholder: '请选择课程'},
]);
let params = reactive(orderDetail);
let rules = {};

/* ==================== 修改成功后 ==================== */

function updateSuccess() {
  ElMessage.success('修改记录成功！');
  setTimeout(() => router.push('/OrderDetail'), 1000);
}

/* ==================== 加载函数 ==================== */

onMounted(async () => {
  // 查询全部课程并添加到下拉菜单选项中
  Object.values(getResponseData(await simpleListApi(null, {module: 'course'}))).forEach(course => {
    courseOptions.value.push({label: course['title'] + '（￥' + course['price'] + '）', value: course['id']});
  });
});
</script>

<template>
  <my-nav :items="navItems"/>
  <el-card v-if="courseOptions.length > 0" class="order-detail-update-card" header="修改订单详情信息">
    <my-form type="update"
             :items="items"
             :rules="rules"
             :params="params"
             :api="updateApi"
             :args="{module: 'orderDetail'}"
             :callback="updateSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.order-detail-update-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
