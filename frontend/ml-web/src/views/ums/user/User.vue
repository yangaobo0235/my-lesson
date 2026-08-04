<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyHead from "../../../components/MyHead.vue";
import MyTable from "../../../components/MyTable.vue";
import {onMounted, reactive, ref} from "vue";
import {deleteApi, deleteBatchApi, excelApi, pageApi} from "../../../api/index.js";
import {MINIO_AVATAR} from "../../../const/index.js";
import {getResponseData, myPage} from "../../../request/index.js";
import {isNotEmpty, isNotNull} from "../../../util/index.js";
import {genderFormat} from "../../../util/index.js";
import {resetPasswordApi} from "../../../api/ums/user.js";
import {ElMessage, ElMessageBox} from "element-plus";
import router from "../../../router";

// 路径导航
const navItems = [
  {icon: 'User', label: '用户管理'},
  {icon: 'User', label: '用户列表'},
];
// 数据头
const headItems = [
  {type: 'ipt', span: 5, placeholder: '按账号搜索', callback: pageByUsername},
  {type: 'ipt', span: 5, placeholder: '按昵称搜索', callback: pageByNickname},
  {type: 'ipt', span: 5, placeholder: '按手机搜索', callback: pageByPhone},
]

// 表格列
const columns = [
  {label: '头像', prop: 'avatar', type: 'img', minio: MINIO_AVATAR},
  {label: '性别', prop: 'gender', type: 'tag', format: genderFormat, width: 80, tagTypeFn:  e => e === 0 ? 'danger' : e === 1 ? 'primary' : 'warning'},
  {label: '昵称', prop: 'nickname'},
  {label: '账号', prop: 'username'},
  {label: '手机', prop: 'phone', width: 100},
  {label: '邮件', prop: 'email'},
  {label: '姓名', prop: 'realname', width: 80},
  {label: '身份证号', prop: 'idcard'},
  {label: '年龄', prop: 'age', width: 80},
  {label: '星座', prop: 'zodiac', type: 'tag', width: 80},
  {label: '籍贯', prop: 'province', type: 'tag', width: 100},
  {label: '描述', prop: 'info', type: 'card'},
];
// 按钮列
const buttons = [
  {label: '重设角色', type: 'success', callback: toUserUpdateRoles},
  {label: '重置密码', type: 'danger', callback: resetPassword}
];

/* ==================== 分页查询 ==================== */

// 表格数据 + 分页数据
let records = ref();
let pageInfo = reactive({pageNum: 1, pageSize: 5, total: 0, callback: page});
// 分页查询条件字段：账号，昵称，手机
let username = ref();
let nickname = ref();
let phone = ref();

/**
 * 分页查询记录
 *
 * 1. 定义分页基础配置，包括 records, pageInfo, api, params 等。
 * 2. 附加分页查询条件，如账号，昵称，手机号码等。
 * 3. 异步发送分页查询请求。
 *
 * @param pageNum 当前第几页，默认 1
 * @param pageSize 每页多少条，默认 5
 */
async function page(pageNum = pageInfo['pageNum'], pageSize = pageInfo['pageSize']) {

  // 分页基础配置
  let config = {
    api: pageApi,
    args: {module: 'user'},
    params: {pageNum, pageSize},
    records, pageInfo,
  }
  // 附加为分页条件
  if (isNotEmpty(username.value)) config['params']['username'] = username.value;
  if (isNotEmpty(nickname.value)) config['params']['nickname'] = nickname.value;
  if (isNotEmpty(phone.value)) config['params']['phone'] = phone.value;
  // 发送分页请求
  await myPage(config);
}

/* ==================== 按登录账号模糊查询 ==================== */

/**
 * 若输入框有值，或者当前正处于按条件分页状态时，进行操作：
 *
 * <p> 1. 将输入框中的值赋值给分页条件字段变量。
 * <p> 2. 重新发送分页请求。
 *
 * @param val 输入框中的值
 */
function pageByUsername(val) {
  // 仅当输入框有值，或者当前处于按条件分页状态时，发送分页请求
  if (isNotNull(val) || username.value) {
    username.value = val;
    page();
  }
}

/* ==================== 按用户昵称模糊查询 ==================== */

/**
 * 若输入框有值，或者当前正处于按条件分页状态时，进行操作：
 *
 * <p> 1. 将输入框中的值赋值给分页条件字段变量。
 * <p> 2. 重新发送分页请求。
 *
 * @param val 输入框中的值
 */
function pageByNickname(val) {

  // 仅当输入框有值，或者当前处于按条件分页状态时，发送分页请求
  if (isNotNull(val) || nickname.value) {
    nickname.value = val;
    page();
  }
}

/* ==================== 按手机号码模糊查询 ==================== */

/**
 * 若输入框有值，或者当前正处于按条件分页状态时，进行操作：
 *
 * <p> 1. 将输入框中的值赋值给分页条件字段变量。
 * <p> 2. 重新发送分页请求。
 *
 * @param val 输入框中的值
 */
function pageByPhone(val) {

  // 仅当输入框有值，或者当前处于按条件分页状态时，发送分页请求
  if (isNotNull(val) || phone.value) {
    phone.value = val;
    page();
  }
}

/* ==================== 重置密码 ==================== */

async function resetPassword(row) {
  ElMessageBox.confirm('确认重置密码吗？').then(() => {
    resetPasswordApi(row['id']).then(res => {
          if (isNotNull(getResponseData(res))) {
            ElMessage.success('密码重置为 123456789');
          }
        }
    ).catch(() => {
      ElMessage.info('已取消');
    });
  });
}

/* ==================== 重设角色 ==================== */

function toUserUpdateRoles(row) {
  sessionStorage.setItem('userId', row['id']);
  sessionStorage.setItem('nickname', row['nickname'].toString());
  router.push('/UserUpdateRoles')
}

/* ==================== 报表打印 ==================== */

function downloadExcel() {
  excelApi('/user/excel', '用户报表');
}

/* ==================== 删除成功回调 ==================== */

function deleteSuccess() {
  ElMessage.success('删除成功');
  page();
}

/* ==================== 加载函数 ==================== */

onMounted(() => page());

</script>

<template v-if="records">
  <my-nav :items="navItems"/>
  <my-head :items="headItems"/>
  <my-table module="user"
            insert-page="/UserInsert"
            update-page="/UserUpdate"
            :records="records"
            :columns="columns"
            :buttons="buttons"
            :delete-api="deleteApi"
            :delete-batch-api="deleteBatchApi"
            :delete-callback="deleteSuccess"
            :excel-api="downloadExcel"
            :page-info="pageInfo"/>
</template>

<style scoped lang="scss"></style>
