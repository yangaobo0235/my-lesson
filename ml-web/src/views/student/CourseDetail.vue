<script setup>
import {computed, onMounted, reactive, ref} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {ElMessage} from 'element-plus';
import MyPlayer from '../../components/MyPlayer.vue';
import {studentApi} from '../../api/student.js';
import {getResponseData} from '../../request/index.js';
import {MINIO_COURSE_COVER, MINIO_COURSE_SUMMARY, MINIO_EPISODE_VIDEO, MINIO_EPISODE_VIDEO_COVER} from '../../const/index.js';
import {readJsonStorage} from '../../util/auth.js';
import {createQrCode, startPaymentPolling} from './pay.js';
import './student.scss';

const route = useRoute();
const router = useRouter();
const loginUser = readJsonStorage('loginUser', {});
const course = ref(null);
const activeTab = ref('summary');
const currentEpisode = ref(null);
const followMap = ref({});
const payDialog = ref(false);
const qrCode = ref('');
const paySn = ref('');
const paying = ref(false);
let payTimer = null;

const firstEpisode = computed(() => course.value?.seasons?.flatMap(season => season.episodes || [])[0]);

async function loadCourse() {
  course.value = getResponseData(await studentApi.course(route.params.id));
  currentEpisode.value = firstEpisode.value || null;
  await loadFollows();
}

async function loadFollows() {
  const data = getResponseData(await studentApi.follows({pageNum: 1, pageSize: 999, fkUserId: loginUser.id})) || {};
  followMap.value = {};
  (data.records || []).forEach(item => followMap.value[item.fkEpisodeId] = item.id);
}

async function addCart() {
  if (!course.value?.id) return;
  try {
    if (getResponseData(await studentApi.addCart(loginUser.id, course.value.id))) {
      ElMessage.success('已加入购物车');
      router.push('/student/cart');
    }
  } catch (error) {
    ElMessage.error(error.message || '加入购物车失败');
  }
}

async function toggleFollow(episode) {
  const followId = followMap.value[episode.id];
  if (followId) {
    if (getResponseData(await studentApi.removeFollow(followId))) {
      ElMessage.success('已取消收藏');
      await loadFollows();
    }
    return;
  }
  if (getResponseData(await studentApi.addFollow(loginUser.id, episode.id))) {
    ElMessage.success('收藏成功');
    await loadFollows();
  }
}

async function buyNow() {
  paying.value = true;
  try {
    paySn.value = getResponseData(await studentApi.prePay({
      fkUserId: loginUser.id,
      courseIds: [course.value.id],
      totalAmount: course.value.price,
      payAmount: course.value.price,
      fkCouponsId: null
    }));
    qrCode.value = await createQrCode(paySn.value);
    payDialog.value = true;
    payTimer = startPaymentPolling(paySn.value, () => {
      clearInterval(payTimer);
      payDialog.value = false;
      router.push('/student/orders');
    });
  } catch (error) {
    ElMessage.error(error.message || '创建支付订单失败');
  } finally {
    paying.value = false;
  }
}

async function cancelPay() {
  clearInterval(payTimer);
  if (paySn.value) await studentApi.cancelOrder(paySn.value);
  payDialog.value = false;
}

onMounted(loadCourse);
</script>

<template>
  <div v-if="course" class="student-page">
    <header class="detail-head panel">
      <img :src="MINIO_COURSE_COVER(course.cover)" :alt="course.title">
      <div class="detail-info">
        <el-tag>{{ course.category?.title || '课程' }}</el-tag>
        <h2>{{ course.title }}</h2>
        <p>{{ course.info }}</p>
        <div class="detail-meta">
          <span>讲师：{{ course.author }}</span>
          <strong class="price">￥{{ course.price }}</strong>
        </div>
        <div class="detail-actions">
          <el-button type="primary" :loading="paying" @click="buyNow">立即购买</el-button>
          <el-button icon="ShoppingCart" @click="addCart">加入购物车</el-button>
          <el-button icon="ChatLineRound" @click="router.push({path:'/ai/chat', query:{prompt:`请介绍课程《${course.title}》，并告诉我适合什么学习目标。`}})">问 AI</el-button>
        </div>
      </div>
    </header>

    <section v-if="currentEpisode" class="panel">
      <h3 class="block-title">课程试看</h3>
      <MyPlayer :url="MINIO_EPISODE_VIDEO(currentEpisode.video)" :poster="MINIO_EPISODE_VIDEO_COVER(currentEpisode.cover)" :key="currentEpisode.id"/>
    </section>

    <el-tabs v-model="activeTab" class="panel">
      <el-tab-pane label="课程摘要" name="summary">
        <img class="summary-img" :src="MINIO_COURSE_SUMMARY(course.summary)" :alt="course.title">
      </el-tab-pane>
      <el-tab-pane label="课程目录" name="episodes">
        <el-collapse>
          <el-collapse-item v-for="season in course.seasons" :key="season.id" :title="season.title" :name="season.id">
            <div v-for="episode in season.episodes" :key="episode.id" class="episode-row">
              <div><strong>{{ episode.title }}</strong><span>{{ episode.info }}</span></div>
              <div>
                <el-button text type="primary" @click="currentEpisode = episode">试看</el-button>
                <el-button text :type="followMap[episode.id] ? 'warning' : 'primary'" @click="toggleFollow(episode)">
                  {{ followMap[episode.id] ? '取消收藏' : '收藏' }}
                </el-button>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="payDialog" title="扫码支付" width="360px" @close="cancelPay">
      <div class="qr-box"><img :src="qrCode" alt="支付二维码"><span>订单号：{{ paySn }}</span></div>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.detail-head { display:grid; grid-template-columns:360px minmax(0,1fr); gap:18px; }
.detail-head img { width:100%; aspect-ratio:16/9; object-fit:cover; border-radius:8px; }
.detail-info { display:grid; gap:12px; align-content:start; }
.detail-info h2 { margin:0; color:var(--ml-text); font-size:24px; }
.detail-info p { margin:0; color:var(--ml-muted); line-height:1.7; }
.detail-meta,.detail-actions { display:flex; align-items:center; gap:12px; flex-wrap:wrap; }
.block-title { margin:0 0 12px; color:var(--ml-text); }
.summary-img { width:100%; max-height:520px; object-fit:contain; background:var(--ml-surface-soft); border-radius:8px; }
.episode-row { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:10px 0; border-bottom:1px solid var(--ml-border); }
.episode-row span { display:block; margin-top:4px; color:var(--ml-muted); }
.qr-box { display:grid; justify-items:center; gap:10px; }
.qr-box img { width:220px; height:220px; }
@media (max-width:900px) { .detail-head { grid-template-columns:1fr; } }
</style>
