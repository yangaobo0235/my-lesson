<script setup>
import MyNav from "../../../../components/MyNav.vue";
import MyHead from "../../../../components/MyHead.vue";
import MyTable from "../../../../components/MyTable.vue";
import {onMounted, reactive, ref} from "vue";
import {deleteApi, deleteBatchApi, pageApi} from "../../../../api/index.js";
import {myPage} from "../.././../../request/index.js";
import {isNotEmpty, isNotNull} from "../../../../util/index.js";
import router from "../../../../router";

// 获取当前课程主键和课程标题
let courseId = sessionStorage.getItem('courseId');
let courseTitle = sessionStorage.getItem('courseTitle');
// 路径导航
const navItems = [
  {icon: 'Notebook', label: '课程管理', url: '/Course'},
  {icon: 'Notebook', label: '（' + courseTitle + '）的季次列表'},
];
// 数据头
const headItems = [
  {type: 'ipt', span: 5, placeholder: '按标题搜索', callback: pageLikeTitle},
];
// 表格列
const columns = [
  {label: '序号', prop: 'idx', type: 'tag', width: 65},
  {label: '标题', prop: 'title', width: 300},
  {label: '描述', prop: 'info', type: 'card', width: 400, tooltip: false},
];

// 表格按钮
const buttons = [
  {label: '集次列表', type: 'success', callback: seasonEpisodes}
];

/* ==================== 分页查询 ==================== */

// 表格数据 + 分页数据 + 季次标题
let records = ref();
let pageInfo = reactive({pageNum: 1, pageSize: 5, callback: page});
let title = ref();

/**
 * 分页查询
 *
 * 1. 定义分页基础配置，包括 records, pageInfo, api, params 等。
 * 2. 附加分页查询条件，如课程主键，标题等。
 * 3. 异步发送分页查询请求。
 * 4. 将 courseTitle 融入表格数据的每一项，方便进行展示。
 *
 * @param pageNum 当前第几页，默认 1
 * @param pageSize 每页多少条，默认 5
 */
async function page(pageNum = pageInfo['pageNum'], pageSize = pageInfo['pageSize']) {
  let config = {
    api: pageApi,
    args: {module: 'season'},
    params: {pageNum, pageSize},
    records, pageInfo,
  }
  config['params']['fkCourseId'] = courseId;
  if (isNotEmpty(title.value)) config['params']['title'] = title.value;
  await myPage(config);
  // 整理季次标题
  if (isNotEmpty(records.value)) {
    // 遍历表格数据，将课程标题与季次标题拼接起来
    Object.values(records.value).forEach(season => season['title'] = `${courseTitle} / ${season['title']}`);
  }
}

/* ==================== 搜索季次标题 ==================== */

/**
 * 按季次标题模糊查询
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

/* ==================== 集次列表 ==================== */

/**
 * 查询集次列表
 *
 * 1. 存储课程主键 courseId
 * 2. 存储课程标题 courseTitle
 * 3. 存储季次主键 episodeId
 * 4. 存储季次标题 episodeTitle
 * 5. 路由到 Episode 页面
 *
 * @param row 表格行
 */
function seasonEpisodes(row) {
  sessionStorage.setItem('courseId', courseId);
  sessionStorage.setItem('courseTitle', courseTitle);
  sessionStorage.setItem('seasonId', row['id']);
  sessionStorage.setItem('seasonTitle', row['title'].toString().split(' / ')[1]);
  router.push('/Episode')
}

/* ==================== 加载函数 ==================== */

onMounted(() => page());

</script>

<template v-if="records">
  <my-nav :items="navItems"/>
  <my-head :items="headItems"/>
  <my-table module="season"
            insert-page="/SeasonInsert"
            update-page="/SeasonUpdate"
            :insert-page-param="{courseId, courseTitle}"
            :update-page-param="{courseId, courseTitle}"
            :records="records"
            :columns="columns"
            :buttons="buttons"
            :delete-api="deleteApi"
            :delete-batch-api="deleteBatchApi"
            :delete-callback="page"
            :pageInfo="pageInfo"/>
</template>

<style scoped lang="scss"></style>
