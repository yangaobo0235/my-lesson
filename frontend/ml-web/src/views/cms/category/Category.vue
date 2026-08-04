<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyHead from "../../../components/MyHead.vue";
import MyTable from "../../../components/MyTable.vue";
import {onMounted, reactive, ref} from "vue";
import {deleteApi, deleteBatchApi, pageApi} from "../../../api/index.js";
import {myPage} from "../../../request/index.js";
import {isNotEmpty, isNotNull} from "../../../util/index.js";
import {ElMessage} from "element-plus";

// 路径导航
const navItems = [
  {icon: 'Notebook', label: '课程管理'},
  {icon: 'Management', label: '类别列表'},
];
// 数据头
const headItems = [
  {type: 'ipt', span: 5, placeholder: '按标题搜索', callback: pageLikeTitle},
];
// 表格列
const columns = [
  {label: '序号', prop: 'idx', type: 'tag', width: 65},
  {label: '标题', prop: 'title', width: 100},
  {label: '描述', prop: 'info', type: 'card', width: 500, sortable: false, tooltip: false},
];

/* ==================== 分页查询 ==================== */

// 表格数据 + 分页数据 + 类别标题
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
    args: {module: 'category'},
    params: {pageNum, pageSize},
    records, pageInfo,
  }
  if (isNotEmpty(title.value)) config['params']['title'] = title.value;
  await myPage(config);
}

/* ==================== 搜索类别标题 ==================== */

/**
 * 按类别标题模糊查询
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

/* ==================== 删除成功回调 ==================== */

function deleteSuccess() {
  ElMessage.success('删除成功！');
  page();
}

/* ==================== 加载函数 ==================== */

onMounted(() => page());

</script>

<template v-if="records">
  <my-nav :items="navItems"/>
  <my-head :items="headItems"/>
  <my-table module="category"
            insert-page="/CategoryInsert"
            update-page="/CategoryUpdate"
            :records="records"
            :columns="columns"
            :delete-api="deleteApi"
            :delete-batch-api="deleteBatchApi"
            :delete-callback="deleteSuccess"
            :pageInfo="pageInfo"/>
</template>

<style scoped lang="scss"></style>
