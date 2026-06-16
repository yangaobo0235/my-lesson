<script setup>
import MyNav from "../../../../components/MyNav.vue";
import MyHead from "../../../../components/MyHead.vue";
import MyTable from "../../../../components/MyTable.vue";
import {onMounted, reactive, ref} from "vue";
import {deleteApi, deleteBatchApi, pageApi} from "../../../../api/index.js";
import {isNotEmpty, isNotNull} from "../../../../util/index.js";
import {myPage} from "../../../../request/index.js";
import {MINIO_COURSE_COVER} from "../../../../const/index.js";

// 所属秒杀活动ID和秒杀活动标题
let seckillId = sessionStorage.getItem('seckillId');
let seckillTitle = sessionStorage.getItem('seckillTitle');
// 路径导航
const navItems = [
  {icon: 'Goods', label: '营销管理'},
  {icon: 'Stopwatch', label: '秒杀列表', url: '/Seckill'},
  {icon: 'Stopwatch', label: `《${seckillTitle}》活动详情`},
];
// 数据头
const headItems = [
  {type: 'ipt', span: 5, placeholder: '按标题搜索', callback: pageLikeCourseTitle},
];
// 表格列
const columns = [
  {label: '课程标题', prop: 'courseTitle'},
  {label: '课程封面', prop: 'courseCover', type: 'img', minio: MINIO_COURSE_COVER},
  {label: '课程原价', prop: 'coursePrice', suffix: '.00元'},
  {label: '课程秒杀价', prop: 'skPrice', suffix: '.00元'},
  {label: '课程秒杀数量', prop: 'skCount'},
  {label: '秒杀描述', prop: 'info', type: 'card', tooltip: false},
];
// 表格数据 + 分页数据 + 课程标题
let records = ref();
let pageInfo = reactive({pageNum: 1, pageSize: 5, callback: page});
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
    args: {module: 'seckillDetail'},
    params: {pageNum, pageSize, seckillId},
    records, pageInfo,
  }
  if (isNotEmpty(courseTitle.value)) config['params']['courseTitle'] = courseTitle.value;
  await myPage(config);
  if(isNotNull(records.value)){
    Object.values(records.value).forEach(seckill => seckill['seckillTitle'] = `${seckillTitle}`);
  }
}

/* ==================== 搜索课程标题 ==================== */

/**
 * 按秒杀详情标题模糊查询
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
  <my-table module="seckillDetail"
            insert-page="/SeckillDetailInsert"
            update-page="/SeckillDetailUpdate"
            :records="records"
            :columns="columns"
            :delete-api="deleteApi"
            :delete-batch-api="deleteBatchApi"
            :delete-callback="page"
            :page-info="pageInfo"/>
</template>

<style scoped lang="scss"></style>
