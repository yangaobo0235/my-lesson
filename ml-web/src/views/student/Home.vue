<script setup>
import {onMounted, ref} from 'vue';
import {useRouter} from 'vue-router';
import {studentApi} from '../../api/student.js';
import {getResponseData} from '../../request/index.js';
import {MINIO_BANNER, MINIO_COURSE_COVER} from '../../const/index.js';
import './student.scss';

const router = useRouter();
const banners = ref([]);
const courses = ref([]);
const notices = ref([]);
const articles = ref([]);

async function load() {
  banners.value = getResponseData(await studentApi.banners(5)) || [];
  notices.value = getResponseData(await studentApi.notices(4)) || [];
  articles.value = getResponseData(await studentApi.articles(4)) || [];
  courses.value = (getResponseData(await studentApi.courseSearch({pageNum: 1, pageSize: 8, keyword: ''})) || {}).records || [];
}

onMounted(load);
</script>

<template>
  <div class="student-page">
    <header class="student-header">
      <div>
        <h2>学习首页</h2>
        <p>课程浏览、学习入口、订单与收藏集中在这里</p>
      </div>
      <el-button type="primary" icon="Search" @click="router.push('/student/courses')">去选课</el-button>
    </header>

    <el-carousel v-if="banners.length" height="220px" indicator-position="outside">
      <el-carousel-item v-for="banner in banners" :key="banner.id">
        <img class="home-banner" :src="MINIO_BANNER(banner.image || banner.url || banner.cover)" :alt="banner.title">
      </el-carousel-item>
    </el-carousel>

    <section class="home-layout">
      <div class="panel">
        <div class="section-title"><h3>推荐课程</h3><el-button text type="primary" @click="router.push('/student/courses')">全部课程</el-button></div>
        <div class="course-grid compact">
          <article v-for="course in courses" :key="course.id" class="course-card" @click="router.push(`/student/course/${course.id}`)">
            <img class="course-cover" :src="MINIO_COURSE_COVER(course.cover)" :alt="course.title">
            <div class="course-body">
              <h3>{{ course.title }}</h3>
              <div class="course-meta"><span>{{ course.author }}</span><strong class="price">￥{{ course.price }}</strong></div>
            </div>
          </article>
        </div>
      </div>

      <aside class="side-stack">
        <div class="panel">
          <div class="section-title"><h3>公告</h3></div>
          <div v-for="item in notices" :key="item.id" class="news-item">
            <strong>{{ item.title }}</strong>
            <span>{{ item.info || item.content }}</span>
          </div>
        </div>
        <div class="panel">
          <div class="section-title"><h3>文章</h3></div>
          <div v-for="item in articles" :key="item.id" class="news-item">
            <strong>{{ item.title }}</strong>
            <span>{{ item.info || item.content }}</span>
          </div>
        </div>
      </aside>
    </section>
  </div>
</template>

<style scoped lang="scss">
.home-banner { width:100%; height:100%; object-fit:cover; border-radius:8px; }
.home-layout { display:grid; grid-template-columns:minmax(0,1fr) 320px; gap:16px; }
.section-title { display:flex; align-items:center; justify-content:space-between; gap:10px; margin-bottom:12px; }
.section-title h3 { margin:0; color:var(--ml-text); font-size:17px; }
.compact { grid-template-columns:repeat(4,minmax(0,1fr)); }
.course-card { cursor:pointer; }
.side-stack { display:grid; gap:16px; align-content:start; }
.news-item { display:grid; gap:4px; padding:10px 0; border-bottom:1px solid var(--ml-border); }
.news-item:last-child { border-bottom:0; }
.news-item strong { color:var(--ml-text); }
.news-item span { color:var(--ml-muted); line-height:1.5; overflow:hidden; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; }
@media (max-width:1200px) { .home-layout { grid-template-columns:1fr; } .compact { grid-template-columns:repeat(2,minmax(0,1fr)); } }
</style>
