<script setup>
import MyNav from "../../../../components/MyNav.vue";
import MyHead from "../../../../components/MyHead.vue";
import MyTable from "../../../../components/MyTable.vue";
import {onMounted, reactive, ref} from "vue";
import {deleteApi, deleteBatchApi, pageApi} from "../../../../api/index.js";
import {myPage} from "../.././../../request/index.js";
import {isNotEmpty, isNotNull} from "../../../../util/index.js";
import {MINIO_EPISODE_VIDEO_COVER} from "../../../../const/index.js";
import router from "../../../../router";

// 获取当前课程主键，课程标题，季次主键，季次标题
let courseId = sessionStorage.getItem('courseId');
let courseTitle = sessionStorage.getItem('courseTitle');
let seasonId = sessionStorage.getItem('seasonId');
let seasonTitle = sessionStorage.getItem('seasonTitle');
// 路径导航
const navItems = [
  {icon: 'Notebook', label: '课程管理', url: '/Course'},
  {icon: 'Notebook', label: '（' + courseTitle + '）的季次列表', url: '/Season'},
  {icon: 'Notebook', label: '（' + seasonTitle + '）的集次列表'},
];
// 数据头
const headItems = [
  {type: 'ipt', span: 5, placeholder: '按标题搜索', callback: pageLikeTitle},
];
// 表格列
const columns = [
  {label: '序号', prop: 'idx', type: 'tag', width: 70},
  {label: '封面图片', prop: 'cover', width: 120, type: 'img', minio: MINIO_EPISODE_VIDEO_COVER},
  {label: '标题', prop: 'title', width: 400},
  {label: '描述', prop: 'info', width: 400, type: 'card', tooltip: false},
];
// 按钮列
const buttons = [
  {label: '集次视频', type: 'success', callback: episodeVideo}
];

/* ==================== 分页查询 ==================== */

// 表格数据 + 分页数据 + 集次标题
let records = ref();
let pageInfo = reactive({pageNum: 1, pageSize: 5, callback: page});
let title = ref();

/**
 * 分页查询
 *
 * 1. 定义分页基础配置，包括 records, pageInfo, api, params 等。
 * 2. 附加分页查询条件，如季次主键，标题等。
 * 3. 异步发送分页查询请求。
 * 4. 将 courseTitle 和 seasonTitle 融入表格数据的每一项，方便进行展示。
 *
 * @param pageNum 当前第几页，默认 1
 * @param pageSize 每页多少条，默认 5
 */
async function page(pageNum = pageInfo['pageNum'], pageSize = pageInfo['pageSize']) {
  let config = {
    api: pageApi,
    args: {module: 'episode'},
    params: {pageNum, pageSize},
    records, pageInfo,
  }
  config['params']['fkSeasonId'] = seasonId;
  if (isNotEmpty(title.value)) config['params']['title'] = title.value;
  await myPage(config);
  // 整合课程标题和季次标题
  if (isNotEmpty(records.value)) {
    // 将课程标题和季次标题融入表格数据的每一项
    Object.values(records.value).forEach(episode => episode['title'] = `${courseTitle} / ${seasonTitle} / ${episode['title']}`);
  }
}

/* ==================== 搜索集次标题 ==================== */

/**
 * 按集次标题模糊查询
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

/* ==================== 集次视频 ==================== */

/**
 * 查询集次视频
 *
 * 1. 存储课程主键 courseId
 * 2. 存储课程标题 courseTitle
 * 3. 存储季次主键 episodeId
 * 4. 存储季次标题 episodeTitle
 * 5. 路由到 Episode 页面
 *
 * @param row 表格行
 */
function episodeVideo(row) {
  sessionStorage.setItem('courseTitle', courseTitle);
  sessionStorage.setItem('seasonTitle', seasonTitle);
  sessionStorage.setItem('episodeId', row['id']);
  sessionStorage.setItem('episodeTitle', row['title'].toString().split(' / ')[2]);
  sessionStorage.setItem('video', row['video']);
  sessionStorage.setItem('cover', row['cover']);
  router.push('/EpisodeVideo');
}

/* ==================== 加载函数 ==================== */

onMounted(() => page());
</script>

<template v-if="records">
  <my-nav :items="navItems"/>
  <my-head :items="headItems"/>
  <my-table module="episode"
            insert-page="/EpisodeInsert"
            update-page="/EpisodeUpdate"
            :insert-page-param="{courseId, courseTitle, seasonId, seasonTitle}"
            :update-page-param="{courseId, courseTitle, seasonId, seasonTitle}"
            :delete-api="deleteApi"
            :delete-batch-api="deleteBatchApi"
            :delete-callback="page"
            :records="records"
            :columns="columns"
            :buttons="buttons"
            :pageInfo="pageInfo"/>
</template>

<style scoped lang="scss"></style>
