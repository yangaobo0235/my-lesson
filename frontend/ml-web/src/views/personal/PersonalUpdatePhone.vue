<script setup>
import router from "../../router/index.js";
import MyNav from "../../components/MyNav.vue";
import {RULE} from "../../const/index.js";
import {ElMessage} from "element-plus";
import {ref, reactive} from "vue";
import {checkUnboundVcodeApi, getBoundVcodeApi} from "../../api/ums/user.js";
import {getUnboundVcodeApi, updatePhoneApi} from "../../api/ums/user.js";
import {getResponseData} from "../../request/index.js";
import {isEmpty, isNotNull} from "../../util/index.js";

// 获取当前登录的用户记录
let loginUser = JSON.parse(sessionStorage.getItem('loginUser'));
// 路径导航
const navItems = [
  {icon: 'House', label: 'Dashboard', url: '/Dashboard'},
  {icon: 'Edit', label: '换绑手机号码'},
];
// 当前进度条步骤
const stepActive = ref(1);
// 解绑旧手机：表单 + 表单项 + 表单值 + 表单规则
let unboundForm = ref();
let unboundFormData = reactive({id: loginUser['id'], phone: loginUser['phone']});
let unboundFormRules = {vcode: RULE.CODE};

/**
 * 获取验证码 - 解绑旧手机
 */
function getUnboundVcode() {
  getUnboundVcodeApi(loginUser['id']).then(res => {
    let data = getResponseData(res);
    if (data) {
      ElMessage.success('验证码已发送');
    }
  });
}

/**
 * 解绑旧手机
 */
function unboundPhone() {
  unboundForm.value.validate(valid => {
    if (valid) {
      checkUnboundVcodeApi(loginUser['id'], unboundFormData.vcode).then(res => {
        let data = getResponseData(res);
        if (data) {
          ElMessage.success('解绑成功！');
          stepActive.value = 2;
        } else {
          ElMessage.warning('验证码错误！');
        }
      });
    }
  });
}

// 绑定新手机：表单 + 表单项 + 表单值 + 表单规则
let boundForm = ref();
let boundFormData = reactive({id: loginUser['id']});
let boundFormRules = {phone: RULE.PHONE, vcode: RULE.CODE};

/**
 * 获取验证码 - 绑定新手机
 */
function getBoundVcode() {
  if (isEmpty(boundFormData['phone'])) {
    ElMessage.warning('请输入手机号码！');
    return;
  }
  getBoundVcodeApi(boundFormData['phone']).then(res => {
    let data = getResponseData(res);
    if (data) {
      ElMessage.success('验证码已发送');
    }
  });
}

/**
 * 绑定新手机
 */
function boundPhone() {
  boundForm.value.validate(valid => {
    if (valid) {
      let params = {
        id: loginUser['id'],
        phone: boundFormData['phone'],
        vcode: boundFormData['vcode'],
      };
      updatePhoneApi(params).then(res => {
        let data = getResponseData(res);
        if (data) {
          ElMessage.success('绑定成功！');
          stepActive.value = 3;
          setTimeout(() => router.push('/'), 3000);
        } else {
          ElMessage.warning('验证码错误！');
        }
      });
    }
  });
}
</script>

<template>
  <my-nav :items="navItems"/>
  <el-steps class="update-steps" :active="stepActive"
            finish-status="success">
    <el-step title="步骤 1" description="解绑旧手机"/>
    <el-step title="步骤 2" description="绑定新手机"/>
    <el-step title="步骤 3" description="修改完成"/>
  </el-steps>
  <div class="personal-update-phone-body">
    <el-card v-if="stepActive === 1" header="解绑旧手机">
      <el-form class="update-form" :model="unboundFormData" :rules="unboundFormRules"
               ref="unboundForm" status-icon>
        <el-form-item prop="phone" required>
          <el-input v-model="unboundFormData['phone']"
                    prefix-icon="Phone" disabled/>
        </el-form-item>
        <el-form-item prop="vcode" required>
          <el-input v-model="unboundFormData['vcode']"
                    prefix-icon="Lock" clearable placeholder="输入验证码 ..">
            <template #append>
              <el-button type="primary" @click="getUnboundVcode">获取验证码</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-button type="primary" @click="unboundPhone">确认解绑</el-button>
      </el-form>
    </el-card>
    <el-card v-if="stepActive === 2" header="绑定新手机">
      <el-form class="update-form" :model="boundFormData" :rules="boundFormRules"
               ref="boundForm" status-icon>
        <el-form-item prop="phone" required>
          <el-input v-model="boundFormData['phone']"
                    prefix-icon="Phone" clearable placeholder="输入新手机号 .."/>
        </el-form-item>
        <el-form-item prop="vcode" required>
          <el-input v-model="boundFormData['vcode']"
                    prefix-icon="Lock" clearable placeholder="输入验证码 ..">
            <template #append>
              <el-button type="primary" @click="getBoundVcode">获取验证码</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-button type="primary" @click="boundPhone">确认绑定</el-button>
      </el-form>
    </el-card>
    <el-card v-if="stepActive === 3">
      <h1 class="success-tip">修改成功！3秒后自动跳转到登录页面 ..</h1>
    </el-card>
  </div>
</template>

<style scoped lang="scss">

.update-steps {
  margin: 65px auto 0; // 外边距
  width: 80%; // 宽度
}

.personal-update-phone-body {
  width: 50%; // 宽度
  margin: 70px auto 0; // 外边距

  .success-tip {
    text-align: center; // 文本居中
  }
}
</style>
