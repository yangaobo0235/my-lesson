<script setup>
import {computed, onMounted, ref} from 'vue';
import {useRoute} from 'vue-router';
import {ElMessage} from 'element-plus';
import MyPlayer from '../../components/MyPlayer.vue';
import {studentApi} from '../../api/student.js';
import {getResponseData} from '../../request/index.js';
import {MINIO_EPISODE_VIDEO, MINIO_EPISODE_VIDEO_COVER} from '../../const/index.js';
import {readJsonStorage} from '../../util/auth.js';
import './student.scss';

const route = useRoute();
const loginUser = readJsonStorage('loginUser', {});
const course = ref(null);
const currentEpisode = ref(null);
const comments = ref([]);
const commentText = ref('');
const reportText = ref('');
const reportDialog = ref(false);

const seasons = computed(() => course.value?.seasons || []);

async function loadCourse() {
  course.value = getResponseData(await studentApi.course(route.params.courseId));
  currentEpisode.value = seasons.value.flatMap(season => season.episodes || [])[0] || null;
  await loadComments();
}

async function selectEpisode(episode) {
  currentEpisode.value = episode;
  await loadComments();
}

async function loadComments() {
  if (!currentEpisode.value?.id) return;
  const data = getResponseData(await studentApi.comments({pageNum: 1, pageSize: 20, fkEpisodeId: currentEpisode.value.id})) || {};
  comments.value = data.records || [];
}

async function sendComment() {
  const content = commentText.value.trim();
  if (!content || !currentEpisode.value?.id) return;
  if (getResponseData(await studentApi.addComment(loginUser.id, currentEpisode.value.id, content))) {
    ElMessage.success('评论成功');
    commentText.value = '';
    await loadComments();
  }
}

async function sendReport() {
  const content = reportText.value.trim();
  if (!content || !currentEpisode.value?.id) return;
  if (getResponseData(await studentApi.report(loginUser.id, currentEpisode.value.id, content))) {
    ElMessage.success('举报已提交');
    reportText.value = '';
    reportDialog.value = false;
  }
}

onMounted(loadCourse);
</script>

<template>
  <div v-if="course && currentEpisode" class="student-page">
    <header class="student-header">
      <div><h2>{{ course.title }}</h2><p>当前播放：{{ currentEpisode.title }}</p></div>
      <el-button icon="WarnTriangleFilled" plain @click="reportDialog = true">举报</el-button>
    </header>

    <section class="player-layout">
      <div class="panel">
        <MyPlayer :key="currentEpisode.id" :url="MINIO_EPISODE_VIDEO(currentEpisode.video)" :poster="MINIO_EPISODE_VIDEO_COVER(currentEpisode.cover)"/>
      </div>
      <aside class="panel catalog">
        <h3>课程目录</h3>
        <el-collapse>
          <el-collapse-item v-for="season in seasons" :key="season.id" :title="season.title" :name="season.id">
            <button v-for="episode in season.episodes" :key="episode.id" class="episode-button"
                    :class="{active: episode.id === currentEpisode.id}" @click="selectEpisode(episode)">
              {{ episode.title }}
            </button>
          </el-collapse-item>
        </el-collapse>
      </aside>
    </section>

    <section class="panel comments">
      <h3>评论</h3>
      <div class="comment-editor">
        <el-input v-model="commentText" type="textarea" :rows="2" maxlength="170" placeholder="写下你的学习感受"/>
        <el-button type="primary" @click="sendComment">发布</el-button>
      </div>
      <div v-for="comment in comments" :key="comment.id" class="comment-item">
        <strong>{{ comment.username || comment.user?.nickname || '学员' }}</strong>
        <p>{{ comment.content }}</p>
      </div>
      <el-empty v-if="!comments.length" description="暂无评论"/>
    </section>

    <el-dialog v-model="reportDialog" title="举报内容" width="460px">
      <el-input v-model="reportText" type="textarea" :rows="4" maxlength="170" placeholder="说明举报原因"/>
      <template #footer>
        <el-button @click="reportDialog = false">取消</el-button>
        <el-button type="primary" @click="sendReport">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.player-layout { display:grid; grid-template-columns:minmax(0,1fr) 320px; gap:16px; align-items:start; }
.catalog h3,.comments h3 { margin:0 0 12px; color:var(--ml-text); }
.episode-button { display:block; width:100%; padding:9px 10px; border:0; border-radius:6px; background:transparent; color:var(--ml-text); text-align:left; cursor:pointer; }
.episode-button.active,.episode-button:hover { color:var(--ml-primary); background:var(--ml-surface-soft); }
.comment-editor { display:grid; grid-template-columns:minmax(0,1fr) auto; gap:10px; align-items:start; margin-bottom:14px; }
.comment-item { padding:10px 0; border-bottom:1px solid var(--ml-border); }
.comment-item strong { color:var(--ml-text); }
.comment-item p { margin:6px 0 0; color:var(--ml-muted); }
@media (max-width:1100px) { .player-layout { grid-template-columns:1fr; } }
</style>
