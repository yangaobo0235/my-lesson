<script setup>
import router from "../router/index.js";
import vuex from "../vuex/index.js";
import {ElMessage, ElNotification} from "element-plus";
import {RULE} from "../const/index.js";
import {onMounted, reactive, ref} from "vue";
import {getResponseData} from "../request/index.js";
import {isNotNull} from "../util/index.js";
import {loginByAccountApi, registerApi} from "../api/ums/user.js";

// 表单 + 表单数据 + 表单规则
let loginForm = ref();
let loginFormData = reactive({username: '', password: ''});
let loginFormRules = {username: RULE.USERNAME, password: RULE.PASSWORD};
let registerForm = ref();
let registerDialog = ref(false);
let registerLoading = ref(false);
let registerFormData = reactive({
  username: '',
  password: '',
  realname: '',
  phone: '',
  email: '',
  idcard: '',
  info: '新注册学员'
});
let registerFormRules = {
  username: RULE.USERNAME,
  password: RULE.PASSWORD,
  realname: RULE.REALNAME,
  phone: RULE.PHONE,
  email: RULE.EMAIL,
  idcard: RULE.IDCARD,
  info: RULE.INFO
};

/**
 * 登录系统
 *
 * 1. 验证表单，验证通过后发送登录请求。
 * 2. vuex修改登录状态。
 * 3. 存储Token令牌，该用户的个人信息，该用户的菜单列表和该用户的角色列表。
 * 4. 路由到 Main 页面。
 */
function login() {
  loginForm.value.validate(valid => {
    if (valid) {
      // 同步发送登录请求
      loginByAccountApi(loginFormData).then(res => {
        let data = getResponseData(res);
        if (isNotNull(data)) {
          ElMessage.success('登录成功！');
          vuex.dispatch('setLoginFlag', true);
          sessionStorage.setItem('token', data['token']);
          sessionStorage.setItem('loginUser', JSON.stringify(data['user']));
          sessionStorage.setItem('loginMenus', JSON.stringify(data['menus']));
          sessionStorage.setItem('loginRoleTitles', JSON.stringify(data['roleTitles']));
          router.push('/Main');
        }
      });
    }
  });
}

/**
 * 重置表单
 *
 * 1. 清空表单数据。
 */
function resetForm() {
  loginForm.value.resetFields();
}

function openRegister() {
  registerDialog.value = true;
}

function resetRegisterForm() {
  registerForm.value?.resetFields();
  registerFormData.info = '新注册学员';
}

function register() {
  registerForm.value.validate(valid => {
    if (!valid) return;
    registerLoading.value = true;
    registerApi(registerFormData).then(res => {
      if (getResponseData(res)) {
        ElMessage.success('注册成功，请登录');
        loginFormData.username = registerFormData.username;
        loginFormData.password = '';
        registerDialog.value = false;
        resetRegisterForm();
      }
    }).finally(() => registerLoading.value = false);
  });
}

/**
 * 忘记密码
 *
 * 1. 提示联系管理员处理账号问题。
 */
function forgetPassword() {
  ElNotification.info({
    title: '通知列表',
    message: '请联系系统管理员重置密码。',
    position: 'top-right',
  });
}

/**
 * 加载函数
 *
 * 1. 清空SessionStorage 中的所有信息。
 * 2. vuex修改登录状态为 false。
 */
onMounted(() => {
  sessionStorage.clear();
  vuex.dispatch('setLoginFlag', false);
});
</script>

<template>
  <section class="login-body">
    <el-card class="login-card" header="MyLesson 课程平台">
      <el-form class="login-form" ref="loginForm" :model="loginFormData" :rules="loginFormRules"
               status-icon>
        <el-form-item prop="username" required>
          <el-input v-model="loginFormData['username']"
                    prefix-icon="User" suffix-icon="User" clearable placeholder="输入账号 .."/>
        </el-form-item>
        <el-form-item prop="password" required>
          <el-input v-model="loginFormData['password']"
                    prefix-icon="Lock" suffix-icon="Lock" clearable placeholder="输入密码 .." show-password/>
        </el-form-item>
        <el-button class="login-btn" @click="login"
                   type="primary">
          登录系统
        </el-button>
        <el-button class="register-btn" @click="openRegister" plain type="primary">
          注册账号
        </el-button>
        <el-checkbox class="remember-cbx"
                     label="记住账号" size="small"/>
        <el-button class="forget-btn" @click="forgetPassword"
                   link size="small">
          忘记密码
        </el-button>
        <el-button class="reset-btn" @click="resetForm"
                   link size="small" type="warning">
          重置内容
        </el-button>
      </el-form>
    </el-card>

    <el-dialog v-model="registerDialog" title="注册学员账号" width="520px" @closed="resetRegisterForm">
      <el-form ref="registerForm" :model="registerFormData" :rules="registerFormRules" label-width="86px" status-icon>
        <el-form-item label="账号" prop="username" required>
          <el-input v-model="registerFormData.username" clearable placeholder="4-20位英文或数字"/>
        </el-form-item>
        <el-form-item label="密码" prop="password" required>
          <el-input v-model="registerFormData.password" clearable show-password placeholder="4-20位英文或数字"/>
        </el-form-item>
        <el-form-item label="姓名" prop="realname" required>
          <el-input v-model="registerFormData.realname" clearable placeholder="请输入真实姓名"/>
        </el-form-item>
        <el-form-item label="手机号" prop="phone" required>
          <el-input v-model="registerFormData.phone" clearable placeholder="请输入手机号"/>
        </el-form-item>
        <el-form-item label="邮箱" prop="email" required>
          <el-input v-model="registerFormData.email" clearable placeholder="请输入邮箱"/>
        </el-form-item>
        <el-form-item label="身份证号" prop="idcard" required>
          <el-input v-model="registerFormData.idcard" clearable placeholder="用于生成默认资料"/>
        </el-form-item>
        <el-form-item label="简介" prop="info">
          <el-input v-model="registerFormData.info" type="textarea" :rows="3" maxlength="170"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerDialog = false">取消</el-button>
        <el-button type="primary" :loading="registerLoading" @click="register">完成注册</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.login-body {
  height: 100vh; // 高度
  background: url("../assets/image/loginBackground.png") no-repeat; // 背景图片（不平铺）
  background-size: cover; // 上下 左右
  background-position: center;
  padding-top: 18vh; // 上内边距
  box-sizing: border-box; // 忽略内边距影响
  position: relative;

  &::before {
    content: "";
    position: absolute;
    inset: 0;
    background: rgba(246, 247, 249, 0.62);
  }

  .login-card {
    position: relative;
    margin: auto; // 自居中
    width: min(420px, calc(100vw - 40px)); // 宽度
    opacity: 0.98; // 透明度
  }

  .login-btn {
    width: 100%; // 宽度
    margin: 0 auto 10px; // 外边距
    letter-spacing: 0; // 字母间距
  }

  .register-btn {
    width: 100%;
    margin: 0 0 10px;
    letter-spacing: 0;
  }

  .forget-btn, .reset-btn {
    float: right; // 右浮动
    line-height: 18px; // 行高
  }
}
</style>
