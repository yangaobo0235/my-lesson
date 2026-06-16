<script setup>
import MyNav from "../../../components/MyNav.vue";
import {onMounted, ref} from "vue";
import {ElMessage} from "element-plus";
import {getResponseData} from "../../../request/index.js";
import {listRoleIdsByUserIdApi, updateRolesByUserIdApi} from "../../../api/ums/role.js";
import {isNotNull} from "../../../util/index.js";
import {simpleListApi} from "../../../api/index.js";

// 获取当前用户主键和用户昵称
let userId = sessionStorage.getItem('userId');
let nickname = sessionStorage.getItem('nickname');
// 路径导航
const navItems = [
  {icon: 'User', label: '用户管理'},
  {icon: 'User', label: '用户列表', url: '/User'},
  {icon: 'UserFilled', label: '为用户重设角色'},
];
// 全部角色 + 我的角色主键数组
let allRoles = ref([]);
let myRoleIds = ref([]);

/* ==================== 确认修改用户的角色 ==================== */

async function updateMyRoles() {
  let data = getResponseData(await updateRolesByUserIdApi(userId, myRoleIds.value));
  if (isNotNull(data)) {
    ElMessage.success('角色重设成功，下次登录生效！');
  }
}

/* ==================== 加载函数 ==================== */

onMounted(async () => {
  // 查询全部角色
  Object.values(getResponseData(await simpleListApi(null, {module: 'role'}))).forEach(role => {
    allRoles.value.push({label: role['title'], key: role['id']});
  });

  // 查询该员工的角色
  Object.values(getResponseData(await listRoleIdsByUserIdApi(userId))).forEach(roleIds => {
    myRoleIds.value.push(roleIds);
  });
});

</script>

<template v-if="allRoles">
  <div class="user-roles-body">
    <my-nav :items="navItems"/>
    <el-transfer class="user-roles-transfer"
                 filterable filter-placeholder="输入关键字"
                 v-if="allRoles.length > 0"
                 v-model="myRoleIds"
                 :data="allRoles"
                 :titles="['全部可选角色', '当前已选角色']"
                 :props="{key: 'key', label: 'label'}"
                 :button-texts="['移除', '添加']">
      <template #left-footer>
        <el-text class="mx-1" type="info">tips: 请重新选择该用户的角色！</el-text>
      </template>
      <template #right-footer>
        <el-button type="primary" @click="updateMyRoles" size="small">确认修改</el-button>
      </template>
    </el-transfer>
  </div>
</template>

<style scoped lang="scss">
.user-roles-body {
  text-align: center; // 内容居中

  .user-roles-transfer {
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
