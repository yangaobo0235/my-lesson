<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyHead from "../../../components/MyHead.vue";
import MyTable from "../../../components/MyTable.vue";
import {onMounted, reactive, ref} from "vue";
import {deleteApi, deleteBatchApi, pageApi} from "../../../api/index.js";
import {myPage} from "../../../request/index.js";
import {isNotEmpty, isNotNull} from "../../../util/index.js";
import router from "../../../router/index.js";

// 路径导航
const navItems = [
  {icon: 'Notebook', label: '课程管理'},
  {icon: 'Comment', label: '评论列表'},
];
// 数据头
const headItems = [
  {type: 'ipt', span: 5, placeholder: '按视频集ID搜索', callback: pageByEpisodeId},
  {type: 'ipt', span: 5, placeholder: '按评论人昵称搜索', callback: pageByNickname},
];
// 表格列
const columns = [
  {label: '被评论视频', prop: 'episode.title'},
  {label: '评论人昵称', prop: 'nickname'},
  {label: '评论内容', prop: 'content', type: 'card', width: 500, tooltip: false},
];
// 按钮列
const buttons = [
  {label: '回复列表', type: 'success', callback: listSubComments}
];

/* ==================== 分页查询 ==================== */

// 表格数据 + 分页数据 + 视频集ID + 评论人昵称
let records = ref();
let pageInfo = reactive({pageNum: 1, pageSize: 5, callback: page});
let episodeId = ref();
let nickname = ref();

/**
 * 分页查询
 *
 * 1. 定义分页基础配置，包括 records, pageInfo, api, params 等。
 * 2. 附加分页查询条件，如视频集ID，评论人ID等。
 * 3. 异步发送分页查询请求。
 *
 * @param pageNum 当前第几页，默认 1
 * @param pageSize 每页多少条，默认 5
 */
async function page(pageNum = pageInfo['pageNum'], pageSize = pageInfo['pageSize']) {
  let config = {
    api: pageApi,
    args: {module: 'comment'},
    params: {pageNum, pageSize, pid: 0},
    records, pageInfo,
  }
  if (isNotEmpty(episodeId.value)) config['params']['fkEpisodeId'] = episodeId.value;
  if (isNotEmpty(nickname.value)) config['params']['nickname'] = nickname.value;
  await myPage(config);
  if(isNotNull(records.value)){
    Object.values(records.value).forEach(comment => comment['episode']['title'] = `${comment['episode']['title']}【${comment['episode']['id']}】`);
  }
}

/* ==================== 按视频集ID查询 ==================== */

/**
 * 按视频集ID查询
 *
 * 1. 将输入框中的值赋值给分页条件字段变量。
 * 2. 重新发送分页请求。
 *
 * @param val 输入框中的值
 */
function pageByEpisodeId(val) {
  if (isNotNull(val) || episodeId.value) {
    episodeId.value = val;
    page();
  }
}

/* ==================== 按评论人昵称查询 ==================== */

/**
 * 按评论人昵称查询
 *
 * 1. 将输入框中的值赋值给分页条件字段变量。
 * 2. 重新发送分页请求。
 *
 * @param val 输入框中的值
 */
function pageByNickname(val) {
  if (isNotNull(val) || nickname.value) {
    nickname.value = val;
    page();
  }
}

/* ==================== 查询子评论 ==================== */

/**
 * 查询子评论
 *
 * 1. 存储父评论主键 pid
 * 2. 存储父评论内容 parentContent
 * 3. 存储被评论视频集ID episodeId
 * 4. 存储被评论视频集标题 episodeTitle
 * 5. 路由到 SubComment 页面
 *
 * @param row 表格行
 */
function listSubComments(row) {
  sessionStorage.setItem('pid', row['id']);
  sessionStorage.setItem('parentContent', row['content'].toString());
  sessionStorage.setItem('episodeId', row['episode']['id']);
  sessionStorage.setItem('episodeTitle', row['episode']['title'].toString());
  router.push('/SubComment');
}

/* ==================== 加载函数 ==================== */

onMounted(() => page());

</script>

<template v-if="records">
  <my-nav :items="navItems"/>
  <my-head :items="headItems"/>
  <my-table class="comment-list-table"
            module="comment"
            insert-page="/CommentInsert"
            update-page="/CommentUpdate"
            :delete-api="deleteApi"
            :delete-batch-api="deleteBatchApi"
            :delete-callback="page"
            :records="records"
            :columns="columns"
            :buttons="buttons"
            :pageInfo="pageInfo"/>
</template>

<style scoped lang="scss"></style>
