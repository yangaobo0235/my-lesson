<script setup>
import MyNav from "../../../../components/MyNav.vue";
import MyHead from "../../../../components/MyHead.vue";
import MyTable from "../../../../components/MyTable.vue";
import {onMounted, reactive, ref} from "vue";
import {deleteApi, deleteBatchApi, pageApi} from "../../../../api/index.js";
import {isNotEmpty, isNotNull} from "../../../../util/index.js";
import {myPage} from "../../../../request/index.js";

// 当前父评论ID，父评论内容，视频集ID，视频集标题
let pid = sessionStorage.getItem('pid');
let parentContent = sessionStorage.getItem('parentContent');
let episodeId = sessionStorage.getItem('episodeId');
let episodeTitle = sessionStorage.getItem('episodeTitle');
// 路径导航
const navItems = [
  {icon: 'Notebook', label: '课程管理', url: '/Course'},
  {icon: 'Flag', label: '评论列表', url: '/Comment'},
  {icon: 'Flag', label: '回复列表'},
];
// 数据头
const headItems = [
  {type: 'ipt', span: 5, placeholder: '按评论人昵称搜索', callback: pageLikeNickname},
];
// 表格列
const columns = [
  {label: '被评论视频', prop: 'episode.title'},
  {label: '评论人昵称', prop: 'nickname'},
  {label: '评论内容', prop: 'parentContent', type: 'card', width: 400, tooltip: false},
  {label: '回复内容', prop: 'content', type: 'card', width: 400, tooltip: false},
];

/* ==================== 分页查询 ==================== */

// 表格数据 + 分页数据 + 评论人昵称
let records = ref();
let pageInfo = reactive({pageNum: 1, pageSize: 5, callback: page});
let nickname = ref();

/**
 * 分页查询记录
 *
 * 1. 定义分页基础配置，包括 records, pageInfo, api, params 等。
 * 2. 附加分页查询条件，如标题等。
 * 3. 异步发送分页查询请求。
 * 4. 将 parentContent 融入表格数据的每一项，方便进行展示。
 *
 * @param pageNum 当前第几页，默认 1
 * @param pageSize 每页多少条，默认 5
 */
async function page(pageNum = pageInfo['pageNum'], pageSize = pageInfo['pageSize']) {
  let config = {
    api: pageApi,
    args: {module: 'comment'},
    params: {pageNum, pageSize, pid},
    records, pageInfo,
  }
  if (isNotEmpty(nickname.value)) config['params']['nickname'] = nickname.value;
  await myPage(config);
  if(isNotNull(records.value)){
    Object.values(records.value).forEach(comment => comment['parentContent'] = parentContent);
  }
}

/* ==================== 搜索评论人昵称 ==================== */

/**
 * 按评论人昵称模糊查询
 *
 * 1. 将输入框中的值赋值给分页条件字段变量。
 * 2. 重新发送分页请求。
 *
 * @param val 输入框中的值
 */
function pageLikeNickname(val) {
  if (isNotNull(val) || nickname.value) {
    nickname.value = val;
    page();
  }
}

/* ==================== 加载函数 ==================== */

onMounted(() => page());

</script>

<template v-if="records">
  <my-nav :items="navItems"/>
  <my-head :items="headItems"/>
  <my-table module="comment"
            insert-page="/SubCommentInsert"
            update-page="/SubCommentUpdate"
            :insert-page-param="{parentContent, pid, episodeId, episodeTitle}"
            :update-page-param="{parentContent, episodeTitle}"
            :delete-api="deleteApi"
            :delete-batch-api="deleteBatchApi"
            :delete-callback="page"
            :records="records"
            :columns="columns"
            :page-info="pageInfo"/>
</template>

<style scoped lang="scss"></style>
