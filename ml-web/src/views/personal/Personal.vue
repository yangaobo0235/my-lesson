<script setup>
import {MINIO_AVATAR} from "../../const/index.js";
import {dateFormat, genderFormat} from "../../util/index.js";
import MyNav from "../../components/MyNav.vue";

// 当前登录的用户信息
const loginUser = JSON.parse(sessionStorage.getItem('loginUser'));
// 当前登录的用户头像
const avatar = MINIO_AVATAR(loginUser['avatar']);
// 路径导航
const navItems = [
  {icon: 'House', label: 'DashBoard', url: '/DashBoard'},
  {icon: 'View', label: '个人信息'},
];
</script>

<template>
  <my-nav :items="navItems"/>
  <div class="personal-body">
    <el-row :gutter="100">
      <el-col :span="8">
        <el-image class="avatar" :src="avatar"></el-image>
        <el-divider/>
        <el-descriptions column="1">
          <el-descriptions-item label="登录账号">{{ loginUser['username'] }}</el-descriptions-item>
          <el-descriptions-item label="真实姓名">{{ loginUser['realname'] }}</el-descriptions-item>
          <el-descriptions-item label="用户昵称">{{ loginUser['nickname'] }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ dateFormat(loginUser['created']) }}</el-descriptions-item>
          <el-descriptions-item label="修改时间">{{ dateFormat(loginUser['updated']) }}</el-descriptions-item>
        </el-descriptions>
      </el-col>
      <el-col :span="16">
        <el-descriptions title="当前用户详细信息" border column="2">
          <el-descriptions-item label="用户年龄" width="120">{{ loginUser['age'] }}</el-descriptions-item>
          <el-descriptions-item label="用户性别" width="120">{{genderFormat(loginUser['gender'])}}</el-descriptions-item>
          <el-descriptions-item label="用户星座">{{ loginUser['zodiac'] }}</el-descriptions-item>
          <el-descriptions-item label="所属省份">{{ loginUser['province'] }}</el-descriptions-item>
          <el-descriptions-item label="手机号码">{{ loginUser['phone'] }}</el-descriptions-item>
          <el-descriptions-item label="电子邮件">{{ loginUser['email'] }}</el-descriptions-item>
          <el-descriptions-item label="身份证号" :span="2">{{ loginUser['idcard'] }}</el-descriptions-item>
          <el-descriptions-item label="用户描述" :span="2">
            <el-card style="height: 300px">{{ loginUser['info'] }}</el-card>
          </el-descriptions-item>
        </el-descriptions>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
.personal-body {
  width: 80%; // 宽度
  margin: 65px auto 0; //外边距

  .avatar {
    width: 278px; // 宽度
    height: 278px; // 高度
    border-radius: 10%; // 圆角
  }
}
</style>
