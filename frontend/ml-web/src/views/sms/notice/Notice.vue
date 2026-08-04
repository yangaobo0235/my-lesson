<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyTable from "../../../components/MyTable.vue";
import {onMounted, reactive, ref} from "vue";
import {deleteApi, deleteBatchApi, pageApi} from "../../../api/index.js";
import {myPage} from "../../../request/index.js";

// 路径导航
const navItems = [
  {icon: 'Goods', label: '营销管理'},
  {icon: 'Opportunity', label: '通知列表'},
];
// 表格列
const columns = [
  {label: '序号', prop: 'idx', type: 'tag', width: 65},
  {label: '通知内容', prop: 'content', type: 'card', width: 500, tooltip: false},
];
// 表格数据 + 分页数据
let records = ref();
let pageInfo = reactive({pageNum: 1, pageSize: 5, callback: page});

/**
 * 分页查询
 *
 * 1. 定义分页基础配置，包括 records, pageInfo, api, params 等。
 * 2. 异步发送分页查询请求。
 *
 * @param pageNum 当前第几页，默认 1
 * @param pageSize 每页多少条，默认 5
 */
async function page(pageNum = pageInfo['pageNum'], pageSize = pageInfo['pageSize']) {
  let config = {
    api: pageApi,
    args: {module: 'notice'},
    params: {pageNum, pageSize},
    records, pageInfo,
  }
  await myPage(config);
}

/* ==================== 加载函数 ==================== */

onMounted(() => page());

</script>

<template v-if="records">
  <my-nav :items="navItems"/>
  <div style="height: 60px"/>
  <my-table module="notice"
            insert-page="/NoticeInsert"
            update-page="/NoticeUpdate"
            :delete-api="deleteApi"
            :delete-batch-api="deleteBatchApi"
            :delete-callback="page"
            :records="records"
            :columns="columns"
            :page-info="pageInfo"/>
</template>

<style scoped lang="scss"></style>
