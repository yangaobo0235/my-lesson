<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyForm from "../../../components/MyForm.vue";
import MyUpload from "../../../components/MyUpload.vue";
import {MINIO_BANNER, MINIO_COURSE_SUMMARY, RULE} from "../../../const/index.js";
import {reactive, ref} from "vue";
import {updateApi} from "../../../api/index.js";
import {UPLOAD_BANNER_URL} from "../../../api/sms/banner.js";
import {ElMessage} from "element-plus";
import router from "../../../router/index.js";

// 获取当前横幅记录
let banner = JSON.parse(sessionStorage.getItem('row'));
let bannerUrl = ref(MINIO_BANNER(banner['url']));
// 路径导航
const navItems = [
  {icon: 'Goods', label: '营销管理'},
  {icon: 'Picture', label: '横幅列表', url: '/Banner'},
  {icon: 'Edit', label: '修改横幅信息'},
];
// 表单项 + 表单值 + 表单规则
let updateFormItems = ref([
  {label: '横幅排序', prop: 'idx', type: 'number', min: 1, required: true, span: 12},
  {label: '横幅描述', prop: 'info', required: true, type: 'textarea', rows: 18},
]);
let updateFormParams = reactive(banner);
let updateFormRules = {info: RULE.INFO,};

/* ==================== 修改成功后 ==================== */

function updateSuccess() {
  ElMessage.success('修改记录成功！');
  setTimeout(() => router.push('/Banner'), 1000);
}

/* ==================== 上传成功后 ==================== */

function uploadSuccess(data) {
  bannerUrl.value = MINIO_BANNER(data);
}
</script>

<template>
  <my-nav :items="navItems"/>
  <div class="banner-update-body">
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card class="banner-info-card">
          <el-image class="banner-image" :src="bannerUrl"/>
        </el-card>
        <el-card class="upload-banner-card" header="上传轮播图片">
          <my-upload name="bannerFile"
                     :url="UPLOAD_BANNER_URL + '/' +banner['id']"
                     :autoUpload="true"
                     :callback="uploadSuccess"/>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card class="update-card" header="修改基本信息">
          <my-form type="update"
                   :items="updateFormItems"
                   :rules="updateFormRules"
                   :params="updateFormParams"
                   :api="updateApi"
                   :args="{module: 'banner'}"
                   :callback="updateSuccess"/>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
.banner-update-body {
  width: 90%; // 宽度
  margin: 65px auto 0; // 外边距

  .banner-info-card {
    text-align: center; // 内容居中

    .banner-image {
      height: 170px; // 高度
    }
  }

  .upload-banner-card {
    margin-top: 18px; // 上外边距
  }
}
</style>
