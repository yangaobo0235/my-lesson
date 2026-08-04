<script setup>
import router from "../../../router/index.js";
import MyNav from "../../../components/MyNav.vue";
import MyForm from "../../../components/MyForm.vue";
import MyUpload from "../../../components/MyUpload.vue";
import {MINIO_AVATAR, RULE} from "../../../const/index.js";
import {reactive, ref} from "vue";
import {updateApi} from "../../../api/index.js";
import {UPLOAD_AVATAR_URL} from "../../../api/ums/user.js";
import {GENDER_OPTIONS, ZODIAC_OPTIONS} from "../../../const/index.js";
import {ElMessage} from "element-plus";

// 获取当前用户记录
let user = JSON.parse(sessionStorage.getItem('row'));
let avatarUrl = ref(MINIO_AVATAR(user['avatar']));

// 路径导航
const navItems = [
  {icon: 'User', label: '用户管理'},
  {icon: 'User', label: '用户列表', url: '/User'},
  {icon: 'Edit', label: '修改用户信息'},
];

/* ==================== 修改基本信息 ==================== */

// 表单项 + 表单值 + 表单规则
let updateFormItems = ref([
  {label: '账号', prop: 'username', disabled: true, span: 12},
  {label: '姓名', prop: 'realname', disabled: true, span: 12},
  {label: '手机号码', prop: 'phone', disabled: true, span: 12},
  {label: '身份证号', prop: 'idcard', disabled: true, span: 12},
  {label: '昵称', prop: 'nickname', required: true, span: 12},
  {label: '邮箱', prop: 'email', required: true, span: 12},
  {label: '性别', prop: 'gender', required: true, span: 12, type: 'select', options: GENDER_OPTIONS},
  {label: '年龄', prop: 'age', span: 12, type: 'number', required: true, },
  {label: '星座', prop: 'zodiac', span: 12, type: 'select', options: ZODIAC_OPTIONS, required: true, },
  {label: '省份', prop: 'province', span: 12, required: true, },
  {label: '描述', prop: 'info', type: 'textarea',  required: true, rows: 10},
]);
let updateFormParams= reactive(user);
let updateFormRules = {
  nickname: RULE.NICKNAME,
  email: RULE.EMAIL,
  province: RULE.PROVINCE,
  info: RULE.INFO,
};

/* ==================== 修改成功后 ==================== */

function updateSuccess() {
  ElMessage.success('修改记录成功！');
  setTimeout(() => router.push('/User'), 1000);
}

/* ==================== 上传成功后 ==================== */

function uploadAvatarSuccess(data) {
  avatarUrl.value = MINIO_AVATAR(data);
}
</script>

<template>
  <my-nav :items="navItems"/>
  <div class="user-update-body">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card class="user-info-card">
          <el-image class="avatar-image" :src="avatarUrl"/>
        </el-card>
        <el-card class="upload-avatar-card" header="上传个人头像">
          <my-upload :url="UPLOAD_AVATAR_URL + '/' + user['id']"
                     name="avatarFile"
                     :callback="uploadAvatarSuccess"
                     :autoUpload="true"/>
        </el-card>
      </el-col>
      <el-col :span="18">
        <el-card class="update-card" header="修改基本信息">
          <my-form type="update"
                   :items="updateFormItems"
                   :params="updateFormParams"
                   :rules="updateFormRules"
                   :api="updateApi"
                   :args="{module: 'user'}"
                   :callback="updateSuccess"/>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
.user-update-body {
  padding: 0 100px; // 内边距
  margin: 65px auto 0; // 外边距

  .user-info-card {
    text-align: center; // 内容居中

    .nickname {
      margin-bottom: 5px; // 下边距
    }

    .avatar-image {
      height: 170px; // 高度
    }
  }

  .upload-avatar-card {
    margin-top: 25px; // 上外边距
  }
}
</style>
