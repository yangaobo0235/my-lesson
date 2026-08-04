<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyHead from "../../../components/MyHead.vue";
import MyTable from "../../../components/MyTable.vue";
import {onMounted, ref, reactive} from "vue";
import {myPage} from "../../../request";
import {deleteApi, deleteBatchApi, excelApi, pageApi} from "../../../api/index.js";
import {ElMessage} from "element-plus";
import router from "../../../router/index.js";

// 路径导航
const navItems = [
  {icon: 'User', label: '用户管理'},
  {icon: 'UserFilled', label: '角色列表'}
];
// 数据头
const headItems = [
  {type: 'ipt', span: 5, placeholder: '搜索角色标题', callback: pageByTitle},
];
// 表格列
const columns = [
  {label: '序号', prop: 'idx', type: 'tag'},
  {label: '标题', prop: 'title'},
  {label: '描述', prop: 'info', type: 'card', width: 520},
];
// 按钮列
const buttons = [
  {label: '重设菜单', icon: 'Edit', callback: toRoleUpdateMenus},
];

/* ==================== 重设菜单 ==================== */

function toRoleUpdateMenus(row) {
  router.push({
    path: '/RoleUpdateMenus', query: {
      roleId: row['id'],
      roleTitle: row['title']
    }
  });
}

/* ==================== 分页查询 ==================== */

// 表格数据 + 分页数据 + 角色标题
let records = ref();
let pageInfo = reactive({pageNum: 1, pageSize: 5, total: 0, callback: page});
let roleTitle = ref();

async function page(pageNum = pageInfo['pageNum'], pageSize = pageInfo['pageSize']) {
  // 分页参数
  let config = {
    api: pageApi,
    args: {module: 'role'},
    params: {pageNum, pageSize},
    records, pageInfo
  };
  // 若角色标题不为空，则附加为分页条件
  if (roleTitle.value) config['params']['title'] = roleTitle.value;
  // 发送分页请求
  await myPage(config);
}

/* ==================== 搜索角色标题 ==================== */

function pageByTitle(val) {
  // 仅当输入框有值，或者当前处于按条件分页状态时，发送分页请求
  if (val || roleTitle.value) {
    roleTitle.value = val;
    page();
  }
}

/* ==================== 删除成功回调 ==================== */

function deleteSuccess() {
  ElMessage.success('删除成功！');
  page();
}

/* ==================== 报表打印 ==================== */

function downloadExcel() {
  excelApi('/role/excel', '角色报表');
}

/* ==================== 加载函数 ==================== */

onMounted(() => page());
</script>

<template>
  <my-nav :items="navItems"/>
  <my-head :items="headItems"/>
  <my-table module="role"
            insert-page="/RoleInsert"
            update-page="/RoleUpdate"
            :records="records"
            :columns="columns"
            :buttons="buttons"
            :delete-api="deleteApi"
            :delete-batch-api="deleteBatchApi"
            :delete-callback="deleteSuccess"
            :excel-api="downloadExcel"
            :pageInfo="pageInfo"/>
</template>

<style scoped lang="scss"></style>
