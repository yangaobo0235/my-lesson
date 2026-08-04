<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyHead from "../../../components/MyHead.vue";
import MyTable from "../../../components/MyTable.vue";
import {onMounted, reactive, ref} from "vue";
import {deleteApi, deleteBatchApi, pageApi} from "../../../api/index.js";
import {myPage} from "../../../request/index.js";
import {isNotEmpty, isNotNull} from "../../../util/index.js";
import {MINIO_COURSE_COVER} from "../../../const/index.js";

// 路径导航
const navItems = [
  {icon: 'Files', label: '订单管理'},
  {icon: 'ShoppingCart', label: '购物车列表'},
];
// 数据头
const headItems = [
  {type: 'ipt', span: 5, placeholder: '按用户账号搜索', callback: pageByUsername},
  {type: 'ipt', span: 5, placeholder: '按课程标题搜索', callback: pageByCourseTitle},
];
// 表格列
const columns = [
  {label: '用户', prop: 'username'},
  {label: '课程', prop: 'courseTitle'},
  {label: '课程封面', prop: 'courseCover', type: 'img', minio: MINIO_COURSE_COVER},
  {label: '课程价格', prop: 'coursePrice', suffix: ' 元'},
];
// 表格数据 + 分页数据 + 用户账号 + 课程标题
let records = ref();
let pageInfo = reactive({pageNum: 1, pageSize: 5, callback: page});
let username = ref();
let courseTitle = ref();

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
    args: {module: 'cart'},
    params: {pageNum, pageSize},
    records, pageInfo,
  }
  if (isNotEmpty(username.value)) config['params']['username'] = username.value;
  if (isNotEmpty(courseTitle.value)) config['params']['courseTitle'] = courseTitle.value;
  await myPage(config);
}

/* ==================== 搜索用户账号 ==================== */

/**
 * 按用户账号查询
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

/* ==================== 搜索课程标题 ==================== */

/**
 * 按课程标题模糊查询
 *
 * 1. 将输入框中的值赋值给分页条件字段变量。
 * 2. 重新发送分页请求。
 *
 * @param val 输入框中的值
 */
function pageByCourseTitle(val) {
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
  <my-table module="cart"
            insert-page="/CartInsert"
            update-page="/CartUpdate"
            :records="records"
            :columns="columns"
            :delete-api="deleteApi"
            :delete-batch-api="deleteBatchApi"
            :delete-callback="page"
            :page-info="pageInfo"/>
</template>

<style scoped lang="scss"></style>
