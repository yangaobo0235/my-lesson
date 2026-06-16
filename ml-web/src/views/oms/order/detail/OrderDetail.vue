<script setup>
import MyNav from "../../../../components/MyNav.vue";
import MyHead from "../../../../components/MyHead.vue";
import MyTable from "../../../../components/MyTable.vue";
import {onMounted, reactive, ref} from "vue";
import {deleteApi, deleteBatchApi, pageApi} from "../../../../api/index.js";
import {isNotEmpty, isNotNull} from "../../../../util/index.js";
import {myPage} from "../../../../request/index.js";
import {MINIO_COURSE_COVER} from "../../../../const/index.js";

// 所属订单ID和所属订单编号
let orderId = sessionStorage.getItem('orderId');
let orderSn = sessionStorage.getItem('orderSn');
// 路径导航
const navItems = [
  {icon: 'Files', label: '订单管理'},
  {icon: 'Goods', label: '订单列表', url: '/Order'},
  {icon: 'Stopwatch', label: `《${orderSn}》订单详情`},
];
// 数据头
const headItems = [
  {type: 'ipt', span: 5, placeholder: '按订单编号搜索', callback: pageBySn},
  {type: 'ipt', span: 5, placeholder: '按课程标题搜索', callback: pageLikeCourseTitle},
];
// 表格列
const columns = [
  {label: '订单编号', prop: 'sn', type: 'tag', tagType: 'info', width: 200},
  {label: '课程标题', prop: 'courseTitle'},
  {label: '课程封面', prop: 'courseCover', type: 'img', minio: MINIO_COURSE_COVER},
  {label: '课程原价', prop: 'coursePrice', suffix: '.00元'},
];
// 表格数据 + 分页数据 + 订单编号 + 课程标题
let records = ref();
let pageInfo = reactive({pageNum: 1, pageSize: 5, callback: page});
let sn = ref();
let courseTitle = ref();

/* ==================== 分页查询 ==================== */

/**
 * 分页查询记录
 *
 * 1. 定义分页基础配置，包括 records, pageInfo, api, params 等。
 * 2. 附加分页查询条件，如标题等。
 * 3. 异步发送分页查询请求。
 * 4. 将 seckillTitle 融入表格数据的每一项，方便进行展示。
 *
 * @param pageNum 当前第几页，默认 1
 * @param pageSize 每页多少条，默认 5
 */
async function page(pageNum = pageInfo['pageNum'], pageSize = pageInfo['pageSize']) {
  let config = {
    api: pageApi,
    args: {module: 'orderDetail'},
    params: {pageNum, pageSize, orderId},
    records, pageInfo,
  }
  if (isNotEmpty(sn.value)) config['params']['sn'] = sn.value;
  if (isNotEmpty(courseTitle.value)) config['params']['courseTitle'] = courseTitle.value;
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

/* ==================== 搜索课程标题 ==================== */

/**
 * 按课程标题模糊查询
 *
 * 1. 将输入框中的值赋值给分页条件字段变量。
 * 2. 重新发送分页请求。
 *
 * @param val 输入框中的值
 */
function pageLikeCourseTitle(val) {
  if (isNotNull(val) || courseTitle.value) {
    courseTitle.value = val;
    page();
  }
}

/* ==================== 加载函数 ==================== */

onMounted(() => page());

</script>

<template v-if="records">
  <my-nav :items="navItems"/>
  <my-head :items="headItems"/>
  <my-table module="orderDetail"
            insert-page="/OrderDetailInsert"
            update-page="/OrderDetailUpdate"
            :records="records"
            :columns="columns"
            :delete-api="deleteApi"
            :delete-batch-api="deleteBatchApi"
            :delete-callback="page"
            :page-info="pageInfo"/>
</template>

<style scoped lang="scss"></style>
