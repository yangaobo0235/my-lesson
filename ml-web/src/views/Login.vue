<script setup>
import router from "../router/index.js";
import vuex from "../vuex/index.js";
import {ElMessage, ElNotification} from "element-plus";
import {RULE} from "../const/index.js";
import {onMounted, reactive, ref} from "vue";
import {getResponseData} from "../request/index.js";
import {isNotNull} from "../util/index.js";
import {loginByAccountApi} from "../api/ums/user.js";

// 表单 + 表单数据 + 表单规则
let loginForm = ref();
let loginFormData = reactive({username: '', password: ''});
let loginFormRules = {username: RULE.USERNAME, password: RULE.PASSWORD};

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
    <el-card class="login-card" header="《我的课堂》后台管理系统">
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
          管理员登录
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
  </section>
</template>

<style scoped lang="scss">
.login-body {
  height: 100vh; // 高度
  background: url("../assets/image/loginBackground.png") no-repeat; // 背景图片（不平铺）
  background-size: 100% 100%; // 上下 左右
  padding-top: 200px; // 上内边距
  box-sizing: border-box; // 忽略内边距影响

  .login-card {
    margin: auto; // 自居中
    width: 50vh; // 宽度
    opacity: 0.95; // 透明度
  }

  .login-btn {
    width: 100%; // 宽度
    margin: 0 auto 10px; // 外边距
    letter-spacing: 2px; // 字母间距
  }

  .forget-btn, .reset-btn {
    float: right; // 右浮动
    line-height: 18px; // 行高
  }
}
</style>
