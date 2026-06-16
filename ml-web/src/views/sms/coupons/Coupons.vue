<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyTable from "../../../components/MyTable.vue";
import MyHead from "../../../components/MyHead.vue";
import {onMounted, reactive, ref} from "vue";
import {deleteApi, deleteBatchApi, pageApi} from "../../../api/index.js";
import {myPage} from "../../../request/index.js";
import {dateFormat, isNotEmpty, isNotNull} from "../../../util/index.js";

// 路径导航
const navItems = [
  {icon: 'Goods', label: '营销管理'},
  {icon: 'Opportunity', label: '优惠卷列表'},
];
// 数据头
const headItems = [
  {type: 'ipt', span: 5, placeholder: '按标题搜索', callback: pageLikeTitle},
  {type: 'ipt', span: 5, placeholder: '按口令搜索', callback: pageLikeCode},
];
// 表格列
const columns = [
  {label: '兑换码', prop: 'code', type: 'tag', sortable: false},
  {label: '标题', prop: 'title'},
  {label: '优惠价格', prop: 'cpPrice', suffix: '.00 元', width: 130},
  {label: '生效时间', prop: 'startTime', format: dateFormat, width: 150},
  {label: '失效时间', prop: 'endTime', format: dateFormat, width: 150},
  {label: '优惠卷描述', prop: 'info', type: 'card', tooltip: false},
];

/* ==================== 分页查询 ==================== */

// 表格数据 + 分页数据 + 优惠卷标题 + 优惠卷口令
let records = ref();
let pageInfo = reactive({pageNum: 1, pageSize: 5, callback: page});
let title = ref();
let code = ref();

/**
 * 分页查询
 *
 * 1. 定义分页基础配置，包括 records, pageInfo, api, params 等。
 * 2. 附加分页查询条件，如标题，口令等。
 * 3. 异步发送分页查询请求。
 *
 * @param pageNum 当前第几页，默认 1
 * @param pageSize 每页多少条，默认 5
 */
async function page(pageNum = pageInfo['pageNum'], pageSize = pageInfo['pageSize']) {
  let config = {
    api: pageApi,
    args: {module: 'coupons'},
    params: {pageNum, pageSize, pid: 0},
    records, pageInfo,
  }
  if (isNotEmpty(title.value)) config['params']['title'] = title.value;
  if (isNotEmpty(code.value)) config['params']['code'] = code.value;
  await myPage(config);
}

/* ==================== 搜索优惠卷标题 ==================== */

/**
 * 按优惠卷标题模糊查询
 *
 * 1. 将输入框中的值赋值给分页条件字段变量。
 * 2. 重新发送分页请求。
 *
 * @param val 输入框中的值
 */
function pageLikeTitle(val) {
  if (isNotNull(val) || title.value) {
    title.value = val;
    page();
  }
}

/* ==================== 搜索优惠卷口令 ==================== */

/**
 * 按优惠卷口令模糊查询
 *
 * 1. 将输入框中的值赋值给分页条件字段变量。
 * 2. 重新发送分页请求。
 *
 * @param val 输入框中的值
 */
function pageLikeCode(val) {
  if (isNotNull(val) || code.value) {
    code.value = val;
    page();
  }
}

/* ==================== 加载函数 ==================== */

onMounted(() => page());

</script>

<template v-if="records">
  <my-nav :items="navItems"/>
  <my-head :items="headItems"/>
  <my-table module="coupons"
            insert-page="/CouponsInsert"
            update-page="/CouponsUpdate"
            :records="records"
            :columns="columns"
            :delete-api="deleteApi"
            :delete-batch-api="deleteBatchApi"
            :delete-callback="page"
            :page-info="pageInfo"/>
</template>

<style scoped lang="scss"></style>
