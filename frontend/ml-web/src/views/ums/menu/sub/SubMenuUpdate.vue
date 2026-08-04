<script setup>
import MyForm from "../../../../components/MyForm.vue";
import MyNav from "../../../../components/MyNav.vue";
import {reactive, ref} from "vue";
import {updateApi} from "../../../../api/index.js";
import {RULE} from "../../../../const";
import {ElMessage} from "element-plus";
import router from "../../../../router";

// 菜单记录
let menu = JSON.parse(sessionStorage.getItem('row'));
// 父菜单ID和父菜单名称
const pid = sessionStorage.getItem('pid');
const parentTitle = sessionStorage.getItem('parentTitle');
menu['pid'] = pid;
menu['parentTitle'] = parentTitle;
// 路径导航
const navItems = [
  {icon: 'Avatar', label: '用户管理'},
  {icon: 'Menu', label: '菜单列表（父菜单）', url: '/Menu'},
  {icon: 'Menu', label: '菜单列表（子菜单）', url: '/SubMenu'},
  {icon: 'Edit', label: '修改菜单（子菜单）'},
];
// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '父菜单', prop: 'parentTitle', disabled: true},
  {label: '名称', prop: 'title', required: true, span: 12},
  {label: '序号', prop: 'idx', type: 'number', required: true, span: 12},
  {label: '地址', prop: 'url', required: true},
  {label: '图标', prop: 'icon', required: true, type: 'icon'},
  {label: '描述', prop: 'info', required: true, type: 'textarea', rows: 5},
]);
let params = reactive(menu);
let rules = {title: RULE.TITLE, info: RULE.INFO};

/* ==================== 修改成功后 ==================== */

function updateSuccess() {
  ElMessage.success('修改记录成功！');
  setTimeout(() => router.push('/SubMenu'), 1000);
}
</script>

<template>
  <my-nav :items="navItems"/>
  <el-card class="menu-update-card" header="修改菜单（子菜单）">
    <my-form type="update"
             :items="items"
             :params="params"
             :rules="rules"
             :api="updateApi"
             :args="{module: 'menu'}"
             :callback="updateSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.menu-update-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
