<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyForm from "../../../components/MyForm.vue";
import MyUpload from "../../../components/MyUpload.vue";
import {MINIO_COURSE_COVER, MINIO_COURSE_SUMMARY, RULE} from "../../../const/index.js";
import {onMounted, reactive, ref} from "vue";
import {simpleListApi, updateApi} from "../../../api/index.js";
import {getResponseData} from "../../../request/index.js";
import {UPLOAD_COURSE_COVER_URL, UPLOAD_COURSE_SUMMARY_URL} from "../../../api/cms/course.js";
import router from "../../../router/index.js";

// 获取当前课程记录
let course = JSON.parse(sessionStorage.getItem('row'));
let courseCoverUrl = ref(MINIO_COURSE_COVER(course['cover']));
let courseSummaryUrl = ref(MINIO_COURSE_SUMMARY(course['summary']));
// 所在课程类别下拉菜单选项
let categoryOptions = ref([]);
// 路径导航
const navItems = [
  {icon: 'Notebook', label: '课程管理'},
  {icon: 'Notebook', label: '课程列表', url: '/Course'},
  {icon: 'Edit', label: '修改课程'},
];
// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '标题', prop: 'title', required: true},
  {label: '排序', prop: 'idx', type: 'number', min: 1, required: true},
  {label: '作者', prop: 'author', required: true},
  {label: '类别', prop: 'fkCategoryId', required: true, type: 'select', options: categoryOptions, placeholder: '请选择课程类别'},
  {label: '价格', prop: 'price', type: 'number', min: 1, required: true},
  {label: '描述', prop: 'info', type: 'textarea', rows: 9, required: true},
]);
let data = reactive(course);
let rules = {title: RULE.TITLE, author: RULE.AUTHOR, info: RULE.INFO};

/* ==================== 修改成功后 ==================== */

function updateSuccess() {
  router.push('/Course');
}

/* ==================== 封面图片上传成功后 ==================== */

/**
 * 封面图片上传成功后调用
 *
 * 1. 更新封面图片 URL
 *
 * @param data 上传成功后返回的数据
 */
function coverUploadSuccess(data) {
  courseCoverUrl.value = MINIO_COURSE_COVER(data);
}

/* ==================== 摘要图片上传成功后 ==================== */

/**
 * 摘要图片上传成功后调用
 *
 * 1. 更新摘要图片 URL
 *
 * @param data 上传成功后返回的数据
 */
function summaryUploadSuccess(data) {
  courseSummaryUrl.value = MINIO_COURSE_SUMMARY(data);
}

/* ==================== 加载函数 ==================== */

onMounted(async () => {
  // 查询全部课程类别并添加到下拉菜单选项中
  Object.values(getResponseData(await simpleListApi(null, {module: 'category'}))).forEach(category => {
    categoryOptions.value.push({label: category['title'], value: category['id']});
  });
});

</script>

<template>
  <my-nav :items="navItems"/>
  <div class="course-update-body">
    <el-row :gutter="20">
      <el-col :span="8">
        <el-image class="course-cover-image" :src="courseCoverUrl"/>
        <el-card class="upload-course-cover-card" header="重传课程封面图">
          <my-upload name="coverFile" :url="UPLOAD_COURSE_COVER_URL + course['id']" :autoUpload="true" :callback="coverUploadSuccess"/>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="update-card" header="修改课程信息">
          <my-form type="update"
                   :items="items"
                   :rules="rules"
                   :args="{module: 'course'}"
                   :api="updateApi"
                   :params="data"
                   :callback="updateSuccess"/>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-image class="course-summary-image" :src="courseSummaryUrl"/>
        <el-card class="upload-course-summary-card" header="重传课程摘要图">
          <my-upload name="summaryFile" :url="UPLOAD_COURSE_SUMMARY_URL + course['id']" :autoUpload="true" :callback="summaryUploadSuccess"/>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
.course-update-body {
  margin: 65px auto 0; // 外边距

  .course-cover-image, .course-summary-image {
    border: 1px solid grey; // 边框;
    height: 208px; // 高度
    width: 100%; // 宽度
  }

  .upload-course-cover-card, .upload-course-summary-card {
    margin-top: 24px; // 上外边距
  }
}
</style>
