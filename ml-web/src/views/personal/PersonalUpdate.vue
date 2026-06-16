<script setup>
import router from "../../router/index.js";
import MyForm from "../../components/MyForm.vue";
import MyNav from "../../components/MyNav.vue";
import MyUpload from "../../components/MyUpload.vue";
import {reactive, ref} from "vue";
import {updateApi} from "../../api/index.js";
import {updatePasswordApi, UPLOAD_AVATAR_URL} from "../../api/ums/user.js";
import {GENDER_OPTIONS, PROVINCE_OPTIONS, RULE, ZODIAC_OPTIONS} from "../../const/index.js";
import {ElMessage} from "element-plus";

// 获取当前登录的用户记录
const loginUser = JSON.parse(sessionStorage.getItem('loginUser'));
// 路径导航
const navItems = [
  {icon: 'House', label: 'DashBoard', url: '/DashBoard'},
  {icon: 'Edit', label: '修改个人信息'},
];
// 修改基本信息表单：表单项 + 表单值 + 表单规则
let items = ref([
  {label: '账号', prop: 'username', required: true, disabled: true},
  {label: '姓名', prop: 'realname', required: true, disabled: true},
  {label: '手机号码', prop: 'phone', required: true, disabled: true},
  {label: '身份证号', prop: 'idcard', required: true, disabled: true},
  {label: '昵称', prop: 'nickname', required: true, span: 12},
  {label: '邮箱', prop: 'email', required: true, span: 12},
  {label: '性别', prop: 'gender', required: true, type: 'select', options: GENDER_OPTIONS, span: 12},
  {label: '年龄', prop: 'age', required: true, span: 12, type: 'number'},
  {label: '星座', prop: 'zodiac', required: true, type: 'select', options: ZODIAC_OPTIONS, span: 12},
  {label: '省份', prop: 'province', required: true, type: 'select', options: PROVINCE_OPTIONS, span: 12},
  {label: '描述', prop: 'info', type: 'textarea', rows: 8},
]);
let params = reactive(loginUser);
let rules = {nickname: RULE.NICKNAME, email: RULE.EMAIL, province: RULE.PROVINCE, info: RULE.INFO};
// 修改密码表单：表单项 + 表单值 + 表单规则
let updatePasswordItems = ref([
  {label: '原密码', prop: 'oldPassword', type: 'password', required: true, placeholder: '请输入原密码'},
  {label: '新密码', prop: 'newPassword', type: 'password', required: true, placeholder: '请输入新密码'},
  {label: '确认密码', prop: 'rePassword', type: 'password', required: true, placeholder: '请确认新密码'},
]);
let updatePasswordParams = reactive({id: loginUser['id']});
let updatePasswordRules = {
  oldPassword: RULE.PASSWORD,
  newPassword: [RULE.PASSWORD[0], {
    validator: (rule, value, callback) => {
      if (value === updatePasswordParams['oldPassword']) callback('新旧密码不能相同');
      else callback();
    },
    trigger: ['blur', 'input']
  }],
  rePassword: [RULE.PASSWORD[0], {
    validator: (rule, value, callback) => {
      if (value !== updatePasswordParams['newPassword']) callback('两次密码不一致');
      else callback();
    },
    trigger: ['blur', 'input']
  }],
};

/**
 * 修改成功后
 */
function updateSuccess() {
  ElMessage('修改个人信息后需要重新登录！');
  setTimeout(() => router.push('/'), 1000);
}
</script>

<template>
  <my-nav :items="navItems"/>
  <el-row class="personal-update-body" :gutter="20" >
    <el-col :span="16">
      <el-card class="update-card" header="修改基本信息">
        <my-form type="update"
                 :items="items"
                 :params="params"
                 :rules="rules"
                 :api="updateApi"
                 :args="{'module': 'user'}"
                 :callback="updateSuccess"/>
      </el-card>
    </el-col>
    <el-col :span="8">
      <el-card class="upload-avatar-card" header="上传头像">
        <my-upload name="avatarFile"
                   :url="UPLOAD_AVATAR_URL + '/' + loginUser['id']"
                   :callback="updateSuccess"
                   :autoUpload="true"/>
      </el-card>
      <el-card class="update-password-card" header="修改密码">
        <my-form type="update"
                 :items="updatePasswordItems"
                 :params="updatePasswordParams"
                 :rules="updatePasswordRules"
                 :api="updatePasswordApi"
                 :callback="updateSuccess"/>
      </el-card>
    </el-col>
  </el-row>
</template>

<style scoped lang="scss">
.personal-update-body {
  padding: 0 100px; // 内边距
  margin-top: 22px; //外边距
  overflow-y: scroll; // Y轴溢出滚动

  .update-password-card {
    margin-top: 25px; // 上外边距
  }
}
</style>
