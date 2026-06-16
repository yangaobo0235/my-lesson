<script setup>
import MyNav from "../../../../components/MyNav.vue";
import MyForm from "../../../../components/MyForm.vue";
import {onMounted, reactive, ref} from "vue";
import {insertApi, simpleListApi} from "../../../../api/index.js";
import {getResponseData} from "../../../../request/index.js";
import {RULE} from "../../../../const/index.js";
import {ElMessage} from "element-plus";
import router from "../../../../router/index.js";

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
  {icon: 'Plus', label: '添加订单课程'},
];

/* ==================== 添加表单 ==================== */

// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '所属订单', prop: 'orderSn', disabled: true},
  {label: '课程', prop: 'fkCourseId', required: true, type: 'select', options: courseOptions, placeholder: '请选择课程'},
]);
let params = reactive({orderSn, 'fkOrderId': orderId});
let rules = {};

/* ==================== 添加成功后 ==================== */

function insertSuccess() {
  ElMessage.success('添加成功！');
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
  <el-card v-if="courseOptions.length > 0" class="order-detail-insert-card" header="添加订单课程">
    <my-form type="insert"
             :items="items"
             :rules="rules"
             :params="params"
             :api="insertApi"
             :args="{module: 'orderDetail'}"
             :callback="insertSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.order-detail-insert-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
