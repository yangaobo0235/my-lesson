<script setup>
import MyNav from "../../../components/MyNav.vue";
import {getResponseData} from "../../../request";
import {onMounted, ref} from "vue";
import {simpleListApi} from "../../../api/index.js";
import {listMenuIdsByRoleIdApi, updateMenusByRoleIdApi} from "../../../api/ums/menu.js";
import {ElMessage} from "element-plus";
import router from "../../../router";

// 获取路由中的角色主键和角色标题
let roleId = router.currentRoute.value.query['roleId'];
let roleTitle = router.currentRoute.value.query['roleTitle'];
// 路径导航
const navItems = [
  {icon: 'Avatar', label: '用户管理'},
  {icon: 'UserFilled', label: '角色列表', url: '/Role'},
  {icon: 'Edit', label: '为角色重设菜单'},
];
// 全部菜单 + 我的菜单主键数组
let allMenus = ref([]);
let myMenuIds = ref([]);
// 菜单的父子关系
let idToPidMap = {};

/* ==================== 确认修改菜单 ==================== */

/**
 * 处理选中的菜单ID列表
 *
 * 1. 遍历处理选中的菜单ID列表。
 * 2. 将选中的菜单ID追加到临时数组 result 中。
 * 3. 根据菜单的父子关系 idToPidMap 对象获取该菜单的父菜单ID。
 * 4. 若临时数组 result 中已存在相同的父菜单ID，则不会重复追加。
 * 5. 若临时数组 result 中不存在相同的父菜单ID，则追加到临时数组 result 中。
 *
 * @param menuIds 选中的菜单列表
 * @return result 处理后的菜单ID列表，包括全部子菜单的ID和对应父菜单的ID，不存在重复项。
 */
function buildMenuIds(menuIds) {
  let result = [];
  for (let i in menuIds) {
    result.push(menuIds[i]);
    let parentMenuId = idToPidMap[menuIds[i]];
    if (result.indexOf(parentMenuId) === -1) {
      result.push(parentMenuId);
    }
  }
  return result;
}

async function updateMenus() {
  let data = getResponseData(await updateMenusByRoleIdApi(roleId, buildMenuIds(myMenuIds.value)));
  if (data) {
    ElMessage.success('菜单重设成功，下次登录生效！');
  }
}

/* ==================== 加载函数 ==================== */

onMounted(async () => {
  // 查询全部菜单
  Object.values(getResponseData(await simpleListApi(null, {module: 'menu'}))).forEach(menu => {
    // 记录菜单的父子关系： {id01: pid01, id02: pid02 .. }
    idToPidMap[menu['id']] = menu['pid'];
    // 组装 ElTransfer 数据
    if (menu['pid'] !== 0) {
      allMenus.value.push({label: menu['parentTitle'] + ' / ' + menu['title'], key: menu['id']});
    }
  });

  // 查询该角色的菜单
  Object.values(getResponseData(await listMenuIdsByRoleIdApi(roleId))).forEach(menuIds => {
    myMenuIds.value.push(menuIds);
  });
});

</script>

<template>
  <div class="role-menus-body">
    <my-nav :items="navItems"/>
    <el-transfer class="role-menus-transfer"
                 filterable filter-placeholder="输入关键字"
                 v-if="allMenus.length > 0"
                 v-model="myMenuIds"
                 :data="allMenus"
                 :titles="['全部可选菜单', '【' + roleTitle + '】已选菜单']"
                 :props="{key: 'key', label: 'label'}"
                 :button-texts="['移除', '添加']">
      <template #left-footer>
        <el-text class="mx-1" type="info">tips: 请重新选择该角色的菜单！</el-text>
      </template>
      <template #right-footer>
        <el-button type="primary" @click="updateMenus" size="small">确认修改</el-button>
      </template>
    </el-transfer>
  </div>
</template>

<style scoped lang="scss">
.role-menus-body {
  text-align: center; // 内容居中

  .role-menus-transfer {
    margin-top: 65px; // 上外边距
  }
}

:deep(.el-transfer-panel) {
  width: 300px; // 宽度
}

:deep(.el-transfer-panel__body) {
  height: 400px; // 高度
}

:deep(.el-transfer-panel__footer) {
  text-align: center; // 内容居中
}
</style>
