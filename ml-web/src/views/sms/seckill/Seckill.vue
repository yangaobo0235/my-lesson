<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyHead from "../../../components/MyHead.vue";
import MyTable from "../../../components/MyTable.vue";
import {onMounted, reactive, ref} from "vue";
import {deleteApi, deleteBatchApi, pageApi} from "../../../api/index.js";
import {myPage} from "../../../request/index.js";
import {dateFormat, isNotEmpty, isNotNull, seckillStatusFormat} from "../../../util/index.js";
import router from "../../../router/index.js";

// 路径导航
const navItems = [
  {icon: 'Goods', label: '营销管理'},
  {icon: 'Stopwatch', label: '秒杀列表'},
];
// 数据头
const headItems = [
  {type: 'ipt', span: 5, placeholder: '按标题搜索', callback: pageLikeTitle},
];
// 表格列
const columns = [
  {label: '活动标题', prop: 'title', width: 120},
  {label: '开始时间', prop: 'startTime', format: dateFormat},
  {label: '结束时间', prop: 'endTime', format: dateFormat},
  {label: '活动状态', prop: 'status', type: 'tag', format: seckillStatusFormat},
  {label: '活动描述', prop: 'info', type: 'card', tooltip: false},
];
// 按钮列
const buttons = [
  {label: '秒杀详情', type: 'success', callback: seckillDetail},
];
// 表格数据 + 分页数据 + 秒杀活动标题
let records = ref();
let pageInfo = reactive({pageNum: 1, pageSize: 5, callback: page});
let title = ref();

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
    args: {module: 'seckill'},
    params: {pageNum, pageSize},
    records, pageInfo,
  }
  if (isNotEmpty(title.value)) config['params']['title'] = title.value;
  await myPage(config);
}

/* ==================== 搜索秒杀标题 ==================== */

/**
 * 按秒杀标题模糊查询
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

/* ==================== 秒杀详情 ==================== */

function seckillDetail(row) {
  sessionStorage.setItem('seckillId', row['id']);
  sessionStorage.setItem('seckillTitle', row['title'].toString());
  router.push('/SeckillDetail');
}

/* ==================== 加载函数 ==================== */

onMounted(() => page());

</script>

<template v-if="records">
  <my-nav :items="navItems"/>
  <my-head :items="headItems"/>
  <my-table module="seckill"
            insert-page="/SeckillInsert"
            update-page="/SeckillUpdate"
            :records="records"
            :columns="columns"
            :buttons="buttons"
            :delete-api="deleteApi"
            :delete-batch-api="deleteBatchApi"
            :delete-callback="page"
            :page-info="pageInfo"/>
</template>

<style scoped lang="scss"></style>
