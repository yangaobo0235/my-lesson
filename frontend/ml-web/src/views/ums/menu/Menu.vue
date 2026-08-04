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
  {icon: 'Avatar', label: '用户管理'},
  {icon: 'Menu', label: '菜单列表（父菜单）'}
];
// 数据头
const headItems = [
  {type: 'ipt', span: 5, placeholder: '搜索父菜单名', callback: pageByTitle},
];
// 表格列
const columns = [
  {label: '序号', prop: 'idx', type: 'tag'},
  {label: '图标', prop: 'icon', type: 'icon', width: 90},
  {label: '名称', prop: 'title', width: 120},
  {label: '描述', prop: 'info', type: 'card', width: 520},
];
// 按钮列
const buttons = [
  {label: '下级菜单', icon: 'Menu', callback: toSubMenu},
];

/* ==================== 跳转下级菜单页面 ==================== */

function toSubMenu(row) {
  sessionStorage.setItem('pid', row['id']);
  sessionStorage.setItem('parentTitle', row['title']);
  router.push('/SubMenu');
}

/* ==================== 分页查询 ==================== */

// 表格数据 + 分页数据 + 菜单名称
let records = ref();
let pageInfo = reactive({pageNum: 1, pageSize: 5, total: 0, callback: page});
let menuTitle = ref();

async function page(pageNum = pageInfo['pageNum'], pageSize = pageInfo['pageSize']) {
  // 分页参数（额外添加 pid=0 保证仅查询父菜单）
  let config = {
    api: pageApi,
    args: {module: 'menu'},
    params: {pageNum, pageSize, pid: 0},
    records, pageInfo
  };
  // 若菜单名不为空，则附加为分页条件
  if (menuTitle.value) config['params']['title'] = menuTitle.value;
  // 发送分页请求
  await myPage(config);
}

/* ==================== 搜索菜单名 ==================== */

function pageByTitle(val) {
  // 仅当输入框有值，或者当前处于按条件分页状态时，发送分页请求
  if (val || menuTitle.value) {
    menuTitle.value = val;
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
  excelApi('/menu/excel', '菜单报表');
}

/* ==================== 加载函数 ==================== */

onMounted(() => page());

</script>

<template>
  <my-nav :items="navItems"/>
  <my-head :items="headItems"/>
  <my-table module="menu"
            insert-page="/MenuInsert"
            update-page="/MenuUpdate"
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
