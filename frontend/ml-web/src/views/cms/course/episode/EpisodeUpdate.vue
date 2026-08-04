<script setup>
import MyNav from "../../../../components/MyNav.vue";
import MyForm from "../../../../components/MyForm.vue";
import MyUpload from "../../../../components/MyUpload.vue";
import MyPlayer from "../../../../components/MyPlayer.vue";
import {MINIO_EPISODE_VIDEO, MINIO_EPISODE_VIDEO_COVER} from "../../../../const/index.js";
import {reactive, ref} from "vue";
import {updateApi} from "../../../../api/index.js";
import {RULE} from "../../../../const/index.js";
import {UPLOAD_EPISODE_VIDEO_COVER_URL} from "../../../../api/cms/episode.js";
import {UPLOAD_EPISODE_VIDEO_URL} from "../../../../api/cms/episode.js";
import {ElMessage} from "element-plus";
import router from "../../../../router/index.js";

// 获取当前课程主键和课程标题
let updatePageParam = JSON.parse(sessionStorage.getItem('updatePageParam'));
let courseId = updatePageParam['courseId'];
let courseTitle = updatePageParam['courseTitle'];
let seasonId = updatePageParam['seasonId'];
let seasonTitle = updatePageParam['seasonTitle'];

// 获取当前集次记录
let episode = JSON.parse(sessionStorage.getItem('row'));
let episodePart = episode['title'].split(' / ');
episode['courseTitle'] = episodePart[0];
episode['seasonTitle'] = episodePart[1];
episode['title'] = episodePart[2];
let episodeCoverUrl = ref(MINIO_EPISODE_VIDEO_COVER(episode['cover']));
let episodeVideoUrl = ref(MINIO_EPISODE_VIDEO(episode['video']));

// 路径导航
const navItems = [
  {icon: 'Notebook', label: '课程管理'},
  {icon: 'Notebook', label: '（' + courseTitle + '）的季次列表', url: '/Season'},
  {icon: 'Notebook', label: '（' + seasonTitle + '）的集次列表', url: '/Episode'},
  {icon: 'Edit', label: '修改集次'},
];
// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '所属课程', prop: 'courseTitle', disabled: true},
  {label: '所属季次', prop: 'seasonTitle', disabled: true},
  {label: '标题', prop: 'title', required: true},
  {label: '排序', prop: 'idx', type: 'number', min: 1, required: true},
  {label: '描述', prop: 'info', required: true, type: 'textarea', rows: 11},
]);
let params = reactive(episode);
let rules = {title: RULE.TITLE, info: RULE.INFO};

/* ==================== 修改成功后 ==================== */

function updateSuccess() {
  ElMessage.success('修改记录成功！');
  setTimeout(() => router.push('/Episode'), 1000);
}

/* ==================== 封面图片上传成功后调用 ==================== */

function coverUploadSuccess(data) {
  episodeCoverUrl.value = MINIO_EPISODE_VIDEO_COVER(data);
}

/* ==================== 摘要图片上传成功后调用 ==================== */

function videoUploadSuccess(data) {
  episodeVideoUrl.value = MINIO_EPISODE_VIDEO(data);
}
</script>

<template>
  <my-nav :items="navItems"/>
  <div class="episode-update-body">
    <el-row :gutter="20">
      <el-col :span="8">
        <div class="player">
          <my-player :poster="episodeCoverUrl" :url="episodeVideoUrl"/>
        </div>
        <el-card class="upload-video-card" header="重传集次视频">
          <my-upload name="videoFile"
                     :url="UPLOAD_EPISODE_VIDEO_URL + episode['id']"
                     :autoUpload="true"
                     :allowTypes="['video/mp4']"
                     :maxSize="300"
                     :callback="videoUploadSuccess"/>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="update-card" header="修改集次信息">
          <my-form type="update"
                   :items="items"
                   :rules="rules"
                   :params="params"
                   :api="updateApi"
                   :args="{module: 'episode'}"
                   :callback="updateSuccess"/>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-image class="episode-image" :src="episodeCoverUrl"/>
        <el-card class="upload-video-cover-card" header="重传视频封面">
          <my-upload name="videoCoverFile"
                     :url="UPLOAD_EPISODE_VIDEO_COVER_URL + episode['id']"
                     :autoUpload="true"
                     :callback="coverUploadSuccess"/>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
.episode-update-body {
  margin: 20px auto 0; // 外边距

  .player, .episode-image {
    border: 1px solid grey; // 边框;
  }

  .episode-image {
    height: 208px; // 高度
    width: 100%; // 宽度
  }

  .upload-video-card, .upload-video-cover-card {
    margin-top: 18px; // 上外边距
  }
}
</style>
