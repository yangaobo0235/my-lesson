<script setup>
import {onMounted, reactive, ref} from 'vue';
import {useRouter} from 'vue-router';
import {studentApi} from '../../api/student.js';
import {getResponseData} from '../../request/index.js';
import {MINIO_COURSE_COVER} from '../../const/index.js';
import {readJsonStorage} from '../../util/auth.js';
import './student.scss';

const router = useRouter();
const loginUser = readJsonStorage('loginUser', {});
const courses = ref([]);
const pageInfo = reactive({pageNum: 1, pageSize: 20, total: 0});

async function page() {
  const data = getResponseData(await studentApi.orders({
    pageNum: pageInfo.pageNum,
    pageSize: pageInfo.pageSize,
    fkUserId: loginUser.id,
    status: 1
  })) || {};
  const items = [];
  (data.records || []).forEach(order => (order.orderDetails || []).forEach(detail => {
    items.push({
      orderId: order.id,
      orderSn: order.sn,
      courseId: detail.fkCourseId,
      title: detail.courseTitle,
      cover: detail.courseCover,
      price: detail.coursePrice
    });
  }));
  courses.value = items;
}

onMounted(page);
</script>

<template>
  <div class="student-page">
    <header class="student-header">
      <div><h2>已购课程</h2><p>已支付课程会出现在这里，可直接进入播放器学习</p></div>
    </header>
    <div v-if="courses.length" class="course-grid">
      <article v-for="course in courses" :key="course.orderId + '-' + course.courseId" class="course-card">
        <img class="course-cover" :src="MINIO_COURSE_COVER(course.cover)" :alt="course.title">
        <div class="course-body">
          <h3>{{ course.title }}</h3>
          <div class="course-meta"><span>{{ course.orderSn }}</span><strong class="price">￥{{ course.price }}</strong></div>
          <el-button type="primary" @click="router.push(`/student/player/${course.courseId}`)">开始学习</el-button>
        </div>
      </article>
    </div>
    <el-empty v-else class="empty-panel" description="暂无已购课程">
      <el-button type="primary" @click="router.push('/student/courses')">去选课</el-button>
    </el-empty>
  </div>
</template>
