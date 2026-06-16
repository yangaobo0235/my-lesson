<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyHead from "../../../components/MyHead.vue";
import MyTable from "../../../components/MyTable.vue";
import {onMounted, reactive, ref} from "vue";
import {deleteApi, deleteBatchApi, pageApi} from "../../../api/index.js";
import {myPage} from "../../../request/index.js";
import {isNotEmpty, isNotNull, orderPayTypeFormat, orderStateFormat} from "../../../util/index.js";
import {ORDER_STATE_OPTIONS} from "../../../const/index.js";
import router from "../../../router/index.js";

// 路径导航
const navItems = [
  {icon: 'Files', label: '订单管理'},
  {icon: 'Goods', label: '订单列表'},
];
// 数据头
const headItems = [
  {type: 'ipt', span: 5, placeholder: '按订单编号搜索', callback: pageBySn},
  {type: 'ipt', span: 5, placeholder: '按用户账号搜索', callback: pageByUsername},
  {type: 'opt', span: 5, placeholder: '按订单状态搜索', options: ORDER_STATE_OPTIONS, callback: pageByStatus},
];
// 表格列
const columns = [
  {label: '订单编号', prop: 'sn', type: 'tag', tagType: 'info', width: 200},
  {label: '订单总金额', prop: 'totalAmount', suffix: ' 元', width: 120},
  {label: '实际支付价格', prop: 'payAmount', suffix: ' 元', width: 120},
  {label: '支付方式', prop: 'payType', type: 'tag', format: orderPayTypeFormat},
  {label: '支付状态', prop: 'status', type: 'tag', tagType: 'primary', format: orderStateFormat},
  {label: '付款账号', prop: 'username'},
  {label: '描述', prop: 'info', type: 'card'},
];
// 按钮列
const buttons = [
  {label: '订单详情', type: 'success', callback: orderDetail},
];
// 表格数据 + 分页数据 + 订单编号 + 订单状态 + 付款账号
let records = ref();
let pageInfo = reactive({pageNum: 1, pageSize: 5, callback: page});
let sn = ref();
let status = ref();
let username = ref();

/**
 * 分页查询
 *
 * 1. 定义分页基础配置，包括 records, pageInfo, api, params 等。
 * 2. 附加分页查询条件，如标题等。
 * 3. 异步发送分页查询请求。
 *
 * @param pageNum 当前第几页，默认 1
 * @param pageSize 每页多少条，默认 5
 */
async function page(pageNum = pageInfo['pageNum'], pageSize = pageInfo['pageSize']) {
  let config = {
    api: pageApi,
    args: {module: 'order'},
    params: {pageNum, pageSize},
    records, pageInfo,
  }
  if (isNotEmpty(sn.value)) config['params']['sn'] = sn.value;
  if (isNotEmpty(status.value)) config['params']['status'] = status.value;
  if (isNotEmpty(username.value)) config['params']['username'] = username.value;
  await myPage(config);
}

/* ==================== 搜索订单编号 ==================== */

/**
 * 按订单编号模糊查询
 *
 * 1. 将输入框中的值赋值给分页条件字段变量。
 * 2. 重新发送分页请求。
 *
 * @param val 输入框中的值
 */
function pageBySn(val) {
  if (isNotNull(val) || sn.value) {
    sn.value = val;
    page();
  }
}

/* ==================== 搜索订单状态 ==================== */

/**
 * 按订单状态查询
 *
 * 1. 将输入框中的值赋值给分页条件字段变量。
 * 2. 重新发送分页请求。
 *
 * @param val 输入框中的值
 */
function pageByStatus(val) {
  if (isNotNull(val) || status.value) {
    status.value = val;
    page();
  }
}

/* ==================== 搜索付款账号 ==================== */

/**
 * 按付款账号查询
 *
 * 1. 将输入框中的值赋值给分页条件字段变量。
 * 2. 重新发送分页请求。
 *
 * @param val 输入框中的值
 */
function pageByUsername(val) {
  if (isNotNull(val) || username.value) {
    username.value = val;
    page();
  }
}

/* ==================== 订单详情 ==================== */

function orderDetail(row) {
  sessionStorage.setItem('orderId', row['id']);
  sessionStorage.setItem('orderSn', row['sn'].toString());
  router.push('/OrderDetail');
}

/* ==================== 加载函数 ==================== */

onMounted(() => page());

</script>

<template v-if="records">
  <my-nav :items="navItems"/>
  <my-head :items="headItems"/>
  <my-table module="order"
            insert-page="/OrderInsert"
            update-page="/OrderUpdate"
            :records="records"
            :columns="columns"
            :buttons="buttons"
            :delete-api="deleteApi"
            :delete-batch-api="deleteBatchApi"
            :delete-callback="page"
            :page-info="pageInfo"/>
</template>

<style scoped lang="scss"></style>
